package com.triggerconnect.app.ui.screens.home

import androidx.lifecycle.ViewModel
import com.triggerconnect.app.domain.repository.AuthRepository
import com.triggerconnect.app.domain.repository.AuthState
import com.triggerconnect.app.data.local.PreferencesStore
import com.triggerconnect.app.domain.repository.ConnectionRepository
import com.triggerconnect.app.domain.repository.GroupRepository
import com.triggerconnect.app.domain.model.Recipient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    authRepository: AuthRepository,
    preferencesStore: PreferencesStore,
    private val connectionRepository: ConnectionRepository,
    private val groupRepository: GroupRepository,
) : ViewModel() {

    val displayName: StateFlow<String> = authRepository.authState
        .map { (it as? AuthState.Authenticated)?.profile?.displayName ?: "there" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "there")

    val confirmUrgent: StateFlow<Boolean> = preferencesStore.prefs
        .map { it.confirmUrgent }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val defaultRecipient: StateFlow<Recipient?> = combine(
        preferencesStore.prefs,
        connectionRepository.observeContacts(),
        groupRepository.observeGroups(),
    ) { prefs, contacts, groups ->
        prefs.defaultRecipientId?.let { id ->
            contacts.firstOrNull { it.userId == id }?.let {
                Recipient.Person(it.userId, it.displayName)
            }
        } ?: prefs.defaultGroupId?.let { id ->
            groups.firstOrNull { it.groupId == id }?.let {
                Recipient.GroupTarget(it.groupId, it.name, it.memberCount)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            connectionRepository.refresh()
            groupRepository.refresh()
        }
    }
}
