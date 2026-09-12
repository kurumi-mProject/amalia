package com.my.amali.data.ai

/**
 * События, которые [AIOrchestrator] стримит во время обработки одной команды.
 * По ним UI ведёт свою машину состояний (волна, подписи, карточки диалога).
 *
 * Типичная последовательность:
 * `Level`* → `PartialTranscript`* → `Transcript` → `Thinking(true)` →
 * `ReplyDelta`* → `Thinking(false)` → `Speaking(true)` → `Audio`* →
 * `Speaking(false)` → `Finished`.
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
