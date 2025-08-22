package com.jabook.app.core.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date
import java.util.Locale

/** Domain model representing an audiobook. This is the business logic representation, independent of data source. */
@Parcelize
data class Audiobook(
  val id: String,
  val title: String,
  val author: String,
  val narrator: String? = null,
  val description: String? = null,
  val category: String,
  val coverUrl: String? = null,
  val localCoverPath: String? = null,
  val localAudioPath: String? = null,
  val fileSize: Long = 0,
  val durationMs: Long = 0,
  val quality: String? = null,
  val bitrate: Int? = null,
  val sampleRate: Int? = null,
  // Torrent information
  val torrentUrl: String? = null,
  val magnetLink: String? = null,
  val seeders: Int = 0,
  val leechers: Int = 0,
  // Download status
  val downloadStatus: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
  val downloadProgress: Float = 0f,
  val downloadError: String? = null,
  // Playback information
  val currentPositionMs: Long = 0,
  val isCompleted: Boolean = false,
  val lastPlayedAt: Date? = null,
  // Metadata
  val addedAt: Date = Date(),
  val updatedAt: Date = Date(),
  val isFavorite: Boolean = false,
  val userRating: Float? = null,
  val playbackSpeed: Float = 1.0f,
  val chapterCount: Int = 0,
) : Parcelable {
  /** Check if the audiobook is currently downloaded. */
  val isDownloaded: Boolean
    get() = downloadStatus == DownloadStatus.COMPLETED

  /** Check if the audiobook is currently downloading. */
  val isDownloading: Boolean
    get() = downloadStatus in listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING)

  /** Get human-readable duration string. */
  val durationFormatted: String
    get() = formatDuration(durationMs)

  /** Get human-readable current position string. */
  val currentPositionFormatted: String
    get() = formatDuration(currentPositionMs)

  /** Get progress percentage (0.0 to 1.0). */
  val progressPercentage: Float
    get() = if (durationMs > 0) currentPositionMs.toFloat() / durationMs else 0f

  /** Get remaining time in milliseconds. */
  val remainingTimeMs: Long
    get() = maxOf(0, durationMs - currentPositionMs)

  /** Get human-readable remaining time string. */
  val remainingTimeFormatted: String
    get() = formatDuration(remainingTimeMs)

  /** Get human-readable file size string. */
  val fileSizeFormatted: String
    get() = formatFileSize(fileSize)

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
