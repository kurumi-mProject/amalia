package com.my.amali.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.amali.core.di.ServiceLocator
import com.my.amali.data.ai.AIOrchestrator
import com.my.amali.data.ai.AiResponse
import com.my.amali.data.ai.AudioChunk
import com.my.amali.data.ai.AudioPlayer
import com.my.amali.data.ai.EngineOptions
import com.my.amali.data.model.ChatMessage
import com.my.amali.data.model.MessageRole
import com.my.amali.data.repository.ConversationRepository
import com.my.amali.data.repository.SettingsRepository
import com.my.amali.domain.entity.UserSettings
import com.my.amali.domain.entity.VoiceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Состояние главного экрана ассистента.
 *
 * @property voiceState фаза цикла: покой → слушаю → думаю → говорю.
 * @property audioLevel громкость 0..1 (микрофон при слушании, речь при ответе).
 * @property userTranscript распознанная реплика пользователя (живые субтитры).
 * @property amaliaReply текст ответа, который дописывается по мере генерации.
 * @property replyProgress 0..1 — доля проявленного ответа; 1 = ответ завершён.
 * @property micPermissionRequired true → экран должен запросить RECORD_AUDIO.
 * @property handsFree true → после ответа микрофон включается снова.
 */
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
    val micPermissionRequired: Boolean = false,
    val handsFree: Boolean = false,
) {
    /** Идёт активный цикл — кнопка работает как «Стоп». */
    val isBusy: Boolean
        get() = voiceState == VoiceState.Listening ||
            voiceState == VoiceState.Thinking ||
            voiceState == VoiceState.Speaking
}

/**
 * ViewModel главного экрана: единственный владелец голосового цикла.
 *
 * Что здесь важно для «человеческого» поведения:
 *  1. **Один диалог на сессию.** Реплики дописываются в тот же разговор,
 *     поэтому история не превращается в кашу из односообщенных диалогов,
 *     а модель видит контекст предыдущих вопросов.
 *  2. **Живая память.** В LLM уходит реальная история сессии
 *     (до [HISTORY_LIMIT] сообщений), а не последняя пара реплик.
 *  3. **Нет гонки состояний.** Все переходы идут через один
 *     [conversationJob]; повторное нажатие микрофона гасит предыдущий цикл.
 *  4. **Звук не рвётся.** Аудио-чанки уходят в отдельный проигрыватель
 *     через канал, который закрывается в `finally` — раньше при ошибке
 *     канал оставался открытым и корутина висела навсегда.
 *  5. **Hands-free.** При включённом авто-слушании после ответа микрофон
 *     включается сам — получается настоящий диалог, а не пинг-понг кнопкой.
 */
class AssistantViewModel(
    private val orchestrator: AIOrchestrator = ServiceLocator.aiOrchestrator,
    private val conversationRepo: ConversationRepository = ServiceLocator.conversationRepository,
    private val settingsRepo: SettingsRepository = ServiceLocator.settingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState(suggestions = DEFAULT_SUGGESTIONS))
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private val settings: StateFlow<UserSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings.DEFAULT)

    private val player = AudioPlayer(ServiceLocator.appContextValue)

    private var conversationJob: Job? = null

    /** Сообщения текущей сессии — контекст для модели и для истории. */
    private val sessionMessages = mutableListOf<ChatMessage>()

    /** Идентификатор разговора, в который дописывается сессия. */
    private var sessionConversationId: String? = null

    /** Микрофон уже запрашивался в этой сессии — не спамим диалогом. */
    private var micPermissionAsked = false

    init {
        viewModelScope.launch {
            val count = runCatching { conversationRepo.count() }.getOrDefault(0)
            _uiState.update { it.copy(conversationCount = count) }
        }
    }

    // ── Публичное API экрана ─────────────────────────────────────────────

    /** Скрывает приветственную карточку после первого показа. */
    fun onFirstLaunchHandled() {
        _uiState.update { it.copy(isFirstLaunch = false) }
    }

    /** Главная кнопка: старт разговора или мгновенная остановка. */
    fun toggleConversation() {
        if (_uiState.value.isBusy) cancelConversation() else startListening()
    }

    /**
     * Запускает голосовой цикл: микрофон → распознавание → ответ → озвучка.
     * Если разрешение на микрофон ещё не выдано, экран получит флаг
     * [AssistantUiState.micPermissionRequired] и покажет системный запрос.
     */
    fun startListening() {
        if (!ServiceLocator.hasMicPermission()) {
            micPermissionAsked = true
            _uiState.update {
                it.copy(
                    micPermissionRequired = true,
                    voiceState = VoiceState.Idle,
                    errorMessage = null,
                )
            }
            return
        }
        launchCycle(prompt = null)
    }

    /** Отправляет готовый текст (подсказка, «повтори», ввод с клавиатуры). */
    fun startConversation(prompt: String) {
        val clean = prompt.trim()
        if (clean.isEmpty()) return
        launchCycle(prompt = clean)
    }

    /** Пользователь ответил на системный запрос доступа к микрофону. */
    fun onMicPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(micPermissionRequired = false) }
        if (granted) {
            launchCycle(prompt = null)
        } else {
            _uiState.update {
                it.copy(
                    voiceState = VoiceState.Error,
                    errorMessage = MIC_DENIED,
                    suggestions = DEFAULT_SUGGESTIONS,
                )
            }
        }
    }

    /** Останавливает всё: распознавание, генерацию и звук. */
    fun cancelConversation() {
        conversationJob?.cancel()
        conversationJob = null
        player.stopImmediately()
        _uiState.update {
            it.copy(
                voiceState = VoiceState.Idle,
                audioLevel = 0f,
                isSpeaking = false,
                errorMessage = null,
                replyProgress = if (it.amaliaReply.isBlank()) 0f else 1f,
                suggestions = DEFAULT_SUGGESTIONS,
                handsFree = false,
                micPermissionRequired = false,
            )
        }
    }

    /** Сбрасывает баннер ошибки, не трогая остальной экран. */
    fun dismissError() {
        _uiState.update {
            if (it.voiceState == VoiceState.Error) {
                it.copy(voiceState = VoiceState.Idle, errorMessage = null)
            } else {
                it.copy(errorMessage = null)
            }
        }
    }

    /** Начинает разговор с чистого листа: новая сессия и пустой экран. */
    fun startNewSession() {
        conversationJob?.cancel()
        conversationJob = null
        player.stopImmediately()
        sessionMessages.clear()
        sessionConversationId = null
        _uiState.update {
            AssistantUiState(
                isFirstLaunch = false,
                conversationCount = it.conversationCount,
                suggestions = DEFAULT_SUGGESTIONS,
            )
        }
    }

    /** Повторяет последний вопрос пользователя. */
    fun repeatLast() {
        val lastUser = sessionMessages.lastOrNull { it.role == MessageRole.USER }?.content
            ?: _uiState.value.userTranscript
        if (lastUser.isNotBlank()) startConversation(lastUser)
    }

    override fun onCleared() {
        conversationJob?.cancel()
        player.stopImmediately()
        super.onCleared()
    }

    // ── Ядро цикла ───────────────────────────────────────────────────────

    /**
     * Единая точка запуска цикла.
     *
     * @param prompt null → полный голосовой цикл; иначе — текстовая команда.
     */
    private fun launchCycle(prompt: String?) {
        conversationJob?.cancel()
        player.stopImmediately()

        val handsFree = settings.value.autoListen

        _uiState.update {
            it.copy(
                voiceState = if (prompt == null) VoiceState.Listening else VoiceState.Thinking,
                userTranscript = prompt.orEmpty(),
                amaliaReply = "",
                replyProgress = 0f,
                isSpeaking = false,
                errorMessage = null,
                audioLevel = 0f,
                isFirstLaunch = false,
                suggestions = ACTIVE_SUGGESTIONS,
                handsFree = handsFree,
                micPermissionRequired = false,
            )
        }

        conversationJob = viewModelScope.launch {
            val options = EngineOptions.from(settings.value)
            val audioChannel = Channel<AudioChunk>(capacity = Channel.UNLIMITED)
            val historySnapshot = sessionMessages.takeLast(HISTORY_LIMIT).toList()

            // Проигрыватель живёт параллельно: звук начинает играть сразу,
            // не дожидаясь конца генерации ответа.
            val playJob = launch {
                player.play(audioChannel.receiveAsFlow()) { level ->
                    _uiState.update { state ->
                        if (state.voiceState == VoiceState.Speaking) {
                            state.copy(audioLevel = level)
                        } else {
                            state
                        }
                    }
                }
            }

            var userText = prompt.orEmpty()
            var replyText = ""
            var failed = false

            try {
                val events = if (prompt == null) {
                    orchestrator.processVoiceCommand(historySnapshot, options)
                } else {
                    orchestrator.processTextCommand(prompt, historySnapshot, options)
                }

                events.collect { event ->
                    when (event) {
                        is AiResponse.Level -> _uiState.update { state ->
                            if (state.voiceState == VoiceState.Listening) {
                                state.copy(audioLevel = event.level)
                            } else {
                                state
                            }
                        }

                        is AiResponse.PartialTranscript -> _uiState.update {
                            it.copy(userTranscript = event.text)
                        }

                        is AiResponse.Transcript -> {
                            userText = event.text
                            _uiState.update {
                                it.copy(
                                    userTranscript = event.text,
                                    voiceState = VoiceState.Thinking,
                                    audioLevel = 0f,
                                )
                            }
                        }

                        is AiResponse.Thinking -> _uiState.update { state ->
                            if (event.isThinking && state.voiceState != VoiceState.Speaking) {
                                state.copy(voiceState = VoiceState.Thinking)
                            } else {
                                state
                            }
                        }

                        is AiResponse.ReplyDelta -> {
                            replyText = event.fullText
                            _uiState.update {
                                it.copy(amaliaReply = event.fullText, replyProgress = 1f)
                            }
                        }

                        is AiResponse.Speaking -> _uiState.update { state ->
                            state.copy(
                                voiceState = if (event.isSpeaking) {
                                    VoiceState.Speaking
                                } else {
                                    state.voiceState
                                },
                                isSpeaking = event.isSpeaking,
                                audioLevel = if (event.isSpeaking) state.audioLevel else 0f,
                            )
                        }

                        is AiResponse.Audio -> audioChannel.send(event.chunk)

                        is AiResponse.Finished -> {
                            replyText = event.responseText
                            _uiState.update {
                                it.copy(amaliaReply = event.responseText, replyProgress = 1f)
                            }
                        }

                        is AiResponse.Error -> {
                            failed = true
                            _uiState.update {
                                it.copy(
                                    voiceState = VoiceState.Error,
                                    errorMessage = event.message,
                                    isSpeaking = false,
                                    audioLevel = 0f,
                                    suggestions = DEFAULT_SUGGESTIONS,
                                    handsFree = false,
                                )
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
                _uiState.update {
                    it.copy(
                        voiceState = VoiceState.Error,
                        errorMessage = e.message ?: UNKNOWN_ERROR,
                        isSpeaking = false,
                        audioLevel = 0f,
                        suggestions = DEFAULT_SUGGESTIONS,
                        handsFree = false,
                    )
                }
            } finally {
                // Канал закрывается всегда: и при ошибке, и при отмене —
                // иначе проигрыватель ждал бы данные вечно.
                audioChannel.close()
            }

            playJob.join()

            if (failed) {
                _uiState.update { it.copy(audioLevel = 0f, isSpeaking = false) }
                return@launch
            }

            persistTurn(userText, replyText)

            _uiState.update {
                it.copy(
                    voiceState = VoiceState.Idle,
                    audioLevel = 0f,
                    isSpeaking = false,
                    replyProgress = 1f,
                    suggestions = FOLLOW_UP_SUGGESTIONS,
                )
            }

            // Hands-free: продолжаем диалог без нажатий.
            if (handsFree && settings.value.autoListen && ServiceLocator.hasMicPermission()) {
                kotlinx.coroutines.delay(HANDS_FREE_GAP_MS)
                if (_uiState.value.voiceState == VoiceState.Idle) {
                    launchCycle(prompt = null)
                }
            }
        }
    }

    /**
     * Дописывает пару «вопрос-ответ» в сессию и в персистентную историю.
     * Вся сессия живёт в одном разговоре, поэтому в истории видно диалог,
     * а не набор обрывков.
     */
    private suspend fun persistTurn(userText: String, replyText: String) {
        if (userText.isBlank() || replyText.isBlank()) return

        val userMessage = ChatMessage.user(userText)
        val assistantMessage = ChatMessage(
            id = ChatMessage.newId(),
            role = MessageRole.ASSISTANT,
            content = replyText,
            timestamp = System.currentTimeMillis(),
        )
        sessionMessages += userMessage
        sessionMessages += assistantMessage
        if (sessionMessages.size > SESSION_TRIM) {
            repeat(sessionMessages.size - SESSION_TRIM) { sessionMessages.removeAt(0) }
        }

        runCatching {
            val id = sessionConversationId
            if (id == null) {
                val created = conversationRepo.create(
                    com.my.amali.data.model.Conversation.deriveTitle(userText)
                )
                sessionConversationId = created.id
                conversationRepo.appendMessage(created.id, userMessage)
                conversationRepo.appendMessage(created.id, assistantMessage)
                val count = conversationRepo.count()
                _uiState.update { it.copy(conversationCount = count) }
            } else {
                conversationRepo.appendMessage(id, userMessage)
                conversationRepo.appendMessage(id, assistantMessage)
            }
        }
    }

    private companion object {
        val DEFAULT_SUGGESTIONS = listOf("Привет", "Что ты умеешь?", "Который час?")
        val ACTIVE_SUGGESTIONS = listOf("Стоп")
        val FOLLOW_UP_SUGGESTIONS = listOf("Повтори", "Расскажи подробнее", "Спасибо")

        const val MIC_DENIED =
            "Без доступа к микрофону я не слышу. Разреши доступ в настройках приложения."
        const val UNKNOWN_ERROR = "Что-то пошло не так. Попробуй ещё раз."

        /** Сколько сообщений сессии уходит в модель как контекст. */
        const val HISTORY_LIMIT = 12

        /** Максимальный размер памяти сессии. */
        const val SESSION_TRIM = 40

        /** Пауза между ответом и новым слушанием в hands-free режиме. */
        const val HANDS_FREE_GAP_MS = 450L
    }
}
