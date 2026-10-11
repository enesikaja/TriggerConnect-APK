package com.triggerconnect.app.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triggerconnect.app.domain.model.EventDirection
import com.triggerconnect.app.domain.model.TriggerEvent
import com.triggerconnect.app.domain.repository.TriggerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val triggerRepository: TriggerRepository,
) : ViewModel() {

    val received: StateFlow<List<TriggerEvent>> = triggerRepository.observeHistory()
        .map { list -> list.filter { it.direction == EventDirection.RECEIVED } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { viewModelScope.launch { triggerRepository.refreshHistory() } }

    fun refresh() { viewModelScope.launch { triggerRepository.refreshHistory() } }
}
