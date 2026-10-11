package com.triggerconnect.app.ui.screens.groups

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
fun GroupsScreen(
    composer: TriggerComposerViewModel,
    onCreateGroup: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onScan: () -> Unit,
    onPickedRecipient: () -> Unit,
    viewModel: GroupsViewModel = hiltViewModel(),
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
                title = { Text("Groups") },
                actions = { IconButton(onClick = onScan) { Icon(Icons.Default.QrCodeScanner, "Scan to join") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateGroup,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New group") },
            )
        },
    ) { padding ->
        if (state.groups.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(padding),
                title = "No groups yet",
                subtitle = "Create a group or scan a group QR code to join one.",
                icon = Icons.Default.GroupWork,
                action = { OutlinedButton(onClick = onCreateGroup) { Text("Create a group") } },
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.groups, key = { it.groupId }) { group ->
                    Card(Modifier.fillMaxWidth().clickable { onOpenGroup(group.groupId) }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            InitialsAvatar(group.name)
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(group.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${group.memberCount} members · ${group.role.name.lowercase()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            OutlinedButton(onClick = {
                                composer.selectRecipient(
                                    Recipient.GroupTarget(group.groupId, group.name, group.memberCount),
                                )
                                onPickedRecipient()
                            }) { Text("Select") }
                        }
                    }
                }
            }
        }
    }
}
