package com.triggerconnect.app.ui.screens.login

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

data class LoginState(
    val code: String = "",
    val loading: Boolean = false,
    val error: String? = null,
) {
    val valid: Boolean get() = code.length == Constants.LOGIN_CODE_LENGTH
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private val _loggedIn = Channel<Unit>(Channel.BUFFERED)
    val loggedIn = _loggedIn.receiveAsFlow()

    fun onCodeChange(value: String) {
        val digits = value.filter { it.isDigit() }.take(Constants.LOGIN_CODE_LENGTH)
        _state.value = _state.value.copy(code = digits, error = null)
        if (digits.length == Constants.LOGIN_CODE_LENGTH) login()
    }

    fun login() {
        val current = _state.value
        if (!current.valid || current.loading) return
        _state.value = current.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val result = authRepository.login(current.code)) {
                is AppResult.Success -> {
                    _state.value = _state.value.copy(loading = false)
                    _loggedIn.send(Unit)
                }
                is AppResult.Failure -> _state.value = _state.value.copy(
                    loading = false,
                    error = result.error.message,
                    code = "",
                )
            }
        }
    }
}
