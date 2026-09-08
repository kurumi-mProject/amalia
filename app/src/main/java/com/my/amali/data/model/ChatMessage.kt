package com.my.amali.data.model

import kotlinx.serialization.Serializable

/**
 * A single chat message exchanged with the assistant.
 *
 * @property id unique message identifier (UUID string).
 * @property role author of the message.
 * @property content text content of the message.
 * @property content text content of the message.
 * @property timestamp creation time in epoch milliseconds.
 * @property isStreaming true while the assistant is still generating this message;
 *   always false for persisted messages.
 */
@Serializable
data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long,
    val isStreaming: Boolean = false
) {
    /** Convenience check: the message was written by the user. */
    val isFromUser: Boolean
        get() = role == MessageRole.USER

    /** Convenience check: the message was written by the assistant. */
    val isFromAssistant: Boolean
        get() = role == MessageRole.ASSISTANT

    /** Returns a copy marked as fully received (streaming finished). */
    fun asCompleted(): ChatMessage = copy(isStreaming = false)

    companion object {
        /** Creates a new user message with a generated [id] and current [timestamp]. */
        fun user(content: String): ChatMessage = ChatMessage(
            id = newId(),
            role = MessageRole.USER,
            content = content,
            timestamp = System.currentTimeMillis(),
            isStreaming = false
        )

        /** Creates a new streaming assistant message placeholder. */
        fun streamingAssistant(): ChatMessage = ChatMessage(
            id = newId(),
            role = MessageRole.ASSISTANT,
            content = "",
            timestamp = System.currentTimeMillis(),
            isStreaming = true
        )

        /** Creates a system-level message (e.g. persona instructions). */
        fun system(content: String): ChatMessage = ChatMessage(
            id = newId(),
            role = MessageRole.SYSTEM,
            content = content,
            timestamp = System.currentTimeMillis(),
            isStreaming = false
        )

        /** Generates a fresh UUID string for message ids. */
        fun newId(): String = java.util.UUID.randomUUID().toString()
    }
}

/** Author of a [ChatMessage]. */
@Serializable
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}
