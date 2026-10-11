package com.triggerconnect.app

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.triggerconnect.app.messaging.NotificationChannels
import com.triggerconnect.app.messaging.NotificationHelper
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationTest {
    @get:Rule val permission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Test fun channelsKeepSoundAndVibrationIndependent() {
        for (sound in listOf(false, true)) for (vibration in listOf(false, true)) {
            NotificationChannels.ensureCreated(context, sound, vibration)
            for (priority in listOf("NORMAL", "IMPORTANT", "URGENT")) {
                val channel = manager.getNotificationChannel(
                    NotificationChannels.channelIdFor(context, priority, sound, vibration),
                )
                assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
                assertEquals(vibration, channel.shouldVibrate())
                assertEquals(sound, channel.sound != null)
                if (sound) context.contentResolver.openInputStream(channel.sound)!!.use {
                    assertTrue("Bundled ringtone must be readable", it.read() >= 0)
                }
                if (vibration) assertArrayEquals(longArrayOf(0, 800, 200, 800, 200, 1000), channel.vibrationPattern)
            }
        }
    }

    @Test fun vibrationOnlyNotificationIsPostedAndDuplicatesReplace() {
        val id = "notification-regression-test"
        try {
            assertTrue(NotificationHelper.show(context, id, "Test", "First", "URGENT", false, true))
            assertTrue(NotificationHelper.show(context, id, "Test", "Second", "URGENT", false, true))
            // NotificationManager queues updates in system_server asynchronously.
            val deadline = android.os.SystemClock.elapsedRealtime() + 5_000
            while (android.os.SystemClock.elapsedRealtime() < deadline &&
                manager.activeNotifications.none {
                    it.id == id.hashCode() &&
                        it.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == "Second"
                }) {
                android.os.SystemClock.sleep(50)
            }
            val active = manager.activeNotifications.filter { it.id == id.hashCode() }
            assertEquals(1, active.size)
            val notification = active.single().notification
            assertEquals("Second", notification.extras.getCharSequence(Notification.EXTRA_TEXT))
            assertEquals(NotificationChannels.channelIdFor(context, "URGENT", false, true), notification.channelId)
            // setSilent(true) would suppress vibration via group alert behavior.
            assertEquals(Notification.GROUP_ALERT_ALL, notification.groupAlertBehavior)
            assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        } finally {
            manager.cancel(id.hashCode())
        }
    }
}
