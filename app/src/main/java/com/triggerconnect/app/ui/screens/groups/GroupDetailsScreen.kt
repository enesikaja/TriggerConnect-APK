package com.triggerconnect.app.ui.screens.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triggerconnect.app.domain.model.GroupRole
import com.triggerconnect.app.ui.components.InitialsAvatar
import com.triggerconnect.app.ui.components.LoadingState
import com.triggerconnect.app.ui.composer.TriggerComposerViewModel
import com.triggerconnect.app.domain.model.Recipient
import com.triggerconnect.app.ui.components.TriggerEventRow
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailsScreen(
    groupId: String,
    composer: TriggerComposerViewModel,
    onBack: () -> Unit,
    onShowInvite: (String) -> Unit,
    onTriggerGroup: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onLeftOrDeleted: () -> Unit,
    viewModel: GroupDetailsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var renameOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.leftOrDeleted.collect { onLeftOrDeleted() } }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() } }

    val group = state.group
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: "Group") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (state.canManage) {
                        IconButton(onClick = { onShowInvite(groupId) }) { Icon(Icons.Default.QrCode2, "Invite") }
                    }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (state.canManage) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; renameOpen = true })
                        }
                        if (state.isOwner) {
                            DropdownMenuItem(text = { Text("Delete group") }, onClick = { menuOpen = false; confirmDelete = true })
                        } else {
                            DropdownMenuItem(text = { Text("Leave group") }, onClick = { menuOpen = false; confirmLeave = true })
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (group == null) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        if (!group.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = group.imageUrl,
                                contentDescription = "Group image",
                                modifier = Modifier.fillMaxWidth().height(180.dp),
                            )
                            Spacer(Modifier.size(12.dp))
                        }
                        if (!group.description.isNullOrBlank()) {
                            Text(group.description!!, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.size(12.dp))
                        }
                        Text("${group.memberCount} members", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.size(12.dp))
                        OutlinedButton(
                            onClick = {
                                composer.selectRecipient(Recipient.GroupTarget(group.groupId, group.name, group.memberCount))
                                onTriggerGroup()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Select for trigger") }
                    }
                }
            }
            item {
                Text("Members", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(state.members, key = { it.userId }) { member ->
                MemberRow(
                    name = member.displayName,
                    role = member.role,
                    isSelf = member.userId == state.currentUserId,
                    pendingApproval = member.pendingApproval,
                    canManage = state.canManage && member.role != GroupRole.OWNER && member.userId != state.currentUserId,
                    onToggleAdmin = { viewModel.setAdmin(member.userId, member.role != GroupRole.ADMIN) },
                    onRemove = { viewModel.removeMember(member.userId) },
                    onApprove = { viewModel.reviewMember(member.userId, true) },
                    onReject = { viewModel.reviewMember(member.userId, false) },
                )
            }
            if (state.recentEvents.isNotEmpty()) {
                item {
                    Text(
                        "Recent group triggers",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.recentEvents, key = { "event-${it.eventId}" }) { event ->
                    TriggerEventRow(event = event, onClick = { onOpenEvent(event.eventId) })
                }
            }
        }
    }

    if (renameOpen) {
        var newName by remember { mutableStateOf(group?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Rename group") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it.take(60) }, singleLine = true)
            },
            confirmButton = { TextButton(onClick = { viewModel.rename(newName); renameOpen = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } },
        )
    }
    if (confirmLeave) {
        ConfirmDialog("Leave group?", "You'll stop receiving this group's triggers.", "Leave",
            onConfirm = { confirmLeave = false; viewModel.leave() }, onDismiss = { confirmLeave = false })
    }
    if (confirmDelete) {
        ConfirmDialog("Delete group?", "This permanently removes the group for all members.", "Delete",
            onConfirm = { confirmDelete = false; viewModel.delete() }, onDismiss = { confirmDelete = false })
    }
}

@Composable
private fun MemberRow(
    name: String,
    role: GroupRole,
    isSelf: Boolean,
    pendingApproval: Boolean,
    canManage: Boolean,
    onToggleAdmin: () -> Unit,
    onRemove: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            InitialsAvatar(name)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (isSelf) "$name (you)" else name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (pendingApproval) "awaiting approval" else role.name.lowercase(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canManage && pendingApproval) {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = onApprove) { Text("Approve") }
                    TextButton(onClick = onReject) { Text("Reject") }
                }
            } else if (canManage) {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Manage member") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (role == GroupRole.ADMIN) "Remove admin" else "Make admin") },
                        onClick = { menu = false; onToggleAdmin() },
                    )
                    DropdownMenuItem(text = { Text("Remove from group") }, onClick = { menu = false; onRemove() })
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
