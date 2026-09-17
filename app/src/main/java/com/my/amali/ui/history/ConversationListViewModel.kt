package com.my.amali.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.amali.core.di.ServiceLocator
import com.my.amali.data.model.Conversation
import com.my.amali.data.repository.ConversationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel списка истории разговоров: подписка на репозиторий,
 * живой поиск по заголовкам и текстам сообщений, удаление и очистка.
 */
class ConversationListViewModel : ViewModel() {

    private val repository: ConversationRepository = ServiceLocator.conversationRepository

    private val query = MutableStateFlow("")

    init {
        // Ретеншн применяется и здесь: пользователь может открыть приложение
        // сразу на вкладке «История» (restore state после свёртывания).
        viewModelScope.launch {
            runCatching {
                ServiceLocator.settingsRepository.settings.first().dataRetentionDays.let {
                    repository.applyRetention(it)
                }
            }
        }
    }

    /** Отфильтрованный список разговоров (новые сверху). */
    val conversations: StateFlow<List<Conversation>> = combine(
        repository.conversations,
        query,
    ) { list, filter ->
        val trimmed = filter.trim()
        if (trimmed.isEmpty()) {
            list
        } else {
            list.filter { conversation ->
                conversation.title.contains(trimmed, ignoreCase = true) ||
                    conversation.messages.any { it.content.contains(trimmed, ignoreCase = true) }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    /** Общее число разговоров до фильтрации (для пустого состояния поиска). */
    val totalCount: StateFlow<Int> = repository.conversations
        .combine(query) { list, _ -> list.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setQuery(value: String) {
        query.value = value
    }

    fun delete(conversationId: String) = viewModelScope.launch {
        repository.delete(conversationId)
    }

    fun clearAll() = viewModelScope.launch {
        repository.clearAll()
    }
}
