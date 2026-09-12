package com.my.amali.data.ai

import com.my.amali.data.model.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
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
     * LLM и TTS с пофразовым пайплайном.
     *
     * @param emit коллбэк отправки события в поток (уже внутри channelFlow).
     */
    private suspend fun runPipeline(
        commandText: String,
        history: List<ChatMessage>,
        options: EngineOptions,
        emit: suspend (AiResponse) -> Unit,
    ) = kotlinx.coroutines.coroutineScope {
        emit(AiResponse.Thinking(true))

        // Очередь готовых к озвучке предложений.
        val sentences = Channel<String>(capacity = Channel.UNLIMITED)
        var thinkingClosed = false
        var speakingOpened = false

        // Озвучка идёт параллельно генерации, но строго по порядку фраз.
        // Prefetch: пока текущее предложение синтезируется/играет, следующий
        // HTTP-запрос к TTS уже летит — нет паузы между предложениями.
        val speakJob = launch {
            // Буфер аудио текущего предложения накапливается заранее.
            var prefetchedChunks: List<AudioChunk>? = null
            var prefetchedFor: String? = null

            for (sentence in sentences) {
                if (!speakingOpened) {
                    speakingOpened = true
                    emit(AiResponse.Speaking(true))
                }

                // Используем уже prefetch-нутые данные если они для этого предложения.
                val chunks: List<AudioChunk> = if (prefetchedFor == sentence && prefetchedChunks != null) {
                    prefetchedChunks!!
                } else {
                    val buf = mutableListOf<AudioChunk>()
                    ttsEngine.speak(sentence, options).collect { buf.add(it) }
                    buf
                }
                prefetchedChunks = null
                prefetchedFor = null

                // Запускаем prefetch следующего предложения параллельно с отдачей текущего.
                val nextSentence = sentences.tryReceive().getOrNull()
                val prefetchJob = if (nextSentence != null) {
                    launch {
                        val buf = mutableListOf<AudioChunk>()
                        ttsEngine.speak(nextSentence, options).collect { buf.add(it) }
                        prefetchedChunks = buf
                        prefetchedFor = nextSentence
                    }
                } else null

                // Отдаём аудио текущего предложения.
                for (chunk in chunks) emit(AiResponse.Audio(chunk))

                // Если следующее предложение было взято из канала — обрабатываем его.
                if (nextSentence != null) {
                    prefetchJob?.join()
                    if (!speakingOpened) {
                        speakingOpened = true
                        emit(AiResponse.Speaking(true))
                    }
                    val nextChunks = prefetchedChunks ?: emptyList()
                    prefetchedChunks = null
                    prefetchedFor = null
                    for (chunk in nextChunks) emit(AiResponse.Audio(chunk))
                }
            }
        }

        val answer = StringBuilder()
        val pending = StringBuilder()

        try {
            llmEngine.generateResponse(commandText, history, options).collect { delta ->
                answer.append(delta)
                pending.append(delta)
                emit(AiResponse.ReplyDelta(delta, answer.toString()))

                // Как только набралось законченное предложение — отправляем в синтез.
                while (true) {
                    val cut = sentenceBoundary(pending) ?: break
                    val sentence = pending.substring(0, cut).trim()
                    pending.delete(0, cut)
                    if (sentence.isNotEmpty()) {
                        if (!thinkingClosed) {
                            thinkingClosed = true
                            emit(AiResponse.Thinking(false))
                        }
                        sentences.send(sentence)
                    }
                }
            }

            // Хвост, не заканчивающийся знаком препинания.
            val tail = pending.toString().trim()
            if (tail.isNotEmpty()) {
                if (!thinkingClosed) {
                    thinkingClosed = true
                    emit(AiResponse.Thinking(false))
                }
                sentences.send(tail)
            }
            sentences.close()
            speakJob.join()
        } catch (e: CancellationException) {
            sentences.close()
            speakJob.cancel()
            throw e
        } catch (e: Throwable) {
            sentences.close()
            speakJob.cancel()
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

    /**
     * Возвращает индекс конца первого законченного предложения в [buffer]
     * или null, если предложение ещё не набралось.
     *
     * Предложение считается готовым, если найден терминальный знак и
     * накопилось хотя бы [MIN_SENTENCE_CHARS] символов — иначе синтез
     * дробился бы на бессмысленные обрывки вроде «Да.».
     * Если текста уже много, а знаков препинания нет, режем по запятой
     * или пробелу, чтобы не ждать конца длинной фразы.
     */
    private fun sentenceBoundary(buffer: StringBuilder): Int? {
        val length = buffer.length
        if (length < MIN_SENTENCE_CHARS) return null

        for (i in MIN_SENTENCE_CHARS - 1 until length) {
            val c = buffer[i]
            if (c in TERMINATORS) {
                // Не режем внутри «т.д.» и сокращений: следующий символ должен
                // быть пробелом или концом буфера.
                val next = if (i + 1 < length) buffer[i + 1] else ' '
                if (next.isWhitespace()) return i + 1
            }
        }

        if (length >= SOFT_CUT_CHARS) {
            val comma = buffer.lastIndexOf(",")
            if (comma >= MIN_SENTENCE_CHARS) return comma + 1
            val space = buffer.lastIndexOf(" ")
            if (space >= MIN_SENTENCE_CHARS) return space + 1
        }
        return null
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

        /** Минимальная длина фразы, отдаваемой в синтез. */
        const val MIN_SENTENCE_CHARS = 24

        /** Длина, после которой режем фразу даже без терминального знака. */
        const val SOFT_CUT_CHARS = 140

        val TERMINATORS = charArrayOf('.', '!', '?', '…', ';', ':')
    }
}
