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
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TTS через Fish Audio WebSocket + OGG/Opus.
 *
 * Fish Audio WS протокол использует msgpack для бинарных сообщений.
 * Каждое сообщение — map с полем "event":
 *   - "audio": поле "audio" = bytes (OGG/Opus данные)
 *   - "finish": сессия завершена
 *
 * MediaCodec декодирует накопленный OGG/Opus → PCM 16-bit 48кГц.
 * 48кГц = нативная частота Android HAL, ноль ресемплинга.
 */
class FishAudioTTS : TextToSpeechEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(0, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private val isStopped = AtomicBoolean(false)

    // Пересоздаётся на каждый startStreaming
    @Volatile private var _audioChannel = Channel<AudioChunk>(capacity = Channel.UNLIMITED)
    private val oggBuffer = java.io.ByteArrayOutputStream()

    override suspend fun initialize() {}

    override suspend fun close() {
        ws?.close(1000, "close")
        ws = null
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    override fun speak(text: String, options: EngineOptions): Flow<AudioChunk> = emptyFlow()

    override val streamingAudio: Flow<AudioChunk>
        get() = _audioChannel.receiveAsFlow()

    override suspend fun startStreaming(options: EngineOptions) = withContext(Dispatchers.IO) {
        // Пересоздаём channel — предыдущий мог быть закрыт
        _audioChannel = Channel(capacity = Channel.UNLIMITED)
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
                // Отправляем start как JSON строку
                webSocket.send(JSONObject()
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
                    ).toString()
                )
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                parseMsgpackMessage(bytes.toByteArray())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val j = JSONObject(text)
                    handleEvent(j.optString("event"), j.optString("audio", "").let {
                        if (it.isNotEmpty()) android.util.Base64.decode(it, android.util.Base64.DEFAULT)
                        else null
                    })
                } catch (_: Exception) {}
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected.set(false)
                _audioChannel.close(EngineException("Fish Audio WS: ${t.message}"))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected.set(false)
                _audioChannel.close()
            }
        })
    }

    /**
     * Минимальный msgpack парсер для Fish Audio протокола.
     * Формат: fixmap с string ключами "event" и "audio".
     *
     * msgpack fixmap: 0x8N где N = число пар
     * fixstr: 0xAN где N = длина строки
     * bin8:   0xC4 + 1 байт длины + данные
     * bin16:  0xC5 + 2 байта длины + данные
     * bin32:  0xC6 + 4 байта длины + данные
     */
    private fun parseMsgpackMessage(data: ByteArray) {
        try {
            var pos = 0
            if (pos >= data.size) return

            // Первый байт — тип (fixmap, map16, map32)
            val firstByte = data[pos++].toInt() and 0xFF
            val mapSize = when {
                firstByte and 0xF0 == 0x80 -> firstByte and 0x0F // fixmap
                firstByte == 0xDE -> { // map16
                    val s = ((data[pos].toInt() and 0xFF) shl 8) or (data[pos+1].toInt() and 0xFF)
                    pos += 2; s
                }
                else -> return
            }

            var eventStr = ""
            var audioBytes: ByteArray? = null

            repeat(mapSize) {
                // Читаем ключ (строка)
                val key = readMsgpackString(data, pos) ?: return
                pos = key.second

                // Читаем значение
                when (key.first) {
                    "event" -> {
                        val v = readMsgpackString(data, pos) ?: return
                        eventStr = v.first
                        pos = v.second
                    }
                    "audio" -> {
                        val v = readMsgpackBytes(data, pos) ?: return
                        audioBytes = v.first
                        pos = v.second
                    }
                    else -> {
                        // Пропускаем значение
                        pos = skipMsgpackValue(data, pos)
                    }
                }
            }

            handleEvent(eventStr, audioBytes)
        } catch (_: Exception) {}
    }

    private fun readMsgpackString(data: ByteArray, pos: Int): Pair<String, Int>? {
        if (pos >= data.size) return null
        var p = pos
        val b = data[p++].toInt() and 0xFF
        val len = when {
            b and 0xE0 == 0xA0 -> b and 0x1F // fixstr
            b == 0xD9 -> { val l = data[p++].toInt() and 0xFF; l } // str8
            b == 0xDA -> { val l = ((data[p].toInt() and 0xFF) shl 8) or (data[p+1].toInt() and 0xFF); p += 2; l } // str16
            else -> return null
        }
        if (p + len > data.size) return null
        return Pair(String(data, p, len, Charsets.UTF_8), p + len)
    }

    private fun readMsgpackBytes(data: ByteArray, pos: Int): Pair<ByteArray, Int>? {
        if (pos >= data.size) return null
        var p = pos
        val b = data[p++].toInt() and 0xFF
        val len = when (b) {
            0xC4 -> { val l = data[p++].toInt() and 0xFF; l } // bin8
            0xC5 -> { val l = ((data[p].toInt() and 0xFF) shl 8) or (data[p+1].toInt() and 0xFF); p += 2; l } // bin16
            0xC6 -> { val l = ((data[p].toInt() and 0xFF) shl 24) or ((data[p+1].toInt() and 0xFF) shl 16) or ((data[p+2].toInt() and 0xFF) shl 8) or (data[p+3].toInt() and 0xFF); p += 4; l } // bin32
            else -> return null
        }
        if (p + len > data.size) return null
        return Pair(data.copyOfRange(p, p + len), p + len)
    }

    private fun skipMsgpackValue(data: ByteArray, pos: Int): Int {
        if (pos >= data.size) return pos
        val b = data[pos].toInt() and 0xFF
        return when {
            b and 0x80 == 0 -> pos + 1 // positive fixint
            b and 0xE0 == 0xE0 -> pos + 1 // negative fixint
            b and 0xE0 == 0xA0 -> pos + 1 + (b and 0x1F) // fixstr
            b and 0xF0 == 0x80 -> pos + 1 // fixmap (упрощённо)
            b == 0xC0 -> pos + 1 // nil
            b == 0xC2 || b == 0xC3 -> pos + 1 // bool
            b == 0xCC || b == 0xD0 -> pos + 2 // uint8/int8
            b == 0xCD || b == 0xD1 -> pos + 3 // uint16/int16
            b == 0xCE || b == 0xD2 || b == 0xCA -> pos + 5 // uint32/int32/float32
            b == 0xCF || b == 0xD3 || b == 0xCB -> pos + 9 // uint64/int64/float64
            b == 0xD9 -> pos + 2 + (data[pos+1].toInt() and 0xFF) // str8
            b == 0xDA -> pos + 3 + ((data[pos+1].toInt() and 0xFF) shl 8) + (data[pos+2].toInt() and 0xFF) // str16
            b == 0xC4 -> pos + 2 + (data[pos+1].toInt() and 0xFF) // bin8
            b == 0xC5 -> pos + 3 + ((data[pos+1].toInt() and 0xFF) shl 8) + (data[pos+2].toInt() and 0xFF) // bin16
            else -> pos + 1
        }
    }

    private fun handleEvent(event: String, audioBytes: ByteArray?) {
        when (event) {
            "audio" -> {
                if (audioBytes != null && audioBytes.isNotEmpty()) {
                    decodeOpusChunk(audioBytes)
                }
            }
            "finish" -> {
                flushOggBuffer()
                _audioChannel.close()
            }
        }
    }

    private fun decodeOpusChunk(opusData: ByteArray) {
        oggBuffer.write(opusData)
        val accumulated = oggBuffer.toByteArray()
        // Пробуем декодировать только если есть полный OGG пакет (заголовок OggS)
        if (accumulated.size >= 64 && hasCompleteOggPage(accumulated)) {
            val pcm = decodeOggOpusToPcm(accumulated)
            if (pcm != null && pcm.isNotEmpty()) {
                oggBuffer.reset()
                _audioChannel.trySend(AudioChunk(data = pcm, sampleRate = SAMPLE_RATE))
            }
        }
    }

    private fun flushOggBuffer() {
        val accumulated = oggBuffer.toByteArray()
        if (accumulated.size < 27) return
        val pcm = decodeOggOpusToPcm(accumulated) ?: return
        if (pcm.isNotEmpty()) {
            _audioChannel.trySend(AudioChunk(data = pcm, sampleRate = SAMPLE_RATE))
        }
        oggBuffer.reset()
    }

    /** Проверяем что есть хотя бы 2 OGG страницы (заголовок + данные). */
    private fun hasCompleteOggPage(data: ByteArray): Boolean {
        var count = 0
        var i = 0
        while (i < data.size - 3) {
            if (data[i] == 'O'.code.toByte() && data[i+1] == 'g'.code.toByte() &&
                data[i+2] == 'g'.code.toByte() && data[i+3] == 'S'.code.toByte()) {
                count++
                if (count >= 2) return true
                i += 27
            } else i++
        }
        return false
    }

    private fun decodeOggOpusToPcm(oggData: ByteArray): ByteArray? {
        if (oggData.size < 64) return null
        return try {
            val tmpFile = File.createTempFile("opus_", ".ogg")
            tmpFile.deleteOnExit()
            try {
                FileOutputStream(tmpFile).use { it.write(oggData) }

                val extractor = MediaExtractor()
                extractor.setDataSource(tmpFile.absolutePath)

                var trackIndex = -1
                var format: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val fmt = extractor.getTrackFormat(i)
                    val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("audio/")) {
                        trackIndex = i; format = fmt; break
                    }
                }
                if (trackIndex < 0 || format == null) return null

                extractor.selectTrack(trackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

                val codec = MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()

                val out = java.io.ByteArrayOutputStream()
                val info = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false

                while (!outputDone) {
                    if (!inputDone) {
                        val idx = codec.dequeueInputBuffer(10_000L)
                        if (idx >= 0) {
                            val buf = codec.getInputBuffer(idx)!!
                            buf.clear()
                            val size = extractor.readSampleData(buf, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(idx, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val idx = codec.dequeueOutputBuffer(info, 10_000L)
                    if (idx >= 0) {
                        val buf = codec.getOutputBuffer(idx)!!
                        val chunk = ByteArray(info.size)
                        buf.get(chunk)
                        out.write(chunk)
                        codec.releaseOutputBuffer(idx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }

                codec.stop(); codec.release(); extractor.release()
                out.toByteArray()
            } finally {
                tmpFile.delete()
            }
        } catch (_: Exception) { null }
    }

    override suspend fun sendToken(token: String) {
        if (!isConnected.get()) return
        ws?.send(JSONObject().put("event", "text").put("text", token).toString())
    }

    override suspend fun flushStreaming() {
        if (!isConnected.get()) return
        ws?.send(JSONObject().put("event", "flush").toString())
    }

    override suspend fun stopStreaming() {
        isStopped.set(true)
        
        // Если не подключены - сразу закрываем канал
        if (!isConnected.get()) {
            _audioChannel.close()
            ws?.close(1000, "stopped")
            return
        }
        
        // Отправляем stop в WS
        ws?.send(JSONObject().put("event", "stop").toString())
        
        // Закрываем WS соединение (это должно вызвать onClosed -> _audioChannel.close())
        ws?.close(1000, "stopped")
    }

    private companion object {
        val API_KEY: String get() = BuildConfig.FISH_AUDIO_API_KEY
        const val WS_ENDPOINT = "wss://api.fish.audio/v1/tts/live"
        const val MODEL = "s2.1-pro-free"
        const val REFERENCE_ID = "096d410e860346a7a73762d557a290d7"
        const val SAMPLE_RATE = 48_000
        const val OPUS_BITRATE = 32_000

        val CODE_BLOCK = Regex("```[\\s\\S]*?```")
        val INLINE_CODE = Regex("`([^`]*)`")
        val MARKDOWN_LINK = Regex("\\[([^\\]]+)]\\([^)]+\\)")
        val URL = Regex("https?://\\S+")
        val EMPHASIS = Regex("[*_#>~|]")
        val LIST_BULLET = Regex("(?m)^\\s*[-•–]\\s+")
        val EMOJI = Regex("[\\p{So}\\p{Cn}\\uFE0F]")
        val MULTISPACE = Regex("\\s{2,}")
        const val MAX_CHARS = 1200
    }
}
