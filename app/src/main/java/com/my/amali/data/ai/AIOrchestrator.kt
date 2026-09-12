package com.my.amali.data.ai

import com.my.amali.data.model.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * Оркестратор конвейера STT → LLM → TTS.
 *
 * Ключевая идея: **пофразовый пайплайн**. Как только модель дописала первое
 * законченное предложение, оно немедленно уходит в синтез, пока LLM
 * продолжает генерировать остальное. За счёт этого первый звук слышен
 * примерно через 0.7–1.2 с вместо 4–6 с (раньше синтез стартовал только
 * после полного ответа).
 *
 * Порядок звука сохраняется: предложения синтезируются строго по очереди,
 * но их генерация и озвучка идут параллельно.
 */
class AIOrchestrator(
    internal val sttEngine: SpeechToTextEngine,
    internal val llmEngine: LanguageModel,
    internal val ttsEngine: TextToSpeechEngine,
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
                    // Длинная речь приходит несколькими финальными сегментами —
                    // склеиваем их, а не заменяем (иначе терялось начало фразы).
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
        if (transcript.isBlank()) {
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
    ): Flow<String> = flow {
        val acc = StringBuilder()
        llmEngine.generateResponse(text.trim(), history, options).collect { delta ->
            acc.append(delta)
            emit(acc.toString())
        }
    }

    // ── Ядро конвейера ───────────────────────────────────────────────────

    /**
     * LLM и TTS с прямым стримингом токенов в Fish Audio WebSocket.
     *
     * Токены от Groq идут напрямую в WS без накопления предложений.
     * Fish Audio начинает синтез сразу — минимальная задержка.
     */
    private suspend fun runPipeline(
        commandText: String,
        history: List<ChatMessage>,
        options: EngineOptions,
        emit: suspend (AiResponse) -> Unit,
    ) = kotlinx.coroutines.coroutineScope {
        emit(AiResponse.Thinking(true))

        var thinkingClosed = false
        var speakingOpened = false

        // Сначала startStreaming — он ждёт onOpen (CountDownLatch).
        // Только после этого подписываемся на streamingAudio, иначе
        // audioJob захватит старый канал до пересоздания в startStreaming.
        ttsEngine.startStreaming(options)

        // Теперь канал уже актуальный — подписываемся
        val audioJob = launch {
            ttsEngine.streamingAudio.collect { chunk ->
                if (!speakingOpened) {
                    speakingOpened = true
                    emit(AiResponse.Speaking(true))
                }
                emit(AiResponse.Audio(chunk))
            }
        }

        val answer = StringBuilder()

        try {
            llmEngine.generateResponse(commandText, history, options).collect { delta ->
                answer.append(delta)
                emit(AiResponse.ReplyDelta(delta, answer.toString()))

                // Первый токен — закрываем Thinking
                if (!thinkingClosed) {
                    thinkingClosed = true
                    emit(AiResponse.Thinking(false))
                }

                // Стримим токен напрямую в Fish Audio WS
                // isConnected уже true (startStreaming подождал onOpen)
                ttsEngine.sendToken(delta)
            }

            // LLM закончил — флашим и стопаем WS
            ttsEngine.flushStreaming()
            ttsEngine.stopStreaming()

            // Ждём пока всё аудио придёт (с таймаутом 5 сек)
            kotlinx.coroutines.withTimeoutOrNull(5000) {
                audioJob.join()
            } ?: run {
                audioJob.cancel()
            }

        } catch (e: CancellationException) {
            ttsEngine.stopStreaming()
            audioJob.cancel()
            throw e
        } catch (e: Throwable) {
            ttsEngine.stopStreaming()
            audioJob.cancel()
            if (!thinkingClosed) emit(AiResponse.Thinking(false))
            if (speakingOpened) emit(AiResponse.Speaking(false))
            throw e
        }

        if (!thinkingClosed) emit(AiResponse.Thinking(false))
        if (speakingOpened) emit(AiResponse.Speaking(false))

        val responseText = answer.toString().trim().ifEmpty { FALLBACK_ANSWER }
        lastError = null
        emit(AiResponse.Finished(responseText))
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

    private companion object {
        const val ERROR_NO_SPEECH = "Не услышала ни слова. Нажми микрофон и скажи ещё раз."
        const val ERROR_EMPTY_COMMAND = "Пустая команда — нечего обрабатывать."
        const val ERROR_UNKNOWN = "Что-то пошло не так. Попробуй ещё раз."
        const val FALLBACK_ANSWER = "Я не смогла сформулировать ответ. Попробуй переспросить."
    }
}
