package com.my.amali.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * A single chat message exchanged with the assistant.
 *
 * @property id unique message identifier (UUID string).
 * @property role author of the message.
 * @property content text content of the message.
 * @property timestamp creation time in epoch milliseconds.
 * @property isStreaming true while the assistant is still generating this message;
 *   always false for persisted messages.
 * @property toolCallId для сообщений роли [MessageRole.TOOL] — id вызова,
 *   чей результат мы храним. LLM использует его для матчинга результатов
 *   с запросами; без него tool-результаты попадают в никуда.
 * @property toolCalls для сообщений роли [MessageRole.ASSISTANT] — список
 *   вызовов инструментов, которые модель сделала В ЭТОМ сообщении.
 *   Поле transient: не попадает в персистентную историю, но пробрасывается
 *   в LLM на следующем раунде multi-turn цикла.
 * @property actions человекочитаемая сводка того, что Амалия сделала в этом
 *   ответе («Wi-Fi включён», «яркость 30%»). В отличие от [toolCalls] поле
 *   СЕРИАЛИЗУЕТСЯ: история обязана показывать действия, а не только текст,
 *   иначе восстановленный диалог выглядит как обрывок.
 * @property contextTurnedIntoSummary true для служебной реплики-маркера: всё,
 *   что было до неё, модель сейчас видит как сжатое резюме, а не дословно.
 */
@Serializable
data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long,
    val isStreaming: Boolean = false,
    val toolCallId: String? = null,
    val actions: List<String> = emptyList(),
    val contextTurnedIntoSummary: Boolean = false,
    @Transient
    val toolCalls: List<com.my.amali.data.ai.ToolCall> = emptyList(),
) {
    /** Convenience check: the message was written by the user. */
    val isFromUser: Boolean
        get() = role == MessageRole.USER

    /** Convenience check: the message was written by the assistant. */
    val isFromAssistant: Boolean
        get() = role == MessageRole.ASSISTANT

    /** Convenience check: the message is a tool-call result for the LLM. */
    val isToolResult: Boolean
        get() = role == MessageRole.TOOL

    /** Convenience check: the message references tool calls (multi-turn). */
    val hasToolCalls: Boolean
        get() = toolCalls.isNotEmpty()

    /** Returns a copy marked as fully received (streaming finished). */
    fun asCompleted(): ChatMessage = copy(isStreaming = false)

    /** Возвращает копию с прикреплённой сводкой действий для истории. */
    fun withActions(actions: List<String>): ChatMessage =
        copy(actions = actions.filter { it.isNotBlank() }.distinct())

    /**
     * Возвращает копию с прикреплёнными вызовами инструментов и пустым content
     * (по OpenAI спеке вызовы и текст не смешиваются в одном сообщении: либо
     * `content`, либо `tool_calls`, но не оба сразу).
     */
    fun withToolCalls(calls: List<com.my.amali.data.ai.ToolCall>): ChatMessage =
        copy(content = "", toolCalls = calls)

    companion object {
        /** Creates a new user message with a generated [id] and current [timestamp]. */
        fun user(content: String): ChatMessage = ChatMessage(
            id = newId(),
            role = MessageRole.USER,
            content = content,
            timestamp = System.currentTimeMillis(),
            isStreaming = false,
        )

        /** Creates a new streaming assistant message placeholder. */
        fun streamingAssistant(): ChatMessage = ChatMessage(
            id = newId(),
            role = MessageRole.ASSISTANT,
            content = "",
            timestamp = System.currentTimeMillis(),
            isStreaming = true,
        )

        /**
         * Готовая реплика ассистента.
         *
         * Used by the orchestrator to put **human text only** into the LLM
         * history: сырой JSON-контракт модели в историю не попадает никогда.
         */
        fun assistant(content: String, actions: List<String> = emptyList()): ChatMessage = ChatMessage(
            id = newId(),
            role = MessageRole.ASSISTANT,
            content = content,
            timestamp = System.currentTimeMillis(),
            isStreaming = false,
            actions = actions,
        )

        /** Creates a system-level message (e.g. persona instructions). */
        fun system(content: String): ChatMessage = ChatMessage(
            id = newId(),
            role = MessageRole.SYSTEM,
            content = content,
            timestamp = System.currentTimeMillis(),
            isStreaming = false,
        )

        /**
         * Создаёт tool-result сообщение для отправки LLM.
         *
         * @param toolCallId id вызова из [com.my.amali.data.ai.ToolCall.id].
         * @param content результат в виде строки (как правило JSON).
         */
        fun toolResult(toolCallId: String, content: String): ChatMessage = ChatMessage(
            id = newId(),
            role = MessageRole.TOOL,
            content = content,
            timestamp = System.currentTimeMillis(),
            isStreaming = false,
            toolCallId = toolCallId,
        )

        /** Generates a fresh UUID string for message ids. */
        fun newId(): String = java.util.UUID.randomUUID().toString()
    }
}

/**
 * Автор [ChatMessage].
 *
 * Расширен [MessageRole.TOOL] для поддержки функции-вызовов:
 * такие сообщения — это ответы обработчиков инструментов, которые LLM
 * видит как подтверждение и идёт делать следующий вызов/ответ.
 */
@Serializable
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL,
}
