package com.my.amali.data.ai

import com.my.amali.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow

/**
 * Speech-to-text engine contract. Implementations consume microphone audio
 * (represented here by the instantaneous [SpeechToTextEngine.transcribe]
 * audio level) and produce progressively refined transcriptions.
 */
interface SpeechToTextEngine {
    /** Prepares engine resources. Must be called before [transcribe]. */
    suspend fun initialize()

    /** Releases engine resources. Safe to call multiple times. */
    suspend fun close()

    /**
     * Starts a listening session and emits transcription results:
     * interim partials first, then a single final transcript.
     *
     * @param audioLevel normalized microphone loudness in [0.0, 1.0].
     */
    fun transcribe(audioLevel: Float): Flow<String>
}

/**
 * Text-to-speech engine contract. Implementations synthesize spoken audio
 * from text and stream it back in playable chunks.
 */
interface TextToSpeechEngine {
    /** Prepares engine resources. Must be called before [speak]. */
    suspend fun initialize()

    /** Releases engine resources. Safe to call multiple times. */
    suspend fun close()

    /**
     * Synthesizes [text] into speech, emitting [AudioChunk]s in playback order.
     */
    fun speak(text: String): Flow<AudioChunk>
}

/**
 * Language model contract. Implementations generate an assistant answer
 * for a [prompt], optionally grounded in conversation [history].
 */
interface LanguageModel {
    /** Prepares engine resources. Must be called before [generateResponse]. */
    suspend fun initialize()

    /** Releases engine resources. Safe to call multiple times. */
    suspend fun close()

    /**
     * Generates a response to [prompt], emitting the answer incrementally
     * (typically word by word or token by token).
     *
     * @param prompt the user's request text.
     * @param history prior conversation turns for context, oldest first.
     */
    fun generateResponse(prompt: String, history: List<ChatMessage>): Flow<String>
}

/**
 * A single unit of synthesized audio.
 *
 * @property data PCM audio bytes (16-bit mono little-endian by convention).
 * @property sampleRate sample rate of [data] in Hz.
 */
data class AudioChunk(val data: ByteArray, val sampleRate: Int) {
    /** Number of samples contained in this chunk. */
    val sampleCount: Int
        get() = data.size / 2 // 16-bit PCM

    /** Duration of this chunk in milliseconds. */
    val durationMs: Int
        get() = if (sampleRate <= 0) 0 else (sampleCount * 1000) / sampleRate

    override fun equals(other: Any?): Boolean =
        other is AudioChunk && other.sampleRate == sampleRate && other.data.contentEquals(data)

    override fun hashCode(): Int = 31 * data.contentHashCode() + sampleRate

    override fun toString(): String =
        "AudioChunk(bytes=${data.size}, sampleRate=$sampleRate, durationMs=$durationMs)"
}

/**
 * Describes the concrete engine implementations wired into the orchestrator.
 * Used for diagnostics, settings screens, and mock/real engine selection.
 */
data class AIConfig(
    val sttEngineName: String,
    val ttsEngineName: String,
    val llmEngineName: String
) {
    /** One-line summary suitable for logging. */
    fun describe(): String = "STT=$sttEngineName, TTS=$ttsEngineName, LLM=$llmEngineName"

    companion object {
        /** Configuration describing the built-in mock pipeline. */
        val Mock: AIConfig = AIConfig(
            sttEngineName = "MockSpeechToTextEngine",
            ttsEngineName = "MockTextToSpeechEngine",
            llmEngineName = "MockLanguageModel"
        )
    }
}
