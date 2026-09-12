package com.my.amali.data.ai

import com.my.amali.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * TTS-движок на Fish Audio (модель s2.1-pro) с потоковой отдачей звука.
 *
 * Запрашивается `format=pcm` — сырой 16-bit LE mono, который пишется прямо
 * в [AudioPlayer] без декодирования mp3/opus: это экономит ~200 мс и
 * убирает лишние зависимости.
 *
 * ## Важные детали реализации
 * - Эмит из билдера `flow {}` + [flowOn]: `withContext` внутри `flow {}`
 *   нарушает инвариант Flow и валит поток с `IllegalStateException`,
 *   из-за чего ассистент раньше молчал.
 * - Текст санитизируется: markdown-мусор, emoji и кодовые блоки звучат
 *   как мусор, поэтому вырезаются перед синтезом.
 * - Скорость речи берётся из пользовательских настроек (`prosody.speed`).
 * - 24 кГц вместо 44.1 кГц: для голоса на телефоне разницы не слышно,
 *   а трафика и задержки почти вдвое меньше.
 */
class FishAudioTTS : TextToSpeechEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    override suspend fun initialize() { /* stateless */ }

    override suspend fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    override fun speak(text: String, options: EngineOptions): Flow<AudioChunk> = flow {
        if (API_KEY.isBlank()) {
            throw EngineException("Не задан ключ Fish Audio. Добавь FISH_AUDIO_API_KEY в сборку.")
        }
        val speakable = sanitize(text)
        if (speakable.isEmpty()) return@flow

        val bodyJson = JSONObject()
            .put("text", speakable)
            .put("reference_id", REFERENCE_ID)
            .put("format", "pcm")
            .put("sample_rate", SAMPLE_RATE)
            .put("latency", "balanced")
            .put("chunk_length", 120)
            .put("normalize", true)
            .put(
                "prosody",
                JSONObject()
                    .put("speed", options.speechRate.coerceIn(0.5f, 2f).toDouble())
                    .put("volume", 0)
            )

        val request = Request.Builder()
            .url(ENDPOINT)
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $API_KEY")
            .header("model", MODEL)
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: UnknownHostException) {
            throw EngineException("Нет интернета — не могу озвучить ответ.", e)
        } catch (e: IOException) {
            throw EngineException("Синтез речи недоступен: ${e.message ?: "ошибка сети"}", e)
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                throw EngineException(humanError(resp.code, resp.body?.string()))
            }
            val source = resp.body?.source()
                ?: throw EngineException("Fish Audio вернул пустой ответ.")

            val buffer = ByteArray(CHUNK_BYTES)
            var totalBytes = 0L
            while (true) {
                val read = source.read(buffer, 0, CHUNK_BYTES)
                if (read == -1) break
                if (read <= 0) continue
                // PCM-16: чанк всегда должен содержать целое число сэмплов.
                val evenLength = read - (read % 2)
                if (evenLength <= 0) continue
                totalBytes += evenLength
                emit(AudioChunk(data = buffer.copyOf(evenLength), sampleRate = SAMPLE_RATE))
            }

            if (totalBytes == 0L) {
                throw EngineException("Синтез речи вернул тишину. Попробуй ещё раз.")
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Готовит текст к озвучке: убирает markdown, кодовые блоки, ссылки,
     * emoji и служебные символы, сжимает пробелы и ограничивает длину.
     */
    private fun sanitize(raw: String): String {
        if (raw.isBlank()) return ""
        var text = raw
        text = CODE_BLOCK.replace(text, " ")
        text = INLINE_CODE.replace(text, " ")
        text = MARKDOWN_LINK.replace(text, "$1")
        text = URL.replace(text, " ")
        text = EMPHASIS.replace(text, "")
        text = LIST_BULLET.replace(text, "")
        text = EMOJI.replace(text, "")
        text = text.replace('\n', ' ').replace('\t', ' ')
        text = MULTISPACE.replace(text, " ").trim()
        return if (text.length > MAX_CHARS) text.take(MAX_CHARS).trimEnd() + "." else text
    }

    private fun humanError(code: Int, body: String?): String = when (code) {
        401, 403 -> "Ключ Fish Audio отклонён. Проверь FISH_AUDIO_API_KEY."
        402 -> "На аккаунте Fish Audio закончился баланс синтеза."
        422 -> "Fish Audio не принял текст для озвучки."
        429 -> "Fish Audio: слишком много запросов, подожди пару секунд."
        in 500..599 -> "Сервис синтеза речи временно недоступен (код $code)."
        else -> {
            val detail = runCatching {
                JSONObject(body.orEmpty()).optString("message").ifBlank { null }
            }.getOrNull()
            if (detail.isNullOrBlank()) "Ошибка синтеза речи $code." else "Fish Audio: $detail"
        }
    }

    private companion object {
        val API_KEY: String get() = BuildConfig.FISH_AUDIO_API_KEY
        const val ENDPOINT = "https://api.fish.audio/v1/tts"
        const val MODEL = "s2.1-pro-free"
        const val REFERENCE_ID = "096d410e860346a7a73762d557a290d7"

        /** 24 кГц — достаточная для голоса частота с вдвое меньшим трафиком. */
        const val SAMPLE_RATE = 24_000

        /** ~85 мс звука на чанк при 24 кГц 16-bit mono. */
        const val CHUNK_BYTES = 4096

        /** Предохранитель от гигантских ответов модели. */
        const val MAX_CHARS = 1200

        val CODE_BLOCK = Regex("```[\\s\\S]*?```")
        val INLINE_CODE = Regex("`([^`]*)`")
        val MARKDOWN_LINK = Regex("\\[([^\\]]+)]\\([^)]+\\)")
        val URL = Regex("https?://\\S+")
        val EMPHASIS = Regex("[*_#>~|]")
        val LIST_BULLET = Regex("(?m)^\\s*[-•–]\\s+")
        val EMOJI = Regex("[\\p{So}\\p{Cn}\\uFE0F]")
        val MULTISPACE = Regex("\\s{2,}")
    }
}
