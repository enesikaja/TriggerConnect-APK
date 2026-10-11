package com.triggerconnect.app.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.content.getSystemService
import com.triggerconnect.app.R

/**
 * Creates the three priority channels (section 12). Users control sound,
 * vibration and importance per-channel via Android/One UI settings — we never
 * bypass Do Not Disturb or notification policies.
 */
object NotificationChannels {

    fun ensureCreated(context: Context, sound: Boolean = true, vibration: Boolean = true) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return

        val suffix = "_s${if (sound) 1 else 0}v${if (vibration) 1 else 0}_v2"
        val normal = NotificationChannel(
            context.getString(R.string.channel_id_normal) + suffix,
            context.getString(R.string.channel_name_normal),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_desc_normal)
            if (!sound) setSound(null, null)
            enableVibration(vibration)
        }

        val important = NotificationChannel(
            context.getString(R.string.channel_id_important) + suffix,
            context.getString(R.string.channel_name_important),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_desc_important)
            if (!sound) setSound(null, null)
            enableVibration(vibration)
        }

        val urgent = NotificationChannel(
            context.getString(R.string.channel_id_urgent) + suffix,
            context.getString(R.string.channel_name_urgent),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_desc_urgent)
            if (!sound) setSound(null, null)
            enableVibration(vibration)
            if (vibration) vibrationPattern = longArrayOf(0, 400, 200, 400)
        }

        listOf(normal, important, urgent).forEach { channel ->
            channel.setSound(
                if (sound) Uri.parse("android.resource://${context.packageName}/raw/trigger_alert") else null,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            if (vibration) channel.vibrationPattern = longArrayOf(0, 800, 200, 800, 200, 1000)
            // Migrate the alert defaults once, preserving explicit system choices.
            val old = manager.getNotificationChannel(channel.id.removeSuffix("_v2"))
            if (old != null) {
                if (Build.VERSION.SDK_INT >= 29 && old.hasUserSetImportance() ||
                    old.importance == NotificationManager.IMPORTANCE_NONE) {
                    channel.importance = old.importance
                }
                if (Build.VERSION.SDK_INT >= 30 && old.hasUserSetSound()) {
                    channel.setSound(old.sound, old.audioAttributes)
                }
                if (!old.shouldVibrate()) channel.enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun channelIdFor(context: Context, priority: String, sound: Boolean, vibration: Boolean): String {
        val base = when (priority.uppercase()) {
            "URGENT" -> context.getString(R.string.channel_id_urgent)
            "IMPORTANT" -> context.getString(R.string.channel_id_important)
            else -> context.getString(R.string.channel_id_normal)
        }
        return base + "_s${if (sound) 1 else 0}v${if (vibration) 1 else 0}_v2"
    }
}
