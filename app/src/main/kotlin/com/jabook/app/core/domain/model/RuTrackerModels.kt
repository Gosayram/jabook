package com.jabook.app.core.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** Category from RuTracker */
@Parcelize
data class RuTrackerCategory(
    val id: String,
    val name: String,
    val description: String? = null,
    val parentId: String? = null,
    val isActive: Boolean = true,
    val topicCount: Int = 0,
) : Parcelable

/** Audiobook information from RuTracker */
@Parcelize
data class RuTrackerAudiobook(
    val id: String,
    val title: String,
    val author: String,
    val narrator: String? = null,
    val description: String,
    val category: String,
    val categoryId: String,
    val year: Int? = null,
    val quality: String? = null,
    val duration: String? = null,
    val size: String,
    val sizeBytes: Long,
    val magnetUri: String,
    val torrentUrl: String,
    val seeders: Int,
    val leechers: Int,
    val completed: Int,
    val addedDate: String,
    val lastUpdate: String? = null,
    val coverUrl: String? = null,
    val rating: Float? = null,
    val genreList: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val isVerified: Boolean = false,
) : Parcelable

/** Search result from RuTracker */
@Parcelize
data class RuTrackerSearchResult(
    val query: String,
    val totalResults: Int,
    val currentPage: Int,
    val totalPages: Int,
    val results: List<RuTrackerAudiobook>,
) : Parcelable

/** RuTracker statistics */
@Parcelize
data class RuTrackerStats(
    val totalAudiobooks: Int,
    val totalCategories: Int,
    val activeUsers: Int,
    val totalSize: String,
    val lastUpdate: String,
) : Parcelable

/** Download status for torrent */
enum class TorrentStatus {
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    ERROR,
    SEEDING,
    STOPPED,

    /** Torrent is added but currently inactive / waiting */
    IDLE,
}

/** Download progress information */
@Parcelize
data class DownloadProgress(
    val torrentId: String,
    val audiobookId: String,
    val progress: Float, // 0.0 to 1.0
    val downloadSpeed: Long, // bytes per second
    val uploadSpeed: Long, // bytes per second
    val downloaded: Long, // bytes
    val total: Long, // bytes
    val eta: Long, // seconds remaining
    val status: TorrentStatus,
    val seeders: Int,
    val leechers: Int,
    val lastUpdate: Long = System.currentTimeMillis(),
) : Parcelable

/** Torrent handle for managing downloads */
data class TorrentHandle(
    val torrentId: String,
    val magnetUri: String,
    val title: String,
    val status: TorrentStatus,
    val addedDate: Long = System.currentTimeMillis(),
)
