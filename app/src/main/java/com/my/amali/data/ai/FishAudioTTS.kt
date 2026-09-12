package com.my.amali.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Real TTS engine backed by Fish Audio (model s2.1-pro-free).
 *
 * Sends the text to Fish Audio's HTTP streaming endpoint and emits raw
 * 16-bit PCM [AudioChunk]s as they arrive from the server.
 * Using `format=pcm` skips mp3/opus decoding entirely — chunks can be
 * written straight to [AudioPlayer] / [android.media.AudioTrack].
 *
 * Latency profile:
 *   - `latency="balanced"` ≈ 300ms to first audio chunk
 *   - `chunk_length=150` — moderate buffering, good balance of stability vs speed
 */
class FishAudioTTS : TextToSpeechEngine {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS) // TTS for long text can take time
        .build()

    override suspend fun initialize() { /* stateless */ }
    override suspend fun close() {}

    override fun speak(text: String): Flow<AudioChunk> = flow {
        if (text.isBlank()) return@flow

        val bodyJson = JSONObject().apply {
            put("text", text)
            put("reference_id", REFERENCE_ID)
            put("format", "pcm")        // raw 16-bit LE mono — zero decode overhead
            put("sample_rate", SAMPLE_RATE)
            put("latency", "balanced")  // ~300ms TTFA, good for conversational use
            put("chunk_length", 150)    // chars per synthesis chunk
        }

        val request = Request.Builder()
            .url("https://api.fish.audio/v1/tts")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $API_KEY")
            .header("Content-Type", "application/json")
            .header("model", "s2.1-pro-free")
            .build()

        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: "(no body)"
                    throw RuntimeException("Fish Audio TTS error ${response.code}: $errBody")
                }

                val source = response.body?.source()
                    ?: throw RuntimeException("Fish Audio: empty response body")

                val buffer = ByteArray(CHUNK_BYTES)
                while (!source.exhausted()) {
                    val read = source.read(buffer).toInt()
                    if (read > 0) {
                        emit(AudioChunk(
                            data = buffer.copyOf(read),
                            sampleRate = SAMPLE_RATE
                        ))
                    }
                }
            }
        }
    }

    private companion object {
        val API_KEY: String get() = com.my.amali.BuildConfig.FISH_AUDIO_API_KEY
        const val REFERENCE_ID = "096d410e860346a7a73762d557a290d7"
        const val SAMPLE_RATE  = 44100
        /** ~23ms of audio per chunk at 44100 Hz 16-bit mono. */
        const val CHUNK_BYTES  = 4096
    }
}
