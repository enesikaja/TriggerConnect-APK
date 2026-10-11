package com.triggerconnect.app.ui.screens.home

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triggerconnect.app.domain.model.TriggerPriority
import com.triggerconnect.app.ui.components.OfflineBanner
import com.triggerconnect.app.ui.composer.ComposerEvent
import com.triggerconnect.app.ui.composer.TriggerComposerViewModel
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    composer: TriggerComposerViewModel,
    onPickRecipient: () -> Unit,
    onPickGroup: () -> Unit,
    onEditMessage: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotifications: () -> Unit,
    onSent: (String) -> Unit,
    homeViewModel: HomeViewModel = hiltViewModel(),
) {
    val state by composer.state.collectAsStateWithLifecycle()
    val online by composer.isOnline.collectAsStateWithLifecycle()
    val name by homeViewModel.displayName.collectAsStateWithLifecycle()
    val requireUrgentConfirmation by homeViewModel.confirmUrgent.collectAsStateWithLifecycle()
    val defaultRecipient by homeViewModel.defaultRecipient.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmUrgent by remember { mutableStateOf(false) }

    LaunchedEffect(defaultRecipient) {
        if (state.recipient == null) defaultRecipient?.let(composer::selectRecipient)
    }

    LaunchedEffect(Unit) {
        composer.events.collect { event ->
            when (event) {
                is ComposerEvent.Sent -> onSent(event.eventId)
                is ComposerEvent.Error -> snackbar.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Hi, $name", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onOpenNotifications) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (!online) OfflineBanner(message = "You're offline. Triggers are unavailable until you reconnect.")

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(16.dp))

                // Recipient selector
                RecipientCard(
                    recipientName = state.recipient?.displayName,
                    onPickPerson = onPickRecipient,
                    onPickGroup = onPickGroup,
                    onClear = composer::clearRecipient,
                )
                Spacer(Modifier.height(12.dp))

                // Message preview
                MessageCard(message = state.message, onEdit = onEditMessage)
                Spacer(Modifier.height(20.dp))

                // Priority
                PriorityRow(selected = state.priority, onSelect = composer::setPriority)

                if (state.recipient?.destinationType == com.triggerconnect.app.domain.model.DestinationType.GROUP) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Also notify me", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.size(8.dp))
                        Switch(checked = state.notifySelf, onCheckedChange = composer::setNotifySelf)
                    }
                }

                Spacer(Modifier.height(28.dp))

                // The big TRIGGER button
                TriggerButton(
                    enabled = state.canSend && online,
                    sending = state.sending,
                    onClick = {
                        if (state.priority == TriggerPriority.URGENT && requireUrgentConfirmation) {
                            confirmUrgent = true
                        } else {
                            composer.send()
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    when {
                        state.recipient == null -> "Select a person or group to begin"
                        state.message.isBlank() -> "Add a message to send"
                        !online -> "Reconnect to send"
                        else -> "Ready to send"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmUrgent) {
        AlertDialog(
            onDismissRequest = { confirmUrgent = false },
            title = { Text("Send urgent trigger?") },
            text = { Text("This will send an urgent, high-visibility notification to ${state.recipient?.displayName ?: "the recipient"}.") },
            confirmButton = {
                TextButton(onClick = { confirmUrgent = false; composer.send() }) { Text("Send") }
            },
            dismissButton = { TextButton(onClick = { confirmUrgent = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RecipientCard(
    recipientName: String?,
    onPickPerson: () -> Unit,
    onPickGroup: () -> Unit,
    onClear: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Recipient", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(
                recipientName ?: "No recipient selected",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipButton(icon = Icons.Default.Person, label = "Person", onClick = onPickPerson)
                ChipButton(icon = Icons.Default.GroupWork, label = "Group", onClick = onPickGroup)
                if (recipientName != null) {
                    ChipButton(icon = Icons.Default.Edit, label = "Clear", onClick = onClear)
                }
            }
        }
    }
}

@Composable
private fun ChipButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MessageCard(message: String, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Message", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text(
                    message.ifBlank { "Tap to choose or write a message" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (message.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriorityRow(selected: TriggerPriority, onSelect: (TriggerPriority) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TriggerPriority.entries.forEach { p ->
            FilterChip(
                selected = selected == p,
                onClick = { onSelect(p) },
                label = { Text(p.name.lowercase().replaceFirstChar { it.uppercase() }) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TriggerButton(enabled: Boolean, sending: Boolean, onClick: () -> Unit) {
    val activeBrush = Brush.radialGradient(
        colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer),
    )
    Box(
        modifier = Modifier
            .size(200.dp)
            .clip(CircleShape)
            .then(
                if (enabled) Modifier.background(activeBrush)
                else Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            )
            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.6f else 0.15f), CircleShape)
            .clickable(enabled = enabled && !sending, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Bolt,
                null,
                modifier = Modifier.size(56.dp),
                tint = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (sending) "SENDING…" else "TRIGGER",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
