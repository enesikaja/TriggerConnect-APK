package com.triggerconnect.app.ui.screens.trigger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triggerconnect.app.core.TimeFormat
import com.triggerconnect.app.domain.model.AcknowledgmentStatus
import com.triggerconnect.app.domain.model.DispatchStatus
import com.triggerconnect.app.domain.model.EventDirection
import com.triggerconnect.app.domain.model.EventRecipient
import com.triggerconnect.app.ui.components.ErrorState
import com.triggerconnect.app.ui.components.InitialsAvatar
import com.triggerconnect.app.ui.components.LoadingState
import com.triggerconnect.app.ui.components.PriorityChip
import com.triggerconnect.app.ui.components.PrimaryButton
import com.triggerconnect.app.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggerDetailsScreen(
    eventId: String,
    onBack: () -> Unit,
    viewModel: TriggerDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Trigger") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.error != null && state.event == null ->
                ErrorState(state.error!!, onRetry = viewModel::load, modifier = Modifier.padding(padding))
            state.event != null -> {
                val event = state.event!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (event.direction == EventDirection.SENT) "To ${event.destinationName}" else "From ${event.senderName}",
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.weight(1f),
                                    )
                                    PriorityChip(event.priority)
                                }
                                Spacer(Modifier.size(10.dp))
                                Text(event.content, style = MaterialTheme.typography.bodyLarge)
                                Spacer(Modifier.size(10.dp))
                                Text(
                                    TimeFormat.dateTime(event.createdAt),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (event.direction == EventDirection.RECEIVED &&
                        event.myAcknowledgment == AcknowledgmentStatus.NONE
                    ) {
                        item {
                            PrimaryButton(
                                text = "Acknowledge",
                                onClick = viewModel::acknowledge,
                                loading = state.acknowledging,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else if (event.direction == EventDirection.RECEIVED) {
                        item {
                            Text("You acknowledged this.", color = SuccessGreen, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    if (event.direction == EventDirection.SENT) {
                        item {
                            Text(
                                "Recipients (${event.acknowledgedCount}/${event.recipientCount} acknowledged)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item {
                            Text(
                                "“Accepted” means the push provider accepted the message for delivery — it is not confirmation the device received or showed it.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(state.recipients, key = { it.recipientId }) { r -> RecipientRow(r) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipientRow(r: EventRecipient) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            InitialsAvatar(r.displayName, size = 36)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    dispatchLabel(r.dispatchStatus),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (r.acknowledgmentStatus == AcknowledgmentStatus.ACKNOWLEDGED) {
                Text("Acknowledged", color = SuccessGreen, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private fun dispatchLabel(status: DispatchStatus): String = when (status) {
    DispatchStatus.PENDING -> "Dispatch pending"
    DispatchStatus.ACCEPTED -> "Accepted by push provider"
    DispatchStatus.FAILED -> "Dispatch failed"
    DispatchStatus.SKIPPED -> "Skipped (no device registered)"
}
