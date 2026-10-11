package com.triggerconnect.app.ui.screens.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triggerconnect.app.core.AppResult
import com.triggerconnect.app.core.Constants
import com.triggerconnect.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WelcomeState(
    val name: String = "",
    val loading: Boolean = false,
    val error: String? = null,
) {
    val valid: Boolean get() =
        name.trim().length in Constants.DISPLAY_NAME_MIN_LENGTH..Constants.DISPLAY_NAME_MAX_LENGTH
}

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WelcomeState())
    val state: StateFlow<WelcomeState> = _state.asStateFlow()

    private val _registered = Channel<String>(Channel.BUFFERED)
    val registered = _registered.receiveAsFlow()

    fun onNameChange(value: String) {
        _state.value = _state.value.copy(name = value.take(Constants.DISPLAY_NAME_MAX_LENGTH), error = null)
    }

    fun createAccount() {
        val current = _state.value
        if (!current.valid || current.loading) return
        _state.value = current.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val result = authRepository.register(current.name.trim())) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(loading = false)
                    _registered.send(result.data.loginCode)
                }
                is AppResult.Failure -> _state.value =
                    _state.value.copy(loading = false, error = result.error.message)
            }
        }
    }
}
