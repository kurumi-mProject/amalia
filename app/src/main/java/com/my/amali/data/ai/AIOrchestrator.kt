package com.my.amali.data.ai

import com.my.amali.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Coordinates the three AI engines (STT -> LLM -> TTS) into a single
 * streaming pipeline.
 *
 * Given an engine triple (real or mock), the orchestrator turns a voice
 * or text command into a [Flow] of [AiResponse] events:
 * `Interim` (recognized/partial text) → `Thinking` → `Speaking` +
 * `Audio` chunks → `Finished` (complete response text).
 *
 * Instantiated manually via the app's DI graph; engine lifecycles are
 * owned by the caller.
 */
class AIOrchestrator(
    internal val sttEngine: SpeechToTextEngine,
    internal val llmEngine: LanguageModel,
    internal val ttsEngine: TextToSpeechEngine
) {
    /** True after a successful [processTextCommand] run, cleared on errors. */
    var lastError: String? = null
        private set

    /**
     * Initializes all three engines. Idempotent; engines also self-initialize
     * lazily, so calling this is optional.
     */
    suspend fun initialize() {
        sttEngine.initialize()
        llmEngine.initialize()
        ttsEngine.initialize()
    }

    /** Releases all engine resources. Safe to call multiple times. */
    suspend fun close() {
        sttEngine.close()
        llmEngine.close()
        ttsEngine.close()
    }

    /**
     * Processes one voice command.
     *
     * Suspends until the STT engine produces its final transcript for the
     * given [audioLevel], then returns a streaming flow of response events.
     */
    suspend fun processVoiceCommand(audioLevel: Float): Flow<AiResponse> {
        val transcript = sttEngine
            .transcribe(audioLevel)
            .catch { throwable ->
                lastError = throwable.message
                emit(EMPTY_TRANSCRIPT)
            }
            .first()
            .trim()
        if (transcript.isEmpty()) {
            lastError = ERROR_NO_SPEECH
            return flow { emit(AiResponse.Error(ERROR_NO_SPEECH)) }
        }
        return processInternal(transcript, history = emptyList())
    }

    /**
     * Processes a typed command directly, skipping the STT stage.
     *
     * @param text the user's request.
     * @param history optional prior conversation turns for LLM context.
     */
    suspend fun processTextCommand(
        text: String,
        history: List<ChatMessage> = emptyList()
    ): Flow<AiResponse> = processInternal(text.trim(), history)

    /**
     * Shared pipeline: Interim → Thinking → streamed LLM tokens →
     * Speaking + Audio chunks → Finished.
     */
    private fun processInternal(commandText: String, history: List<ChatMessage>): Flow<AiResponse> = flow {
        if (commandText.isEmpty()) {
            val message = ERROR_EMPTY_COMMAND
            lastError = message
            emit(AiResponse.Error(message))
            return@flow
        }

        // 1) Echo the recognized command as an interim update.
        emit(AiResponse.Interim(commandText))

        // 2) The model starts working on the answer.
        emit(AiResponse.Thinking(isThinking = true))

        // 3) Stream the generated answer token by token, accumulating it.
        val answer = StringBuilder()
        try {
            llmEngine.generateResponse(commandText, history).collect { token ->
                answer.append(token)
                emit(AiResponse.Interim(answer.toString()))
            }
        } finally {
            emit(AiResponse.Thinking(isThinking = false))
        }

        val responseText = answer.toString().trim().ifEmpty { FALLBACK_ANSWER }

        // 4) Synthesize speech for the final answer.
        emit(AiResponse.Speaking(isSpeaking = true))
        try {
            ttsEngine.speak(responseText).collect { chunk ->
                emit(AiResponse.Audio(chunk))
            }
        } finally {
            emit(AiResponse.Speaking(isSpeaking = false))
        }

        // 5) Complete with the full response text.
        lastError = null
        emit(AiResponse.Finished(responseText))
    }.catch { throwable ->
        // Any engine failure surfaces as a single Error event.
        val message = throwable.message ?: ERROR_UNKNOWN
        lastError = message
        emit(AiResponse.Error(message))
    }

    /**
     * Convenience: full pipeline as a plain final-text flow — useful for
     * repositories that only need the answer string.
     */
    fun finalResponseFlow(text: String, history: List<ChatMessage> = emptyList()): Flow<String> =
        processInternal(text.trim(), history)
            .map { event -> (event as? AiResponse.Finished)?.responseText.orEmpty() }

    private companion object {
        const val EMPTY_TRANSCRIPT = ""
        const val ERROR_NO_SPEECH = "Не удалось распознать речь. Попробуй ещё раз."
        const val ERROR_EMPTY_COMMAND = "Пустая команда — нечего обрабатывать."
        const val ERROR_UNKNOWN = "Неизвестная ошибка обработки запроса."
        const val FALLBACK_ANSWER = "Я не смогла сформулировать ответ. Попробуй переспросить."
    }
}
