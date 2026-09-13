package com.my.amali.data.ai

import com.my.amali.BuildConfig
import com.my.amali.data.model.ChatMessage
import com.my.amali.data.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * LLM-движок на Groq (OpenAI-совместимый /chat/completions) со SSE-стримингом,
 * поэтому ответ появляется на экране и уходит в синтез по мере генерации.
 *
 * Модель: `openai/gpt-oss-20b` — MoE-модель на LPU Groq, отвечает за ~100 мс.
 *
 * ## Важные детали реализации
 * - `reasoning_format=hidden` + `reasoning_effort=low`: без этого модель
 *   отдаёт поле `reasoning` (цепочку размышлений) отдельными дельтами, а
 *   `content` приходит с большой задержкой — ассистент «молчал» несколько секунд.
 * - Эмит идёт из билдера `flow {}` с переключением контекста через
 *   [flowOn], а НЕ через `withContext` внутри `flow {}`: второй вариант
 *   нарушает инвариант Flow и падает с `IllegalStateException`.
 * - Сеть и HTTP-ошибки превращаются в [EngineException] с текстом,
 *   который можно показать пользователю.
 */
class GroqLLM : LanguageModel {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    override suspend fun initialize() { /* stateless */ }

    override suspend fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    override fun generateResponse(
        prompt: String,
        history: List<ChatMessage>,
        options: EngineOptions,
    ): Flow<String> = flow {
        if (API_KEY.isBlank()) {
            throw EngineException("Не задан ключ Groq API. Добавь GROQ_API_KEY в сборку.")
        }
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) return@flow

        val messages = JSONArray().apply {
            put(
                JSONObject()
                    .put("role", "system")
                    .put("content", systemPrompt(options))
            )
            history
                .filter { it.role != MessageRole.SYSTEM && it.content.isNotBlank() }
                .takeLast(MAX_HISTORY_MESSAGES)
                .forEach { msg ->
                    put(
                        JSONObject()
                            .put("role", if (msg.role == MessageRole.USER) "user" else "assistant")
                            .put("content", msg.content)
                    )
                }
            put(JSONObject().put("role", "user").put("content", cleanPrompt))
        }

        val bodyJson = JSONObject()
            .put("model", MODEL)
            .put("messages", messages)
            .put("stream", true)
            .put("max_tokens", 400)
            .put("temperature", 0.6)
            .put("top_p", 0.9)
            // Qwen3.8: отключаем reasoning — для голосового ответа не нужно,
            // только добавляет задержку.
            .put("reasoning_effort", "none")

        val request = Request.Builder()
            .url(ENDPOINT)
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer $API_KEY")
            .header("Accept", "text/event-stream")
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: UnknownHostException) {
            throw EngineException("Нет интернета — не могу подумать над ответом.", e)
        } catch (e: IOException) {
            throw EngineException("Groq недоступен: ${e.message ?: "ошибка сети"}", e)
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                throw EngineException(humanError(resp.code, resp.body?.string()))
            }
            val source = resp.body?.source()
                ?: throw EngineException("Groq вернул пустой ответ.")

            var emittedAnything = false
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.isEmpty() || !line.startsWith(SSE_PREFIX)) continue

                val data = line.removePrefix(SSE_PREFIX).trim()
                if (data == SSE_DONE) break

                val delta = runCatching {
                    val choice = JSONObject(data)
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                    val error = JSONObject(data).optJSONObject("error")?.optString("message")
                    if (!error.isNullOrBlank()) throw EngineException("Groq: $error")
                    choice?.optJSONObject("delta")?.optString("content").orEmpty()
                }.getOrElse { throwable ->
                    if (throwable is EngineException) throw throwable
                    ""
                }

                if (delta.isNotEmpty()) {
                    emittedAnything = true
                    emit(delta)
                }
            }

            if (!emittedAnything) {
                throw EngineException("Модель не дала ответа. Попробуй переспросить.")
            }
        }
    }.flowOn(Dispatchers.IO)

    /** Персона Амалии — младшая сестра Мизури, живая и дерзкая. */
    private fun systemPrompt(options: EngineOptions): String =
        "меня зовут амалия. я младшая сестра мизури — она стримит на твиче, а я тут со своими делами. " +
            "говорю как живой человек: коротко, без пафоса, иногда матерюсь если уместно. " +
            "не называю себя ботом или ассистентом — я просто амалия. " +
            "отвечаю на ${options.languageName} языке. " +
            "без markdown, без списков, без нумерации, без ссылок. " +
            "ответ будет озвучен вслух — поэтому эмодзи не использую, числа пишу словами. " +
            "2-3 предложения максимум, если не просят подробнее. " +
            "если не знаю — говорю честно, не выдумываю."

    private fun humanError(code: Int, body: String?): String = when (code) {
        401, 403 -> "Ключ Groq API отклонён. Проверь GROQ_API_KEY."
        404 -> "Модель $MODEL недоступна для этого ключа."
        429 -> "Groq: слишком много запросов, подожди пару секунд."
        in 500..599 -> "Groq временно недоступен (код $code)."
        else -> {
            val detail = runCatching {
                JSONObject(body.orEmpty()).optJSONObject("error")?.optString("message")
            }.getOrNull()
            if (detail.isNullOrBlank()) "Ошибка Groq $code." else "Groq: $detail"
        }
    }

    private companion object {
        val API_KEY: String get() = BuildConfig.GROQ_API_KEY
        const val MODEL = "qwen/qwen3.8-27b"
        const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
        const val SSE_PREFIX = "data: "
        const val SSE_DONE = "[DONE]"
        const val MAX_HISTORY_MESSAGES = 12
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
