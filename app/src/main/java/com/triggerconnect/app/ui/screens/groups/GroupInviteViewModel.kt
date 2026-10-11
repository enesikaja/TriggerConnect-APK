package com.triggerconnect.app.ui.screens.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triggerconnect.app.core.AppResult
import com.triggerconnect.app.domain.model.GroupInvitation
import com.triggerconnect.app.domain.repository.GroupRepository
import com.triggerconnect.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupInviteState(
    val invitation: GroupInvitation? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class GroupInviteViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val groupId: String = checkNotNull(savedStateHandle[Routes.ARG_GROUP_ID])
    private val _state = MutableStateFlow(GroupInviteState())
    val state: StateFlow<GroupInviteState> = _state.asStateFlow()

    init { generate() }

    fun generate() {
        val current = _state.value
        if (current.loading) return
        _state.value = current.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val result = groupRepository.createInvitation(groupId)) {
                is AppResult.Success -> _state.value = _state.value.copy(loading = false, invitation = result.data)
                is AppResult.Failure -> _state.value = _state.value.copy(loading = false, error = result.error.message)
            }
        }
    }
}
