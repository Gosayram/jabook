package com.jabook.app.core.domain.model

import java.util.Date
import java.util.Locale

/** Domain model representing a bookmark within an audiobook. */
data class Bookmark(
    val id: String,
    val audiobookId: String,
    val title: String,
    val note: String? = null,
    val positionMs: Long,
    val chapterId: String? = null,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
) {
    /** Get human-readable position string. */
    val positionFormatted: String
        get() = formatDuration(positionMs)

    /** Check if bookmark has a note. */
    val hasNote: Boolean
        get() = !note.isNullOrBlank()

    companion object {
        /** Format duration from milliseconds to human-readable string. */
        private fun formatDuration(durationMs: Long): String {
            val totalSeconds = durationMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60

            return when {
                hours > 0 -> String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
                else -> String.format(Locale.US, "%d:%02d", minutes, seconds)
            }
        }
    }
}
