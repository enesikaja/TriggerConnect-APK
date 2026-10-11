package com.triggerconnect.app.ui.screens.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triggerconnect.app.core.AppResult
import com.triggerconnect.app.core.Constants
import com.triggerconnect.app.domain.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateGroupState(
    val name: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val requiresApproval: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
) {
    val valid: Boolean get() = name.trim().length in 2..Constants.GROUP_NAME_MAX_LENGTH
}

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateGroupState())
    val state: StateFlow<CreateGroupState> = _state.asStateFlow()

    private val _created = Channel<String>(Channel.BUFFERED)
    val created = _created.receiveAsFlow()

    fun onName(value: String) { _state.value = _state.value.copy(name = value.take(Constants.GROUP_NAME_MAX_LENGTH)) }
    fun onDescription(value: String) { _state.value = _state.value.copy(description = value.take(200)) }
    fun onImageUrl(value: String) { _state.value = _state.value.copy(imageUrl = value.take(500)) }
    fun onRequiresApproval(value: Boolean) { _state.value = _state.value.copy(requiresApproval = value) }

    fun create() {
        val current = _state.value
        if (!current.valid || current.loading) return
        _state.value = current.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val result = groupRepository.createGroup(
                current.name,
                current.description,
                current.imageUrl,
                current.requiresApproval,
            )) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(loading = false)
                    _created.send(result.data.groupId)
                }
                is AppResult.Failure -> _state.value = _state.value.copy(loading = false, error = result.error.message)
            }
        }
    }
}
