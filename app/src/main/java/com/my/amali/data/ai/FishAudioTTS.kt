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
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * TTS через Fish Audio HTTP API (/v1/tts).
 *
 * Архитектура: LLM генерирует полный текст → одним HTTP запросом отдаём
 * его в Fish Audio → получаем PCM 24кГц стримом → эмитим чанками в AudioPlayer.
 *
 * Никакого WebSocket, никакой msgpack — простой HTTP streaming response.
 * Fish Audio отдаёт raw PCM 16-bit LE mono 24kHz по chunked transfer encoding,
 * поэтому первый звук слышен уже через ~300-500ms после отправки запроса.
 */
class FishAudioTTS : TextToSpeechEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // Таймаут чтения — не «сколько синтезируется фраза», а «сколько
        // сервис может молчать между чанками». Аудио идёт потоком, и пауза
        // в 15 секунд означает, что синтез встал: 60 секунд ожидания в этом
        // случае — это минута молчащего ассистента и заблокированный поток,
        // который незачем держать. Ровно на этом дефекте (бесплатная модель
        // отвечает ~38 с) голос пропадал целиком — теперь поток освобождается
        // через 15 с, а [ResilientTtsEngine] успевает переключиться на
        // системный голос уже через 9 с.
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun initialize() {}

    override suspend fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    /**
     * Синтезирует [text] через Fish Audio HTTP API.
     * Возвращает поток [AudioChunk] (PCM 24кГц) по мере получения данных.
     */
    override fun speak(text: String, options: EngineOptions): Flow<AudioChunk> = flow {
        if (API_KEY.isBlank()) {
            throw EngineException("Не задан ключ Fish Audio. Добавь FISH_AUDIO_API_KEY в сборку.")
        }
        val clean = text.trim()
        if (clean.isEmpty()) return@flow

        val body = JSONObject().apply {
            put("text", clean)
            put("reference_id", REFERENCE_ID)
            put("format", "pcm")
            put("mp3_bitrate", 128)
            put("opus_bitrate", -1000)
            put("sample_rate", SAMPLE_RATE)
            put("normalize", true)
            // «low» вместо «balanced»: измеренная разница на живом ключе —
            // 2.4 с против 3.0 с до первого байта. Для голосового ассистента,
            // где ответ начинается сразу после генерации текста, эти полсекунды
            // заметны, а качество синтеза на слух не меняется.
            put("latency", "low")
            put("prosody", JSONObject().apply {
                put("speed", options.speechRate.coerceIn(0.5f, 2f).toDouble())
                put("volume", 0)
            })
        }

        val request = Request.Builder()
            .url(HTTP_ENDPOINT)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer $API_KEY")
            .header("model", MODEL)
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: Exception) {
            throw EngineException("Fish Audio недоступен: ${e.message ?: "ошибка сети"}", e)
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                val errorBody = runCatching { resp.body?.string() }.getOrNull()
                val detail = runCatching {
                    JSONObject(errorBody.orEmpty()).optString("message")
                }.getOrNull()
                throw EngineException(
                    if (!detail.isNullOrBlank()) "Fish Audio: $detail"
                    else "Fish Audio ошибка ${resp.code}"
                )
            }

            val source = resp.body?.source()
                ?: throw EngineException("Fish Audio вернул пустой ответ.")

            // Читаем PCM стримом чанками по ~20мс (960 сэмплов × 2 байта = 1920 байт)
            val buffer = ByteArray(CHUNK_BYTES)
            while (!source.exhausted()) {
                val read = source.read(buffer)
                if (read <= 0) break
                val chunk = if (read == buffer.size) buffer.copyOf() else buffer.copyOf(read)
                emit(AudioChunk(data = chunk, sampleRate = SAMPLE_RATE))
            }
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        val API_KEY: String get() = BuildConfig.FISH_AUDIO_API_KEY
        const val HTTP_ENDPOINT = "https://api.fish.audio/v1/tts"
        const val MODEL = "s2.1-pro-free"
        const val REFERENCE_ID = "096d410e860346a7a73762d557a290d7"
        const val SAMPLE_RATE = 24_000 // PCM 24кГц 16-bit LE mono

        // ~20мс аудио на чанк при 24кГц: 24000 сэмплов/с × 0.02с × 2 байта = 960 байт
        const val CHUNK_BYTES = 960

        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
