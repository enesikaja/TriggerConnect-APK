package com.triggerconnect.app.ui.screens.trigger

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triggerconnect.app.core.AppResult
import com.triggerconnect.app.domain.model.EventRecipient
import com.triggerconnect.app.domain.model.TriggerEvent
import com.triggerconnect.app.domain.repository.TriggerRepository
import com.triggerconnect.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TriggerDetailsState(
    val loading: Boolean = true,
    val event: TriggerEvent? = null,
    val recipients: List<EventRecipient> = emptyList(),
    val acknowledging: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class TriggerDetailsViewModel @Inject constructor(
    private val triggerRepository: TriggerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val eventId: String = checkNotNull(savedStateHandle[Routes.ARG_EVENT_ID])
    private val _state = MutableStateFlow(TriggerDetailsState())
    val state: StateFlow<TriggerDetailsState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            // Make sure the event itself is cached before reading detail.
            triggerRepository.refreshHistory()
            _state.value = when (val result = triggerRepository.eventDetail(eventId)) {
                is AppResult.Success -> TriggerDetailsState(
                    loading = false,
                    event = result.data.first,
                    recipients = result.data.second,
                )
                is AppResult.Failure -> TriggerDetailsState(loading = false, error = result.error.message)
            }
        }
    }

    fun acknowledge() {
        if (_state.value.acknowledging) return
        _state.value = _state.value.copy(acknowledging = true)
        viewModelScope.launch {
            val result = triggerRepository.acknowledge(eventId)
            _state.value = _state.value.copy(acknowledging = false)
            if (result is AppResult.Success) load() else if (result is AppResult.Failure) {
                _state.value = _state.value.copy(error = result.error.message)
            }
        }
    }
}
