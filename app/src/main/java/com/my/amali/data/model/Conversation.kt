package com.my.amali.data.model

import kotlinx.serialization.Serializable

/**
 * A persisted conversation consisting of an ordered list of [ChatMessage]s.
 *
 * @property id unique conversation identifier (UUID string).
 * @property title human-readable title, usually derived from the first user message.
 * @property messages messages in chronological order.
 * @property createdAt creation time in epoch milliseconds.
 * @property updatedAt last modification time in epoch milliseconds.
 * @property contextSummary сжатое резюме начала диалога, которое модель
 *   подставляет вместо старых реплик. null — пока нечего сжимать.
 * @property summarizedCount сколько первых сообщений диалога закрыто этим
 *   резюме. Нужно UI, чтобы нарисовать метку «сжато» ровно там, где контекст
 *   начался не дословно, а пересказом.
 */
@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val contextSummary: String? = null,
    val summarizedCount: Int = 0,
) {
    /** Сколько реплик сейчас дословно видно в истории (не закрыто резюме). */
    val visibleTailCount: Int
        get() = (messages.size - summarizedCount).coerceAtLeast(0)

    /** Есть ли что показывать как «сжатый контекст». */
    val hasCompressedContext: Boolean
        get() = !contextSummary.isNullOrBlank() && summarizedCount > 0

    /** Резюме в одну строку для превью и карточек. */
    val summaryPreview: String
        get() = contextSummary?.trim()?.replace(Regex("\\s+"), " ").orEmpty()

    /** Возвращает копию с новым резюме диалога. */
    fun withSummary(summary: String?, covered: Int): Conversation = copy(
        contextSummary = summary?.takeIf { it.isNotBlank() },
        summarizedCount = covered.coerceIn(0, messages.size),
    )
    /** Whether this conversation contains any messages. */
    val isEmpty: Boolean
        get() = messages.isEmpty()

    /** The most recent message, or null when the conversation is empty. */
    val lastMessage: ChatMessage?
        get() = messages.lastOrNull()

    /** Returns a copy with [message] appended and [updatedAt] refreshed. */
    fun withMessage(message: ChatMessage): Conversation = copy(
        messages = messages + message,
        updatedAt = System.currentTimeMillis()
    )

    /** Returns a copy with a new [title] and refreshed [updatedAt]. */
    fun withTitle(newTitle: String): Conversation = copy(
        title = newTitle,
        updatedAt = System.currentTimeMillis()
    )

    /** Returns a copy where the last assistant message is replaced by [text]. */
    fun withUpdatedLastAssistantText(text: String): Conversation {
        val index = messages.indexOfLast { it.role == MessageRole.ASSISTANT }
        if (index == -1) return this
        val updated = messages[index].copy(content = text)
        return copy(
            messages = messages.toMutableList().apply { set(index, updated) },
            updatedAt = System.currentTimeMillis()
        )
    }

    companion object {
        /** Generates a fresh UUID string for conversation ids. */
        fun newId(): String = java.util.UUID.randomUUID().toString()

        /** Creates a new empty conversation titled after [title]. */
        fun create(title: String = "Новый диалог"): Conversation {
            val now = System.currentTimeMillis()
            return Conversation(
                id = newId(),
                title = title,
                messages = emptyList(),
                createdAt = now,
                updatedAt = now
            )
        }

        /**
         * Creates a conversation seeded with a user [message] and derives
         * the title from the first words of that message.
         */
        fun fromFirstMessage(message: ChatMessage): Conversation {
            val now = System.currentTimeMillis()
            return Conversation(
                id = newId(),
                title = deriveTitle(message.content),
                messages = listOf(message),
                createdAt = now,
                updatedAt = now
            )
        }

        /** Derives a short conversation title from a first user message. */
        fun deriveTitle(text: String, maxLength: Int = 32): String {
            val trimmed = text.trim().replace(Regex("\\s+"), " ")
            return if (trimmed.length <= maxLength) {
                trimmed.ifEmpty { "Новый диалог" }
            } else {
                trimmed.take(maxLength).trimEnd() + "…"
            }
        }
    }
}
