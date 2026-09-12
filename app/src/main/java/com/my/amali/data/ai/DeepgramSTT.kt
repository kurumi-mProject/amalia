package com.my.amali.data.ai

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Real STT engine backed by Deepgram nova-2 via WebSocket streaming.
 *
 * Audio flow:
 *   AudioRecord (16kHz PCM-16 mono) → WebSocket binary frames → Deepgram
 *   Deepgram JSON responses → interim + final transcripts emitted on the Flow
 *
 * The microphone stays open for as long as the flow is collected.
 * Cancel the collecting coroutine (e.g. from the ViewModel) to stop recording
 * — the [awaitClose] block will cleanly stop AudioRecord and close the WebSocket.
 */
class DeepgramSTT : SpeechToTextEngine {

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // infinite — keep WebSocket alive
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun initialize() { /* stateless — nothing to pre-warm */ }

    override suspend fun close() {
        // Shut down the OkHttp thread pool when the engine is fully discarded.
        client.dispatcher.executorService.shutdown()
    }

    override fun transcribe(audioLevel: Float): Flow<String> = callbackFlow {
        // ── Audio format ──────────────────────────────────────────────────────
        val sampleRate   = SAMPLE_RATE
        val channelCfg   = AudioFormat.CHANNEL_IN_MONO
        val encoding     = AudioFormat.ENCODING_PCM_16BIT
        val minBuf       = AudioRecord.getMinBufferSize(sampleRate, channelCfg, encoding)
        val bufferSize   = minBuf.coerceAtLeast(3200) // at least 100ms at 16kHz

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate, channelCfg, encoding, bufferSize
        )

        // ── WebSocket setup ───────────────────────────────────────────────────
        val url = buildString {
            append("wss://api.deepgram.com/v1/listen")
            append("?model=nova-2")
            append("&language=ru")
            append("&punctuate=true")
            append("&interim_results=true")
            append("&encoding=linear16")
            append("&sample_rate=$sampleRate")
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Token $API_KEY")
            .build()

        var lastFinalTranscript = ""
        var wsRef: WebSocket? = null

        val listener = object : WebSocketListener() {

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json       = JSONObject(text)
                    val isFinal    = json.optBoolean("is_final", false)
                    val transcript = json
                        .optJSONObject("channel")
                        ?.optJSONArray("alternatives")
                        ?.optJSONObject(0)
                        ?.optString("transcript", "")
                        .orEmpty()

                    if (transcript.isNotEmpty()) {
                        if (isFinal) lastFinalTranscript = transcript
                        // Emit both interim and final — UI shows live captions.
                        trySend(transcript)
                    }
                } catch (_: Exception) { /* malformed frame — ignore */ }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                // Ensure the last confirmed final transcript is always the last emission.
                if (lastFinalTranscript.isNotEmpty()) trySend(lastFinalTranscript)
                close()
            }
        }

        wsRef = client.newWebSocket(request, listener)

        // ── Mic capture loop ─────────────────────────────────────────────────
        recorder.startRecording()

        val micJob = launch(Dispatchers.IO) {
            val buffer = ByteArray(bufferSize)
            while (true) {
                val read = recorder.read(buffer, 0, bufferSize)
                if (read > 0) {
                    wsRef?.send(ByteString.of(*buffer.copyOf(read)))
                }
            }
        }

        // ── Cleanup when flow is cancelled ───────────────────────────────────
        awaitClose {
            micJob.cancel()
            recorder.stop()
            recorder.release()
            // Tell Deepgram to finalize the current utterance and return a final result.
            wsRef?.send("""{"type":"CloseStream"}""")
            wsRef?.close(1000, "recording stopped")
        }
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        val API_KEY: String get() = com.my.amali.BuildConfig.DEEPGRAM_API_KEY
    }
}
