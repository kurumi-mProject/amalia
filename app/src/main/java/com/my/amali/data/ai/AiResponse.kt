package com.my.amali.data.ai

/**
 * Streamed events emitted by [AIOrchestrator] while a single voice or text
 * command is being processed. Consumers use these to drive the assistant's
 * UI state machine (orb animation, captions, bubbles).
 */
sealed class AiResponse {

    /**
     * A transient textual update: the recognized user input or a partial
     * assistant answer still being generated.
     */
    data class Interim(val text: String) : AiResponse()

    /**
     * Signals a transition in/out of the thinking phase
     * (the language model is preparing an answer).
     */
    data class Thinking(val isThinking: Boolean) : AiResponse()

    /**
     * Signals a transition in/out of the speaking phase
     * (audio response is being synthesized and played).
     */
    data class Speaking(val isSpeaking: Boolean) : AiResponse()

    /**
     * Raw synthesized audio ready for playback.
     */
    data class Audio(val chunk: AudioChunk) : AiResponse()

    /**
     * The final, complete assistant response text. Always the last
     * successful event of a processing flow.
     */
    data class Finished(val responseText: String) : AiResponse()

    /**
     * Processing failed; [message] describes the reason and is safe
     * to display to the user.
     */
    data class Error(val message: String) : AiResponse()
}
