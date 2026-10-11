package com.triggerconnect.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triggerconnect.app.domain.repository.AuthState
import com.triggerconnect.app.messaging.NotificationHelper
import com.triggerconnect.app.ui.MainViewModel
import com.triggerconnect.app.ui.navigation.AppRoot
import com.triggerconnect.app.ui.theme.TriggerConnectTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep the splash visible until auth state resolves to avoid a flash
        // of the login screen for already-signed-in users.
        splash.setKeepOnScreenCondition { viewModel.authState.value is AuthState.Unknown }

        handleIntent(intent)

        setContent {
            TriggerConnectTheme {
                val authState by viewModel.authState.collectAsStateWithLifecycle()
                val pendingEventId by viewModel.pendingEventId.collectAsStateWithLifecycle()
                val notificationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { }
                LaunchedEffect(authState) {
                    if (authState is AuthState.Authenticated &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                AppRoot(
                    authState = authState,
                    pendingEventId = pendingEventId,
                    onPendingEventConsumed = viewModel::consumePendingEventId,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAuthenticatedData()
    }

    private fun handleIntent(intent: Intent?) {
        viewModel.onNotificationEventId(intent?.getStringExtra(NotificationHelper.EXTRA_EVENT_ID))
    }
}
