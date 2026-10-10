/**
 * Short, human times for list subtitles: "just now", "2 min ago", "1 h ago", "Yesterday",
 * "3 days ago", then a date ("Oct 2", or "Oct 2, 2025" in another year).
 */
package com.kunotes.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object RelativeTime {
    private val sameYear = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    private val otherYear = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

    /** [millis] since 1970 relative to [now]. Returns `null` for an unknown time (0). */
    fun format(
        millis: Long,
        now: Long = System.currentTimeMillis(),
    ): String? {
        if (millis <= 0) return null
        val minutes = (now - millis) / 60_000
        val zone = ZoneId.systemDefault()
        val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            day == today -> "${minutes / 60} h ago"
            day == today.minusDays(1) -> "Yesterday"
            day.isAfter(today.minusDays(7)) -> "${today.toEpochDay() - day.toEpochDay()} days ago"
            day.year == LocalDate.now(zone).year -> day.format(sameYear)
            else -> day.format(otherYear)
        }
    }
}
