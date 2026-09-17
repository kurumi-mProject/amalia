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
 * поэтому первый звук слышен уже через ~0.5–1 с после отправки запроса.
 *
 * ═══════════════════════════════════════════════════════════
 *  МОДЕЛЬ: drama-3-preview
 * ═══════════════════════════════════════════════════════════
 *
 * Доступ по гранту программы Fish Audio для стартапов. Что это меняет
 * по сравнению с прежней бесплатной моделью `s2.1-pro-free`:
 *
 *  — **скорость**: ~0.5–1.2 с до первого байта против 2.4–3 с в прогретом
 *    состоянии и ~38 с на холодном старте. Для голосового ассистента это
 *    разница между «живым собеседником» и «сломанным приложением»;
 *  — **стабильность**: модель не встаёт в общую очередь бесплатного тарифа,
 *    поэтому пауз в 30+ секунд больше нет;
 *  — **голос**: тот же `reference_id` (голос Амалии) поддерживается наравне
 *    с семейством S2 — это прямо указано в схеме API: мультиспикерный режим
 *    доступен для `s2-pro`, `s2.1-pro`, `s2.1-pro-free` и `drama-3-preview`.
 *
 * Параметры запроса сверены с официальной схемой (`/openapi.json`):
 *  — `latency`: `low` — «наименьшая задержка» (есть ещё `balanced` и `normal`);
 *  — `sample_rate`: 24000 Гц; при `null` сервис отдал бы 44100, и звук
 *    играл бы быстрее и выше тоном;
 *  — `prosody.speed` — реально поддержан, поэтому ползунок «скорость речи»
 *    в настройках влияет на голос, а не висит декорацией;
 *  — `mp3_bitrate`/`opus_bitrate` не отправляются: они действуют только для
 *    mp3/opus, а мы просим PCM.
 */
class FishAudioTTS : TextToSpeechEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // Таймаут чтения — не «сколько синтезируется фраза», а «сколько
        // сервис может молчать между чанками». Аудио идёт потоком, и пауза
        // в 15 секунд означает, что синтез встал: держать поток заблокированным
        // незачем. На новой модели (drama-3-preview) ответ приходит за ~1 с,
        // поэтому 15 с — очень щедрый потолок: он нужен только на случай,
        // если сервис начал отвечать, но замолчал посреди фразы.
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
            put("sample_rate", SAMPLE_RATE)
            put("normalize", true)
            // `low` — минимальная задержка (в схеме API есть ещё balanced/normal).
            // Для ассистента важнее начать звучать, чем выиграть доли в качестве.
            put("latency", "low")
            put("prosody", JSONObject().apply {
                // Скорость речи из настроек: сервис применяет её к синтезу,
                // поэтому ползунок «скорость» работает по-настоящему.
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

        /**
         * Модель голоса Амалии.
         *
         * `drama-3-preview` — доступ по гранту Fish Audio для стартапов.
         * Замеры на живом ключе: 0.5–1.2 с до первого байта, тогда как
         * бесплатная `s2.1-pro-free` отвечала 2.4–3 с в прогретом состоянии
         * и до 38 с после паузы.
         */
        const val MODEL = "drama-3-preview"

        /** Голос Амалии из библиотеки Fish Audio. */
        const val REFERENCE_ID = "096d410e860346a7a73762d557a290d7"

        /**
         * Частота PCM. Задаётся явно: при `sample_rate: null` сервис отдаёт
         * 44100 Гц, а [AudioPlayer] строит дорожку по частоте первого чанка —
         * то есть звук играл бы быстрее и выше тоном.
         */
        const val SAMPLE_RATE = 24_000 // PCM 24кГц 16-bit LE mono

        // ~20мс аудио на чанк при 24кГц: 24000 сэмплов/с × 0.02с × 2 байта = 960 байт
        const val CHUNK_BYTES = 960

        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
