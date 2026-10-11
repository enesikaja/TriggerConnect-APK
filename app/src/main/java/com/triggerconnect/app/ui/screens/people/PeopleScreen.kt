package com.triggerconnect.app.ui.screens.people

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
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triggerconnect.app.domain.model.Recipient
import com.triggerconnect.app.ui.components.EmptyState
import com.triggerconnect.app.ui.components.InitialsAvatar
import com.triggerconnect.app.ui.composer.TriggerComposerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
    composer: TriggerComposerViewModel,
    onShowMyQr: () -> Unit,
    onScan: () -> Unit,
    onPickedRecipient: () -> Unit,
    viewModel: PeopleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("People") },
                actions = {
                    IconButton(onClick = onShowMyQr) { Icon(Icons.Default.QrCode2, "My QR code") }
                    IconButton(onClick = onScan) { Icon(Icons.Default.QrCodeScanner, "Scan") }
                },
            )
        },
    ) { padding ->
        if (state.contacts.isEmpty() && state.requests.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(padding),
                title = "No connections yet",
                subtitle = "Share your QR code or scan someone else's to connect.",
                icon = Icons.Default.PersonAdd,
                action = {
                    OutlinedButton(onClick = onShowMyQr) { Text("Show my QR code") }
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.requests.isNotEmpty()) {
                    item { SectionLabel("Requests") }
                    items(state.requests, key = { it.connectionId }) { req ->
                        RequestRow(
                            name = req.fromDisplayName,
                            onAccept = { viewModel.respond(req.connectionId, true) },
                            onReject = { viewModel.respond(req.connectionId, false) },
                        )
                    }
                }
                if (state.contacts.isNotEmpty()) {
                    item { SectionLabel("Contacts") }
                    items(state.contacts, key = { it.connectionId }) { contact ->
                        ContactRow(
                            name = contact.displayName,
                            onTrigger = {
                                composer.selectRecipient(Recipient.Person(contact.userId, contact.displayName))
                                onPickedRecipient()
                            },
                            onRemove = { viewModel.remove(contact.connectionId) },
                            onBlock = { viewModel.block(contact.connectionId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun RequestRow(name: String, onAccept: () -> Unit, onReject: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            InitialsAvatar(name)
            Spacer(Modifier.size(12.dp))
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onReject) { Text("Reject") }
            TextButton(onClick = onAccept) { Text("Accept") }
        }
    }
}

@Composable
private fun ContactRow(name: String, onTrigger: () -> Unit, onRemove: () -> Unit, onBlock: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            InitialsAvatar(name)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Row {
                    TextButton(onClick = onRemove, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) { Text("Remove") }
                    TextButton(onClick = onBlock, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) { Text("Block") }
                }
            }
            OutlinedButton(onClick = onTrigger) { Text("Select") }
        }
    }
}
