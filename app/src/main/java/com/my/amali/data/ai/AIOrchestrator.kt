package com.my.amali.data.ai

import com.my.amali.data.model.ChatMessage
import com.my.amali.data.model.MessageRole
import kotlinx.coroutines.CancellationException
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
        emit(AiResponse.Thinking(true))

        lastExecutedTools = emptyList()

        val responseText: String = try {
            if (llmEngine.supportsTools && registry.names.isNotEmpty()) {
                runToolLoop(commandText, history, options, emit)
            } else {
                runLegacyFreeText(commandText, history, options, emit)
            }
        } catch (e: CancellationException) {
            emit(AiResponse.Thinking(false))
            throw e
        } catch (e: Throwable) {
            emit(AiResponse.Thinking(false))
            throw e
        }

        emit(AiResponse.Thinking(false))
        if (responseText.isBlank()) {
            throw EngineException("Модель не дала ответа. Попробуй переспросить.")
        }

        // ── Фаза 2: TTS синтезирует и стримит аудио ──────────────────────
        emit(AiResponse.Speaking(true))
        try {
            ttsEngine.speak(responseText.trim(), options).collect { chunk ->
                emit(AiResponse.Audio(chunk))
            }
        } catch (e: CancellationException) {
            emit(AiResponse.Speaking(false))
            throw e
        } catch (e: Throwable) {
            emit(AiResponse.Speaking(false))
            throw e
        }
        emit(AiResponse.Speaking(false))
        lastError = null
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
            val calls = mutableListOf<ToolCall>()

            llmEngine.chatWithTools(
                messages = messages.toList(),
                tools = toolSpecs,
                options = options,
                alreadyExecutedTools = executedNames.toSet(),
            ).collect { event ->
                when (event) {
                    is LLMEvent.ContentDelta -> collected.append(event.text)
                    is LLMEvent.ToolCallDetected -> calls += event.call
                    is LLMEvent.Completed -> Unit
                }
            }

            // В UI и в историю уходит только человеческий текст.
            extractReply(collected.toString()).takeIf { it.isNotBlank() }?.let { text ->
                reply = if (reply.isBlank() || reply.contains(text)) text else "$reply $text"
                emit(AiResponse.ReplyDelta(text, reply))
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
        val message = (throwable as? EngineException)?.message
            ?: throwable.message
            ?: ERROR_UNKNOWN
        lastError = message
        emit(AiResponse.Error(message))
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
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val fromJson = runCatching {
            org.json.JSONObject(trimmed).optString("reply", "").trim()
        }.getOrDefault("")
        if (fromJson.isNotBlank()) return fromJson
        // Модель ответила не JSON — берём как есть, но без фигурных скобок
        return if (trimmed.startsWith("{")) "" else trimmed
    }

    private companion object {
        const val ERROR_NO_SPEECH = "Не услышала ни слова. Нажми микрофон и скажи ещё раз."
        const val ERROR_EMPTY_COMMAND = "Пустая команда — нечего обрабатывать."
        const val ERROR_UNKNOWN = "Что-то пошло не так. Попробуй ещё раз."

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
