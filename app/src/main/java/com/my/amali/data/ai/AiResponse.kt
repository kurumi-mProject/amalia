package com.my.amali.data.ai

/**
 * События, которые [AIOrchestrator] стримит во время обработки одной команды.
 * По ним UI ведёт свою машину состояний (волна, подписи, карточки диалога).
 *
 * Типичная последовательность:
 * `Level`* → `PartialTranscript`* → `Transcript` →
 * `Thinking(true)` →
 * [`ToolRunning`* → `ToolCompleted`*]* →
 * `ReplyDelta`* → `Thinking(false)` →
 * `Speaking(true)` → `Audio`* →
 * `Speaking(false)` → `Finished`.
 *
 * Tool-события между `Thinking(true)` и `ReplyDelta` появляются только если
 * LLM хочет вызвать устройство/инструменты; для чисто текстового диалога
 * их нет в потоке.
 */
sealed interface AiResponse {

    /** Мгновенная громкость микрофона 0..1 — только на этапе слушания. */
    data class Level(val level: Float) : AiResponse

    /** Неточная гипотеза распознавания: показывается как живые субтитры. */
    data class PartialTranscript(val text: String) : AiResponse

    /** Итоговый распознанный текст пользователя. */
    data class Transcript(val text: String) : AiResponse

    /** Вход/выход из фазы размышления модели. */
    data class Thinking(val isThinking: Boolean) : AiResponse

    /**
     * Инструмент начал исполнение. UI может показать короткий индикатор
     * (например, «⚙️ выполняю…»), если хочет прозрачности для пользователя.
     */
    data class ToolRunning(
        val toolName: String,
        val arguments: Map<String, Any?>,
    ) : AiResponse

    /**
     * Инструмент отработал.
     *
     * @property ok true = инструмент успешно выполнил операцию.
     * @property output машинно-читаемый результат, который LLM получила в ответ
     *   (обычно JSON-строка).
     * @property errorMessage человекочитаемое объяснение ошибки, если [ok] = false.
     */
    data class ToolCompleted(
        val toolName: String,
        val ok: Boolean,
        val output: String,
        val errorMessage: String?,
    ) : AiResponse

    /** Очередной фрагмент ответа модели (дописывается к предыдущим). */
    data class ReplyDelta(val delta: String, val fullText: String) : AiResponse

    /** Вход/выход из фазы озвучки. */
    data class Speaking(val isSpeaking: Boolean) : AiResponse

    /** Готовый к воспроизведению фрагмент синтезированного звука. */
    data class Audio(val chunk: AudioChunk) : AiResponse

    /** Полный текст ответа: последнее успешное событие обработки. */
    data class Finished(val responseText: String) : AiResponse

    /** Обработка не удалась; [message] можно показать пользователю. */
    data class Error(val message: String) : AiResponse
}
