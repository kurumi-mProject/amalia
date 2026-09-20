package com.my.amali.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.my.amali.data.model.ChatMessage
import com.my.amali.data.model.Conversation
import com.my.amali.domain.entity.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
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
        .catch { emit(androidx.datastore.preferences.core.emptyPreferences()) }
        .map { prefs -> decode(prefs[Keys.CONVERSATIONS]).sortedByDescending { it.updatedAt } }

    /** Возвращает разговор по id или null, если он не найден. */
    suspend fun get(id: String): Conversation? =
        conversationsList().firstOrNull { it.id == id }

    /** Текущее число разговоров (для бейджей на главном экране). */
    suspend fun count(): Int = conversationsList().size

    /**
     * N самых свежих разговоров одним снимком.
     *
     * Нужен, чтобы при старте продолжить последний диалог и не подписываться
     * на весь поток истории ради одной записи.
     */
    suspend fun recentSnapshot(limit: Int): List<Conversation> =
        conversationsList().take(limit.coerceAtLeast(1))

    /** Сколько всего сохранённых реплик (для строки приватности). */
    suspend fun messageCount(): Int = conversationsList().sumOf { it.messages.size }

    /**
     * Снимок текущей истории.
     *
     * Используется [first], а не `collect`: DataStore-поток бесконечен,
     * поэтому `collect` с `return@collect` не завершался и вызов висел вечно
     * (из-за этого счётчик разговоров в шапке всегда оставался нулевым).
     */
    private suspend fun conversationsList(): List<Conversation> =
        decode(dataStore.data.first()[Keys.CONVERSATIONS])
            .sortedByDescending { it.updatedAt }

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
    suspend fun upsert(conversation: Conversation) = mutate { list ->
        (list.filterNot { it.id == conversation.id } + conversation)
            .sortedByDescending { it.updatedAt }
    }

    /** Добавляет сообщение в конец разговора; создаёт разговор при отсутствии. */
    suspend fun appendMessage(conversationId: String, message: ChatMessage) =
        appendMessages(conversationId, listOf(message))

    /**
     * Добавляет сразу несколько сообщений ОДНОЙ записью в DataStore.
     *
     * Раньше пара «вопрос-ответ» уходала двумя-тремя отдельными `edit{}`, и
     * каждый вызов перечитывал и переписывал весь JSON истории. На длинной
     * истории это десятки миллисекунд на раунд диалога — ровно там, где
     * пользователь ждёт звук. Теперь одно чтение и одна запись на цикл.
     */
    suspend fun appendMessages(conversationId: String, messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        mutate { list ->
            val existing = list.firstOrNull { it.id == conversationId }
            val seed = existing ?: Conversation(
                id = conversationId,
                title = Conversation.deriveTitle(messages.first().content),
                createdAt = messages.first().timestamp,
                updatedAt = messages.first().timestamp,
            )
            // Индекс сжатой границы — это позиция в списке, и он обязан
            // пережить любую запись в тот же разговор.
            //
            // Здесь был тонкий баг: `copy(messages = …)` неявно сбрасывал
            // `summarizedCount` в значение по умолчанию — ноль. В памяти
            // ViewModel граница оставалась верной, а на диске обнулялась,
            // и после перезапуска модель получала резюме, но при этом
            // «несжатым» считался весь диалог целиком. На длинном разговоре
            // это возвращало в промпт реплики, которые давно должны были
            // жить пересказом, — контекст не сокращался вовсе.
            val updated = seed.copy(
                messages = seed.messages + messages,
                updatedAt = messages.last().timestamp,
                contextSummary = seed.contextSummary,
                summarizedCount = seed.summarizedCount.coerceIn(0, seed.messages.size + messages.size),
            )
            (list.filterNot { it.id == conversationId } + updated)
                .sortedByDescending { it.updatedAt }
        }
    }

    /**
     * Сохраняет сжатое резюме контекста разговора.
     *
     * [covered] — сколько первых сообщений закрыто пересказом. UI рисует по
     * этому метку, а модель получает резюме вместо дословных старых реплик.
     */
    suspend fun setSummary(conversationId: String, summary: String?, covered: Int) {
        mutate { list ->
            val target = list.firstOrNull { it.id == conversationId } ?: return@mutate list
            val updated = target.withSummary(summary, covered)
            (list.filterNot { it.id == conversationId } + updated)
                .sortedByDescending { it.updatedAt }
        }
    }

    /** Заменяет последний ответ ассистента в разговоре (для стриминга). */
    suspend fun updateLastAssistantText(conversationId: String, text: String) {
        mutate { list ->
            val updated = list.firstOrNull { it.id == conversationId }
                ?.withUpdatedLastAssistantText(text) ?: return@mutate list
            (list.filterNot { it.id == conversationId } + updated)
                .sortedByDescending { it.updatedAt }
        }
    }

    /** Удаляет разговор по id. */
    suspend fun delete(id: String) {
        mutate { list -> list.filterNot { it.id == id } }
    }

    /** Полностью очищает историю. */
    suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs[Keys.CONVERSATIONS] = json.encodeToString(serializer, emptyList())
        }
    }

    /**
     * Применяет срок хранения: удаляет разговоры старше [retentionDays] суток.
     *
     * [UserSettings.RETENTION_FOREVER] — no-op. Дата сравнивается по
     * [Conversation.updatedAt]: разговор, в который дописали сообщение вчера,
     * жив, даже если начался месяц назад.
     *
     * Раньше настройка «хранить 7/30/90 дней» была декоративной: ползунок
     * сохранялся, но ничего не удалял, и история росла бесконечно. Теперь
     * вызывается при старте ассистента и при открытии списка истории.
     */
    suspend fun applyRetention(retentionDays: Int) {
        if (retentionDays == UserSettings.RETENTION_FOREVER) return
        if (retentionDays <= 0) return
        val cutoff = System.currentTimeMillis() - retentionDays * DAY_MILLIS
        mutate { list -> list.filter { it.updatedAt >= cutoff } }
    }

    // ── Внутреннее ───────────────────────────────────────────────────────

    /**
     * Единая точка мутации истории: одно чтение → одна трансформация →
     * одна запись. Все публичные методы проходят через неё, поэтому формат
     * хранения и сортировка не разъезжаются.
     */
    private suspend fun mutate(block: (List<Conversation>) -> List<Conversation>) {
        dataStore.edit { prefs ->
            val current = decode(prefs[Keys.CONVERSATIONS])
            prefs[Keys.CONVERSATIONS] = json.encodeToString(serializer, block(current))
        }
    }

    private fun decode(raw: String?): List<Conversation> =
        if (raw.isNullOrEmpty()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
        }

    private companion object {
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
