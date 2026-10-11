package com.triggerconnect.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triggerconnect.app.core.AppResult
import com.triggerconnect.app.data.local.PreferencesStore
import com.triggerconnect.app.domain.repository.AuthRepository
import com.triggerconnect.app.domain.repository.AuthState
import com.triggerconnect.app.domain.repository.UserRepository
import com.triggerconnect.app.messaging.FcmTokenRegistrar
import com.triggerconnect.app.domain.repository.ConnectionRepository
import com.triggerconnect.app.domain.repository.GroupRepository
import com.triggerconnect.app.domain.model.Contact
import com.triggerconnect.app.domain.model.Group
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val displayName: String = "",
    val prefs: PreferencesStore.Prefs = PreferencesStore.Prefs(),
    val working: Boolean = false,
    val message: String? = null,
    val contacts: List<Contact> = emptyList(),
    val groups: List<Group> = emptyList(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val preferencesStore: PreferencesStore,
    private val fcmTokenRegistrar: FcmTokenRegistrar,
    private val connectionRepository: ConnectionRepository,
    private val groupRepository: GroupRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(SettingsState())

    val state: StateFlow<SettingsState> = combine(
        authRepository.authState.map { (it as? AuthState.Authenticated)?.profile?.displayName ?: "" },
        preferencesStore.prefs,
        connectionRepository.observeContacts(),
        groupRepository.observeGroups(),
        _ui,
    ) { name, prefs, contacts, groups, ui ->
        ui.copy(
            displayName = ui.displayName.ifBlank { name },
            prefs = prefs,
            contacts = contacts,
            groups = groups,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    init {
        viewModelScope.launch {
            connectionRepository.refresh()
            groupRepository.refresh()
        }
    }

    fun onDisplayName(value: String) { _ui.value = _ui.value.copy(displayName = value.take(40)) }

    fun saveDisplayName() {
        val name = _ui.value.displayName.trim()
        if (name.length < 2) return
        _ui.value = _ui.value.copy(working = true)
        viewModelScope.launch {
            val result = userRepository.updateDisplayName(name)
            _ui.value = _ui.value.copy(
                working = false,
                message = if (result is AppResult.Success) "Name updated" else (result as AppResult.Failure).error.message,
            )
        }
    }

    fun setSound(enabled: Boolean) { viewModelScope.launch { preferencesStore.setSound(enabled) } }
    fun setVibration(enabled: Boolean) { viewModelScope.launch { preferencesStore.setVibration(enabled) } }
    fun setConfirmUrgent(enabled: Boolean) { viewModelScope.launch { preferencesStore.setConfirmUrgent(enabled) } }
    fun setDefaultRecipient(id: String?) {
        viewModelScope.launch { preferencesStore.setDefaultRecipient(id) }
    }
    fun setDefaultGroup(id: String?) {
        viewModelScope.launch { preferencesStore.setDefaultGroup(id) }
    }

    fun logout() {
        viewModelScope.launch {
            fcmTokenRegistrar.unregisterCurrentToken()
            authRepository.logout()
        }
    }

    fun deleteAccount() {
        _ui.value = _ui.value.copy(working = true)
        viewModelScope.launch {
            when (val result = userRepository.deleteAccount()) {
                is AppResult.Success -> authRepository.logout()
                is AppResult.Failure -> _ui.value = _ui.value.copy(working = false, message = result.error.message)
            }
        }
    }

    fun consumeMessage() { _ui.value = _ui.value.copy(message = null) }
}
