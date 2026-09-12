package com.my.amali.data.ai

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Streams [AudioChunk]s (16-bit PCM mono) to the device speaker via [AudioTrack].
 *
 * Usage:
 * ```kotlin
 * AudioPlayer.play(ttsEngine.speak("Hello"))
 * ```
 *
 * Key properties:
 * - Runs on [Dispatchers.IO] — never blocks the main thread.
 * - Reinitialises [AudioTrack] automatically when [AudioChunk.sampleRate] changes.
 * - Writes each chunk synchronously so playback starts with the very first chunk
 *   (~23ms latency for 4096-byte PCM chunks at 44100 Hz).
 * - Always releases [AudioTrack] on completion or cancellation.
 */
object AudioPlayer {

    /**
     * Collects [chunks] and plays each one in arrival order.
     * Suspends until all chunks have been played.
     *
     * @throws RuntimeException if [AudioTrack] could not be initialised.
     */
    suspend fun play(chunks: Flow<AudioChunk>) = withContext(Dispatchers.IO) {
        var track: AudioTrack? = null
        var currentSampleRate = -1

        try {
            chunks.collect { chunk ->
                // Re-create AudioTrack only if sample rate changed (normally never).
                if (track == null || chunk.sampleRate != currentSampleRate) {
                    track?.stop()
                    track?.release()
                    currentSampleRate = chunk.sampleRate
                    track = buildTrack(chunk.sampleRate, chunk.data.size)
                    track?.play()
                }
                track?.write(chunk.data, 0, chunk.data.size)
            }
        } finally {
            track?.stop()
            track?.release()
        }
    }

    private fun buildTrack(sampleRate: Int, chunkSize: Int): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(chunkSize * 2) // at least 2 chunks of buffering

        return AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            minBuf,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
    }
}
