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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Real STT engine backed by Deepgram nova-2 via WebSocket streaming.
 *
 * ## Auto-stop logic (VAD)
 * The flow closes automatically when **both** conditions are met:
 *   1. Deepgram sends a frame with `speech_final=true` (utterance complete)
 *   2. OR [SILENCE_STOP_MS] ms of silence (RMS below threshold) pass after
 *      the last `is_final=true` result — catches cases where Deepgram doesn't
 *      send speech_final quickly enough.
 *
 * This means: user speaks → pauses → ~800ms later the flow completes
 * and the transcript is handed to the LLM. No button press needed.
 *
 * URL params used:
 *   - `endpointing=800`  — Deepgram waits 800ms of silence then sends speech_final
 *   - `utterance_end_ms=1000` — extra safety net
 *   - `interim_results=true` — live captions while speaking
 */
class DeepgramSTT : SpeechToTextEngine {

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun initialize() {}

    override suspend fun close() {
        client.dispatcher.executorService.shutdown()
    }

    override fun transcribe(audioLevel: Float): Flow<String> = callbackFlow {
        // ── Audio config ──────────────────────────────────────────────────────
        val sampleRate = SAMPLE_RATE
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(3200)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        // ── State ─────────────────────────────────────────────────────────────
        val shouldStop        = AtomicBoolean(false)
        val lastFinalTime     = AtomicLong(0L)
        var lastFinalText     = ""
        var wsRef: WebSocket? = null

        // ── WebSocket URL ─────────────────────────────────────────────────────
        val url = "wss://api.deepgram.com/v1/listen" +
            "?model=nova-2" +
            "&language=ru" +
            "&punctuate=true" +
            "&interim_results=true" +
            "&encoding=linear16" +
            "&sample_rate=$sampleRate" +
            "&endpointing=800" +          // pause → speech_final after 800ms silence
            "&utterance_end_ms=1000"      // backup: close utterance after 1s no audio

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Token $API_KEY")
            .build()

        val listener = object : WebSocketListener() {

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json         = JSONObject(text)
                    val isFinal      = json.optBoolean("is_final", false)
                    val speechFinal  = json.optBoolean("speech_final", false)
                    val transcript   = json
                        .optJSONObject("channel")
                        ?.optJSONArray("alternatives")
                        ?.optJSONObject(0)
                        ?.optString("transcript", "")
                        .orEmpty()

                    if (transcript.isNotEmpty()) {
                        trySend(transcript)
                        if (isFinal) {
                            lastFinalText = transcript
                            lastFinalTime.set(System.currentTimeMillis())
                        }
                    }

                    // speech_final = Deepgram detected end-of-utterance → stop
                    if (speechFinal) {
                        shouldStop.set(true)
                    }
                } catch (_: Exception) {}
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (lastFinalText.isNotEmpty()) trySend(lastFinalText)
                close()
            }
        }

        wsRef = client.newWebSocket(request, listener)
        recorder.startRecording()

        // ── Mic capture loop ──────────────────────────────────────────────────
        val micJob = launch(Dispatchers.IO) {
            val buffer = ByteArray(bufferSize)

            while (!shouldStop.get()) {
                val read = recorder.read(buffer, 0, bufferSize)
                if (read > 0) {
                    wsRef?.send(ByteString.of(*buffer.copyOf(read)))

                    // Local silence VAD: if we have a final result and RMS is low
                    // for SILENCE_STOP_MS → stop without waiting for speech_final
                    val timeSinceFinal = System.currentTimeMillis() - lastFinalTime.get()
                    if (lastFinalTime.get() > 0 &&
                        timeSinceFinal > SILENCE_STOP_MS &&
                        rms(buffer, read) < SILENCE_RMS_THRESHOLD
                    ) {
                        shouldStop.set(true)
                    }
                }
            }

            // Flush Deepgram and close cleanly
            wsRef?.send("""{"type":"CloseStream"}""")
            wsRef?.close(1000, "utterance complete")
        }

        // ── Cleanup on flow cancel ────────────────────────────────────────────
        awaitClose {
            shouldStop.set(true)
            micJob.cancel()
            recorder.stop()
            recorder.release()
            wsRef?.send("""{"type":"CloseStream"}""")
            wsRef?.close(1000, "cancelled")
        }
    }

    /** Root Mean Square amplitude of a PCM-16 buffer — used for silence detection. */
    private fun rms(buffer: ByteArray, len: Int): Double {
        var sum = 0.0
        val samples = len / 2
        for (i in 0 until samples) {
            val sample = (buffer[i * 2].toInt() or (buffer[i * 2 + 1].toInt() shl 8)).toShort()
            sum += sample * sample.toDouble()
        }
        return if (samples > 0) Math.sqrt(sum / samples) else 0.0
    }

    private companion object {
        const val SAMPLE_RATE = 16_000

        /** Stop after this many ms of silence following a final transcript. */
        const val SILENCE_STOP_MS = 1200L

        /** RMS below this value = silence (out of 32768 max for 16-bit PCM). */
        const val SILENCE_RMS_THRESHOLD = 300.0

        val API_KEY: String get() = com.my.amali.BuildConfig.DEEPGRAM_API_KEY
    }
}
