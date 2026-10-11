package com.triggerconnect.app.ui.screens.scan

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.triggerconnect.app.domain.model.DestinationType
import com.triggerconnect.app.ui.components.CameraQrScanner
import com.triggerconnect.app.ui.components.EmptyState
import com.triggerconnect.app.ui.components.PrimaryButton

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ScanScreen(
    onDone: () -> Unit,
    onJoinedGroup: (String) -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ScanEvent.Connected -> { snackbar.showSnackbar("Connection request sent"); onDone() }
                is ScanEvent.Joined -> onJoinedGroup(event.groupId)
                ScanEvent.PendingApproval -> { snackbar.showSnackbar("Request sent — awaiting approval"); onDone() }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Scan QR") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (cameraPermission.status.isGranted) {
                CameraQrScanner(
                    onCode = viewModel::onScanned,
                    scanningEnabled = !state.resolving && state.preview == null && !state.working,
                    modifier = Modifier.fillMaxSize(),
                )
                Column(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Point the camera at a personal or group QR code",
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                EmptyState(
                    title = "Camera permission needed",
                    subtitle = "We use the camera only to scan connection and group QR codes.",
                    icon = Icons.Default.QrCodeScanner,
                    action = {
                        PrimaryButton(text = "Allow camera", onClick = { cameraPermission.launchPermissionRequest() })
                    },
                )
            }
        }
    }

    val preview = state.preview
    if (preview != null) {
        AlertDialog(
            onDismissRequest = viewModel::rescan,
            title = {
                Text(if (preview.type == DestinationType.GROUP) "Join group" else "Connect")
            },
            text = {
                Column {
                    Text(preview.displayName, style = MaterialTheme.typography.titleMedium)
                    if (preview.type == DestinationType.GROUP && preview.memberCount != null) {
                        Spacer(Modifier.height(4.dp))
                        Text("${preview.memberCount} members", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (preview.alreadyMember) {
                        Spacer(Modifier.height(8.dp))
                        Text("You're already connected.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (preview.requiresApproval) {
                        Spacer(Modifier.height(8.dp))
                        Text("This group requires approval to join.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = !preview.alreadyMember && !state.working, onClick = viewModel::confirm) {
                    Text(if (preview.type == DestinationType.GROUP) "Join" else "Send request")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::rescan) { Text("Cancel") } },
        )
    }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it) }
    }
}
