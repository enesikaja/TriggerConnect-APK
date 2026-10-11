package com.triggerconnect.app.ui.composer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triggerconnect.app.core.AppResult
import com.triggerconnect.app.core.Constants
import com.triggerconnect.app.core.network.NetworkMonitor
import com.triggerconnect.app.domain.model.Recipient
import com.triggerconnect.app.domain.model.TriggerPriority
import com.triggerconnect.app.domain.repository.TriggerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ComposerState(
    val recipient: Recipient? = null,
    val message: String = "",
    val priority: TriggerPriority = TriggerPriority.NORMAL,
    val notifySelf: Boolean = false,
    val sending: Boolean = false,
) {
    val canSend: Boolean get() = recipient != null && message.isNotBlank() && !sending
}

sealed interface ComposerEvent {
    data class Sent(val eventId: String) : ComposerEvent
    data class Error(val message: String) : ComposerEvent
}

@HiltViewModel
class TriggerComposerViewModel @Inject constructor(
    private val triggerRepository: TriggerRepository,
    networkMonitor: NetworkMonitor,
) : ViewModel() {

    private val _state = MutableStateFlow(ComposerState())
    val state: StateFlow<ComposerState> = _state.asStateFlow()

    val isOnline: StateFlow<Boolean> =
        networkMonitor.isOnline.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _events = Channel<ComposerEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Reused across double-taps of the same composed trigger for idempotency.
    private var clientEventId = UUID.randomUUID().toString()

    fun selectRecipient(recipient: Recipient) {
        _state.value = _state.value.copy(recipient = recipient)
        regenerateId()
    }

    fun clearRecipient() {
        _state.value = _state.value.copy(recipient = null)
    }

    fun setMessage(text: String) {
        _state.value = _state.value.copy(message = text.take(Constants.MESSAGE_MAX_LENGTH))
        regenerateId()
    }

    fun setPriority(priority: TriggerPriority) {
        _state.value = _state.value.copy(priority = priority)
    }

    fun setNotifySelf(value: Boolean) {
        _state.value = _state.value.copy(notifySelf = value)
    }

    private fun regenerateId() { clientEventId = UUID.randomUUID().toString() }

    fun send() {
        val current = _state.value
        val recipient = current.recipient ?: return
        if (!current.canSend) return
        if (!isOnline.value) {
            viewModelScope.launch {
                _events.send(ComposerEvent.Error("You're offline. Reconnect to send a trigger."))
            }
            return
        }
        _state.value = current.copy(sending = true)
        viewModelScope.launch {
            val result = triggerRepository.sendTrigger(
                clientEventId = clientEventId,
                destinationType = recipient.destinationType,
                destinationId = recipient.id,
                content = current.message,
                priority = current.priority,
                notifySelf = current.notifySelf,
            )
            _state.value = _state.value.copy(sending = false)
            when (result) {
                is AppResult.Success -> {
                    regenerateId()
                    _state.value = _state.value.copy(message = "")
                    _events.send(ComposerEvent.Sent(result.data.eventId))
                }
                is AppResult.Failure -> _events.send(ComposerEvent.Error(result.error.message))
            }
        }
    }
}
