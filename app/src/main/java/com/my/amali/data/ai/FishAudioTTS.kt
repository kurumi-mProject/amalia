package com.my.amali.data.ai

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.my.amali.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TTS через Fish Audio WebSocket + OGG/Opus.
 *
 * Архитектура:
 * - Одно WS соединение на весь диалог
 * - Токены от Groq LLM идут напрямую через [sendToken] без накопления предложений
 * - Fish Audio отдаёт OGG/Opus (48кГц) — нативный формат Android
 * - MediaCodec декодирует OGG/Opus → PCM 48кГц
 * - 48кГц = нативная частота Android HAL, ноль ресемплинга
 */
class FishAudioTTS : TextToSpeechEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // бесконечный — WS живёт долго
        .writeTimeout(0, TimeUnit.SECONDS)
        .build()

    // WebSocket сессия
    private var ws: WebSocket? = null
    private val audioChannel = Channel<AudioChunk>(capacity = Channel.UNLIMITED)
    private val isConnected = AtomicBoolean(false)
    private val isStopped = AtomicBoolean(false)

    // Буфер накопленных OGG байт для декодирования
    private val oggBuffer = java.io.ByteArrayOutputStream()

    override suspend fun initialize() { /* stateless */ }

    override suspend fun close() {
        ws?.close(1000, "close")
        ws = null
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    // ── HTTP speak (fallback, используется если WS не открыт) ─────────────
    override fun speak(text: String, options: EngineOptions): Flow<AudioChunk> =
        emptyFlow() // не используется — всё через WS стриминг

    // ── WebSocket стриминг ─────────────────────────────────────────────────

    override val streamingAudio: Flow<AudioChunk>
        get() = audioChannel.receiveAsFlow()

    override suspend fun startStreaming(options: EngineOptions) = withContext(Dispatchers.IO) {
        isStopped.set(false)
        oggBuffer.reset()

        val request = Request.Builder()
            .url(WS_ENDPOINT)
            .header("Authorization", "Bearer $API_KEY")
            .header("model", MODEL)
            .build()

        ws = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected.set(true)
                // Старт сессии
                val startMsg = JSONObject()
                    .put("event", "start")
                    .put("request", JSONObject()
                        .put("text", "")
                        .put("reference_id", REFERENCE_ID)
                        .put("format", "opus")
                        .put("sample_rate", SAMPLE_RATE)
                        .put("opus_bitrate", OPUS_BITRATE)
                        .put("latency", "balanced")
                        .put("chunk_length", 120)
                        .put("normalize", true)
                        .put("prosody", JSONObject()
                            .put("speed", options.speechRate.coerceIn(0.5f, 2f).toDouble())
                            .put("volume", 0)
                        )
                    )
                webSocket.send(startMsg.toString())
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Fish Audio WS использует JSON с base64 или бинарный msgpack
                // Но мы отправляем JSON — ответ тоже JSON или бинарный
                // Проверим: если первый байт — '{', это JSON
                val raw = bytes.toByteArray()
                if (raw.isNotEmpty() && raw[0] == '{'.code.toByte()) {
                    handleJsonMessage(String(raw))
                } else {
                    // Бинарный msgpack — пробуем парсить
                    handleBinaryMessage(raw)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleJsonMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected.set(false)
                audioChannel.close(EngineException("Fish Audio WS ошибка: ${t.message}"))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected.set(false)
                if (!isStopped.get()) {
                    audioChannel.close()
                }
            }
        })
    }

    private fun handleJsonMessage(text: String) {
        try {
            val j = JSONObject(text)
            when (j.optString("event")) {
                "audio" -> {
                    val audioB64 = j.optString("audio")
                    if (audioB64.isNotEmpty()) {
                        val audioBytes = android.util.Base64.decode(audioB64, android.util.Base64.DEFAULT)
                        decodeOpusChunk(audioBytes)
                    }
                }
                "finish" -> {
                    // Декодируем остаток буфера
                    flushOggBuffer()
                    audioChannel.close()
                }
            }
        } catch (_: Exception) {}
    }

    private fun handleBinaryMessage(raw: ByteArray) {
        // Fish Audio может отдавать бинарный OGG напрямую
        decodeOpusChunk(raw)
    }

    private fun decodeOpusChunk(opusData: ByteArray) {
        // Накапливаем OGG данные
        oggBuffer.write(opusData)

        // Пробуем декодировать накопленный буфер
        val accumulated = oggBuffer.toByteArray()
        val pcm = decodeOggOpusToPcm(accumulated)
        if (pcm != null && pcm.isNotEmpty()) {
            oggBuffer.reset()
            audioChannel.trySend(AudioChunk(data = pcm, sampleRate = SAMPLE_RATE))
        }
    }

    private fun flushOggBuffer() {
        val accumulated = oggBuffer.toByteArray()
        if (accumulated.isEmpty()) return
        val pcm = decodeOggOpusToPcm(accumulated) ?: return
        if (pcm.isNotEmpty()) {
            audioChannel.trySend(AudioChunk(data = pcm, sampleRate = SAMPLE_RATE))
        }
        oggBuffer.reset()
    }

    /**
     * Декодирует OGG/Opus байты → PCM 16-bit LE через MediaCodec.
     * Возвращает null если данных недостаточно для декодирования.
     */
    private fun decodeOggOpusToPcm(oggData: ByteArray): ByteArray? {
        if (oggData.size < 64) return null // слишком мало данных

        return try {
            // Записываем во временный файл — MediaExtractor требует файл или URI
            val tmpFile = File.createTempFile("opus_", ".ogg")
            tmpFile.deleteOnExit()

            try {
                FileOutputStream(tmpFile).use { it.write(oggData) }

                val extractor = MediaExtractor()
                extractor.setDataSource(tmpFile.absolutePath)

                // Ищем аудио трек
                var audioTrackIndex = -1
                var format: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("audio/")) {
                        audioTrackIndex = i
                        format = fmt
                        break
                    }
                }

                if (audioTrackIndex < 0 || format == null) return null

                extractor.selectTrack(audioTrackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

                val codec = MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()

                val pcmOutput = java.io.ByteArrayOutputStream()
                val bufferInfo = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false

                while (!outputDone) {
                    // Подаём данные
                    if (!inputDone) {
                        val inputIdx = codec.dequeueInputBuffer(10_000L)
                        if (inputIdx >= 0) {
                            val buf = codec.getInputBuffer(inputIdx)!!
                            buf.clear()
                            val size = extractor.readSampleData(buf, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inputIdx, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inputIdx, 0, size,
                                    extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    // Получаем PCM
                    val outputIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                    if (outputIdx >= 0) {
                        val outBuf = codec.getOutputBuffer(outputIdx)!!
                        val chunk = ByteArray(bufferInfo.size)
                        outBuf.get(chunk)
                        pcmOutput.write(chunk)
                        codec.releaseOutputBuffer(outputIdx, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    } else if (outputIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // формат изменился — продолжаем
                    }
                }

                codec.stop()
                codec.release()
                extractor.release()

                pcmOutput.toByteArray()

            } finally {
                tmpFile.delete()
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun sendToken(token: String) {
        if (!isConnected.get()) return
        val msg = JSONObject()
            .put("event", "text")
            .put("text", token)
        ws?.send(msg.toString())
    }

    override suspend fun flushStreaming() {
        if (!isConnected.get()) return
        ws?.send(JSONObject().put("event", "flush").toString())
    }

    override suspend fun stopStreaming() {
        isStopped.set(true)
        if (!isConnected.get()) return
        ws?.send(JSONObject().put("event", "stop").toString())
    }

    // ── Санитизация текста ─────────────────────────────────────────────────

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

    private companion object {
        val API_KEY: String get() = BuildConfig.FISH_AUDIO_API_KEY
        const val WS_ENDPOINT = "wss://api.fish.audio/v1/tts/live"
        const val MODEL = "s2.1-pro-free"
        const val REFERENCE_ID = "096d410e860346a7a73762d557a290d7"

        // OGG/Opus 48кГц — нативная частота Android HAL, ноль ресемплинга
        const val SAMPLE_RATE = 48_000
        const val OPUS_BITRATE = 32_000

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
