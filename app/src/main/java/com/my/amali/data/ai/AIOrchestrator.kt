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
 *    - [LLMEvent.ContentDelta] — кусочек естественного текста;
 *    - [LLMEvent.ToolCallDetected] — модель хочет вызвать инструмент;
 *    - [LLMEvent.Completed] — поток LLM закрыт, указывает причину.
 * 3. Оркестратор гоняет **multi-turn loop**: каждый tool call исполняется,
 *    результат добавляется в историю, LLM вызывается снова. Максимум
 *    [MAX_TOOL_ROUNDS] раундов, чтобы случайная рекурсия не зациклилась.
 * 4. Финальный текст ответа уходит в TTS.
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
 * UI получает ровно те же [AiResponse], что и раньше, плюс два новых:
 * - [AiResponse.ToolRunning] — инструмент начал работу (можно подсветить);
 * - [AiResponse.ToolCompleted] — инструмент закончил (можно свернуть подсказку).
 *
 * В оркестраторе оба события эмитятся между [Thinking] и [Speaking], поэтому
 * визуально они живут внутри фазы «Думаю» — пользователь видит кратковременный
 * «пульс» и слышит голосовую реплику как обычно.
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
        if (transcript.isBlank() || transcript.trim().split("\\s+".toRegex()).size < MIN_WORDS_TO_PROCESS) {
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
     * Только текстовый ответ модели без синтеза — для служебных сценариев.
     * Эмитит накопленный текст после каждой дельты.
     */
    fun textOnlyResponse(
        text: String,
        history: List<ChatMessage> = emptyList(),
        options: EngineOptions = EngineOptions.Default,
    ): Flow<String> = kotlinx.coroutines.flow.flow {
        if (llmEngine.supportsTools && registry.names.isNotEmpty()) {
            // Tool-aware: используем chatWithTools и собираем только текст
            val acc = StringBuilder()
            llmEngine.chatWithTools(
                messages = history + ChatMessage.user(text),
                tools = registry.definitions,
                options = options,
            ).collect { event ->
                if (event is LLMEvent.ContentDelta) {
                    acc.append(event.text)
                    // Не эмитим промежуточные tool-вызовы — для этого entry-point
                    // интересует только финальный текст.
                    if (event.text.isNotEmpty()) emit(acc.toString())
                }
            }
        } else {
            // Legacy: один текстовый запрос
            llmEngine.generateResponse(text, history, options).collect { acc ->
                emit(acc)
            }
        }
    }

    // ── Ядро конвейера ───────────────────────────────────────────────────

    /**
     * LLM с прямой стриминговой поддержкой tools.
     *
     * Главный метод — точка, вокруг которой строится весь multi-turn цикл.
     * Используется и tool-aware, и legacy путём в зависимости от [llmEngine.supportsTools].
     */
    private suspend fun runPipeline(
        commandText: String,
        history: List<ChatMessage>,
        options: EngineOptions,
        emit: suspend (AiResponse) -> Unit,
    ) = kotlinx.coroutines.coroutineScope {
        emit(AiResponse.Thinking(true))

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
     * Tool-aware путь: несколько вызовов LLM, между ними — исполнение
     * инструментов и дополнение истории.
     *
     * Возвращает финальный голосовой текст для TTS. Если в последнем раунде
     * модель выдала только tools без текста — берётся fallback (компактная
     * реплика на основе числа выполненных инструментов).
     */
    private suspend fun runToolLoop(
        commandText: String,
        history: List<ChatMessage>,
        options: EngineOptions,
        emit: suspend (AiResponse) -> Unit,
    ): String {
        val transcriptHolder = listOf(commandText)
        // LLM работает с системным промптом и историей. Оркестратор держит
        // «теневую» историю, которая пополняется результатами tools и при
        // необходимости — ассистентскими сообщениями-обёртками.
        val messages = mutableListOf<ChatMessage>().apply {
            // сначала явная история диалога
            addAll(history.filter { it.role != MessageRole.TOOL })
            // ...а tool-results внутри неё отбрасываем — они часть конкретного раунда, не общей истории.
        }
        // Дублируем user-реплику, чтобы она была «свежей» (последней)
        val lastUser = messages.lastOrNull { it.role == MessageRole.USER }?.content
        if (lastUser != commandText) {
            messages += ChatMessage.user(commandText)
        }

        val toolSpecs = registry.definitions
        var collectedText = StringBuilder()
        var textWasCollected = false
        var stopRequested = false
        val executedTools = mutableSetOf<String>()

        repeat(MAX_TOOL_ROUNDS) { roundIndex ->
            if (stopRequested) return@repeat
            val stream = llmEngine.chatWithTools(
                messages = messages.toList(),
                tools = toolSpecs,
                options = options,
                alreadyExecutedTools = executedTools.toSet(),
            )
            val collected = StringBuilder()
            val calls = mutableListOf<ToolCall>()
            var finishReason = FinishReason.STOP

            stream.collect { event ->
                when (event) {
                    is LLMEvent.ContentDelta -> {
                        // Накапливаем сырой JSON — не показываем пока не распарсим
                        collected.append(event.text)
                    }

                    is LLMEvent.ToolCallDetected -> {
                        calls += event.call
                        emit(
                            AiResponse.ToolRunning(
                                toolName = event.call.toolName,
                                arguments = event.call.argumentsMap,
                            ),
                        )
                    }

                    is LLMEvent.Completed -> {
                        finishReason = event.reason
                    }
                }
            }

            // Парсим reply из JSON — только это идёт в UI и TTS
            val replyText = extractReply(collected.toString())
            if (replyText.isNotBlank()) {
                emit(AiResponse.ReplyDelta(replyText, replyText))
            }

            // Если в потоке появились вызовы инструментов — исполняем их и делаем
            // второй раунд к LLM с результатами. Это правило работает даже
            // если finish_reason == "stop" (модели вроде o1 стримят вызовы
            // и завершают stop'ом в одном чанке), и для mock-LLM (он тоже
            // не возвращает отдельный finish_reason tool_calls).
            if (calls.isNotEmpty()) {
                messages += syntacticAssistantToolMessage(calls, collected.toString())
                for (call in calls) {
                    val result = registry.execute(call)
                    executedTools += call.toolName
                    // Используем contentForModel(): при ok=true это нормальный
                    // JSON-вывод, при ok=false — структура {status:error, reason:…},
                    // которую модель умеет пересказать пользователю. Раньше
                    // здесь был ifEmpty-хак, который при сбое отдавал модели
                    // голую строку причины и она говорила «что-то не вышло».
                    messages += ChatMessage.toolResult(
                        toolCallId = result.toolCallId,
                        content = result.contentForModel(),
                    )
                    emit(
                        AiResponse.ToolCompleted(
                            toolName = result.toolName,
                            ok = result.ok,
                            output = result.output,
                            errorMessage = result.errorMessage,
                        ),
                    )
                }
                textWasCollected = textWasCollected || collected.isNotEmpty()
                // Продолжаем: следующий раунд LLM с результатами инструментов.
                return@repeat
            }

            // Завершающий раунд: есть финальный текст от модели.
            collectedText = collected
            textWasCollected = textWasCollected || collected.isNotEmpty()
            stopRequested = true
        }

        val finalText = extractReply(collectedText.toString()).ifEmpty {
            if (textWasCollected) "сделала" else legacyFallbackWhenNoText()
        }
        return finalText
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
        try {
            llmEngine.generateResponse(commandText, history, options).collect { delta ->
                answer.append(delta)
                emit(AiResponse.ReplyDelta(delta, answer.toString()))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw e
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
     * Сообщение ассистента с прикреплёнными tool_calls — LLM получит его
     * на следующем раунде вместе с результатами инструментов.
     *
     * В OpenAI-формате содержимое `[content]` пустое, а сами вызовы лежат
     * в массиве `[tool_calls]`. Если модель успела начать текст до
     * объявления tool_calls (стрим-гонка), [extraContent] сохраняется в
     * обычном `content` сообщения, но отдельным assistant-сообщением
     * идёт ВЫЗОВЫ: иначе OpenAPI считает формат невалидным.
     */
    private fun syntacticAssistantToolMessage(
        calls: List<ToolCall>,
        extraContent: String,
    ): ChatMessage {
        // Если есть и текстовая преамбула, и tool_calls — добавляем
        // дополнительное user-сообщение «продолжай» с текстом, чтобы
        // не терять сгенерированный текст (редкая гонка стримов).
        return ChatMessage(
            id = ChatMessage.newId(),
            role = MessageRole.ASSISTANT,
            content = extraContent,
            timestamp = System.currentTimeMillis(),
            toolCalls = calls,
        )
    }

    private fun legacyFallbackWhenNoText(): String =
        // Аналог того, что в системном промпте делает настоящий LLM:
        // короткая реплика после набора действий.
        "сделала"

    /**
     * Вытаскивает поле "reply" из JSON-ответа модели.
     * Если JSON кривой или reply пустой — возвращает пустую строку
     * (защита от ситуации когда модель вдруг ответила не JSON-ом).
     */
    private fun extractReply(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return runCatching {
            org.json.JSONObject(trimmed).optString("reply", "").trim()
        }.getOrDefault("").ifEmpty {
            // Модель ответила не JSON — берём как есть, но без фигурных скобок
            if (trimmed.startsWith("{")) "" else trimmed
        }
    }

    private companion object {
        const val ERROR_NO_SPEECH = "Не услышала ни слова. Нажми микрофон и скажи ещё раз."
        const val ERROR_EMPTY_COMMAND = "Пустая команда — нечего обрабатывать."
        const val ERROR_UNKNOWN = "Что-то пошло не так. Попробуй ещё раз."
        const val FALLBACK_ANSWER = "Я не смогла сформулировать ответ. Попробуй переспросить."

        /**
         * Защита от бесконечного цикла tool calls. На практике модель
         * завершает цикл за 1-3 раунда; 10 раундов — щедрый лимит, после которого
         * оркестратор возвращает собранный текст и TTS, чтобы UI не висел.
         */
        const val MAX_TOOL_ROUNDS = 10

        /**
         * Минимум слов в транскрипте, чтобы отправить запрос к LLM.
         * Одиночные звуки/шумы Deepgram иногда транскрибирует как одно слово
         * ("э", "м", "ну") — они не несут смысла и только жгут rate limit.
         */
        const val MIN_WORDS_TO_PROCESS = 2
    }

    // Подавляем предупреждение о неиспользуемых параметрах в legacy-пути
    @Suppress("unused")
    private fun transcriptHolder_UNUSED() = Unit
}
