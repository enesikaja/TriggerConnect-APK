package com.triggerconnect.app.ui.screens.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triggerconnect.app.core.AppResult
import com.triggerconnect.app.domain.model.Group
import com.triggerconnect.app.domain.model.GroupMember
import com.triggerconnect.app.domain.model.GroupRole
import com.triggerconnect.app.domain.repository.AuthRepository
import com.triggerconnect.app.domain.repository.GroupRepository
import com.triggerconnect.app.domain.repository.TriggerRepository
import com.triggerconnect.app.domain.model.TriggerEvent
import com.triggerconnect.app.domain.model.DestinationType
import com.triggerconnect.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupDetailsState(
    val group: Group? = null,
    val members: List<GroupMember> = emptyList(),
    val recentEvents: List<TriggerEvent> = emptyList(),
    val currentUserId: String? = null,
    val refreshing: Boolean = false,
    val message: String? = null,
) {
    val myRole: GroupRole? get() = group?.role
    val isOwner: Boolean get() = group?.ownerId == currentUserId
    val canManage: Boolean get() = myRole?.canManageMembers == true
}

@HiltViewModel
class GroupDetailsViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    private val triggerRepository: TriggerRepository,
    authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle[Routes.ARG_GROUP_ID])
    private val currentUserId = authRepository.currentUserId()
    private val _ui = MutableStateFlow(GroupDetailsState(currentUserId = currentUserId))

    private val _events = Channel<Unit>(Channel.BUFFERED)
    val leftOrDeleted = _events.receiveAsFlow()

    val state: StateFlow<GroupDetailsState> = combine(
        groupRepository.observeGroups().map { groups -> groups.firstOrNull { it.groupId == groupId } },
        groupRepository.observeMembers(groupId),
        triggerRepository.observeHistory().map { events ->
            events.filter {
                it.destinationType == DestinationType.GROUP && it.destinationId == groupId
            }.take(20)
        },
        _ui,
    ) { group, members, events, ui ->
        ui.copy(group = group, members = members, recentEvents = events)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupDetailsState(currentUserId = currentUserId))

    init { refresh() }

    fun refresh() {
        _ui.value = _ui.value.copy(refreshing = true)
        viewModelScope.launch {
            groupRepository.refresh()
            triggerRepository.refreshHistory()
            val result = groupRepository.refreshMembers(groupId)
            _ui.value = _ui.value.copy(
                refreshing = false,
                message = (result as? AppResult.Failure)?.error?.message,
            )
        }
    }

    fun rename(name: String) = act { groupRepository.renameGroup(groupId, name) }
    fun removeMember(userId: String) = act { groupRepository.removeMember(groupId, userId) }
    fun setAdmin(userId: String, makeAdmin: Boolean) = act { groupRepository.setMemberRole(groupId, userId, makeAdmin) }
    fun reviewMember(userId: String, approve: Boolean) =
        act { groupRepository.reviewMember(groupId, userId, approve) }

    fun leave() = actThenExit { groupRepository.leaveGroup(groupId) }
    fun delete() = actThenExit { groupRepository.deleteGroup(groupId) }

    fun consumeMessage() { _ui.value = _ui.value.copy(message = null) }

    private fun act(block: suspend () -> AppResult<Unit>) {
        viewModelScope.launch {
            val result = block()
            if (result is AppResult.Failure) _ui.value = _ui.value.copy(message = result.error.message)
        }
    }

    private fun actThenExit(block: suspend () -> AppResult<Unit>) {
        viewModelScope.launch {
            when (val result = block()) {
                is AppResult.Success -> _events.send(Unit)
                is AppResult.Failure -> _ui.value = _ui.value.copy(message = result.error.message)
            }
        }
    }
}
