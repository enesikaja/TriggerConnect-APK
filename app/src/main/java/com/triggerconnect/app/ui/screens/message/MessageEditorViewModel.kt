package com.triggerconnect.app.ui.screens.message

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triggerconnect.app.core.AppResult
import com.triggerconnect.app.core.Constants
import com.triggerconnect.app.domain.model.SavedMessage
import com.triggerconnect.app.domain.repository.MessageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MessageEditorState(
    val draft: String = "",
    val saved: List<SavedMessage> = emptyList(),
    val editingId: String? = null,
    val message: String? = null,
) {
    val draftValid: Boolean get() = draft.isNotBlank() && draft.length <= Constants.MESSAGE_MAX_LENGTH
    val maxLength: Int get() = Constants.MESSAGE_MAX_LENGTH
}

@HiltViewModel
class MessageEditorViewModel @Inject constructor(
    private val messageRepository: MessageRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(MessageEditorState())

    val state: StateFlow<MessageEditorState> =
        combine(messageRepository.observeSavedMessages(), _ui) { saved, ui -> ui.copy(saved = saved) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MessageEditorState())

    init { viewModelScope.launch { messageRepository.refresh() } }

    fun onDraft(value: String) { _ui.value = _ui.value.copy(draft = value.take(Constants.MESSAGE_MAX_LENGTH)) }

    fun edit(message: SavedMessage) {
        _ui.value = _ui.value.copy(draft = message.content, editingId = message.messageId)
    }

    fun cancelEdit() {
        _ui.value = _ui.value.copy(draft = "", editingId = null)
    }

    fun saveDraft() {
        val text = _ui.value.draft
        if (text.isBlank()) return
        viewModelScope.launch {
            val result: AppResult<*> = _ui.value.editingId?.let {
                messageRepository.updateMessage(it, text)
            } ?: messageRepository.createMessage(text)
            _ui.value = when (result) {
                is AppResult.Success -> _ui.value.copy(message = "Saved", draft = "", editingId = null)
                is AppResult.Failure -> _ui.value.copy(message = result.error.message)
            }
        }
    }

    fun delete(messageId: String) {
        viewModelScope.launch {
            val result = messageRepository.deleteMessage(messageId)
            if (result is AppResult.Failure) _ui.value = _ui.value.copy(message = result.error.message)
        }
    }

    fun consumeMessage() { _ui.value = _ui.value.copy(message = null) }
}
