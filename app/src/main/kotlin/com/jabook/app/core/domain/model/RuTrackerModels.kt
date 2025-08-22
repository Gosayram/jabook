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
    val magnetUri: String? = null,
    val torrentUrl: String? = null,
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
    val state: TorrentState = TorrentState.APPROVED,
    val downloads: Int = 0,
    val registered: java.util.Date? = null,
) : Parcelable {
    companion object {
        fun empty() =
            RuTrackerAudiobook(
                id = "",
                title = "",
                author = "",
                narrator = null,
                description = "",
                category = "",
                categoryId = "",
                year = null,
                quality = null,
                duration = null,
                size = "",
                sizeBytes = 0L,
                magnetUri = null,
                torrentUrl = null,
                seeders = 0,
                leechers = 0,
                completed = 0,
                addedDate = "",
                lastUpdate = null,
                coverUrl = null,
                rating = null,
                genreList = emptyList(),
                tags = emptyList(),
                isVerified = false,
                state = TorrentState.APPROVED,
                downloads = 0,
                registered = null,
            )
    }
}

/** Search result from RuTracker */
@Parcelize
data class RuTrackerSearchResult(
    val query: String,
    val totalResults: Int,
    val currentPage: Int,
    val totalPages: Int,
    val results: List<RuTrackerAudiobook>,
) : Parcelable {
    companion object {
        fun empty(query: String) =
            RuTrackerSearchResult(
                query = query,
                totalResults = 0,
                currentPage = 1,
                totalPages = 1,
                results = emptyList(),
            )
    }
}

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

/** Torrent state on RuTracker */
enum class TorrentState {
    APPROVED, // проверено
    NOT_APPROVED, // не проверено
    NEED_EDIT, // недооформлено
    DUBIOUSLY, // сомнительно
    CONSUMED, // поглощена
    TEMPORARY, // временная
    PENDING, // ожидание
    REJECTED, // отклонено
    DUPLICATE, // дубликат
    CLOSED, // закрыто
    CHECKING, // проверка
    ABSORBED, // поглощено
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
