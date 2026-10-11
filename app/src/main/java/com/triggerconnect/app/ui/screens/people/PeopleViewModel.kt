package com.triggerconnect.app.ui.screens.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triggerconnect.app.core.AppResult
import com.triggerconnect.app.domain.model.Contact
import com.triggerconnect.app.domain.model.ConnectionRequest
import com.triggerconnect.app.domain.repository.ConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PeopleState(
    val contacts: List<Contact> = emptyList(),
    val requests: List<ConnectionRequest> = emptyList(),
    val refreshing: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class PeopleViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(PeopleState())

    val state: StateFlow<PeopleState> = combine(
        connectionRepository.observeContacts(),
        connectionRepository.observeIncomingRequests(),
        _ui,
    ) { contacts, requests, ui ->
        ui.copy(contacts = contacts, requests = requests)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeopleState())

    init { refresh() }

    fun refresh() {
        _ui.value = _ui.value.copy(refreshing = true)
        viewModelScope.launch {
            val result = connectionRepository.refresh()
            _ui.value = _ui.value.copy(
                refreshing = false,
                message = (result as? AppResult.Failure)?.error?.message,
            )
        }
    }

    fun respond(connectionId: String, accept: Boolean) = act {
        connectionRepository.respondToRequest(connectionId, accept)
    }

    fun remove(connectionId: String) = act { connectionRepository.removeContact(connectionId) }
    fun block(connectionId: String) = act { connectionRepository.blockContact(connectionId) }

    fun consumeMessage() { _ui.value = _ui.value.copy(message = null) }

    private fun act(block: suspend () -> AppResult<Unit>) {
        viewModelScope.launch {
            val result = block()
            if (result is AppResult.Failure) _ui.value = _ui.value.copy(message = result.error.message)
        }
    }
}
