package com.jabook.app.core.domain.model

import java.util.Locale

/** Domain model representing a chapter within an audiobook. */
data class Chapter(
    val id: String,
    val audiobookId: String,
    val chapterNumber: Int,
    val title: String,
    val filePath: String,
    val durationMs: Long,
    val startPositionMs: Long = 0,
    val endPositionMs: Long = durationMs,
    val fileSize: Long = 0,
    val isDownloaded: Boolean = false,
    val downloadProgress: Float = 0f,
) {
    /** Get human-readable duration string. */
    val durationFormatted: String
        get() = formatDuration(durationMs)

    /** Get human-readable file size string. */
    val fileSizeFormatted: String
        get() = formatFileSize(fileSize)

    /** Check if chapter is currently downloading. */
    val isDownloading: Boolean
        get() = downloadProgress > 0f && downloadProgress < 1f && !isDownloaded

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

        /** Format file size from bytes to human-readable string. */
        private fun formatFileSize(bytes: Long): String {
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var size = bytes.toDouble()
            var unitIndex = 0

            while (size >= 1024 && unitIndex < units.size - 1) {
                size /= 1024
                unitIndex++
            }

            return String.format(Locale.US, "%.1f %s", size, units[unitIndex])
        }
    }
}
