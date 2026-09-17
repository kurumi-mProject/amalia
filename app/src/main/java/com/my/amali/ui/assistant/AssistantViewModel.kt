package com.my.amali.ui.assistant

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.amali.R
import com.my.amali.core.di.ServiceLocator
import com.my.amali.data.ai.AIOrchestrator
import com.my.amali.data.ai.AiResponse
import com.my.amali.data.ai.AudioChunk
import com.my.amali.data.ai.AudioPlayer
import com.my.amali.data.ai.EngineOptions
import com.my.amali.data.model.ChatMessage
import com.my.amali.data.model.Conversation
import com.my.amali.data.model.DeviceStatus
import com.my.amali.data.model.MessageRole
import com.my.amali.data.repository.ConversationRepository
import com.my.amali.data.repository.SettingsRepository
import com.my.amali.domain.entity.UserSettings
import com.my.amali.domain.entity.VoiceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
 * @property activeTools список инструментов, выполняющихся прямо сейчас.
 *   Появляется во время фазы «думаю» и исчезает после возврата результатов.
 * @property lastToolReports список завершённых инструментов последнего цикла —
 *   для сводки «что сделала Амалия». UI сворачивает её в одну строку.
 * @property contextCompressed true → часть диалога модель уже помнит
 *   пересказом, а не дословно. UI показывает это отдельным маркером:
 *   без него «память короткая» выглядит как баг, а не как осознанный режим.
 * @property contextMessageCount сколько реплик сейчас уходит в модель дословно.
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
    val activeTools: List<ToolActivity> = emptyList(),
    val lastToolReports: List<ToolReport> = emptyList(),
    val contextCompressed: Boolean = false,
    val contextMessageCount: Int = 0,
) {
    /** Идёт активный цикл — кнопка работает как «Стоп». */
    val isBusy: Boolean
        get() = voiceState == VoiceState.Listening ||
            voiceState == VoiceState.Thinking ||
            voiceState == VoiceState.Speaking
}

/**
 * Текущий запущенный инструмент — для UI-индикатора во время фазы «думаю».
 *
 * @property name имя из реестра (set_wifi, set_brightness…).
 * @property humanLabel короткая подпись для UI («включаю Wi-Fi»).
 */
data class ToolActivity(
    val name: String,
    val humanLabel: String,
)

/**
 * Результат выполненного инструмента для сводки «что сделано».
 *
 * @property performedAtMillis когда закончил — нужно, чтобы UI мог скрывать
 *   устаревшую сводку, не заваливая карточку ответа.
 */
data class ToolReport(
    val name: String,
    val ok: Boolean,
    val summary: String,
    val performedAtMillis: Long = System.currentTimeMillis(),
)

/**
 * ViewModel главного экрана: единственный владелец голосового цикла.
 *
 * Что здесь важно для «человеческого» поведения:
 *  1. **Один диалог на сессию.** Реплики дописываются в тот же разговор,
 *     поэтому история не превращается в кашу из односообщенных диалогов,
 *     а модель видит контекст предыдущих вопросов.
 *  2. **Живая память с пересказом.** В LLM уходит хвост диалога, а всё, что
 *     старше, — сжатое резюме ([Conversation.contextSummary]). Резюме
 *     ограничено по размеру и перезаписывается, а не накапливается.
 *  3. **Нет гонки состояний.** Все переходы идут через один
 *     [conversationJob]; повторное нажатие микрофона гасит предыдущий цикл.
 *  4. **Звук не рвётся.** Аудио-чанки уходят в проигрыватель через канал,
 *     который закрывается в `finally` — раньше при ошибке канал оставался
 *     открытым и корутина висела навсегда.
 *  5. **Hands-free.** При включённом авто-слушании после ответа микрофон
 *     включается сам — получается настоящий диалог, а не пинг-понг кнопкой.
 *  6. **Functions AI can call.** `ToolRunning`/`ToolCompleted` проброшены в UI:
 *     пользователь видит, что Амалия делает действие, а не молчит.
 *  7. **Подсказки — из ресурсов.** Раньше они были хардкодом на русском и
 *     ломали локализацию на всех языках кроме одного.
 */
class AssistantViewModel(
    private val orchestrator: AIOrchestrator = ServiceLocator.aiOrchestrator,
    private val conversationRepo: ConversationRepository = ServiceLocator.conversationRepository,
    private val settingsRepo: SettingsRepository = ServiceLocator.settingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AssistantUiState(suggestions = defaultSuggestions()),
    )
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private val settings: StateFlow<UserSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings.DEFAULT)

    private val player = AudioPlayer()

    private var conversationJob: Job? = null

    /** Сообщения текущей сессии — контекст для модели и для истории. */
    private val sessionMessages = mutableListOf<ChatMessage>()

    /** Краткое резюме начала диалога; null пока сжимать нечего. */
    private var conversationSummary: String? = null

    /** Сколько первых сообщений [sessionMessages] уже закрыто резюме. */
    private var summarizedCount = 0

    /** Идёт ли сейчас генерация резюме (не запускаем две одновременно). */
    private var summaryInProgress = false

    /** Идентификатор разговора, в который дописывается сессия. */
    private var sessionConversationId: String? = null

    /** Микрофон уже запрашивался в этой сессии — не спамим диалогом. */
    private var micPermissionAsked = false

    init {
        viewModelScope.launch {
            val conversations = runCatching { conversationRepo.recentSnapshot(1) }.getOrDefault(emptyList())
            val count = conversations.size
            val existing = conversations.firstOrNull()
            // Продолжаем вчерашний диалог, а не начинаем новый с нуля:
            // приложение перезапускается — память остаётся.
            if (existing != null && settings.value.resumeLastSession) {
                sessionConversationId = existing.id
                sessionMessages += existing.messages.takeLast(SESSION_TRIM)
                conversationSummary = existing.contextSummary
                summarizedCount = existing.summarizedCount
            }
            _uiState.update {
                it.copy(
                    conversationCount = count,
                    contextCompressed = !conversationSummary.isNullOrBlank(),
                    contextMessageCount = historyForModel().size,
                )
            }
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
     * Вызывается при касании кнопки микрофона (до отпускания).
     * Открывает WS-соединение к Deepgram заранее, пока палец ещё на кнопке.
     * К моменту onClick (~150-300 мс) соединение уже готово — нет задержки.
     */
    fun warmupStt() {
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            runCatching { orchestrator.sttEngine.preconnect() }
        }
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
        // «Повтори» — это не запрос к модели, а повтор последнего вопроса.
        if (clean == localized(R.string.suggestion_repeat) || clean == localized(R.string.assistant_repeat)) {
            repeatLast()
            return
        }
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
                    errorMessage = localized(R.string.assistant_mic_denied),
                    suggestions = defaultSuggestions(),
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
                suggestions = defaultSuggestions(),
                handsFree = false,
                micPermissionRequired = false,
                activeTools = emptyList(),
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
        conversationSummary = null
        summarizedCount = 0
        _uiState.update {
            AssistantUiState(
                isFirstLaunch = false,
                conversationCount = it.conversationCount,
                suggestions = defaultSuggestions(),
            )
        }
    }

    /** Повторяет последний вопрос пользователя. */
    fun repeatLast() {
        val lastUser = sessionMessages.lastOrNull { it.role == MessageRole.USER }?.content
            ?: _uiState.value.userTranscript
        if (lastUser.isNotBlank()) launchCycle(prompt = lastUser)
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
                suggestions = listOf(localized(R.string.assistant_stop)),
                handsFree = handsFree,
                micPermissionRequired = false,
                activeTools = emptyList(),
                lastToolReports = emptyList(),
                contextMessageCount = historyForModel().size,
            )
        }

        conversationJob = viewModelScope.launch {
            val deviceStatus = runCatching {
                ServiceLocator.systemControllers.refresh()
            }.getOrDefault(DeviceStatus.Offline)
            val options = EngineOptions.from(settings.value).copy(
                deviceStatus = deviceStatus,
                conversationSummary = conversationSummary,
            )
            val audioChannel = Channel<AudioChunk>(capacity = Channel.UNLIMITED)
            val historySnapshot = historyForModel()

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
            val reports = mutableListOf<ToolReport>()
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

                        is AiResponse.ToolRunning -> _uiState.update { state ->
                            val label = humanLabelFor(event.toolName, event.arguments)
                            // Заменяем, если такой же инструмент уже крутится (анти-фликер)
                            val without = state.activeTools.filter { it.name != event.toolName }
                            state.copy(
                                voiceState = VoiceState.Thinking,
                                activeTools = without + ToolActivity(event.toolName, label),
                            )
                        }

                        is AiResponse.ToolCompleted -> {
                            // Сборка отчёта — ВНЕ update{}: лямбда состояния
                            // может быть повторена при гонке CAS, и тогда
                            // действие записалось бы в список дважды.
                            val summary = if (event.ok) {
                                humanSuccessSummary(event.toolName, event.output)
                            } else {
                                event.errorMessage ?: localized(R.string.assistant_action_failed)
                            }
                            val report = ToolReport(event.toolName, event.ok, summary)
                            reports += report
                            _uiState.update { state ->
                                state.copy(
                                    activeTools = state.activeTools.filter {
                                        it.name != event.toolName
                                    },
                                    lastToolReports = state.lastToolReports + report,
                                )
                            }
                        }

                        is AiResponse.ReplyDelta -> {
                            replyText = event.fullText
                            _uiState.update {
                                it.copy(
                                    amaliaReply = event.fullText,
                                    replyProgress = 1f,
                                    activeTools = emptyList(),
                                )
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
                                it.copy(
                                    amaliaReply = event.responseText,
                                    replyProgress = 1f,
                                    activeTools = emptyList(),
                                )
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
                                    suggestions = defaultSuggestions(),
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
                        errorMessage = e.message ?: localized(R.string.assistant_generic_error),
                        isSpeaking = false,
                        audioLevel = 0f,
                        suggestions = defaultSuggestions(),
                        handsFree = false,
                    )
                }
            } finally {
                audioChannel.close()
            }

            playJob.join()

            if (failed) {
                _uiState.update {
                    it.copy(audioLevel = 0f, isSpeaking = false, activeTools = emptyList())
                }
                return@launch
            }

            persistTurn(userText, replyText, reports.map { it.summary })

            _uiState.update {
                it.copy(
                    voiceState = VoiceState.Idle,
                    audioLevel = 0f,
                    isSpeaking = false,
                    replyProgress = 1f,
                    suggestions = followUpSuggestions(reports.isNotEmpty()),
                    activeTools = emptyList(),
                    lastToolReports = reports.takeLast(MAX_REPORTS),
                )
            }

            // Прячем сводку действий, когда она уже не к месту: ответ следующий —
            // действия прежних реплик больше не относятся к делу.
            if (reports.isNotEmpty()) {
                launch {
                    delay(TOOL_REPORT_DISPLAY_MS)
                    if (_uiState.value.voiceState == VoiceState.Idle) {
                        _uiState.update { it.copy(lastToolReports = emptyList()) }
                    }
                }
            }

            // Hands-free: продолжаем диалог без нажатий.
            //
            // Берём handsFree из CURRENT-состояния, а не из локального
            // снимка: пользователь мог нажать «Стоп» во время ответа
            // (cancelConversation() сбрасывает handsFree=false), и тогда
            // локальный снимок устарел → микрофон перезапускался бы
            // после паузы против воли пользователя.
            val currentHandsFree = _uiState.value.handsFree
            val autoListenEnabled = settings.value.autoListen
            if (currentHandsFree && autoListenEnabled && ServiceLocator.hasMicPermission()) {
                delay(HANDS_FREE_GAP_MS)
                if (_uiState.value.voiceState == VoiceState.Idle && _uiState.value.handsFree) {
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
    private suspend fun persistTurn(userText: String, replyText: String, actions: List<String>) {
        if (userText.isBlank() || replyText.isBlank()) return

        val userMessage = ChatMessage.user(userText)
        val assistantMessage = ChatMessage.assistant(replyText, actions)
        sessionMessages += userMessage
        sessionMessages += assistantMessage
        if (sessionMessages.size > SESSION_TRIM) {
            repeat(sessionMessages.size - SESSION_TRIM) { sessionMessages.removeAt(0) }
            summarizedCount = 0
        }

        val turn = listOf(userMessage, assistantMessage)

        runCatching {
            val id = sessionConversationId
            if (id == null) {
                val created = conversationRepo.create(
                    Conversation.deriveTitle(userText),
                )
                sessionConversationId = created.id
                conversationRepo.appendMessages(created.id, turn)
                // count() — suspend: снаружи update{}, иначе вызов не скомпилируется
                val newCount = conversationRepo.count()
                _uiState.update { it.copy(conversationCount = newCount) }
            } else {
                conversationRepo.appendMessages(id, turn)
            }
        }

        // Каждые SUMMARY_EVERY сообщений — пересказываем начало диалога.
        if (sessionMessages.size - summarizedCount >= SUMMARY_EVERY) {
            refreshSummary()
        }
    }

    /**
     * Обновляет резюме диалога.
     *
     * Три вещи, из-за которых раньше «сжатие» было вреднее, чем помощь:
     *  1. резюме **дописывалось** в одно поле без предела → контекст рос,
     *     а не сжимался. Теперь每次 новый пересказ перезаписывает прежний;
     *  2. генерация стартовала в `viewModelScope.launch` и не дожидалась
     *     результата → следующий цикл уходил в модель со старым резюме
     *     (или вообще без него). Теперь вызов ждём, но с таймаутом: медленное
     *     резюме не имеет права задерживать разговор;
     *  3. результат не сохранялся → после перезапуска приложения «память»
     *     обнулялась. Теперь резюме живёт в [Conversation.contextSummary].
     */
    private suspend fun refreshSummary() {
        if (summaryInProgress) return
        summaryInProgress = true
        try {
            val pending = sessionMessages.drop(summarizedCount)
            if (pending.isEmpty()) return
            val previous = conversationSummary
            val prompt = buildString {
                append(
                    if (previous.isNullOrBlank()) {
                        "Сожми этот диалог в 3 коротких предложения: только темы, факты и решения."
                    } else {
                        "Прежнее резюме: $previous\nДополни его новыми репликами и верни ОДНО резюме " +
                            "в 3 коротких предложения."
                    },
                )
                append('\n')
                pending.forEach { msg ->
                    when (msg.role) {
                        MessageRole.USER -> append("П: ${msg.content}\n")
                        MessageRole.ASSISTANT -> append("А: ${msg.content}\n")
                        else -> Unit
                    }
                }
            }

            val generated = StringBuilder()
            withTimeoutOrNull(SUMMARY_TIMEOUT_MS) {
                runCatching {
                    orchestrator.textOnlyResponse(prompt, emptyList(), EngineOptions.from(settings.value))
                        .collect { piece -> generated.append(piece) }
                }
            }

            val newSummary = generated.toString().trim().take(SUMMARY_MAX_CHARS)
            if (newSummary.isNotBlank()) {
                conversationSummary = newSummary
                summarizedCount = sessionMessages.size
                _uiState.update {
                    it.copy(
                        contextCompressed = true,
                        contextMessageCount = historyForModel().size,
                    )
                }
                val id = sessionConversationId
                if (id != null) {
                    runCatching { conversationRepo.setSummary(id, newSummary, summarizedCount) }
                }
            }
        } finally {
            summaryInProgress = false
        }
    }

    /**
     * Хвост диалога, который уходит в модель.
     *
     * Когда есть резюме, дословных сообщений нужно заметно меньше: модель уже
     * знает, о чём речь. Короткий промпт — это не только экономия токенов, но и
     * ощутимо более быстрый первый токен.
     */
    private fun historyForModel(): List<ChatMessage> =
        sessionMessages.takeLast(
            if (conversationSummary.isNullOrBlank()) HISTORY_LIMIT else HISTORY_LIMIT_WITH_SUMMARY,
        )

    // ── Подписи для UI ───────────────────────────────────────────────────

    /**
     * Превращает имя инструмента + аргументы в короткую подпись для UI.
     *
     * Примеры:
     *   set_wifi({enabled=true}) → «включаю Wi-Fi»
     *   set_brightness({percent=30}) → «ставлю яркость 30%»
     *   web_search({query="…"}) → «ищу в интернете…»
     *
     * Подписи живут в коде, а не в ресурсах, и это осознанно: они относятся
     * к персоне Амалии (русский голос, русские инфинитивы), а не к локализации
     * интерфейса. Ответ модели при этом идёт на языке пользователя.
     */
    private fun humanLabelFor(toolName: String, args: Map<String, Any?>): String {
        fun str(a: Any?): String = a?.toString().orEmpty()
        fun on(key: String): Boolean = (args[key] as? Boolean) == true
        return when (toolName) {
            "set_wifi" -> if (on("enabled")) "включаю Wi-Fi" else "выключаю Wi-Fi"
            "set_bluetooth" -> if (on("enabled")) "включаю Bluetooth" else "выключаю Bluetooth"
            "set_brightness" -> "ставлю яркость ${args["percent"] ?: "?"}%"
            "set_volume" -> "ставлю громкость ${args["percent"] ?: "?"}%"
            "volume_up" -> "прибавляю громкость"
            "volume_down" -> "убавляю громкость"
            "set_flashlight" -> if (on("enabled")) "включаю фонарик" else "выключаю фонарик"
            "set_timer" -> {
                val seconds = (args["seconds"] as? Number)?.toInt() ?: 0
                if (seconds >= 60) "ставлю таймер на ${seconds / 60} мин"
                else "ставлю таймер на $seconds сек"
            }
            "set_alarm" -> {
                val time = str(args["time"])
                if (time.isNotEmpty()) "ставлю будильник на $time" else "ставлю будильник"
            }
            "open_app" -> "открываю ${str(args["name"]).ifEmpty { "приложение" }}"
            "open_settings" -> "открываю настройки"
            "web_search" -> "ищу «${str(args["query"]).take(28)}»"
            "make_call" -> "набираю номер"
            "send_sms" -> "пишу SMS"
            "take_photo" -> "открываю камеру"
            "open_youtube" -> "открываю YouTube"
            "get_current_time" -> "узнаю время"
            "get_device_status" -> "проверяю устройство"
            "get_battery_level" -> "смотрю батарею"
            "get_location_status" -> "проверяю геолокацию"
            "get_weather" -> "узнаю погоду"
            "search_history" -> "ищу в истории"
            "get_recent_conversations" -> "смотрю историю"
            "clear_history" -> "очищаю историю"
            "change_language" -> "меняю язык на ${str(args["language"])}"
            "toggle_auto_listen" ->
                if (on("enabled")) "включаю автослушание" else "выключаю автослушание"
            else -> "выполняю $toolName"
        }
    }

    /**
     * Сводка результата — она же попадает в историю как «что сделано»,
     * поэтому восстановленный диалог показывает действия, а не только текст.
     */
    private fun humanSuccessSummary(toolName: String, output: String): String = when (toolName) {
        "set_wifi" -> if (output.contains("true")) "Wi-Fi включён" else "Wi-Fi выключен"
        "set_bluetooth" -> if (output.contains("true")) "Bluetooth включён" else "Bluetooth выключен"
        "set_brightness" -> "яркость установлена"
        "set_volume", "volume_up", "volume_down" -> "громкость изменена"
        "set_flashlight" -> if (output.contains("true")) "фонарик включён" else "фонарик выключен"
        "set_timer" -> "таймер поставлен"
        "set_alarm" -> "будильник поставлен"
        "open_app" -> "приложение открыто"
        "open_settings" -> "настройки открыты"
        "web_search" -> "поиск запущен"
        "make_call" -> "набор открыт"
        "send_sms" -> "SMS открыт"
        "take_photo", "open_youtube" -> "готово"
        "get_current_time" -> "время названо"
        "get_device_status" -> "статус проверен"
        "get_battery_level" -> "батарея проверена"
        "get_location_status" -> "локация проверена"
        "get_weather" -> "погода получена"
        "search_history" -> "история найдена"
        "get_recent_conversations" -> "история просмотрена"
        "clear_history" -> "история очищена"
        "change_language" -> "язык изменён"
        "toggle_auto_listen" -> "настройка изменена"
        else -> "готово"
    }

    // ── Строки ───────────────────────────────────────────────────────────

    private fun localized(@StringRes id: Int): String =
        runCatching { ServiceLocator.appContextValue.getString(id) }.getOrDefault("")

    private fun defaultSuggestions(): List<String> = listOf(
        localized(R.string.suggestion_hello),
        localized(R.string.suggestion_about),
        localized(R.string.suggestion_time),
    )

    /**
     * Подсказки после ответа.
     *
     * Разные для «просто поговорили» и «она что-то сделала»: после действий
     * просят проверить результат или откатить, а не «расскажи подробнее».
     */
    private fun followUpSuggestions(hadActions: Boolean): List<String> = if (hadActions) {
        listOf(
            localized(R.string.suggestion_check),
            localized(R.string.suggestion_undo),
            localized(R.string.suggestion_more),
        )
    } else {
        listOf(
            localized(R.string.suggestion_repeat),
            localized(R.string.suggestion_more),
            localized(R.string.suggestion_thanks),
        )
    }

    private companion object {
        /** Сколько сообщений сессии уходит в модель как контекст. */
        const val HISTORY_LIMIT = 12

        /** Когда есть резюме диалога — дословных реплик нужно гораздо меньше. */
        const val HISTORY_LIMIT_WITH_SUMMARY = 6

        /** Максимальный размер памяти сессии. */
        const val SESSION_TRIM = 40

        /** Каждые N новых сообщений — пересказываем начало диалога. */
        const val SUMMARY_EVERY = 10

        /** Потолок длины резюме: оно обязано оставаться коротким. */
        const val SUMMARY_MAX_CHARS = 600

        /** Резюме не имеет права задерживать разговор. */
        const val SUMMARY_TIMEOUT_MS = 4_000L

        /** Пауза между ответом и новым слушанием в hands-free режиме. */
        const val HANDS_FREE_GAP_MS = 1_500L

        /** Сколько последних сводок инструментов держать в UI. */
        const val MAX_REPORTS = 8

        /** Как долго показывать сводку «что сделано» в UI. */
        const val TOOL_REPORT_DISPLAY_MS = 12_000L
    }
}
