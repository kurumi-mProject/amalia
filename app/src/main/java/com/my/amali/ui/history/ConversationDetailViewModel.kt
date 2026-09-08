package com.my.amali.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.my.amali.core.di.ServiceLocator
import com.my.amali.data.model.Conversation
import com.my.amali.data.repository.ConversationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel детального экрана разговора: загрузка одного разговора по id
 * и его удаление с возвратом в список.
 */
class ConversationDetailViewModel(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val conversationId: String = savedStateHandle.get<String>("conversationId").orEmpty()

    private val repository: ConversationRepository = ServiceLocator.conversationRepository

    private val _conversation = MutableStateFlow<Conversation?>(null)

    /** Загруженный разговор; null, пока грузится или не найден. */
    val conversation: StateFlow<Conversation?> = _conversation.asStateFlow()

    init {
        viewModelScope.launch {
            repository.conversations.collect { list ->
                _conversation.value = list.firstOrNull { it.id == conversationId }
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = _conversation.value?.id ?: return
        viewModelScope.launch {
            repository.delete(id)
            onDeleted()
        }
    }
}
