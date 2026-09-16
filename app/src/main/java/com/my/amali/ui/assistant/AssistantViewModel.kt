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
import kotlinx.coroutines.delay
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
 * @property activeTools список инструментов, выполняющихся прямо сейчас.
 *   Появляется во время фазы «думаю» и исчезает после возврата результатов.
 * @property lastToolReports список завершённых инструментов последнего цикла —
 *   для короткой сводки «что сделала Амалия».
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
 */
data class ToolReport(
    val name: String,
    val ok: Boolean,
    val summary: String,
)

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
 *  6. **Functions AI can call.** `ToolRunning`/`ToolCompleted` события
 *     проброшены в UI: пользователь видит, что Амалия делает действие.
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

    private val player = AudioPlayer()

    private var conversationJob: Job? = null

    /** Сообщения текущей сессии — контекст для модели и для истории. */
    private val sessionMessages = mutableListOf<ChatMessage>()
    
    /** Краткое резюме истории (обновляется каждые 10 сообщений). */
    private var conversationSummary: String? = null

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
     * Вызывается при касании кнопки микрофона (до отпускания).
     * Открывает WS соединение к Deepgram заранее, пока палец ещё на кнопке.
     * К моменту onClick (~150-300ms) соединение уже готово — нет задержки.
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
                activeTools = emptyList(),
                lastToolReports = emptyList(),
            )
        }

        conversationJob = viewModelScope.launch {
            val deviceStatus = runCatching {
                ServiceLocator.systemControllers.refresh()
            }.getOrDefault(com.my.amali.data.model.DeviceStatus.Offline)
            val options = EngineOptions.from(settings.value).copy(
                deviceStatus = deviceStatus,
                conversationSummary = conversationSummary
            )
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

                        is AiResponse.ToolRunning -> _uiState.update { state ->
                            val label = humanLabelFor(event.toolName, event.arguments)
                            // Заменяем, если такой же инструмент уже крутится (анти-фликер)
                            val without = state.activeTools.filter { it.name != event.toolName }
                            state.copy(
                                voiceState = VoiceState.Thinking,
                                activeTools = without + ToolActivity(event.toolName, label),
                            )
                        }

                        is AiResponse.ToolCompleted -> _uiState.update { state ->
                            val summary = if (event.ok) {
                                humanSuccessSummary(event.toolName, event.output)
                            } else {
                                event.errorMessage ?: "не получилось"
                            }
                            val newActive = state.activeTools.filter { it.name != event.toolName }
                            val newReports = state.lastToolReports + ToolReport(
                                name = event.toolName,
                                ok = event.ok,
                                summary = summary,
                            )
                            state.copy(
                                activeTools = newActive,
                                lastToolReports = newReports.take(MAX_REPORTS),
                            )
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
                audioChannel.close()
            }

            playJob.join()

            if (failed) {
                _uiState.update { it.copy(audioLevel = 0f, isSpeaking = false, activeTools = emptyList()) }
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
                    activeTools = emptyList(),
                )
            }

            // Прячем сводку tools после паузы — даём пользователю прочитать.
            if (_uiState.value.lastToolReports.isNotEmpty()) {
                launch {
                    delay(TOOL_REPORT_DISPLAY_MS)
                    _uiState.update { it.copy(lastToolReports = emptyList()) }
                }
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
        
        // Каждые 10 сообщений — генерируем резюме для сжатия контекста
        if (sessionMessages.size >= 10 && sessionMessages.size % 10 == 0) {
            generateSummary()
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

    // ── Подписи для UI ───────────────────────────────────────────────────

    /**
     * Превращает имя инструмента + аргументы в короткую русскую подпись для UI.
     *
     * Примеры:
     *   set_wifi({enabled=true}) → "включаю Wi-Fi"
     *   set_brightness({percent=30}) → "ставлю яркость 30%"
     *   set_timer({seconds=300}) → "ставлю таймер 5 мин"
     *   web_search({query="..."}) → "ищу в интернете…"
     */
    private fun humanLabelFor(toolName: String, args: Map<String, Any?>): String {
        fun str(a: Any?): String = a?.toString().orEmpty()
        return when (toolName) {
            "set_wifi" -> {
                val on = (args["enabled"] as? Boolean) == true
                if (on) "включаю Wi-Fi" else "выключаю Wi-Fi"
            }
            "set_bluetooth" -> {
                val on = (args["enabled"] as? Boolean) == true
                if (on) "включаю Bluetooth" else "выключаю Bluetooth"
            }
            "set_brightness" -> "ставлю яркость ${args["percent"] ?: "?"}%"
            "set_volume" -> "ставлю громкость ${args["percent"] ?: "?"}%"
            "set_flashlight" -> {
                val on = (args["enabled"] as? Boolean) == true
                if (on) "включаю фонарик" else "выключаю фонарик"
            }
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
            "web_search" -> "ищу «${str(args["query"])}»"
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
            "toggle_auto_listen" -> {
                val on = (args["enabled"] as? Boolean) == true
                if (on) "включаю автослушание" else "выключаю автослушание"
            }
            else -> "выполняю $toolName"
        }
    }

    /**
     * Русская сводка результата, которая показывается пользователю
     * под ответом Амалии.
     */
    private fun humanSuccessSummary(toolName: String, output: String): String = when (toolName) {
        "set_wifi" -> if (output.contains("true")) "Wi-Fi включён" else "Wi-Fi выключен"
        "set_bluetooth" -> if (output.contains("true")) "Bluetooth включён" else "Bluetooth выключен"
        "set_brightness" -> "яркость установлена"
        "set_volume" -> "громкость установлена"
        "set_flashlight" -> if (output.contains("true")) "фонарик включён" else "фонарик выключен"
        "set_timer" -> "таймер поставлен"
        "set_alarm" -> "будильник поставлен"
        "open_app" -> "приложение открыто"
        "open_settings" -> "настройки открыты"
        "web_search" -> "поиск запущен"
        "make_call" -> "набор открыт"
        "send_sms" -> "SMS открыт"
        "take_photo", "open_youtube" -> "готово"
        "get_current_time" -> "время узнала"
        "get_device_status" -> "статус проверен"
        "get_battery_level" -> "батарею посмотрела"
        "get_location_status" -> "локацию проверила"
        "get_weather" -> "погоду узнала"
        "search_history" -> "историю поискала"
        "get_recent_conversations" -> "историю посмотрела"
        "clear_history" -> "история очищена"
        "change_language" -> "язык изменён"
        "toggle_auto_listen" -> "настройка изменена"
        else -> "готово"
    }
    
    /**
     * Генерирует краткое резюме истории (3-5 предложений) чтобы не переполнять контекст.
     * Вызывается каждые 10 сообщений.
     */
    private fun generateSummary() {
        viewModelScope.launch {
            val prompt = buildString {
                append("Сожми следующую историю диалога в 3-5 кратких предложений. ")
                append("Сохрани только ключевые темы и факты:\n\n")
                sessionMessages.takeLast(10).forEach { msg ->
                    when (msg.role) {
                        MessageRole.USER -> append("Пользователь: ${msg.content}\n")
                        MessageRole.ASSISTANT -> append("Ассистент: ${msg.content}\n")
                        else -> {}
                    }
                }
            }

            val collected = StringBuilder()
            runCatching {
                orchestrator.llmEngine.generateResponse(
                    prompt = prompt,
                    history = emptyList(),
                    options = EngineOptions.from(settings.value),
                ).collect { delta ->
                    collected.append(delta)
                }
            }

            val newSummary = collected.toString().trim()
            if (newSummary.isNotBlank()) {
                conversationSummary = if (conversationSummary != null) {
                    "${conversationSummary}\n$newSummary"
                } else {
                    newSummary
                }
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
        const val HANDS_FREE_GAP_MS = 1500L

        /** Сколько последних сводок инструментов держать в UI. */
        const val MAX_REPORTS = 3

        /** Как долго показывать сводку «что сделано» в UI. */
        const val TOOL_REPORT_DISPLAY_MS = 5_000L
    }
}
