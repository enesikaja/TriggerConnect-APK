package com.triggerconnect.app.ui.screens.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triggerconnect.app.core.AppResult
import com.triggerconnect.app.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonalQrState(
    val token: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class PersonalQrViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PersonalQrState())
    val state: StateFlow<PersonalQrState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            _state.value = when (val result = userRepository.personalQrToken()) {
                is AppResult.Success -> PersonalQrState(token = result.data, loading = false)
                is AppResult.Failure -> PersonalQrState(loading = false, error = result.error.message)
            }
        }
    }
}
