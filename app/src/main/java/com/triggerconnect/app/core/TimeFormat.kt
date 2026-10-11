package com.triggerconnect.app.core

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Human-friendly relative/short timestamps for history and details. */
object TimeFormat {
    fun relative(instant: Instant, now: Instant = Clock.System.now()): String {
        val seconds = (now - instant).inWholeSeconds
        return when {
            seconds < 0 -> shortDate(instant)
            seconds < 60 -> "just now"
            seconds < 3600 -> "${seconds / 60}m ago"
            seconds < 86_400 -> "${seconds / 3600}h ago"
            seconds < 604_800 -> "${seconds / 86_400}d ago"
            else -> shortDate(instant)
        }
    }

    fun shortDate(instant: Instant): String {
        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val month = dt.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
        return "$month ${dt.dayOfMonth}, ${dt.year}"
    }

    fun dateTime(instant: Instant): String {
        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val hh = dt.hour.toString().padStart(2, '0')
        val mm = dt.minute.toString().padStart(2, '0')
        return "${shortDate(instant)} · $hh:$mm"
    }
}
