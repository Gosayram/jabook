package com.jabook.app.core.torrent

import com.jabook.app.core.domain.model.DownloadProgress
import com.jabook.app.core.domain.model.TorrentHandle
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
