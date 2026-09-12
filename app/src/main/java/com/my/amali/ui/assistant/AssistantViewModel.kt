package com.my.amali.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.amali.core.di.ServiceLocator
import com.my.amali.data.ai.AIOrchestrator
import com.my.amali.data.ai.AiResponse
import com.my.amali.data.ai.AudioChunk
import com.my.amali.data.ai.AudioPlayer
import com.my.amali.data.model.ChatMessage
import com.my.amali.data.repository.ConversationRepository
import com.my.amali.domain.entity.VoiceState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssistantUiState(
    val voiceState: VoiceState = VoiceState.Idle,
    val audioLevel: Float = 0f,
    val userTranscript: String = "",
    val amaliaReply: String = "",
    val replyProgress: Float = 0f,
    val isSpeaking: Boolean = false,
    val isFirstLaunch: Boolean = true,
    val errorMessage: String? = null,
    val conversationCount: Int = 0,
    val suggestions: List<String> = emptyList(),
)

class AssistantViewModel(
    private val orchestrator: AIOrchestrator = ServiceLocator.aiOrchestrator,
    private val conversationRepo: ConversationRepository = ServiceLocator.conversationRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var conversationJob: Job? = null

    private val defaultSuggestions = listOf("Привет", "Как дела?", "Расскажи о себе")
    private val activeSuggestions  = listOf("Стоп", "Повтори", "Спасибо")

    // ── Public API ────────────────────────────────────────────────────────────

    fun onFirstLaunchHandled() {
        _uiState.update { it.copy(isFirstLaunch = false) }
    }

    fun toggleConversation() {
        val state = _uiState.value.voiceState
        if (state == VoiceState.Listening ||
            state == VoiceState.Thinking  ||
            state == VoiceState.Speaking
        ) {
            cancelConversation()
        } else {
            startConversation()
        }
    }

    /**
     * Starts the full STT → LLM → TTS pipeline.
     *
     * @param prompt If provided, skips the STT phase and sends this text directly to the LLM.
     *               Used when the user taps a suggestion chip or types text manually.
     */
    fun startConversation(prompt: String? = null) {
        conversationJob?.cancel()

        _uiState.update {
            it.copy(
                voiceState     = if (prompt != null) VoiceState.Thinking else VoiceState.Listening,
                userTranscript = prompt ?: "",
                amaliaReply    = "",
                replyProgress  = 0f,
                isSpeaking     = false,
                errorMessage   = null,
                suggestions    = activeSuggestions,
                audioLevel     = 0f,
            )
        }

        conversationJob = viewModelScope.launch {
            try {
                // ── Phase 1: STT ──────────────────────────────────────────────
                val userText: String = if (prompt != null) {
                    prompt
                } else {
                    listenAndTranscribe()
                }

                if (userText.isBlank()) {
                    _uiState.update {
                        it.copy(
                            voiceState   = VoiceState.Idle,
                            errorMessage = "Не удалось распознать речь. Попробуй ещё раз.",
                            suggestions  = defaultSuggestions,
                        )
                    }
                    return@launch
                }

                // Show what the user said and move to Thinking state.
                _uiState.update {
                    it.copy(
                        voiceState     = VoiceState.Thinking,
                        userTranscript = userText,
                        audioLevel     = 0f,
                    )
                }

                // ── Phase 2+3: LLM + TTS (run in parallel) ───────────────────
                //
                // AIOrchestrator.processTextCommand() emits:
                //   Interim → partial LLM tokens (update reply text live)
                //   Thinking(false) → model done, TTS starting
                //   Speaking(true) → first audio chunk incoming
                //   Audio(chunk) → PCM data to play
                //   Speaking(false) → audio stream ended
                //   Finished → full reply text available, save to history
                //   Error → surface to UI
                //
                // We pipe Audio chunks to a Channel so AudioPlayer can consume
                // them in a parallel coroutine, playing audio as chunks arrive
                // while we keep collecting remaining events (no stall waiting
                // for playback to finish before getting the next LLM token).
                //
                val audioChannel = Channel<AudioChunk>(capacity = Channel.UNLIMITED)
                var fullReply    = ""

                // Parallel coroutine: plays whatever the channel delivers.
                val playJob = launch {
                    AudioPlayer.play(audioChannel.receiveAsFlow())
                }

                // Collect orchestrator events.
                orchestrator.processTextCommand(
                    text    = userText,
                    history = buildHistory(_uiState.value.userTranscript, _uiState.value.amaliaReply),
                ).collect { event ->
                    when (event) {
                        is AiResponse.Thinking -> {
                            if (event.isThinking) {
                                _uiState.update { it.copy(voiceState = VoiceState.Thinking) }
                            }
                        }

                        is AiResponse.Interim -> {
                            // While Thinking: interim is the accumulating LLM reply.
                            // While Speaking: it stays at the final text.
                            _uiState.update { it.copy(amaliaReply = event.text) }
                        }

                        is AiResponse.Speaking -> {
                            _uiState.update {
                                it.copy(
                                    voiceState = if (event.isSpeaking) VoiceState.Speaking else it.voiceState,
                                    isSpeaking = event.isSpeaking,
                                )
                            }
                        }

                        is AiResponse.Audio -> {
                            // Forward PCM chunk to the player coroutine immediately.
                            audioChannel.trySend(event.chunk)
                        }

                        is AiResponse.Finished -> {
                            fullReply = event.responseText
                            // Close channel — player will finish after draining remaining chunks.
                            audioChannel.close()
                            // Persist to conversation history.
                            saveConversation(userText, fullReply)
                        }

                        is AiResponse.Error -> {
                            audioChannel.close()
                            _uiState.update {
                                it.copy(
                                    voiceState   = VoiceState.Error,
                                    errorMessage = event.message,
                                    isSpeaking   = false,
                                    suggestions  = defaultSuggestions,
                                )
                            }
                            return@collect
                        }
                    }
                }

                // Wait for playback to drain before marking Idle.
                playJob.join()

                _uiState.update {
                    it.copy(
                        voiceState        = VoiceState.Idle,
                        audioLevel        = 0f,
                        isSpeaking        = false,
                        replyProgress     = 1f,
                        conversationCount = it.conversationCount + 1,
                        suggestions       = defaultSuggestions,
                    )
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        voiceState   = VoiceState.Idle,
                        isSpeaking   = false,
                        audioLevel   = 0f,
                        errorMessage = e.message ?: "Неизвестная ошибка",
                        suggestions  = defaultSuggestions,
                    )
                }
            }
        }
    }

    fun cancelConversation() {
        conversationJob?.cancel()
        _uiState.update {
            it.copy(
                voiceState   = VoiceState.Idle,
                audioLevel   = 0f,
                isSpeaking   = false,
                errorMessage = null,
                suggestions  = defaultSuggestions,
            )
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Opens the microphone, streams audio to Deepgram, and returns the final
     * transcript. Interim transcripts are shown in the UI as captions while
     * the user is still speaking.
     *
     * The coroutine is cancelled automatically when [cancelConversation] is
     * called because [conversationJob] is cancelled, which propagates to this
     * suspend function via structured concurrency.
     */
    private suspend fun listenAndTranscribe(): String {
        var finalText = ""
        orchestrator.sttEngine.transcribe(0f).collect { partial ->
            // Update caption in real time.
            _uiState.update { it.copy(userTranscript = partial) }
            finalText = partial
        }
        return finalText
    }

    /** Saves the user/assistant exchange to the persistent conversation history. */
    private fun saveConversation(userText: String, replyText: String) {
        viewModelScope.launch {
            try {
                val convId = conversationRepo.create(userText.take(48)).id
                conversationRepo.appendMessage(convId, ChatMessage.user(userText))
                conversationRepo.appendMessage(
                    convId,
                    ChatMessage(
                        id        = ChatMessage.newId(),
                        role      = com.my.amali.data.model.MessageRole.ASSISTANT,
                        content   = replyText,
                        timestamp = System.currentTimeMillis(),
                    )
                )
            } catch (_: Exception) { /* non-fatal — history persistence failure */ }
        }
    }

    /**
     * Builds a minimal history list from the current UI state so the LLM
     * has context for multi-turn conversations. At this point [userTranscript]
     * and [amaliaReply] hold the previous turn (if any).
     */
    private fun buildHistory(prevUser: String, prevAssistant: String): List<ChatMessage> {
        if (prevUser.isBlank() || prevAssistant.isBlank()) return emptyList()
        return listOf(
            ChatMessage.user(prevUser),
            ChatMessage(
                id        = ChatMessage.newId(),
                role      = com.my.amali.data.model.MessageRole.ASSISTANT,
                content   = prevAssistant,
                timestamp = System.currentTimeMillis(),
            )
        )
    }
}
