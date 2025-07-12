package com.jabook.app.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/** Room entity representing an audiobook in the local database. Contains all metadata and playback information for audiobooks. */
@Entity(tableName = "audiobooks")
data class AudiobookEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "author") val author: String,
    @ColumnInfo(name = "narrator") val narrator: String? = null,
    @ColumnInfo(name = "description") val description: String? = null,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "cover_url") val coverUrl: String? = null,
    @ColumnInfo(name = "local_cover_path") val localCoverPath: String? = null,
    @ColumnInfo(name = "local_audio_path") val localAudioPath: String? = null,
    @ColumnInfo(name = "file_size") val fileSize: Long = 0,
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0,
    @ColumnInfo(name = "quality") val quality: String? = null,
    @ColumnInfo(name = "bitrate") val bitrate: Int? = null,
    @ColumnInfo(name = "sample_rate") val sampleRate: Int? = null,
    // Torrent information
    @ColumnInfo(name = "torrent_url") val torrentUrl: String? = null,
    @ColumnInfo(name = "magnet_link") val magnetLink: String? = null,
    @ColumnInfo(name = "seeders") val seeders: Int = 0,
    @ColumnInfo(name = "leechers") val leechers: Int = 0,
    // Download status
    @ColumnInfo(name = "download_status") val downloadStatus: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    @ColumnInfo(name = "download_progress") val downloadProgress: Float = 0f,
    @ColumnInfo(name = "download_error") val downloadError: String? = null,
    // Playback information
    @ColumnInfo(name = "current_position_ms") val currentPositionMs: Long = 0,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean = false,
    @ColumnInfo(name = "last_played_at") val lastPlayedAt: Date? = null,
    // Metadata
    @ColumnInfo(name = "added_at") val addedAt: Date = Date(),
    @ColumnInfo(name = "updated_at") val updatedAt: Date = Date(),
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "user_rating") val userRating: Float? = null,
    @ColumnInfo(name = "playback_speed") val playbackSpeed: Float = 1.0f,
    @ColumnInfo(name = "chapter_count") val chapterCount: Int = 0,
)

/** Download status enumeration for audiobooks. */
enum class DownloadStatus {
    NOT_DOWNLOADED,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
}
