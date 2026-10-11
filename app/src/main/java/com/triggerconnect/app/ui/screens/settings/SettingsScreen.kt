package com.triggerconnect.app.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triggerconnect.app.BuildConfig
import com.triggerconnect.app.messaging.NotificationHelper
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.launch
import com.triggerconnect.app.ui.components.PrimaryButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onShowMyQr: () -> Unit,
    onSavedMessages: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    var recipientMenu by remember { mutableStateOf(false) }
    var groupMenu by remember { mutableStateOf(false) }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Profile
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Profile", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.displayName,
                        onValueChange = viewModel::onDisplayName,
                        label = { Text("Display name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onShowMyQr) {
                            Icon(Icons.Default.QrCode2, null); Spacer(Modifier.width(6.dp)); Text("My QR")
                        }
                        PrimaryButton(
                            text = "Save",
                            onClick = viewModel::saveDisplayName,
                            loading = state.working,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Notifications
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Notifications", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    ToggleRow("Sound", state.prefs.soundEnabled, viewModel::setSound)
                    ToggleRow("Vibration", state.prefs.vibrationEnabled, viewModel::setVibration)
                    ToggleRow("Confirm urgent triggers", state.prefs.confirmUrgent, viewModel::setConfirmUrgent)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Channel importance, Do Not Disturb and per-app settings are controlled by Android — open system notification settings to change them.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            NotificationManagerCompat.from(context).cancel("notification-test".hashCode())
                            val posted = NotificationHelper.show(
                                context, "notification-test", "Trigger test",
                                "Testing ringtone and vibration on this phone.", "URGENT",
                                state.prefs.soundEnabled, state.prefs.vibrationEnabled,
                            )
                            scope.launch {
                                snackbar.showSnackbar(
                                    if (posted) "Test notification posted. Volume and vibration strength follow your phone settings."
                                    else "Notifications are blocked. Enable them in Android notification settings below.",
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Test ringtone and vibration") }
                    Text(
                        "For a stronger alert, raise Notification volume and Vibration intensity in Samsung Sounds and vibration. Allow this app in Do not disturb and remove it from Sleeping apps if needed.",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                },
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Open Android notification settings") }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Defaults", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Default recipient", style = MaterialTheme.typography.labelMedium)
                    OutlinedButton(onClick = { recipientMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            state.contacts.firstOrNull {
                                it.userId == state.prefs.defaultRecipientId
                            }?.displayName ?: "None",
                        )
                    }
                    DropdownMenu(expanded = recipientMenu, onDismissRequest = { recipientMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = { viewModel.setDefaultRecipient(null); recipientMenu = false },
                        )
                        state.contacts.forEach { contact ->
                            DropdownMenuItem(
                                text = { Text(contact.displayName) },
                                onClick = {
                                    viewModel.setDefaultRecipient(contact.userId)
                                    recipientMenu = false
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Default group", style = MaterialTheme.typography.labelMedium)
                    OutlinedButton(onClick = { groupMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            state.groups.firstOrNull {
                                it.groupId == state.prefs.defaultGroupId
                            }?.name ?: "None",
                        )
                    }
                    DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = { viewModel.setDefaultGroup(null); groupMenu = false },
                        )
                        state.groups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group.name) },
                                onClick = {
                                    viewModel.setDefaultGroup(group.groupId)
                                    groupMenu = false
                                },
                            )
                        }
                    }
                }
            }

            OutlinedButton(onClick = onSavedMessages, modifier = Modifier.fillMaxWidth()) {
                Text("Manage saved trigger messages")
            }

            // About
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("About", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("${BuildConfig.APP_DISPLAY_NAME} v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider()

            OutlinedButton(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) { Text("Log out") }
            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Delete account", color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete account?") },
            text = { Text("This permanently removes your profile, connections, group memberships and history. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deleteAccount() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
