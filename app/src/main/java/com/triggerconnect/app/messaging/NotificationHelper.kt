package com.triggerconnect.app.messaging

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.triggerconnect.app.MainActivity
import com.triggerconnect.app.R

/** Builds and posts trigger notifications from an incoming data message. */
object NotificationHelper {

    const val EXTRA_EVENT_ID = "extra_event_id"

    @SuppressLint("MissingPermission") // guarded by hasPermission() before notify()
    fun show(
        context: Context,
        eventId: String,
        title: String,
        body: String,
        priority: String,
        sound: Boolean,
        vibration: Boolean,
    ): Boolean {
        if (!hasPermission(context) || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        NotificationChannels.ensureCreated(context, sound, vibration)
        val channelId = NotificationChannels.channelIdFor(context, priority, sound, vibration)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        if (manager.getNotificationChannel(channelId)?.importance == android.app.NotificationManager.IMPORTANCE_NONE) return false

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (eventId != "notification-test") putExtra(EXTRA_EVENT_ID, eventId)
        }
        val pending = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val isUrgent = priority.equals("URGENT", ignoreCase = true)
        val notification = NotificationCompat.Builder(
            context,
            NotificationChannels.channelIdFor(context, priority, sound, vibration),
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.accent))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (isUrgent) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_MESSAGE)
            .setOnlyAlertOnce(true)
            .build()

        // Dedup: same eventId -> same notification id -> replaces, never stacks.
        NotificationManagerCompat.from(context).notify(eventId.hashCode(), notification)
        return true
    }

    private fun hasPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
}
