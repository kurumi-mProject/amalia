package com.my.amali.data.ai

import com.my.amali.data.model.ChatMessage
import com.my.amali.data.model.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Real LLM engine backed by Groq's OpenAI-compatible chat completions endpoint,
 * using SSE (Server-Sent Events) streaming so tokens are emitted as they arrive.
 *
 * Model: openai/gpt-oss-20b — fast MoE reasoning model running on Groq LPUs.
 *
 * Response tokens are emitted one by one via [generateResponse], allowing the
 * UI to show the assistant answer being "typed out" in real time.
 */
class GroqLLM : LanguageModel {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun initialize() { /* stateless */ }
    override suspend fun close() {}

    override fun generateResponse(prompt: String, history: List<ChatMessage>): Flow<String> = flow {
        // ── Build messages array ──────────────────────────────────────────────
        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", SYSTEM_PROMPT)
            })
            // Keep last 10 turns to avoid blowing up the context budget.
            history.takeLast(10).forEach { msg ->
                put(JSONObject().apply {
                    put("role", if (msg.role == MessageRole.USER) "user" else "assistant")
                    put("content", msg.content)
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }

        val bodyJson = JSONObject().apply {
            put("model", MODEL)
            put("messages", messages)
            put("stream", true)
            put("max_tokens", 512)
            put("temperature", 0.7)
        }

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $API_KEY")
            .header("Content-Type", "application/json")
            .build()

        // ── Read SSE stream on IO dispatcher ─────────────────────────────────
        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: "(no body)"
                    throw RuntimeException("Groq error ${response.code}: $errBody")
                }

                val source = response.body?.source()
                    ?: throw RuntimeException("Groq: empty response body")

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break

                    // SSE lines look like: "data: {...}" or "data: [DONE]"
                    if (!line.startsWith("data: ")) continue

                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break

                    try {
                        val delta = JSONObject(data)
                            .optJSONArray("choices")
                            ?.optJSONObject(0)
                            ?.optJSONObject("delta")
                            ?.optString("content", "")
                            .orEmpty()

                        if (delta.isNotEmpty()) emit(delta)
                    } catch (_: Exception) { /* partial/malformed SSE chunk — skip */ }
                }
            }
        }
    }

    private companion object {
        val API_KEY: String get() = com.my.amali.BuildConfig.GROQ_API_KEY
        const val MODEL   = "openai/gpt-oss-20b"

        /** Persona for the assistant — short, speech-optimised replies in Russian. */
        const val SYSTEM_PROMPT =
            "Ты Амалия — голосовой ассистент на Android. " +
            "Отвечай коротко, по делу, на русском языке. " +
            "Без markdown, без списков, без заголовков — только живая разговорная речь. " +
            "Максимум 2–3 предложения на ответ, если пользователь не просит большего."
    }
}
