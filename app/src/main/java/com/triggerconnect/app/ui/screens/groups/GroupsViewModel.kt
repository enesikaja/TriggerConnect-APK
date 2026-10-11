package com.triggerconnect.app.ui.screens.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triggerconnect.app.core.AppResult
import com.triggerconnect.app.domain.model.Group
import com.triggerconnect.app.domain.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupsState(
    val groups: List<Group> = emptyList(),
    val refreshing: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class GroupsViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(GroupsState())

    val state: StateFlow<GroupsState> = combine(groupRepository.observeGroups(), _ui) { groups, ui ->
        ui.copy(groups = groups)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupsState())

    init { refresh() }

    fun refresh() {
        _ui.value = _ui.value.copy(refreshing = true)
        viewModelScope.launch {
            val result = groupRepository.refresh()
            _ui.value = _ui.value.copy(
                refreshing = false,
                message = (result as? AppResult.Failure)?.error?.message,
            )
        }
    }

    fun consumeMessage() { _ui.value = _ui.value.copy(message = null) }
}
