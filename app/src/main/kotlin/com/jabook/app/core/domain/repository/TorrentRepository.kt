package com.jabook.app.core.domain.repository

import com.jabook.app.core.domain.model.DownloadProgress
import com.jabook.app.core.domain.model.TorrentHandle
import com.jabook.app.core.domain.model.TorrentStatus
import kotlinx.coroutines.flow.Flow

/** Repository interface for torrent management Handles downloading audiobooks via torrent protocol */
interface TorrentRepository {

    /**
     * Add torrent to download queue
     *
     * @param magnetUri Magnet URI of the torrent
     * @param audiobookId Associated audiobook ID
     * @param downloadPath Path where to download files
     * @return Flow of torrent handle
     */
    suspend fun addTorrent(magnetUri: String, audiobookId: String, downloadPath: String): Flow<TorrentHandle>

    /**
     * Get download progress for specific torrent
     *
     * @param torrentId Torrent ID
     * @return Flow of download progress
     */
    fun getDownloadProgress(torrentId: String): Flow<DownloadProgress>

    /**
     * Get all active downloads
     *
     * @return Flow of list of download progress
     */
    fun getActiveDownloads(): Flow<List<DownloadProgress>>

    /**
     * Pause torrent download
     *
     * @param torrentId Torrent ID to pause
     */
    suspend fun pauseTorrent(torrentId: String)

    /**
     * Resume torrent download
     *
     * @param torrentId Torrent ID to resume
     */
    suspend fun resumeTorrent(torrentId: String)

    /**
     * Stop torrent download
     *
     * @param torrentId Torrent ID to stop
     */
    suspend fun stopTorrent(torrentId: String)

    /**
     * Remove torrent from download queue
     *
     * @param torrentId Torrent ID to remove
     * @param deleteFiles Whether to delete downloaded files
     */
    suspend fun removeTorrent(torrentId: String, deleteFiles: Boolean = false)

    /**
     * Get torrent status
     *
     * @param torrentId Torrent ID
     * @return Flow of torrent status
     */
    fun getTorrentStatus(torrentId: String): Flow<TorrentStatus>

    /**
     * Get all torrents
     *
     * @return Flow of list of torrent handles
     */
    fun getAllTorrents(): Flow<List<TorrentHandle>>

    /**
     * Set download speed limit
     *
     * @param limitKBps Download speed limit in KB/s (0 for unlimited)
     */
    suspend fun setDownloadSpeedLimit(limitKBps: Int)

    /**
     * Set upload speed limit
     *
     * @param limitKBps Upload speed limit in KB/s (0 for unlimited)
     */
    suspend fun setUploadSpeedLimit(limitKBps: Int)

    /**
     * Get total download/upload statistics
     *
     * @return Flow of statistics (downloaded, uploaded, active torrents)
     */
    fun getStatistics(): Flow<Map<String, Long>>

    /**
     * Check if torrent engine is available
     *
     * @return true if torrent engine is ready
     */
    suspend fun isAvailable(): Boolean

    /** Initialize torrent engine */
    suspend fun initialize()

    /** Shutdown torrent engine */
    suspend fun shutdown()
}
