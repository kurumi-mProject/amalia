package com.my.amali.data.ai

import com.my.amali.data.model.ChatMessage
import com.my.amali.data.model.MessageRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow

/**
 * Оркестратор конвейера STT → LLM (с инструментами) → TTS.
 *
 * ## Архитектура
 *
 * 1. STT превращает звук в [ChatMessage.user].
 * 2. LLM читает **сообщения + инструменты** и стримит SSE-события:
 *    - [LLMEvent.ContentDelta] — кусочек ответа (в нашем контракте это JSON `{reply,tools}`);
 *    - [LLMEvent.ToolCallDetected] — модель хочет вызвать инструмент;
 *    - [LLMEvent.Completed] — поток LLM закрыт, указывает причину.
 * 3. Оркестратор исполняет **все** вызовы раунда, добавляет результаты в
 *    историю и — только если без результатов нельзя ответить — делает ровно
 *    один дополнительный раунд LLM.
 * 4. Финальный текст ответа уходит в TTS.
 *
 * ## Почему раундов больше не может быть «много»
 *
 * Модель отвечает нативным Tools API не пользуется (мы просим JSON в `content`),
 * поэтому исторически её собственный JSON-ответ возвращался в историю как
 * `assistant`-сообщение. На следующем раунде модель видела
 * `{"reply":"окей","tools":[{"name":"set_wifi"…}]}` в своей же реплике и
 * продолжала вызывать те же инструменты, пока не упиралась в лимит раундов —
 * наружу улетало «сделала», а пользователь слышал бесконечные повторы.
 *
 * Сейчас действует три защиты:
 *  - в историю попадает **только человеческий текст** `reply`, никогда JSON;
 *  - вызовы дедуплицируются по сигнатуре `имя(аргументы)` — повтор не исполняется;
 *  - второй раунд назначается только для `INFORMATIONAL_TOOLS` (те, чей результат
 *    обязан прозвучать в ответе). Действия вида «включи Wi-Fi» закрываются за
 *    ОДИН раунд LLM — это ровно вдвое быстрее прежнего.
 *
 * ## Совместимость
 *
 * Если движок LLM не поддерживает tools ([LanguageModel.supportsTools] = false),
 * оркестратор автоматически падает на legacy-путь: один вызов [generateResponse]
 * с тем же системным промптом и парсингом JSON. Так старые модели и офлайн-режим
 * продолжают работать без изменений в UI.
 *
 * ## Конвейер событий наружу
 *
 * UI получает ровно те же [AiResponse], что и раньше, плюс два служебных:
 * - [AiResponse.ToolRunning] — инструмент начал исполнение;
 * - [AiResponse.ToolCompleted] — инструмент закончил.
 */
class AIOrchestrator(
    internal val sttEngine: SpeechToTextEngine,
    internal val llmEngine: LanguageModel,
    internal val ttsEngine: TextToSpeechEngine,
    internal val registry: ToolRegistry,
    /** Совместимость: legacy-путь использовал commandExecutor напрямую. */
    @Deprecated("Now tools route through ToolRegistry; this is left for fallback only.")
    internal val commandExecutor: com.my.amali.system.DeviceCommandExecutor? = null,
) {
    /** Текст последней ошибки конвейера или null, если последний прогон успешен. */
    @Volatile
    var lastError: String? = null
        private set

    /**
     * Что модель успела сделать в последнем прогоне — по именам инструментов.
     * UI читает это, чтобы показать сводку действий, не разбирая события.
     */
    @Volatile
    var lastExecutedTools: List<String> = emptyList()
        private set

    /**
     * Текст последней ошибки синтеза речи (null — озвучка прошла штатно).
     *
     * Существует отдельно от [lastError] осознанно: [lastError] означает
     * «цикл провалился», а здесь — «ответ получен, но произнести его
     * не удалось». Смешивать эти состояния нельзя: пользователь обязан
     * увидеть текст ответа даже при полностью нерабочем синтезе.
     */
    @Volatile
    var lastVoiceError: String? = null
        private set

    /**
     * Скоуп для озвучки, **независимый** от корутины разговора.
     *
     * `runPipeline` вызывается из UI-цикла, а тот живёт внутри
     * `conversationJob`. Отмена этого job (новый вопрос, «Стоп», hands-free,
     * смена сессии) убивала и LLM-фазу, и TTS-фазу, то есть до озвучки
     * дело не доходило вообще, хотя текст уже был сгенерирован. В логе это
     * выглядело как «pipeline cancelled during LLM phase» и полная тишина при
     * полностью исправном плеере.
     *
     * SupervisorJob гарантирует, что падение одного прогона не утащит
     * соседний, а сам скоуп гасится только явно — из cancelSpeech,
     * то есть по воле пользователя, а не по воле предыдущего цикла.
     */
    private val ttsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Активная задача озвучки (для отмены и диагностики).
     *
     * Хранится отдельно от [ttsScope], чтобы [cancelSpeech] мог погасить
     * ровно текущую речь, не трогая скоуп целиком. — следующий ответ должен
     * получить возможность заговорить сразу.
     */
    @Volatile
    private var ttsJob: Job? = null

    /**
     * Гасит активные озвучки немедленно.
     *
     * Единственный случай, когда обрывать речь на середине — правильно:
     * пользователь сам попросил замолчать.
     */
    fun cancelSpeech() {
        AmaliaLog.i(AmaliaLog.tagWith("ORC"), "cancelSpeech() — aborting TTS")
        ttsJob?.cancel()
        ttsJob = null
    }

    /**
     * Ждёт завершения текущей озвучки — без прерывания по отмене.
     *
     * Нужен вызывающему потоку ([processTextCommand]/[processVoiceCommand]):
     * поток событий обязан оставаться открытым, пока синтез отдаёт чанки.
     * Иначе канал в ViewModel закрывается на `finally` корутины разговора,
     * а синтез продолжает слать в него данные — и падает с
     * `ClosedSendChannelException` уже после успешного HTTP 200.
     *
     * Ждём через полное имя `kotlinx.coroutines.NonCancellable`: если
     * пользователь отменил разговор, канал закрывается его же кодом, и здесь
     * мы не должны мешать этому своим исключением.
     */
    private suspend fun awaitSpeech() {
        val job = ttsJob ?: return
        AmaliaLog.d(AmaliaLog.tagWith("ORC"), "awaitSpeech: waiting for TTS to finish")
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            job.join()
        }
        AmaliaLog.d(AmaliaLog.tagWith("ORC"), "awaitSpeech: TTS finished")
    }

    /** Инициализирует все движки. Идемпотентно. */
    suspend fun initialize() {
        sttEngine.initialize()
        llmEngine.initialize()
        ttsEngine.initialize()
    }

    /** Освобождает ресурсы всех движков. */
    suspend fun close() {
        runCatching { sttEngine.close() }
        runCatching { llmEngine.close() }
        runCatching { ttsEngine.close() }
    }

    // ── Голосовой цикл (entry-point для UI) ──────────────────────────────

    /**
     * Полный голосовой цикл: слушает микрофон, распознаёт речь, отвечает
     * и озвучивает ответ. Поток завершается, когда ответ проигран.
     */
    fun processVoiceCommand(
        history: List<ChatMessage> = emptyList(),
        options: EngineOptions = EngineOptions.Default,
    ): Flow<AiResponse> = channelFlow {
        val segments = StringBuilder()
        var partial = ""

        sttEngine.transcribe(options).collect { event ->
            when (event) {
                is SttEvent.Level -> send(AiResponse.Level(event.level))

                is SttEvent.Partial -> {
                    partial = event.text
                    val preview = joinTranscript(segments.toString(), partial)
                    if (preview.isNotEmpty()) send(AiResponse.PartialTranscript(preview))
                }

                is SttEvent.Final -> {
                    if (event.text.isNotBlank()) {
                        if (segments.isNotEmpty()) segments.append(' ')
                        segments.append(event.text.trim())
                    }
                    partial = ""
                    send(AiResponse.PartialTranscript(segments.toString()))
                }
            }
        }

        val transcript = joinTranscript(segments.toString(), partial)
        if (transcript.isBlank() ||
            transcript.trim().split("\\s+".toRegex()).size < MIN_WORDS_TO_PROCESS ||
            isLikelyNoise(transcript)
        ) {
            lastError = ERROR_NO_SPEECH
            send(AiResponse.Error(ERROR_NO_SPEECH))
            return@channelFlow
        }

        send(AiResponse.Level(0f))
        send(AiResponse.Transcript(transcript))
        runPipeline(transcript, history, options) { event -> send(event) }
        // Та же причина, что и в processTextCommand: канал не должен
        // закрываться, пока синтез ещё льёт байты.
        awaitSpeech()
    }.catch { throwable -> emitFailure(throwable) }

    /**
     * Обрабатывает набранный или выбранный текст, минуя распознавание речи.
     */
    fun processTextCommand(
        text: String,
        history: List<ChatMessage> = emptyList(),
        options: EngineOptions = EngineOptions.Default,
    ): Flow<AiResponse> = channelFlow {
        val command = text.trim()
        if (command.isEmpty()) {
            lastError = ERROR_EMPTY_COMMAND
            send(AiResponse.Error(ERROR_EMPTY_COMMAND))
            return@channelFlow
        }
        send(AiResponse.Transcript(command))
        runPipeline(command, history, options) { event -> send(event) }
        // Ждём озвучку ПОСЛЕ того, как текст отдан.
        //
        // Без этого поток закрывался сразу после LLM-фазы, и канал в
        // ViewModel закрывался вместе с ним — а синтез, вынесенный в
        // отдельный скоуп, продолжал слать в него чанки и падал с
        // ClosedSendChannelException. В логе это выглядело так: «первый
        // чанк получен → TTS error: Channel was closed» при живом и
        // полностью рабочем плеере.
        awaitSpeech()
    }.catch { throwable -> emitFailure(throwable) }

    /**
     * Только текстовый ответ модели без синтеза — для служебных сценариев
     * (например, сжатие контекста). Возвращает уже чистый человеческий текст,
     * а не JSON-контракт модели.
     */
    fun textOnlyResponse(
        text: String,
        history: List<ChatMessage> = emptyList(),
        options: EngineOptions = EngineOptions.Default,
    ): Flow<String> = kotlinx.coroutines.flow.flow {
        if (llmEngine.supportsTools && registry.names.isNotEmpty()) {
            val acc = StringBuilder()
            llmEngine.chatWithTools(
                messages = history + ChatMessage.user(text),
                tools = registry.definitions,
                options = options,
            ).collect { event ->
                if (event is LLMEvent.ContentDelta) acc.append(event.text)
            }
            val clean = extractReply(acc.toString()).ifBlank { stripJson(acc.toString()) }
            if (clean.isNotBlank()) emit(clean)
        } else {
            llmEngine.generateResponse(text, history, options).collect { emit(it) }
        }
    }

    // ── Ядро конвейера ───────────────────────────────────────────────────

    /**
     * Единая точка прогона: LLM (+инструменты) → текст → TTS.
     *
     * Используется и tool-aware, и legacy путём в зависимости от [llmEngine.supportsTools].
     */
    private suspend fun runPipeline(
        commandText: String,
        history: List<ChatMessage>,
        options: EngineOptions,
        emit: suspend (AiResponse) -> Unit,
    ) = kotlinx.coroutines.coroutineScope {
        AmaliaLog.i(AmaliaLog.tagWith("ORC"), "runPipeline | text=${commandText.take(80)} | historySize=${history.size}")
        emit(AiResponse.Thinking(true))

        lastExecutedTools = emptyList()

        val responseText: String = try {
            if (llmEngine.supportsTools && registry.names.isNotEmpty()) {
                AmaliaLog.d(AmaliaLog.tagWith("ORC"), "using tool-aware path | tools=${registry.names}")
                runToolLoop(commandText, history, options, emit)
            } else {
                AmaliaLog.d(AmaliaLog.tagWith("ORC"), "using legacy free-text path")
                runLegacyFreeText(commandText, history, options, emit)
            }
        } catch (e: CancellationException) {
            AmaliaLog.w(AmaliaLog.tagWith("ORC"), "pipeline cancelled during LLM phase")
            emit(AiResponse.Thinking(false))
            throw e
        } catch (e: Throwable) {
            AmaliaLog.e(AmaliaLog.tagWith("ORC"), "pipeline error in LLM phase: ${e.message}", e)
            emit(AiResponse.Thinking(false))
            throw e
        }

        emit(AiResponse.Thinking(false))

        if (responseText.isBlank()) {
            AmaliaLog.e(AmaliaLog.tagWith("ORC"), "responseText is blank — throwing EngineException")
            throw EngineException("Модель не дала ответа. Попробуй переспросить.")
        }

        AmaliaLog.i(AmaliaLog.tagWith("ORC"), "★ LLM response ready | length=${responseText.length} | preview=${responseText.take(80)}")

        // ── TTS фаза ──────────────────────────────────────────────────────
        //
        // Озвучка запускается в отдельном скоупе ([ttsScope]) и НЕ ждётся
        // здесь. Два следствия, и оба важны:
        //
        //  1. Отмена корутины разговора (новый вопрос, «Стоп», hands-free,
        //     смена сессии) больше не обрывает речь на середине. Раньше
        //     `collect` жил внутри `coroutineScope` разговора, и отмена
        //     убивала озвучку вместе с ним — в логе это выглядело как
        //     «TTS cancelled» и полная тишина при живом плеере.
        //  2. `runPipeline` возвращается сразу после запуска речи, поэтому
        //     UI не обязан ждать конца звука, чтобы показать текст ответа.
        //
        // UI по-прежнему узнаёт о конце озвучки: `playJob` в ViewModel
        // завершается, когда плеер доиграл последний чанк, и сбрасывает
        // `Speaking` сам. То есть состояние экрана по-прежнему отражает
        // реальность, но уже не зависит от времени жизни корутины разговора.
        AmaliaLog.i(AmaliaLog.tagWith("ORC"), "► starting TTS in independent scope | len=${responseText.trim().length}")
        emit(AiResponse.Speaking(true))

        val voiceText = responseText.trim()
        val voiceOptions = options
        val voiceEmit = emit
        ttsJob = ttsScope.launch {
            var ttsChunks = 0
            try {
                ttsEngine.speak(voiceText, voiceOptions).collect { chunk ->
                    ttsChunks++
                    if (ttsChunks == 1) {
                        AmaliaLog.i(AmaliaLog.tagWith("ORC"), "★ first Audio chunk from TTS → audioChannel")
                    }
                    voiceEmit(AiResponse.Audio(chunk))
                }
                AmaliaLog.i(AmaliaLog.tagWith("ORC"), "TTS stream collected | total chunks=$ttsChunks")
                lastVoiceError = null
            } catch (e: CancellationException) {
                // Отмена по воле пользователя — не ошибка, а приказ замолчать.
                AmaliaLog.w(AmaliaLog.tagWith("ORC"), "TTS cancelled by user (chunks emitted=$ttsChunks)")
                voiceEmit(AiResponse.Speaking(false))
                throw e
            } catch (e: Throwable) {
                AmaliaLog.e(AmaliaLog.tagWith("ORC"), "TTS error: ${e.message}", e)
                // Ошибку озвучки не затираем молча: пользователь должен видеть,
                // почему нет звука, пока новая озвучка не пройдёт успешно.
                lastVoiceError = e.message ?: ERROR_VOICE
                voiceEmit(AiResponse.Speaking(false))
                return@launch
            }
            voiceEmit(AiResponse.Speaking(false))
        }

        lastError = null
        AmaliaLog.i(AmaliaLog.tagWith("ORC"), "pipeline done (LLM phase) | responseText length=${responseText.length}")
        emit(AiResponse.Finished(responseText))
    }

    /**
     * Tool-aware путь.
     *
     * Возвращает финальный голосовой текст для TTS. Реплика модели из
     * ПЕРВОГО раунда никогда не теряется: если она есть, она и звучит.
     * Дополнительный раунд к LLM — только когда вызван инструмент, чей
     * результат обязан попасть в ответ (время, батарея, погода, поиск по
     * истории). Все остальные случаи закрываются одним обращением к модели.
     */
    private suspend fun runToolLoop(
        commandText: String,
        history: List<ChatMessage>,
        options: EngineOptions,
        emit: suspend (AiResponse) -> Unit,
    ): String {
        val messages = mutableListOf<ChatMessage>().apply {
            // Инструментарные хвосты прошлых циклов в общий контекст не идут —
            // они относятся к конкретному раунду.
            addAll(history.filter { it.role != MessageRole.TOOL })
        }
        // Держим реплику пользователя последней, чтобы модель не читала
        // «свежий» запрос как уже отвеченный.
        val lastUser = messages.lastOrNull { it.role == MessageRole.USER }?.content
        if (lastUser != commandText) messages += ChatMessage.user(commandText)

        val toolSpecs = registry.definitions
        val executedNames = linkedSetOf<String>()
        val executedSignatures = mutableSetOf<String>()
        var reply = ""
        var round = 0

        while (round < MAX_TOOL_ROUNDS) {
            round++

            val collected = StringBuilder()
            val recovered = StringBuilder()
            val calls = mutableListOf<ToolCall>()

            llmEngine.chatWithTools(
                messages = messages.toList(),
                tools = toolSpecs,
                options = options,
                alreadyExecutedTools = executedNames.toSet(),
            ).collect { event ->
                when (event) {
                    is LLMEvent.ContentDelta -> collected.append(event.text)
                    // Восстановленный текст — это уже готовая реплика,
                    // а не сырой JSON: собираем её отдельно, чтобы
                    // extractReply не пытался искать поле reply в прозе.
                    is LLMEvent.ReplyRecovered -> recovered.append(event.text)
                    is LLMEvent.ToolCallDetected -> calls += event.call
                    is LLMEvent.Completed -> Unit
                }
            }

            // В UI и в историю уходит только человеческий текст.
            //
            // `collected` содержит сырой стрим модели: обычно это JSON
            // `{"reply":…,"tools":[…]}`. Если `extractReply` вернула пусто,
            // берём текст через ту же терпимую к формату логику, что и
            // движок: иначе ответ, который модель уже сгенерировала, терялся
            // бы здесь и превращался в «модель не дала ответа».
            val rawCollected = collected.toString()
            // Приоритет: уже восстановленный движком текст → честный JSON →
            // терпимый разбор сырого потока. Порядок важен: восстановленный
            // текст — самое достоверное, что у нас есть.
            val text = recovered.toString().takeIf { it.isNotBlank() }
                ?: extractReply(rawCollected).ifBlank { recoverPlainText(rawCollected) }
                    .takeIf { it.isNotBlank() }

            if (text == null && rawCollected.isNotBlank()) {
                AmaliaLog.w(
                    AmaliaLog.tagWith("ORC"),
                    "unparsable model output (${rawCollected.length} chars), no reply field: " +
                        rawCollected.take(140),
                )
            }

            text?.let { replyText ->
                reply = if (reply.isBlank() || reply.contains(replyText)) {
                    replyText
                } else {
                    "$reply $replyText"
                }
                emit(AiResponse.ReplyDelta(replyText, reply))
            }

            if (calls.isEmpty()) return reply.ifBlank { fallbackReply(executedNames) }

            // Повтор уже сделанного действия не исполняется никогда — это
            // главный стопор против «зацикливания на сделала».
            val fresh = calls.filter { executedSignatures.add(signatureOf(it)) }
            if (fresh.isEmpty()) return reply.ifBlank { fallbackReply(executedNames) }

            // Результаты инструментов, без которых ответ невозможен.
            val feedBack = mutableListOf<String>()
            var dataNeeded = false

            for (call in fresh) {
                emit(AiResponse.ToolRunning(call.toolName, call.argumentsMap))
                val result = registry.execute(call)
                executedNames += call.toolName
                emit(
                    AiResponse.ToolCompleted(
                        toolName = result.toolName,
                        ok = result.ok,
                        output = result.output,
                        errorMessage = result.errorMessage,
                    ),
                )
                if (result.toolName in INFORMATIONAL_TOOLS) {
                    dataNeeded = true
                    feedBack += "${result.toolName}: ${result.contentForModel()}"
                }
            }

            lastExecutedTools = executedNames.toList()

            // Действие без данных (включил/открыл/поставил) — ответ модели уже
            // прозвучит. Второй запрос к LLM только отнял бы ~700 мс у пользователя.
            if (!dataNeeded) return reply.ifBlank { fallbackReply(executedNames) }

            // Единственный доборочный раунд: чистый текст + компактные данные.
            // Сырой JSON модели в историю НЕ попадает.
            if (reply.isNotBlank()) messages += ChatMessage.assistant(reply)
            messages += ChatMessage.user(
                "[РЕЗУЛЬТАТЫ] ${feedBack.joinToString("; ")}\n" +
                    "Скажи это коротко и по-человечески, без списков.",
            )
        }

        return reply.ifBlank { fallbackReply(executedNames) }
    }

    /**
     * Legacy-путь без tools: один вызов [LanguageModel.generateResponse],
     * парсинг JSON с device-командами из ответа LLM.
     *
     * Сохранён для совместимости со старыми моделями, не поддерживающими
     * tools (например, лёгкие qwen-варианты без function calling).
     */
    private suspend fun runLegacyFreeText(
        commandText: String,
        history: List<ChatMessage>,
        options: EngineOptions,
        emit: suspend (AiResponse) -> Unit,
    ): String {
        val answer = StringBuilder()
        // Без локальных try/catch — пусть исключения летят вверх в
        // outer runPipeline, где их ждёт единая обработка ошибок с
        // единообразным `Thinking(false)` и сообщением для UI.
        llmEngine.generateResponse(commandText, history, options).collect { delta ->
            answer.append(delta)
            emit(AiResponse.ReplyDelta(delta, answer.toString()))
        }

        // Парсим JSON с командами (старая схема из AmaliaTools.systemPrompt).
        val rawAnswer = answer.toString().trim()
        val (replyText, commands) = parseLegacyAnswer(rawAnswer)

        // Выполняем команды устройства
        if (commands.isNotEmpty()) {
            @Suppress("DEPRECATION")
            commandExecutor?.execute(commands)
        }
        return replyText.trim()
    }

    private suspend fun FlowCollector<AiResponse>.emitFailure(throwable: Throwable) {
        if (throwable is CancellationException) throw throwable
        // «Канал закрыт» — это не сбой конвейера, а следствие того, что
        // поток отменили извне (новый вопрос, «Стоп», смена сессии).
        // Раньше это исключение доезжало сюда и подменяло собой реальную
        // причину в интерфейсе: пользователь видел «Channel was closed»
        // вместо объяснения, почему нет звука.
        if (throwable is kotlinx.coroutines.channels.ClosedSendChannelException) {
            AmaliaLog.w(AmaliaLog.tagWith("ORC"), "channel closed by host — stream is over")
            return
        }
        val message = (throwable as? EngineException)?.message
            ?: throwable.message
            ?: ERROR_UNKNOWN
        lastError = message
        emit(AiResponse.Error(message))
    }

    /**
     * Спасает текст ответа из ответа модели, который не удалось разобрать
     * как JSON.
     *
     * Три уровня терпимости — те же, что в движке:
     *  1. поле `reply` регуляркой (работает и на оборванной строке);
     *  2. снятие JSON-обвязки, если текст всё-таки структурный;
     *  3. проза как есть.
     *
     * Возвращает пустую строку только если текста нет вообще.
     */
    private fun recoverPlainText(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("```")) {
            val fenced = stripCodeFences(trimmed).trim()
            if (fenced.isNotEmpty() && fenced != trimmed) return recoverPlainText(fenced)
        }
        Regex("\"reply\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)")
            .find(trimmed)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            ?.let { return it.replace("\\n", " ").replace("\\\"", "\"").trim() }

        if (trimmed.startsWith("{")) {
            val stripped = trimmed
                .substringBefore("\"tools\"")
                .replace(Regex("[{}\\[\\]\"]"), " ")
                .replace(Regex("\\breply\\b\\s*:"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (stripped.isNotBlank()) return stripped
        }
        // Единственный случай, когда возвращаем пусто: в тексте нет букв —
        // то есть это обломки структуры, а не ответ.
        return if (trimmed.any { it.isLetter() }) trimmed else ""
    }

    /** Склеивает уже финализированный текст с текущей гипотезой. */
    private fun joinTranscript(finalText: String, partial: String): String = when {
        partial.isBlank() -> finalText.trim()
        finalText.isBlank() -> partial.trim()
        else -> "${finalText.trim()} ${partial.trim()}"
    }

    /**
     * Отсеивает короткие вокальные «паразиты», которые Deepgram иногда
     * фиксирует как полноценные слова: «э-м», «ну-у», «а-а», одиночные
     * согласные. Однословные короткие (≤2 символов кириллицы и не цифры)
     * считаются шумом; многословные никогда.
     */
    private fun isLikelyNoise(text: String): Boolean {
        val words = text.trim().split("\\s+".toRegex())
        if (words.size > 1) return false
        val word = words.single()
        if (word.matches(Regex("\\d+"))) return false // «7», «12» — числа, можно игнорировать, но они короткие
        // Очень короткие однословные реплики без букв кириллицы/латиницы.
        val letters = word.count { it.isLetter() }
        return letters <= 2
    }

    /**
     * Парсит ответ LLM в legacy-формате: если есть JSON-обёртка {reply,commands},
     * возвращает её; иначе весь текст идёт как reply.
     */
    private fun parseLegacyAnswer(raw: String): Pair<String, List<com.my.amali.data.model.DeviceCommand>> {
        val jsonStart = raw.indexOf('{')
        val jsonEnd = raw.lastIndexOf('}')
        if (jsonStart == -1 || jsonEnd <= jsonStart) return raw to emptyList()

        return runCatching {
            val json = org.json.JSONObject(raw.substring(jsonStart, jsonEnd + 1))
            val reply = json.optString("reply", "").ifBlank { raw }
            val commandsArray = json.optJSONArray("commands")?.toString() ?: "[]"
            val commands = com.my.amali.data.model.DeviceCommand.parseList(commandsArray)
            reply to commands
        }.getOrDefault(raw to emptyList())
    }

    /**
     * Ключ повторяемости вызова: имя + нормализованные аргументы.
     *
     * Порядок ключей в JSON модель гуляет от ответа к ответу, поэтому
     * аргументы сортируются — иначе «{a,b}» и «{b,a}» считались бы разными
     * действиями и цикл крутился бы вечно.
     */
    private fun signatureOf(call: ToolCall): String {
        if (call.argumentsMap.isEmpty()) return call.toolName
        val args = call.argumentsMap.entries
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}=${it.value}" }
        return "${call.toolName}($args)"
    }

    /**
     * Реплика, когда модель вызвала действия, но не сказала ни слова.
     *
     * Варианты чередуются по числу инструментов: однообразное «сделала» на
     * каждом цикле пользователь воспринимает как сломанный ответ.
     */
    private fun fallbackReply(executed: Set<String>): String = when {
        executed.isEmpty() -> ""
        executed.size == 1 -> "готово"
        else -> "всё, ${executed.size} дела"
    }

    /**
     * Защита от ситуации, когда служебный вызов (сжатие контекста) получил
     * не JSON, а обрывок: вырезаем фигурные скобки, чтобы в речь не попало
     * «кавычка реплай двоеточие».
     */
    private fun stripJson(raw: String): String =
        raw.replace(Regex("[{}\\[\\]\"]"), " ").replace(Regex("\\s+"), " ").trim()

    /**
     * Вытаскивает поле "reply" из JSON-ответа модели.
     * Если JSON кривой или reply пустой — возвращает пустую строку
     * (защита от ситуации когда модель вдруг ответила не JSON-ом).
     */
    private fun extractReply(raw: String): String {
        var trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        // Markdown-фенс вокруг JSON — частое поведение qwen вопреки контракту.
        if (trimmed.startsWith("```")) trimmed = stripCodeFences(trimmed)

        val fromJson = runCatching {
            org.json.JSONObject(trimmed).optString("reply", "").trim()
        }.getOrDefault("")
        if (fromJson.isNotBlank()) return fromJson

        // JSON не собрался. Раньше сырой текст возвращался «как есть» — и
        // фигурные скобки с кавычками озвучивались голосом. Теперь из сломанного
        // JSON выуживаем reply регэкспом; если и это не вышло — молчим:
        // «ничего не сказали» лучше, чем «прочитали контракт вслух».
        if (trimmed.startsWith("{")) {
            val viaRegex = Regex("\"reply\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .find(trimmed)?.groupValues?.get(1)
            if (!viaRegex.isNullOrBlank()) return viaRegex
            return ""
        }
        return trimmed
    }

    /** Срезает markdown-кодовый фенс: ```json … ``` → чистый текст. */
    private fun stripCodeFences(raw: String): String {
        var text = raw.trim().removePrefix("```").trimStart()
        text = text.removePrefix("json").removePrefix("JSON").trimStart()
        val closing = text.lastIndexOf("```")
        if (closing >= 0) text = text.substring(0, closing)
        return text.trim()
    }

    private companion object {
        const val ERROR_NO_SPEECH = "Не услышала ни слова. Нажми микрофон и скажи ещё раз."
        const val ERROR_EMPTY_COMMAND = "Пустая команда — нечего обрабатывать."
        const val ERROR_UNKNOWN = "Что-то пошло не так. Попробуй ещё раз."

        /** Ошибка синтеза речи: ответ показываем, но озвучить не смогли. */
        const val ERROR_VOICE = "Ответ получен, но озвучить его не удалось."

        /**
         * Жёсткий потолок обращений к LLM за один цикл.
         *
         * Практика показывает: действие закрывается за 1 раунд, «посмотри и
         * скажи» — за 2. Три — уже аномалия, которую надо обрывать, а не
         * обслуживать: раньше лимит в 10 раундов и превращал один клик по
         * подсказке в череду «сделала».
         */
        const val MAX_TOOL_ROUNDS = 3

        /**
         * Инструменты, чей результат обязан прозвучать в ответе.
         *
         * Для них оправдан второй запрос к модели. Всё остальное — действия,
         * их результат модели не нужен: пользователь и так видит сводку.
         */
        val INFORMATIONAL_TOOLS = setOf(
            "get_current_time",
            "get_device_status",
            "get_battery_level",
            "get_location_status",
            "get_weather",
            "search_history",
            "get_recent_conversations",
        )

        /**
         * Минимум слов в транскрипте, чтобы отправить запрос к LLM.
         *
         * Значение 1 — потому что hands-free режим полагается на короткие
         * односложные команды вроде «стоп», «тише», «отмени», и слишком
         * жёсткий фильтр их съедает. Шум «э», «м», «а» отсеиваем отдельно
         * по длине и содержанию символов в [isLikelyNoise] ниже.
         */
        const val MIN_WORDS_TO_PROCESS = 1
    }
}
