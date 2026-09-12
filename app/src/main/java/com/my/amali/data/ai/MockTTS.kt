package com.my.amali.data.ai

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.sin

/**
 * Mock text-to-speech engine for development and demos.
 *
 * Instead of real synthesis it generates pseudo-audio: for each word of the
 * input text it emits an [AudioChunk] containing a synthetic waveform whose
 * duration is proportional to the word length. Playback pacing is simulated
 * with delays matching each chunk's duration.
 *
 * Скорость воспроизведения учитывает [EngineOptions.speechRate], поэтому
 * настройка «скорость речи» проверяется и на заглушке.
 */
class MockTextToSpeechEngine : TextToSpeechEngine {

    private var initialized: Boolean = false

    override suspend fun initialize() {
        initialized = true
    }

    override suspend fun close() {
        initialized = false
    }

    override fun speak(text: String, options: EngineOptions): Flow<AudioChunk> = flow {
        if (!initialized) initialize()

        val cleanText = text.trim()
        if (cleanText.isEmpty()) return@flow

        val rate = options.speechRate.coerceIn(0.5f, 2f)
        val words = cleanText.split(Regex("\\s+")).filter { it.isNotBlank() }
        var phase = 0.0
        for (word in words) {
            val durationMs = (durationFor(word) / rate).toInt().coerceAtLeast(60)
            val chunk = synthesizeChunk(word, durationMs, phase)
            phase += durationMs / 1000.0

            emit(chunk)
            // Real-time playback pacing: wait as long as the chunk would play.
            delay(durationMs.toLong())
        }

        // Trailing silence, as if the speaker finished a sentence.
        delay(TRAILING_SILENCE_MS)
    }

    /** Word length-based synthetic duration in milliseconds. */
    private fun durationFor(word: String): Int =
        (BASE_WORD_DURATION_MS + word.length * MS_PER_CHARACTER)
            .coerceIn(MIN_WORD_DURATION_MS, MAX_WORD_DURATION_MS)

    /**
     * Builds a sine-wave PCM buffer simulating one spoken [word].
     * The [phaseOffset] keeps consecutive chunks phase-continuous so the
     * concatenation does not click.
     */
    private fun synthesizeChunk(word: String, durationMs: Int, phaseOffset: Double): AudioChunk {
        val samples = (SAMPLE_RATE * durationMs) / 1000
        val data = ByteArray(samples * 2)
        val amplitude = Short.MAX_VALUE * 0.6
        var phase = phaseOffset * 2.0 * Math.PI * VOICE_FREQUENCY_HZ
        for (i in 0 until samples) {
            // Simple attack/decay envelope so each "word" sounds like a syllable.
            val t = i.toDouble() / samples
            val envelope = minOf(1.0, t * 8.0) * minOf(1.0, (1.0 - t) * 4.0)
            val sample = (amplitude * envelope * sin(phase)).toInt()
            phase += 2.0 * Math.PI * VOICE_FREQUENCY_HZ / SAMPLE_RATE
            // Little-endian 16-bit PCM.
            data[i * 2] = (sample and 0xFF).toByte()
            data[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return AudioChunk(data = data, sampleRate = SAMPLE_RATE)
    }

    private companion object {
        /** Standard telephony-grade sample rate keeps mock buffers small. */
        const val SAMPLE_RATE = 16_000

        /** Tone frequency of the synthetic voice, in Hz. */
        const val VOICE_FREQUENCY_HZ = 180.0

        /** Duration baseline per word, in ms. */
        const val BASE_WORD_DURATION_MS = 220

        /** Extra milliseconds per character of a word. */
        const val MS_PER_CHARACTER = 45

        const val MIN_WORD_DURATION_MS = 180
        const val MAX_WORD_DURATION_MS = 900

        /** Pause after the last word, in ms. */
        const val TRAILING_SILENCE_MS = 120L

    }
}
