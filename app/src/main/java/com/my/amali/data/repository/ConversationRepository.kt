package com.my.amali.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.my.amali.data.model.ChatMessage
import com.my.amali.data.model.Conversation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Репозиторий истории разговоров.
 *
 * Хранит все [Conversation] одной JSON-записью в DataStore: объём локальной
 * истории ассистента мал, а такой формат не требует базы данных и переживает
 * обновления приложения. Список всегда отсортирован по [Conversation.updatedAt]
 * по убыванию — свежие разговоры сверху.
 */
class ConversationRepository(
    private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {

    private object Keys {
        val CONVERSATIONS = stringPreferencesKey("conversations_json")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val serializer = ListSerializer(Conversation.serializer())

    // ── Чтение ───────────────────────────────────────────────────────────

    /** Поток всех разговоров, новые сверху. */
    val conversations: Flow<List<Conversation>> = dataStore.data
        .catch { emit(emptyList()) }
        .map { prefs ->
            val raw = prefs[Keys.CONVERSATIONS] ?: return@map emptyList()
            runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
        }

    /** Возвращает разговор по id или null, если он не найден. */
    suspend fun get(id: String): Conversation? =
        conversationsList().firstOrNull { it.id == id }

    /** Текущее число разговоров (для бейджей на главном экране). */
    suspend fun count(): Int = conversationsList().size

    private suspend fun conversationsList(): List<Conversation> {
        var snapshot: List<Conversation> = emptyList()
        dataStore.data.collect {
            val raw = it[Keys.CONVERSATIONS] ?: ""
            snapshot = if (raw.isEmpty()) {
                emptyList()
            } else {
                runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
            }
            return@collect
        }
        return snapshot.sortedByDescending { conversation -> conversation.updatedAt }
    }

    // ── Запись ───────────────────────────────────────────────────────────

    /** Создаёт пустой разговор с автозаголовком и возвращает его. */
    suspend fun create(title: String): Conversation {
        val now = System.currentTimeMillis()
        val conversation = Conversation(
            id = ChatMessage.newId(),
            title = title.ifBlank { "…" },
            createdAt = now,
            updatedAt = now,
        )
        upsert(conversation)
        return conversation
    }

    /** Добавляет или обновляет разговор (upsert по id). */
    suspend fun upsert(conversation: Conversation) {
        dataStore.edit { prefs ->
            val raw = prefs[Keys.CONVERSATIONS] ?: ""
            val current = if (raw.isEmpty()) {
                emptyList()
            } else {
                runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
            }
            val updated = (current.filterNot { it.id == conversation.id } + conversation)
                .sortedByDescending { it.updatedAt }
            prefs[Keys.CONVERSATIONS] = json.encodeToString(serializer, updated)
        }
    }

    /** Добавляет сообщение в конец разговора; создаёт разговор при отсутствии. */
    suspend fun appendMessage(conversationId: String, message: ChatMessage) {
        dataStore.edit { prefs ->
            val raw = prefs[Keys.CONVERSATIONS] ?: ""
            val current = if (raw.isEmpty()) {
                emptyList()
            } else {
                runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
            }
            val existing = current.firstOrNull { it.id == conversationId }
            val updated = (existing ?: Conversation(
                id = conversationId,
                title = message.content.take(MAX_TITLE_LENGTH).ifBlank { "…" },
                createdAt = message.timestamp,
                updatedAt = message.timestamp,
            )).withMessage(message)
            val next = (current.filterNot { it.id == conversationId } + updated)
                .sortedByDescending { it.updatedAt }
            prefs[Keys.CONVERSATIONS] = json.encodeToString(serializer, next)
        }
    }

    /** Заменяет последний ответ ассистента в разговоре (для стриминга). */
    suspend fun updateLastAssistantText(conversationId: String, text: String) {
        dataStore.edit { prefs ->
            val raw = prefs[Keys.CONVERSATIONS] ?: ""
            val current = runCatching { json.decodeFromString(serializer, raw) }
                .getOrDefault(emptyList())
            val updated = current
                .firstOrNull { it.id == conversationId }
                ?.withUpdatedLastAssistantText(text) ?: return@edit
            val next = (current.filterNot { it.id == conversationId } + updated)
                .sortedByDescending { it.updatedAt }
            prefs[Keys.CONVERSATIONS] = json.encodeToString(serializer, next)
        }
    }

    /** Удаляет разговор по id. */
    suspend fun delete(id: String) {
        dataStore.edit { prefs ->
            val raw = prefs[Keys.CONVERSATIONS] ?: ""
            val current = runCatching { json.decodeFromString(serializer, raw) }
                .getOrDefault(emptyList())
            prefs[Keys.CONVERSATIONS] =
                json.encodeToString(serializer, current.filterNot { it.id == id })
        }
    }

    /** Полностью очищает историю. */
    suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs[Keys.CONVERSATIONS] = json.encodeToString(serializer, emptyList())
        }
    }

    private companion object {
        const val MAX_TITLE_LENGTH = 48
    }
}
