package com.triggerconnect.app.messaging

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.triggerconnect.app.domain.repository.UserRepository
import com.triggerconnect.app.domain.repository.TriggerRepository
import com.triggerconnect.app.data.local.PreferencesStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Receives FCM pushes. The backend sends DATA messages (not notification
 * messages) so we fully control display in foreground, background, and
 * killed-but-not-force-stopped states, and can deduplicate by event id.
 */
@AndroidEntryPoint
class TriggerMessagingService : FirebaseMessagingService() {

    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var appScope: CoroutineScope
    @Inject lateinit var preferencesStore: PreferencesStore
    @Inject lateinit var triggerRepository: TriggerRepository

    override fun onNewToken(token: String) {
        // Register the refreshed token so dispatch keeps reaching this device.
        appScope.launch { runCatching { userRepository.registerDeviceToken(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val eventId = data["eventId"] ?: return
        val title = data["title"] ?: "Trigger"
        val body = data["body"].orEmpty()
        val priority = data["priority"] ?: "NORMAL"
        // FCM owns this worker thread only until the callback returns. Post here,
        // before launching any network work that may outlive the service.
        val prefs = runBlocking {
            withTimeoutOrNull(1_000) { preferencesStore.prefs.first() }
        } ?: PreferencesStore.Prefs()
        NotificationHelper.show(
            applicationContext, eventId, title, body, priority,
            sound = prefs.soundEnabled,
            vibration = prefs.vibrationEnabled,
        )
        appScope.launch { triggerRepository.refreshHistory() }
    }
}
