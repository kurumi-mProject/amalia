package com.my.amali.data.ai

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
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TTS через Fish Audio WebSocket.
 *
 * Протокол: бинарные WS фреймы + msgpack сериализация.
 * Формат аудио: PCM 16-bit LE mono 24kHz — чистые сэмплы, без заголовков.
 * Каждый чанк независим и сразу идёт в AudioTrack.
 *
 * КРИТИЧЕСКИ ВАЖНО: Fish Audio принимает ТОЛЬКО бинарные WS фреймы с msgpack.
 * Текстовые JSON фреймы (как было раньше) — сервер сразу закрывает соединение.
 *
 * Msgpack кодируется вручную через минимальный encoder — без сторонних зависимостей.
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

    @Volatile private var _audioChannel = Channel<AudioChunk>(capacity = Channel.UNLIMITED)

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
        _audioChannel = Channel(capacity = Channel.UNLIMITED)
        isStopped.set(false)

        val request = Request.Builder()
            .url(WS_ENDPOINT)
            .header("Authorization", "Bearer $API_KEY")
            .header("model", MODEL)
            .build()

        ws = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected.set(true)
                // Отправляем start как msgpack бинарный фрейм
                val startMsg = msgpackMap(
                    "event" to "start",
                    "request" to msgpackMapRaw(
                        "text" to "",
                        "reference_id" to REFERENCE_ID,
                        "format" to "pcm",
                        "sample_rate" to SAMPLE_RATE,
                        "latency" to "balanced",
                        "chunk_length" to 100,
                        "normalize" to true,
                        "prosody" to msgpackMapRaw(
                            "speed" to options.speechRate.coerceIn(0.5f, 2f),
                            "volume" to 0
                        )
                    )
                )
                webSocket.send(startMsg.toByteString())
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                parseMsgpack(bytes.toByteArray())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                // не должно приходить, но на всякий случай
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

    // ── Msgpack парсер ───────────────────────────────────────────────────────

    private fun parseMsgpack(data: ByteArray) {
        try {
            val (map, _) = readValue(data, 0) as? Pair<*, *> ?: return
            @Suppress("UNCHECKED_CAST")
            map as? Map<String, Any?> ?: return
            val event = map["event"] as? String ?: return
            when (event) {
                "audio" -> {
                    val audio = map["audio"]
                    if (audio is ByteArray && audio.isNotEmpty()) {
                        _audioChannel.trySend(AudioChunk(data = audio, sampleRate = SAMPLE_RATE))
                    }
                }
                "finish" -> {
                    _audioChannel.close()
                }
            }
        } catch (_: Exception) {}
    }

    /** Рекурсивный msgpack reader. Возвращает (value, nextPos). */
    private fun readValue(data: ByteArray, pos: Int): Pair<Any?, Int> {
        if (pos >= data.size) return Pair(null, pos)
        val b = data[pos].toInt() and 0xFF
        return when {
            // positive fixint
            b and 0x80 == 0 -> Pair(b, pos + 1)
            // negative fixint
            b and 0xE0 == 0xE0 -> Pair(b - 256, pos + 1)
            // fixmap
            b and 0xF0 == 0x80 -> readMap(data, pos + 1, b and 0x0F)
            // fixarray
            b and 0xF0 == 0x90 -> readArray(data, pos + 1, b and 0x0F)
            // fixstr
            b and 0xE0 == 0xA0 -> {
                val len = b and 0x1F
                Pair(String(data, pos + 1, len, Charsets.UTF_8), pos + 1 + len)
            }
            // nil
            b == 0xC0 -> Pair(null, pos + 1)
            // false/true
            b == 0xC2 -> Pair(false, pos + 1)
            b == 0xC3 -> Pair(true, pos + 1)
            // bin8
            b == 0xC4 -> {
                val len = data[pos + 1].toInt() and 0xFF
                Pair(data.copyOfRange(pos + 2, pos + 2 + len), pos + 2 + len)
            }
            // bin16
            b == 0xC5 -> {
                val len = u16(data, pos + 1)
                Pair(data.copyOfRange(pos + 3, pos + 3 + len), pos + 3 + len)
            }
            // bin32
            b == 0xC6 -> {
                val len = u32(data, pos + 1)
                Pair(data.copyOfRange(pos + 5, pos + 5 + len), pos + 5 + len)
            }
            // uint8
            b == 0xCC -> Pair(data[pos + 1].toInt() and 0xFF, pos + 2)
            // uint16
            b == 0xCD -> Pair(u16(data, pos + 1), pos + 3)
            // uint32
            b == 0xCE -> Pair(u32(data, pos + 1), pos + 5)
            // int8
            b == 0xD0 -> Pair(data[pos + 1].toInt(), pos + 2)
            // int16
            b == 0xD1 -> Pair((data[pos+1].toInt() shl 8) or (data[pos+2].toInt() and 0xFF), pos + 3)
            // int32
            b == 0xD2 -> Pair(u32(data, pos + 1), pos + 5)
            // str8
            b == 0xD9 -> {
                val len = data[pos + 1].toInt() and 0xFF
                Pair(String(data, pos + 2, len, Charsets.UTF_8), pos + 2 + len)
            }
            // str16
            b == 0xDA -> {
                val len = u16(data, pos + 1)
                Pair(String(data, pos + 3, len, Charsets.UTF_8), pos + 3 + len)
            }
            // str32
            b == 0xDB -> {
                val len = u32(data, pos + 1)
                Pair(String(data, pos + 5, len, Charsets.UTF_8), pos + 5 + len)
            }
            // array16
            b == 0xDC -> {
                val len = u16(data, pos + 1)
                readArray(data, pos + 3, len)
            }
            // map16
            b == 0xDE -> {
                val len = u16(data, pos + 1)
                readMap(data, pos + 3, len)
            }
            // map32
            b == 0xDF -> {
                val len = u32(data, pos + 1)
                readMap(data, pos + 5, len)
            }
            else -> Pair(null, pos + 1)
        }
    }

    private fun readMap(data: ByteArray, start: Int, count: Int): Pair<Map<String, Any?>, Int> {
        val map = LinkedHashMap<String, Any?>(count)
        var p = start
        repeat(count) {
            val (k, p1) = readValue(data, p)
            val (v, p2) = readValue(data, p1)
            if (k is String) map[k] = v
            p = p2
        }
        return Pair(map, p)
    }

    private fun readArray(data: ByteArray, start: Int, count: Int): Pair<List<Any?>, Int> {
        val list = ArrayList<Any?>(count)
        var p = start
        repeat(count) {
            val (v, p1) = readValue(data, p)
            list.add(v); p = p1
        }
        return Pair(list, p)
    }

    private fun u16(d: ByteArray, p: Int) = ((d[p].toInt() and 0xFF) shl 8) or (d[p+1].toInt() and 0xFF)
    private fun u32(d: ByteArray, p: Int) = ((d[p].toInt() and 0xFF) shl 24) or ((d[p+1].toInt() and 0xFF) shl 16) or ((d[p+2].toInt() and 0xFF) shl 8) or (d[p+3].toInt() and 0xFF)

    // ── Msgpack encoder ──────────────────────────────────────────────────────

    /** Кодирует Map<String,Any?> → msgpack bytes */
    private fun msgpackMap(vararg pairs: Pair<String, Any?>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val map = pairs.toMap()
        writeMap(out, map)
        return out.toByteArray()
    }

    /** Вложенная map — возвращает уже сериализованные bytes для использования как значение */
    private fun msgpackMapRaw(vararg pairs: Pair<String, Any?>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        writeMap(out, pairs.toMap())
        return out.toByteArray()
    }

    private fun writeMap(out: java.io.ByteArrayOutputStream, map: Map<String, Any?>) {
        val size = map.size
        if (size <= 15) out.write(0x80 or size)
        else { out.write(0xDE); out.write(size shr 8); out.write(size and 0xFF) }
        for ((k, v) in map) {
            writeStr(out, k)
            writeValue(out, v)
        }
    }

    private fun writeStr(out: java.io.ByteArrayOutputStream, s: String) {
        val b = s.toByteArray(Charsets.UTF_8)
        when {
            b.size <= 31 -> { out.write(0xA0 or b.size); out.write(b) }
            b.size <= 255 -> { out.write(0xD9); out.write(b.size); out.write(b) }
            else -> { out.write(0xDA); out.write(b.size shr 8); out.write(b.size and 0xFF); out.write(b) }
        }
    }

    private fun writeValue(out: java.io.ByteArrayOutputStream, v: Any?) {
        when (v) {
            null -> out.write(0xC0)
            is Boolean -> out.write(if (v) 0xC3 else 0xC2)
            is Int -> when {
                v in 0..127 -> out.write(v)
                v in -32..-1 -> out.write(v and 0xFF)
                v in 0..0xFF -> { out.write(0xCC); out.write(v) }
                v in 0..0xFFFF -> { out.write(0xCD); out.write(v shr 8); out.write(v and 0xFF) }
                else -> { out.write(0xD2); out.write((v shr 24) and 0xFF); out.write((v shr 16) and 0xFF); out.write((v shr 8) and 0xFF); out.write(v and 0xFF) }
            }
            is Float -> {
                val bits = java.lang.Float.floatToIntBits(v)
                out.write(0xCA)
                out.write((bits shr 24) and 0xFF); out.write((bits shr 16) and 0xFF)
                out.write((bits shr 8) and 0xFF); out.write(bits and 0xFF)
            }
            is Double -> {
                // encode as float32 для экономии
                val bits = java.lang.Float.floatToIntBits(v.toFloat())
                out.write(0xCA)
                out.write((bits shr 24) and 0xFF); out.write((bits shr 16) and 0xFF)
                out.write((bits shr 8) and 0xFF); out.write(bits and 0xFF)
            }
            is String -> writeStr(out, v)
            is ByteArray -> {
                // Вложенный msgpack — пишем raw bytes напрямую
                out.write(v)
            }
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                writeMap(out, v as Map<String, Any?>)
            }
            else -> writeStr(out, v.toString())
        }
    }

    // ── Отправка событий ─────────────────────────────────────────────────────

    override suspend fun sendToken(token: String) {
        if (!isConnected.get()) return
        val bytes = msgpackMap("event" to "text", "text" to token)
        ws?.send(bytes.toByteString())
    }

    override suspend fun flushStreaming() {
        if (!isConnected.get()) return
        val bytes = msgpackMap("event" to "flush")
        ws?.send(bytes.toByteString())
    }

    override suspend fun stopStreaming() {
        isStopped.set(true)
        if (!isConnected.get()) {
            _audioChannel.close()
            return
        }
        val bytes = msgpackMap("event" to "stop")
        ws?.send(bytes.toByteString())
        ws?.close(1000, "stopped")
    }

    private companion object {
        val API_KEY: String get() = BuildConfig.FISH_AUDIO_API_KEY
        const val WS_ENDPOINT = "wss://api.fish.audio/v1/tts/live"
        const val MODEL = "s2.1-pro-free"
        const val REFERENCE_ID = "096d410e860346a7a73762d557a290d7"
        const val SAMPLE_RATE = 24_000  // Fish Audio PCM: 24kHz, 16-bit LE mono
    }
}
