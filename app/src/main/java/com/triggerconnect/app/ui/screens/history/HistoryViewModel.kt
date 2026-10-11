package com.triggerconnect.app.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triggerconnect.app.core.AppResult
import com.triggerconnect.app.domain.model.DestinationType
import com.triggerconnect.app.domain.model.EventDirection
import com.triggerconnect.app.domain.model.TriggerEvent
import com.triggerconnect.app.domain.repository.TriggerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HistoryFilter { ALL, SENT, RECEIVED, GROUPS, DIRECT }

data class HistoryState(
    val filter: HistoryFilter = HistoryFilter.ALL,
    val events: List<TriggerEvent> = emptyList(),
    val refreshing: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val triggerRepository: TriggerRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(HistoryState())

    val state: StateFlow<HistoryState> =
        combine(triggerRepository.observeHistory(), _ui) { events, ui ->
            ui.copy(events = applyFilter(events, ui.filter))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryState())

    init { refresh() }

    fun setFilter(filter: HistoryFilter) { _ui.value = _ui.value.copy(filter = filter) }

    fun refresh() {
        _ui.value = _ui.value.copy(refreshing = true)
        viewModelScope.launch {
            val result = triggerRepository.refreshHistory()
            _ui.value = _ui.value.copy(
                refreshing = false,
                message = (result as? AppResult.Failure)?.error?.message,
            )
        }
    }

    fun consumeMessage() { _ui.value = _ui.value.copy(message = null) }

    private fun applyFilter(events: List<TriggerEvent>, filter: HistoryFilter) = when (filter) {
        HistoryFilter.ALL -> events
        HistoryFilter.SENT -> events.filter { it.direction == EventDirection.SENT }
        HistoryFilter.RECEIVED -> events.filter { it.direction == EventDirection.RECEIVED }
        HistoryFilter.GROUPS -> events.filter { it.destinationType == DestinationType.GROUP }
        HistoryFilter.DIRECT -> events.filter { it.destinationType == DestinationType.USER }
    }
}
