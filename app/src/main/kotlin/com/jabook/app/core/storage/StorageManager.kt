package com.jabook.app.core.storage

import java.io.File
import java.util.Locale

/** Storage manager interface for audiobook file management Based on IDEA.md architecture specification */
interface StorageManager {
    suspend fun getAudiobooksDirectory(): File

    suspend fun getTempDirectory(): File

    suspend fun getCacheDirectory(): File

    suspend fun getLogsDirectory(): File

    suspend fun createAudiobookDirectory(author: String, title: String): File

    suspend fun extractAudiobookFiles(archivePath: String, destination: File): List<AudioFile>

    suspend fun detectAudioFiles(directory: File): List<AudioFile>

    suspend fun deleteAudiobook(author: String, title: String): Boolean

    suspend fun getStorageInfo(): StorageInfo

    suspend fun cleanupTempFiles(): Long

    suspend fun cleanupOldLogs(daysOld: Int = 7): Long

    fun getAudiobookMetadata(audiobookDirectory: File): AudiobookMetadata?

    suspend fun saveAudiobookMetadata(directory: File, metadata: AudiobookMetadata)

    suspend fun downloadCoverImage(url: String, destination: File): Boolean
}

/** Audio file information */
data class AudioFile(
    val path: String,
    val name: String,
    val size: Long,
    val duration: Long = 0, // milliseconds
    val bitrate: Int = 0,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val format: AudioFormat,
    val chapterNumber: Int? = null,
    val title: String? = null,
) {
    fun getFormattedSize(): String {
        return when {
            size >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", size / (1024.0 * 1024.0 * 1024.0))
            size >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", size / (1024.0 * 1024.0))
            size >= 1024 -> String.format(Locale.US, "%.1f KB", size / 1024.0)
            else -> String.format(Locale.US, "%d B", size)
        }
    }

    fun getFormattedDuration(): String {
        if (duration <= 0) return "Unknown"

        val totalSeconds = duration / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            else -> String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}

/** Audio format enumeration */
enum class AudioFormat(val extension: String, val mimeType: String) {
    MP3("mp3", "audio/mpeg"),
    MP4("mp4", "audio/mp4"),
    M4A("m4a", "audio/mp4"),
    AAC("aac", "audio/aac"),
    OGG("ogg", "audio/ogg"),
    FLAC("flac", "audio/flac"),
    WAV("wav", "audio/wav"),
    UNKNOWN("", "audio/*"),
    ;

    companion object {
        fun fromExtension(extension: String): AudioFormat {
            return values().find { it.extension.equals(extension, ignoreCase = true) } ?: UNKNOWN
        }

        fun fromMimeType(mimeType: String): AudioFormat {
            return values().find { it.mimeType.equals(mimeType, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

/** Storage information */
data class StorageInfo(
    val totalSpace: Long,
    val availableSpace: Long,
    val usedSpace: Long,
    val audiobooksSize: Long,
    val tempSize: Long,
    val cacheSize: Long,
    val logsSize: Long,
) {
    fun getFormattedTotal(): String = formatBytes(totalSpace)

    fun getFormattedAvailable(): String = formatBytes(availableSpace)

    fun getFormattedUsed(): String = formatBytes(usedSpace)

    fun getFormattedAudiobooks(): String = formatBytes(audiobooksSize)

    fun getFormattedTemp(): String = formatBytes(tempSize)

    fun getFormattedCache(): String = formatBytes(cacheSize)

    fun getFormattedLogs(): String = formatBytes(logsSize)

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%d B", bytes)
        }
    }
}

/** Audiobook metadata */
data class AudiobookMetadata(
    val title: String,
    val author: String,
    val narrator: String? = null,
    val description: String? = null,
    val category: String? = null,
    val duration: Long = 0, // total duration in milliseconds
    val size: Long = 0, // total size in bytes
    val coverImagePath: String? = null,
    val audioFiles: List<AudioFile> = emptyList(),
    val dateAdded: Long = System.currentTimeMillis(),
    val lastPlayed: Long = 0,
    val currentPosition: Long = 0,
    val currentChapter: Int = 0,
    val isCompleted: Boolean = false,
    val isFavorite: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val bookmarks: List<Bookmark> = emptyList(),
)

/** Bookmark for audiobook position */
data class Bookmark(
    val id: String,
    val title: String,
    val position: Long, // milliseconds
    val chapterIndex: Int,
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
)
