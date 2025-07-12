package com.jabook.app.core.torrent

import java.util.Locale
import kotlinx.coroutines.flow.Flow

/** Torrent manager interface for audiobook downloads Based on IDEA.md architecture specification */
interface TorrentManager {
    suspend fun addTorrent(magnetUri: String): TorrentHandle

    suspend fun addTorrentFile(torrentFilePath: String): TorrentHandle

    suspend fun pauseTorrent(torrentId: String)

    suspend fun resumeTorrent(torrentId: String)

    suspend fun removeTorrent(torrentId: String, deleteFiles: Boolean = false)

    fun getTorrentProgress(torrentId: String): Flow<DownloadProgress>

    fun getAllTorrents(): Flow<List<TorrentHandle>>

    suspend fun setDownloadLocation(torrentId: String, path: String)

    suspend fun setDownloadLimits(downloadLimit: Long, uploadLimit: Long)
}

/** Torrent handle for managing individual torrents */
data class TorrentHandle(
    val id: String,
    val name: String,
    val magnetUri: String,
    val status: TorrentStatus,
    val progress: Float = 0f,
    val downloadSpeed: Long = 0,
    val uploadSpeed: Long = 0,
    val totalSize: Long = 0,
    val downloadedSize: Long = 0,
    val eta: Long = 0,
    val seeders: Int = 0,
    val leechers: Int = 0,
    val savePath: String,
)

/** Download progress information */
data class DownloadProgress(
    val torrentId: String,
    val progress: Float,
    val downloadSpeed: Long,
    val uploadSpeed: Long,
    val totalSize: Long,
    val downloadedSize: Long,
    val eta: Long,
    val status: TorrentStatus,
) {
    fun getFormattedSpeed(): String {
        return when {
            downloadSpeed >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB/s", downloadSpeed / (1024.0 * 1024.0))
            downloadSpeed >= 1024 -> String.format(Locale.US, "%.1f KB/s", downloadSpeed / 1024.0)
            else -> String.format(Locale.US, "%d B/s", downloadSpeed)
        }
    }

    fun getFormattedEta(): String {
        if (eta <= 0) return "Unknown"

        val hours = eta / 3600
        val minutes = (eta % 3600) / 60
        val seconds = eta % 60

        return when {
            hours > 0 -> String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            minutes > 0 -> String.format(Locale.US, "%02d:%02d", minutes, seconds)
            else -> String.format(Locale.US, "%02d", seconds)
        }
    }
}

/** Torrent status enumeration */
enum class TorrentStatus {
    QUEUED,
    DOWNLOADING,
    SEEDING,
    PAUSED,
    ERROR,
    COMPLETED,
    CHECKING,
    ALLOCATING,
}

/** Torrent event for debugging and logging */
sealed class TorrentEvent {
    data class TorrentAdded(val torrentId: String, val name: String) : TorrentEvent()

    data class TorrentCompleted(val torrentId: String) : TorrentEvent()

    data class TorrentError(val torrentId: String, val error: String) : TorrentEvent()

    data class TorrentPaused(val torrentId: String) : TorrentEvent()

    data class TorrentResumed(val torrentId: String) : TorrentEvent()

    data class TorrentRemoved(val torrentId: String, val filesDeleted: Boolean) : TorrentEvent()
}

/** Torrent configuration */
data class TorrentConfig(
    val downloadLocation: String,
    val maxDownloadSpeed: Long = 0, // 0 = unlimited
    val maxUploadSpeed: Long = 0, // 0 = unlimited
    val maxConnections: Int = 200,
    val enableDht: Boolean = true,
    val enablePeerExchange: Boolean = true,
    val enableLocalServiceDiscovery: Boolean = true,
    val seedingTimeLimit: Long = 0, // 0 = unlimited seeding
    val seedingRatioLimit: Float = 0f, // 0 = unlimited ratio
)
