package com.my.amali.ui.assistant

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.my.amali.data.ai.AmaliaLog
import androidx.lifecycle.viewModelScope
import com.my.amali.R
import com.my.amali.core.di.ServiceLocator
import com.my.amali.data.ai.AIOrchestrator
import com.my.amali.data.ai.AiResponse
import com.my.amali.data.ai.AudioChunk
import com.my.amali.data.ai.AudioPlayer
import com.my.amali.data.ai.EngineOptions
import com.my.amali.data.ai.KnownApp
import com.my.amali.data.model.ChatMessage
import com.my.amali.data.model.Conversation
import com.my.amali.data.model.DeviceStatus
import com.my.amali.data.model.MessageRole
import com.my.amali.data.repository.ConversationRepository
import com.my.amali.data.repository.SettingsRepository
import com.my.amali.domain.entity.UserSettings
import com.my.amali.domain.entity.VoiceState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
 * @property micPermissionDenied true → пользователь отказал в доступе к
 *   микрофону. UI в этом случае показывает не только «Повторить», но и
 *   «Открыть настройки»: системный диалог второй раз не появится, и без
 *   этой кнопки человек упирается в кнопку, которая больше не работает.
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
    val micPermissionDenied: Boolean = false,
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
    private val conversationRepo: ConversationRepository = ServiceLocator.conversationRepository,
    private val settingsRepo: SettingsRepository = ServiceLocator.settingsRepository,
) : ViewModel() {

    /**
     * Оркестратор конвейера STT → LLM → TTS.
     *
     * Читается из [ServiceLocator] при каждом обращении, а не кэшируется
     * в конструкторе: ключи и модели теперь настраиваются пользователем во
     * время работы, и после их смены конвейер обязан подняться заново со
     * свежей конфигурацией. Кэш в поле означал бы «настройки сохранились,
     * но применяются только после перезапуска приложения».
     */
    private val orchestrator: AIOrchestrator
        get() = ServiceLocator.aiOrchestrator

    private val _uiState = MutableStateFlow(
        AssistantUiState(suggestions = defaultSuggestions()),
    )
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private val settings: StateFlow<UserSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings.DEFAULT)

    private val player = AudioPlayer()

    private var conversationJob: Job? = null

    /**
     * Проигрывание текущего цикла.
     *
     * Раньше эта корутина жила сама по себе: `playJob.join()` ждал её до
     * конца, а прибить её было нечем — старый плеер висел в `viewModelScope`
     * и продолжал держать `AudioTrack`, пока новый цикл уже начинал говорить.
     * Два трека на одном устройстве звучат как два наложенных голоса.
     */
    private var playJob: Job? = null

    /**
     * Пользователь нажал «Стоп» во время ответа.
     *
     * Отдельный флаг нужен потому, что сам переход [VoiceState.Idle]
     * наступает и в штатном конце реплики. Без него hands-free смотрел на
     * «состояние покоя» и запускал микрофон заново — сразу после того, как
     * человек попросил замолчать.
     */
    private var stoppedByUser: Boolean = false

    /**
     * Скоуп воспроизведения — **независимый** от корутин разговора.
     *
     * [SupervisorJob]: падение плеера в одном прогоне не должно утаскивать
     * остальные. Скоуп живёт ровно столько, сколько ViewModel, и гасится
     * только явно — по кнопке «Стоп» или при сбросе сессии.
     *
     * Почему это отдельный скоуп, а не `viewModelScope`: `conversationJob`
     * отменяется при каждом новом вопросе, и если плеер жил бы в том же
     * дереве, отмена разговора обрывала бы уже начатую речь. Именно так
     * и выглядел исходный баг «модель ответила — озвучка отменилась».
     */
    private val ttsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Сообщения текущей сессии — контекст для модели и для истории. */
    private val sessionMessages = mutableListOf<ChatMessage>()

    /** Краткое резюме начала диалога; null пока сжимать нечего. */
    private var conversationSummary: String? = null

    /** Сколько первых сообщений [sessionMessages] уже закрыто резюме. */
    private var summarizedCount = 0

    /**
     * Сколько сообщений было в сессии, когда резюме обновилось в последний раз.
     *
     * Существует отдельно от [summarizedCount], потому что тот «плывёт»:
     * [summarizedCount] — это индекс в текущем списке (он сдвигается, когда
     * старые сообщения выбрасываются из буфера), а это — бухгалтерия по
     * количеству **новых** реплик с момента последнего пересказа.
     *
     * Без такого разделения и получался «вечный цикл сжатия»: индекс
     * сбрасывался при обрезке буфера, условие «набралось 10 сообщений»
     * срабатывало снова, и диалог пересказывался на каждом ходу — модель
     * вместо ответа сжимала собственную же сводку.
     */
    private var messagesSinceSummary = 0

    /** Идёт ли сейчас генерация резюме (не запускаем две одновременно). */
    private var summaryInProgress = false

    /** Идентификатор разговора, в который дописывается сессия. */
    private var sessionConversationId: String? = null

    /** Микрофон уже запрашивался в этой сессии — не спамим диалогом. */
    private var micPermissionAsked = false

    init {
        viewModelScope.launch {
            // Срок хранения истории применяется при каждом старте ассистента:
            // настройка «хранить 7/30/90 дней» раньше была декоративной.
            runCatching {
                conversationRepo.applyRetention(settings.first().dataRetentionDays)
            }
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
                // После перезапуска считаем, что «новых» сообщений нет: иначе
                // первая же реплика запустила бы пересказ уже сжатого диалога.
                messagesSinceSummary = 0
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
            // Разрешение выдано — сразу начинаем слушать: пользователь уже
            // выразил намерение тапом по микрофону, второй тап ему не нужен.
            _uiState.update { it.copy(micPermissionDenied = false) }
            launchCycle(prompt = null)
        } else {
            _uiState.update {
                it.copy(
                    voiceState = VoiceState.Error,
                    errorMessage = localized(R.string.assistant_mic_denied),
                    // Флаг для UI: показать «Открыть настройки». Сам экран
                    // не угадывает причину по тексту ошибки.
                    micPermissionDenied = true,
                    suggestions = defaultSuggestions(),
                )
            }
        }
    }

    /**
     * Останавливает всё: распознавание, генерацию и звук.
     *
     * ## Почему здесь больше нет `player.stopImmediately()` в начале
     *
     * Раньше метод начинался с гашения плеера, и это ломало штатный сценарий:
     * плеер, который запустил **этот же** цикл, замолкал ровно в тот момент,
     * когда должен был заговорить. Поколение проигрывания увеличивалось,
     * потребитель выходил по `generation != myGeneration`, `pause()+flush()`
     * выбрасывали уже записанный буфер — и пользователь видел текст ответа
     * без единого звука. Теперь звук глушит сам цикл в своём `finally`:
     * отмена [conversationJob] отменяет и [playJob], а `stopImmediately()`
     * дёргается как аварийный стоп только если корутины почему-то не успели.
     */
    fun cancelConversation() {
        AmaliaLog.i(AmaliaLog.tagWith("VM"), "cancelConversation() called | stoppedByUser=true")
        stoppedByUser = true
        // Порядок важен: сперва глушим источники (разговор и синтез),
        // и только потом трогаем плеер. Иначе между отменой задач и стопом
        // трека успевает проскочить следующий чанк и звучит «огрызок» фразы.
        conversationJob?.cancel()
        conversationJob = null
        orchestrator.cancelSpeech()
        playJob?.cancel()
        playJob = null
        AmaliaLog.d(AmaliaLog.tagWith("VM"), "cancelConversation: calling player.stopImmediately()")
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
                micPermissionDenied = false,
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
        stoppedByUser = false
        conversationJob?.cancel()
        conversationJob = null
        orchestrator.cancelSpeech()
        playJob?.cancel()
        playJob = null
        player.stopImmediately()
        // Плеер живёт на [ttsScope]; его задача могла ещё не дойти до finally
        // и держать аудио-трек. Гасим дочерние задачи явно, чтобы «новый
        // разговор» начинался с действительно чистой звуковой сцены.
        ttsScope.coroutineContext[Job]?.children?.forEach { it.cancel() }
        sessionMessages.clear()
        sessionConversationId = null
        conversationSummary = null
        summarizedCount = 0
        messagesSinceSummary = 0
        summaryInProgress = false
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
        playJob?.cancel()
        player.stopImmediately()
        // Отмену скоупа делаем через его Job: `CoroutineScope.cancel()` —
        // это extension-функция, и без явного импорта она не резолвится
        // (Unresolved reference 'cancel'). Через `coroutineContext[Job]`
        // работает всегда и не зависит от импортов.
        ttsScope.coroutineContext[Job]?.cancel()
        super.onCleared()
    }

    // ── Ядро цикла ───────────────────────────────────────────────────────

    /**
     * Единая точка запуска цикла.
     *
     * @param prompt null → полный голосовой цикл; иначе — текстовая команда.
     */
    private fun launchCycle(prompt: String?) {
        AmaliaLog.i(AmaliaLog.tagWith("VM"), "launchCycle | prompt=${prompt?.take(60) ?: "<voice>"}")
        conversationJob?.cancel()
        // Старый плеер умолкает, старый синтез отменяется — и только потом
        // поднимается новый цикл. Без отмены речи предыдущий ответ продолжал
        // бы звучать поверх нового (два голоса), а без отмены плеера на нём
        // оставался бы старый трек.
        orchestrator.cancelSpeech()
        playJob?.cancel()
        playJob = null
        AmaliaLog.d(AmaliaLog.tagWith("VM"), "launchCycle: old jobs cancelled — NO stopImmediately here")
        stoppedByUser = false

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
                micPermissionDenied = false,
                activeTools = emptyList(),
                lastToolReports = emptyList(),
                contextMessageCount = historyForModel().size,
            )
        }

        conversationJob = viewModelScope.launch {
            val deviceStatus = runCatching {
                ServiceLocator.systemControllers.refresh()
            }.getOrDefault(DeviceStatus.Offline)
            // Приложения, которые пользователь отметил как свои, и его
            // личный словарь синонимов. Без них модель угадывает пакеты, а на
            // не-Google прошивках угадывание промахивается мимо реальных
            // пакетов (там нет `com.google.android.youtube`).
            val knownApps = knownAppsForPrompt()
            val appAliases = ServiceLocator.appPreferencesRepository.aliases.first()

            val options = EngineOptions.from(settings.value).copy(
                deviceStatus = deviceStatus,
                conversationSummary = conversationSummary,
                knownApps = knownApps,
                appAliases = appAliases,
            )
            val audioChannel = Channel<AudioChunk>(capacity = Channel.UNLIMITED)
            val historySnapshot = historyForModel()
            // Идентификатор прогона: по нему в logcat видно судьбу конкретного
            // ответа целиком, даже если циклов одновременно несколько.
            // Все модули ([VM], [ORC], [TTS], [PCM]) добавляют метку сами —
            // см. AmaliaLog.tagWith.
            AmaliaLog.enter("VM", AmaliaLog.newRunId())

            AmaliaLog.i(AmaliaLog.tagWith("VM"), "► launchCycle | prompt=${prompt?.take(60) ?: "<voice>"} | history=${historySnapshot.size}")

            // playJob живёт на СОБСТВЕННОМ скоупе плеера, а не в viewModelScope
            // разговора: озвучка обязана доиграть, даже когда корутина диалога
            // уже отменена новым вопросом или кнопкой «Стоп».
            playJob = ttsScope.launch {
                AmaliaLog.i(AmaliaLog.tagWith("VM"), "playJob: started | waiting for audio chunks")
                player.play(audioChannel.receiveAsFlow()) { level ->
                    _uiState.update { state ->
                        if (state.voiceState == VoiceState.Speaking) {
                            state.copy(audioLevel = level)
                        } else {
                            state
                        }
                    }
                }
                AmaliaLog.i(AmaliaLog.tagWith("VM"), "playJob: AudioPlayer.play() returned — audio finished")
                // Сбрасываем «говорю» только если звук действительно играл.
                //
                // Раньше плеер выходил по таймауту префолла, не воспроизведя
                // ни байта, и сбрасывал состояние Speaking посреди синтеза —
                // пользователь видел «Амалия молчит», хотя звук ещё шёл.
                // Теперь плеер ждёт поток до конца, поэтому возвращение из
                // play() означает именно конец речи, и проверка ниже — это
                // страховка на случай сбоя трека, а не штатный путь.
                _uiState.update { state ->
                    if (state.isSpeaking) {
                        AmaliaLog.d(AmaliaLog.tagWith("VM"), "playJob: resetting Speaking → Idle")
                        state.copy(
                            isSpeaking = false,
                            audioLevel = 0f,
                            voiceState = if (state.voiceState == VoiceState.Speaking)
                                VoiceState.Idle else state.voiceState,
                        )
                    } else state
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
                            if (event.isSpeaking) {
                                AmaliaLog.i(AmaliaLog.tagWith("VM"), "Speaking(true) received — setting VoiceState.Speaking")
                                state.copy(
                                    voiceState = VoiceState.Speaking,
                                    isSpeaking = true,
                                )
                            } else {
                                AmaliaLog.d(AmaliaLog.tagWith("VM"), "Speaking(false) received — ignoring (playJob controls state)")
                                state
                            }
                        }

                        is AiResponse.Audio -> {
                            AmaliaLog.d(AmaliaLog.tagWith("VM"), "Audio chunk → audioChannel | bytes=${event.chunk.data.size}")
                            audioChannel.send(event.chunk)
                        }

                        is AiResponse.Finished -> {
                            replyText = event.responseText
                            // Сбой синтеза раньше был невидим: ответ приходил
                            // текстом, а причина тишины оставалась внутри
                            // оркестратора. Теперь пользователь видит, что
                            // голос не сработал, и не считает приложение
                            // сломанным.
                            val voiceError = orchestrator.lastVoiceError
                            _uiState.update {
                                it.copy(
                                    amaliaReply = event.responseText,
                                    replyProgress = 1f,
                                    activeTools = emptyList(),
                                    errorMessage = voiceError,
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
                // Закрываем канал — продюсер в playJob увидит конец стрима
                // и AudioPlayer доиграет оставшиеся чанки до конца.
                // playJob при этом НЕ отменяем — он независимый и должен
                // доиграть даже если conversationJob уже завершился.
                audioChannel.close()
            }

            if (failed) {
                _uiState.update {
                    it.copy(audioLevel = 0f, isSpeaking = false, activeTools = emptyList())
                }
                return@launch
            }

            persistTurn(userText, replyText, reports.map { it.summary })

            // voiceState и isSpeaking НЕ сбрасываем здесь — это делает
            // playJob после того как AudioPlayer реально доиграл последний чанк.
            // Сбрасывать раньше = микрофон уходит в покой пока TTS ещё играет.
            _uiState.update {
                it.copy(
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
            // Условие трёхсоставное, и каждый пункт — выстраданный баг:
            //  1. локальный снимок handsFree устаревает — пользователь мог
            //     нажать «Стоп» во время ответа, поэтому читаем текущее
            //     состояние, а не значение, взятое на старте цикла;
            //  2. [stoppedByUser] закрывает случай, который не ловился ничем:
            //     «Стоп» во время ОЗВУЧКИ. К моменту проверки состояние уже
            //     вернулось в Idle (аварийный стоп), и микрофон открывался
            //     заново через полторы секунды после просьбы замолчать;
            //  3. отмена самой корутины (новый цикл уже стартовал) не должна
            //     порождать ещё один — иначе бесконечная эстафета.
            val currentHandsFree = _uiState.value.handsFree
            val autoListenEnabled = settings.value.autoListen
            if (!stoppedByUser &&
                currentHandsFree &&
                autoListenEnabled &&
                ServiceLocator.hasMicPermission()
            ) {
                delay(HANDS_FREE_GAP_MS)
                if (!stoppedByUser &&
                    _uiState.value.voiceState == VoiceState.Idle &&
                    _uiState.value.handsFree
                ) {
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
        messagesSinceSummary += 2
        if (sessionMessages.size > SESSION_TRIM) {
            val dropped = sessionMessages.size - SESSION_TRIM
            repeat(dropped) { sessionMessages.removeAt(0) }
            // Сдвигаем индекс вместе с буфером — ровно на столько же позиций,
            // сколько сообщений выбросили. Обнулять нельзя: обнуление означало
            // «всё, что уже сжато, снова считается несжатым», и пересказ
            // запускался заново после каждого сдвига окна.
            summarizedCount = (summarizedCount - dropped).coerceAtLeast(0)
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

        // Пересказ запускается только когда набралось SUMMARY_EVERY **новых**
        // реплик с момента прошлого пересказа — а не когда «в списке стало
        // много сообщений». Именно эта разница убирает вечный цикл: индексы
        // плывут при обрезке буфера, а счётчик новых сообщений — нет.
        if (messagesSinceSummary >= SUMMARY_EVERY) {
            refreshSummary()
        }
    }

    /**
     * Обновляет резюме диалога.
     *
     * ## Как это должно работать
     *
     * Резюме — не «второе окно контекста», а **замена** уже прожитой части
     * разговора. Отсюда три правила, каждое из которых когда-то нарушалось:
     *
     *  1. **Новый пересказ перезаписывает прежний**, а не дописывается к нему.
     *     Иначе «сжатие» только росло бы в объёме.
     *  2. **Индекс сжатого — это граница окна**, а не «сколько всего было».
     *     Считаем позицию в текущем списке, а счётчик новых реплик живёт
     *     отдельно ([messagesSinceSummary]) и не сбивается обрезкой буфера.
     *  3. **Результат обязан сохраниться** — иначе после перезапуска приложения
     *     «память» обнуляется, хотя история на диске есть.
     *
     * ## Почему ушёл вечный цикл
     *
     * Раньше условие было `sessionMessages.size - summarizedCount >= 10`,
     * а `summarizedCount` обнулялся при обрезке буфера. Стоило выбросить
     * старые сообщения — и «уже сжатое» снова считалось несжатым, пересказ
     * запускался на каждом ходу, а модель вместо ответа сжимала собственную
     * же сводку. Теперь условие — «набралось 10 **новых** реплик», и оно
     * не зависит ни от обрезки, ни от перезапуска.
     *
     * Второй источник роста: при непустом прежнем резюме промпт просил
     * «дополни его», и модель склеивала пересказ с пересказом. Теперь
     * прежнее резюме передаётся как контекст, но требуется вернуть **одно**
     * короткое резюме целиком.
     */
    private suspend fun refreshSummary() {
        if (summaryInProgress) return

        // Что именно пересказываем: реплики после последней сжатой границы.
        val start = summarizedCount.coerceIn(0, sessionMessages.size)
        val pending = sessionMessages.drop(start)
        if (pending.isEmpty()) {
            // Сжимать нечего — обнуляем счётчик, чтобы не возвращаться сюда
            // на каждой следующей реплике.
            messagesSinceSummary = 0
            return
        }

        summaryInProgress = true
        try {
            val previous = conversationSummary
            val prompt = buildString {
                if (previous.isNullOrBlank()) {
                    append("Сожми этот диалог в 3 коротких предложения: только темы, факты и решения.")
                } else {
                    append("Вот прежнее резюме разговора:\n")
                    append(previous)
                    append("\n\nНиже — новые реплики. Верни ОДНО новое резюме целиком, ")
                    append("в 3 коротких предложения: прежнее и новое вместе, без повторов ")
                    append("и без вступлений вроде «вот резюме».")
                }
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
                    orchestrator.textOnlyResponse(
                        prompt,
                        emptyList(),
                        EngineOptions.from(settings.value).copy(
                            knownApps = knownAppsForPrompt(),
                            appAliases = ServiceLocator.appPreferencesRepository.aliases.first(),
                        ),
                    )
                        .collect { piece -> generated.append(piece) }
                }
            }

            val newSummary = generated.toString().trim().take(SUMMARY_MAX_CHARS)
            if (newSummary.isNotBlank()) {
                conversationSummary = newSummary
                // Граница сжатия — текущий конец списка, а не «сколько всего
                // пришло»: следующий пересказ начнётся ровно с этой позиции.
                summarizedCount = sessionMessages.size
                messagesSinceSummary = 0
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

    // ── Приложения для промпта ───────────────────────────────────────────

    /**
     * Приложения для промпта: отмеченные пользователем, обогащённые его
     * синонимами и отфильтрованные по факту установки.
     *
     * Фильтр по установке здесь критичен. Если приложение удалили, а запись
     * в избранном осталась, модель получила бы пакет, которого нет на
     * телефоне, и пообещала бы пользователю открыть несуществующее — то
     * есть ровно тот класс ошибок, который хуже честного «не нашла».
     * Поэтому список собирается как пересечение «избранное ∩ установленное».
     *
     * Синонимы подставляются к каждому приложению, а не отдельным списком:
     * модели проще связать «музон» с конкретным Spotify, когда они стоят
     * рядом, чем искать соответствие в двух разных блоках промпта.
     */
    private suspend fun knownAppsForPrompt(): List<KnownApp> {
        // `runCatching` здесь вызывается ВНЕ тела suspend-функции, а внутри —
        // обычный код: `runCatching` объявлен `inline` и не является suspend,
        // поэтому вызов suspend-функций (`first()`, `installedApps()`) в его
        // лямбде — неочевидный случай. Заменено на явный try/catch: он и
        // читается проще, и гарантированно не зависит от того, что компилятор
        // выведет из последнего выражения лямбды.
        return try {
            val pinned = ServiceLocator.appPreferencesRepository.pinnedApps.first()
            if (pinned.isEmpty()) return emptyList()

            val installed = ServiceLocator.appRegistry.installedApps()
            val byPackage = installed.associateBy { it.packageName }
            val aliases = ServiceLocator.appPreferencesRepository.aliases.first()

            pinned.mapNotNull { pin ->
                val app = byPackage[pin.packageName] ?: return@mapNotNull null
                KnownApp(
                    label = app.label,
                    packageName = app.packageName,
                    aliases = aliases
                        .filterValues { it == app.packageName }
                        .keys
                        .sorted(),
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Отмену корутины нельзя глотать — иначе отменённая генерация
            // ответа продолжит жить и перезапишет состояние новой.
            throw e
        } catch (e: Throwable) {
            emptyList()
        }
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

        /**
         * Сколько **новых** реплик должно накопиться, чтобы пересказать диалог.
         *
         * Считаются именно новые сообщения с момента прошлого пересказа.
         * Раньше условие опиралось на размер списка и «индекс сжатой границы»,
         * который сдвигался при обрезке буфера, — из-за чего пересказ
         * запускался снова и снова, пока модель сжимала собственную сводку.
         */
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
