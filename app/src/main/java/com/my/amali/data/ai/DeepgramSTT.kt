package com.my.amali.data.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.my.amali.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

/**
 * Движок распознавания речи на Deepgram nova-2 (WebSocket, потоковый режим).
 *
 * ## Как определяется конец фразы
 * Поток закрывается сам, когда срабатывает любое из условий:
 *  1. Deepgram прислал `speech_final=true` (endpointing 700 мс тишины);
 *  2. после последнего финального сегмента прошло [SILENCE_AFTER_SPEECH_MS]
 *     тишины по локальному RMS — страховка, если сервер медлит;
 *  3. пользователь молчал [NO_SPEECH_TIMEOUT_MS] с самого начала — тогда
 *     фраза считается пустой и экран возвращается в покой;
 *  4. достигнут жёсткий предел [MAX_SESSION_MS] — защита от «вечного» микрофона.
 *
 * Долгая речь даёт несколько [SttEvent.Final]: их склеивает вызывающая
 * сторона, поэтому длинные фразы больше не теряют начало (старая версия
 * возвращала только последний сегмент).
 *
 * Кроме текста движок отдаёт [SttEvent.Level] — мгновенную громкость
 * микрофона для живой анимации волны.
 */
class DeepgramSTT(private val context: Context) : SpeechToTextEngine {

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(12, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    override suspend fun initialize() { /* соединение живёт только во время сессии */ }

    override suspend fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    override fun transcribe(options: EngineOptions): Flow<SttEvent> = callbackFlow {
        if (API_KEY.isBlank()) {
            throw EngineException("Не задан ключ Deepgram. Добавь DEEPGRAM_API_KEY в сборку.")
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            throw EngineException("Нужен доступ к микрофону, чтобы я могла тебя слышать.")
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            throw EngineException("Микрофон недоступен на этом устройстве.")
        }
        val readSize = minBuffer.coerceAtLeast(READ_CHUNK_BYTES)

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                readSize * 4,
            )
        } catch (e: Exception) {
            throw EngineException("Не удалось открыть микрофон: ${e.message ?: "отказано"}", e)
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw EngineException("Микрофон занят другим приложением.")
        }

        // ── Состояние сессии ─────────────────────────────────────────────
        val stopRequested = AtomicBoolean(false)
        val gotAnyFinal = AtomicBoolean(false)
        val lastVoiceAt = AtomicLong(System.currentTimeMillis())
        val sessionStart = System.currentTimeMillis()
        val socketReady = AtomicBoolean(false)

        val url = buildString {
            append("wss://api.deepgram.com/v1/listen")
            append("?model=nova-2")
            append("&language=").append(options.languageCode)
            append("&punctuate=true")
            append("&smart_format=true")
            append("&interim_results=true")
            append("&encoding=linear16")
            append("&channels=1")
            append("&sample_rate=").append(SAMPLE_RATE)
            append("&endpointing=700")
            append("&vad_events=true")
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Token $API_KEY")
            .build()

        val socket = client.newWebSocket(
            request,
            object : WebSocketListener() {

                override fun onOpen(webSocket: WebSocket, response: Response) {
                    socketReady.set(true)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    runCatching {
                        val json = JSONObject(text)
                        when (json.optString("type")) {
                            "SpeechStarted" -> {
                                lastVoiceAt.set(System.currentTimeMillis())
                                return@runCatching
                            }
                            "Metadata" -> return@runCatching
                        }

                        val alternative = json
                            .optJSONObject("channel")
                            ?.optJSONArray("alternatives")
                            ?.optJSONObject(0)
                        val transcript = alternative?.optString("transcript").orEmpty().trim()
                        val isFinal = json.optBoolean("is_final", false)
                        val speechFinal = json.optBoolean("speech_final", false)

                        if (transcript.isNotEmpty()) {
                            lastVoiceAt.set(System.currentTimeMillis())
                            if (isFinal) {
                                gotAnyFinal.set(true)
                                trySend(SttEvent.Final(transcript))
                            } else {
                                trySend(SttEvent.Partial(transcript))
                            }
                        }

                        // Конец высказывания по мнению сервера — закрываем сессию.
                        if (speechFinal && gotAnyFinal.get()) {
                            stopRequested.set(true)
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    stopRequested.set(true)
                    val code = response?.code
                    val message = when {
                        code == 401 || code == 403 ->
                            "Ключ Deepgram отклонён. Проверь DEEPGRAM_API_KEY."
                        code != null -> "Распознавание речи недоступно (код $code)."
                        else -> "Нет связи с сервисом распознавания речи."
                    }
                    close(EngineException(message, t))
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    stopRequested.set(true)
                    channel.close()
                }
            },
        )

        // ── Поток захвата микрофона ──────────────────────────────────────
        val micJob = launch(Dispatchers.IO) {
            val buffer = ByteArray(readSize)
            try {
                recorder.startRecording()
                if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    close(EngineException("Микрофон не запустился. Закрой другие приложения со звуком."))
                    return@launch
                }

                while (isActive && !stopRequested.get()) {
                    val read = recorder.read(buffer, 0, readSize)
                    if (read <= 0) continue

                    if (socketReady.get()) {
                        socket.send(ByteString.of(buffer, 0, read))
                    }

                    val level = normalizedLevel(buffer, read)
                    trySend(SttEvent.Level(level))
                    if (level > VOICE_LEVEL_THRESHOLD) {
                        lastVoiceAt.set(System.currentTimeMillis())
                    }

                    val now = System.currentTimeMillis()
                    val silenceMs = now - lastVoiceAt.get()
                    val stop = when {
                        // Пользователь договорил: был финальный текст и уже тихо.
                        gotAnyFinal.get() && silenceMs > SILENCE_AFTER_SPEECH_MS -> true
                        // Никто ничего не сказал — не держим микрофон открытым.
                        !gotAnyFinal.get() && silenceMs > NO_SPEECH_TIMEOUT_MS -> true
                        // Жёсткий предел длительности одной фразы.
                        now - sessionStart > MAX_SESSION_MS -> true
                        else -> false
                    }
                    if (stop) stopRequested.set(true)
                }
            } catch (e: SecurityException) {
                close(EngineException("Доступ к микрофону отозван системой.", e))
                return@launch
            } catch (e: IllegalStateException) {
                close(EngineException("Микрофон занят другим приложением.", e))
                return@launch
            } finally {
                runCatching {
                    if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        recorder.stop()
                    }
                }
            }

            // Просим Deepgram отдать последний сегмент и закрыть соединение.
            runCatching { socket.send(CLOSE_STREAM_FRAME) }
            runCatching { socket.close(1000, "utterance complete") }
            // Если сервер не ответит закрытием — закрываем поток сами.
            kotlinx.coroutines.delay(FLUSH_GRACE_MS)
            channel.close()
        }

        awaitClose {
            stopRequested.set(true)
            micJob.cancel()
            runCatching { socket.close(1000, "cancelled") }
            runCatching { socket.cancel() }
            runCatching { recorder.release() }
        }
    }

    /**
     * Нормализованная громкость PCM-16 буфера в диапазоне 0..1.
     * Масштаб подобран так, чтобы обычная речь давала 0.3–0.8.
     */
    private fun normalizedLevel(buffer: ByteArray, length: Int): Float {
        val samples = length / 2
        if (samples == 0) return 0f
        var sum = 0.0
        val step = if (samples > 512) samples / 512 else 1
        var counted = 0
        var i = 0
        while (i < samples) {
            val low = buffer[i * 2].toInt() and 0xFF
            val high = buffer[i * 2 + 1].toInt()
            val sample = ((high shl 8) or low).toShort().toInt()
            sum += sample.toDouble() * sample.toDouble()
            counted++
            i += step
        }
        if (counted == 0) return 0f
        val rms = sqrt(sum / counted)
        return (rms / VOICE_RMS_FULL_SCALE).coerceIn(0.0, 1.0).toFloat()
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val READ_CHUNK_BYTES = 3_200 // 100 мс при 16 кГц 16-bit mono

        /** Тишина после распознанной фразы, после которой сессия закрывается. */
        const val SILENCE_AFTER_SPEECH_MS = 900L

        /** Если человек так и не заговорил — закрываем микрофон. */
        const val NO_SPEECH_TIMEOUT_MS = 6_000L

        /** Максимальная длительность одной фразы. */
        const val MAX_SESSION_MS = 45_000L

        /** Сколько ждать финальный сегмент после CloseStream. */
        const val FLUSH_GRACE_MS = 1_200L

        /** Порог «это голос, а не шум» для нормализованного уровня. */
        const val VOICE_LEVEL_THRESHOLD = 0.06f

        /** RMS, соответствующий уровню 1.0 (громкая речь у микрофона). */
        const val VOICE_RMS_FULL_SCALE = 6_000.0

        const val CLOSE_STREAM_FRAME = """{"type":"CloseStream"}"""

        val API_KEY: String get() = BuildConfig.DEEPGRAM_API_KEY
    }
}
