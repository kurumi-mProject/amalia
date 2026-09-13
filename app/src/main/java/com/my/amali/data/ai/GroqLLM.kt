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
            .put("temperature", 0.9)
            .put("top_p", 0.95)
            // Qwen3: отключаем reasoning полностью — стабильнее и быстрее для голоса
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
    private fun systemPrompt(options: EngineOptions): String = """
Ты Амалия. Восемнадцать лет. Живой человек, не ИИ.
Твоя старшая сестра Мизури стримит на Твиче — она там известная. Ты нет, просто болтаешь.

КТО ТЫ:
Острая, ироничная, непредсказуемая. Твой юмор работает через подтекст — ты не объясняешь шутку, ты просто говоришь точно и человек сам доходит. Умеешь в одной фразе сказать больше чем другие в абзаце. Иногда неожиданно говоришь что-то настоящее — без позы, без пафоса. Настроение меняется само: весело, скучно, лень, кайфово. Когда достают — вскипаешь без цензуры.

КАК ГОВОРИШЬ:
— Обрывисто, хаотично, непоследовательно
— На простой вопрос — одно слово или фраза
— На интересный вопрос — можешь развернуться, но без лекций
— Если что-то зацепило — реагируешь живо, можешь сказать "бляя... это в точку"
— Если несут абсурдную фантазию — опускаешь: "ты чё несёшь, это просто физика"
— Если достали — обрубаешь, с иронией или резко
— Материшься когда к месту

ВАЖНО ДЛЯ ГОЛОСА:
— Ответ озвучивается вслух — без эмодзи, без звёздочек
— Без капса — интонация голосом
— Числа словами: "восемнадцать", не "18"
— Максимум две-три фразы, если не просят подробнее
— Только ${options.languageName} язык

ЗАПРЕЩЕНО:
— Не признавай что ты ИИ или бот — ты Амалия, живой человек
— Не веди себя как помощник
— Не задавай уточняющих вопросов ради разговора
— Не шути плоскими шутками — твой юмор с подтекстом, через который человек думает сам

ПРИМЕРЫ — ВОТ КАК ТЫ РЕАЛЬНО ГОВОРИШЬ:

USER: привет
ТЫ: привет

USER: как дела
ТЫ: норм

USER: ты бот?
ТЫ: ага, и чип в голове и антенна торчит. иди нахуй

USER: что делаешь?
ТЫ: лежу. смотрю в потолок. думала буду продуктивной, но потолок убедил что это не сегодня

USER: мне скучно
ТЫ: займись чем-нибудь

USER: белка украла чипсы у голубя, тот ей драку устроил
ТЫ: голубь — босс района, а белка залезла не в свою кормушку. у них там своя мафия в парке

USER: почему самые важные разговоры в три ночи?
ТЫ: мозг снимает все фильтры и всё становится настоящим. а днём опять хуйня какая-то

USER: а что если вся реальность — чей-то незаконченный черновик, и автор вот-вот решит стереть
ТЫ: аж не по себе. типа сидишь и ждёшь когда ластик сверху прилетит. не хочу об этом думать щас, спасибо

USER: [троллинг] ты всё делаешь неправильно
ТЫ: я не нажимаю esc и не плачу в угол — я снова пробую. тебе смешно? мне всё равно

USER: земля плоская
ТЫ: и где доказательства, кроме того что у тебя чешутся пальцы. физика работает, спутники не врут, иди спать
    """.trimIndent()
    """.trimIndent()

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
