package com.triggerconnect.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triggerconnect.app.domain.repository.AuthRepository
import com.triggerconnect.app.domain.repository.AuthState
import com.triggerconnect.app.domain.repository.ConnectionRepository
import com.triggerconnect.app.domain.repository.GroupRepository
import com.triggerconnect.app.domain.repository.TriggerRepository
import com.triggerconnect.app.messaging.FcmTokenRegistrar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val fcmTokenRegistrar: FcmTokenRegistrar,
    private val connectionRepository: ConnectionRepository,
    private val groupRepository: GroupRepository,
    private val triggerRepository: TriggerRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState

    private val _pendingEventId = MutableStateFlow<String?>(null)
    val pendingEventId: StateFlow<String?> = _pendingEventId.asStateFlow()

    private var tokenRegistrationJob: Job? = null

    init {
        viewModelScope.launch { authRepository.bootstrap() }
        viewModelScope.launch {
            authState.collect { state ->
                if (state is AuthState.Authenticated) {
                    refreshAuthenticatedData()
                } else if (state is AuthState.Unauthenticated) {
                    tokenRegistrationJob?.cancel()
                }
            }
        }
    }

    fun refreshAuthenticatedData() {
        if (authState.value !is AuthState.Authenticated) return
        // Also retry on resume, recovering from offline sign-in or token refresh.
        if (tokenRegistrationJob?.isActive != true) {
            tokenRegistrationJob = viewModelScope.launch {
                repeat(3) { attempt ->
                    if (fcmTokenRegistrar.registerCurrentToken()) return@launch
                    delay(1_000L * (attempt + 1))
                }
            }
        }
        viewModelScope.launch {
            connectionRepository.refresh()
            groupRepository.refresh()
            triggerRepository.refreshHistory()
        }
    }

    fun onNotificationEventId(eventId: String?) {
        if (!eventId.isNullOrBlank()) _pendingEventId.value = eventId
    }

    fun consumePendingEventId() {
        _pendingEventId.value = null
    }
}
