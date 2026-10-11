package com.triggerconnect.app.ui.screens.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triggerconnect.app.core.AppResult
import com.triggerconnect.app.domain.model.DestinationType
import com.triggerconnect.app.domain.model.InvitePreview
import com.triggerconnect.app.domain.repository.ConnectionRepository
import com.triggerconnect.app.domain.repository.InvitationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScanState(
    val resolving: Boolean = false,
    val working: Boolean = false,
    val preview: InvitePreview? = null,
    val error: String? = null,
)

sealed interface ScanEvent {
    data object Connected : ScanEvent
    data class Joined(val groupId: String) : ScanEvent
    data object PendingApproval : ScanEvent
}

@HiltViewModel
class ScanViewModel @Inject constructor(
    private val invitationRepository: InvitationRepository,
    private val connectionRepository: ConnectionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val _events = Channel<ScanEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var rawToken: String? = null

    fun onScanned(token: String) {
        if (_state.value.resolving || _state.value.preview != null) return
        rawToken = token
        _state.value = _state.value.copy(resolving = true, error = null)
        viewModelScope.launch {
            _state.value = when (val result = invitationRepository.resolve(token)) {
                is AppResult.Success -> _state.value.copy(resolving = false, preview = result.data)
                is AppResult.Failure -> _state.value.copy(resolving = false, error = result.error.message)
            }
        }
    }

    fun confirm() {
        val preview = _state.value.preview ?: return
        _state.value = _state.value.copy(working = true, error = null)
        viewModelScope.launch {
            when (preview.type) {
                DestinationType.USER -> {
                    val token = rawToken
                    val result = if (token != null) connectionRepository.requestConnection(token)
                    else AppResult.Failure(com.triggerconnect.app.core.AppError(
                        com.triggerconnect.app.core.AppErrorType.UNKNOWN, "Scan again.",
                    ))
                    handle(result) { _events.send(ScanEvent.Connected) }
                }
                DestinationType.GROUP -> {
                    val result = invitationRepository.joinGroup(preview.invitationId)
                    when (result) {
                        is AppResult.Success ->
                            if (preview.requiresApproval) _events.send(ScanEvent.PendingApproval)
                            else _events.send(ScanEvent.Joined(result.data.groupId))
                        is AppResult.Failure ->
                            _state.value = _state.value.copy(working = false, error = result.error.message)
                    }
                    if (result is AppResult.Success) _state.value = _state.value.copy(working = false)
                }
            }
        }
    }

    fun rescan() {
        rawToken = null
        _state.value = ScanState()
    }

    private suspend fun handle(result: AppResult<Unit>, onSuccess: suspend () -> Unit) {
        when (result) {
            is AppResult.Success -> { _state.value = _state.value.copy(working = false); onSuccess() }
            is AppResult.Failure -> _state.value = _state.value.copy(working = false, error = result.error.message)
        }
    }
}
