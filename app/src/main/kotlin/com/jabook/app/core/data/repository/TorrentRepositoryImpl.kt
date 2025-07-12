package com.jabook.app.core.data.repository

import com.jabook.app.core.domain.model.DownloadProgress
import com.jabook.app.core.domain.model.TorrentHandle
import com.jabook.app.core.domain.model.TorrentStatus
import com.jabook.app.core.domain.repository.TorrentRepository
import com.jabook.app.core.torrent.TorrentEvent
import com.jabook.app.shared.debug.IDebugLogger
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Implementation of torrent repository Handles torrent downloads and management
 *
 * FIXME: Replace with actual LibTorrent implementation
 */
@Singleton
class TorrentRepositoryImpl @Inject constructor(private val debugLogger: IDebugLogger) : TorrentRepository {

    companion object {
        private const val TAG = "TorrentRepo"
    }

    // In-memory storage for mock implementation
    private val activeTorrents = mutableMapOf<String, TorrentHandle>()
    private val downloadProgresses = mutableMapOf<String, DownloadProgress>()
    private var isInitialized = false

    override suspend fun addTorrent(magnetUri: String, audiobookId: String, downloadPath: String): Flow<TorrentHandle> = flow {
        debugLogger.logInfo("Adding torrent: audiobook=$audiobookId, magnet=$magnetUri", TAG)

        try {
            val torrentId = UUID.randomUUID().toString()
            val torrentHandle =
                TorrentHandle(
                    torrentId = torrentId,
                    magnetUri = magnetUri,
                    title = "Audiobook_$audiobookId",
                    status = TorrentStatus.PENDING,
                )

            activeTorrents[torrentId] = torrentHandle

            // Create initial download progress
            val progress =
                DownloadProgress(
                    torrentId = torrentId,
                    audiobookId = audiobookId,
                    progress = 0.0f,
                    downloadSpeed = 0L,
                    uploadSpeed = 0L,
                    downloaded = 0L,
                    total = 1000000L, // Mock 1MB total
                    eta = 0L,
                    status = TorrentStatus.PENDING,
                    seeders = 0,
                    leechers = 0,
                )

            downloadProgresses[torrentId] = progress

            emit(torrentHandle)

            // Log torrent event
            debugLogger.logTorrentEvent(
                TorrentEvent.TorrentAdded(
                    torrentId = torrentId,
                    name = torrentHandle.title,
                    magnetUri = magnetUri,
                    audiobookId = audiobookId,
                )
            )

            debugLogger.logInfo("Torrent added successfully: $torrentId", TAG)
        } catch (e: Exception) {
            debugLogger.logError("Failed to add torrent", e, TAG)
            throw e
        }
    }

    override fun getDownloadProgress(torrentId: String): Flow<DownloadProgress> = flow {
        debugLogger.logDebug("Getting download progress for torrent: $torrentId", TAG)

        val progress = downloadProgresses[torrentId]
        if (progress != null) {
            emit(progress)
        } else {
            debugLogger.logWarning("Download progress not found for torrent: $torrentId", TAG)
            // Return default progress for missing torrent
            emit(
                DownloadProgress(
                    torrentId = torrentId,
                    audiobookId = "unknown",
                    progress = 0.0f,
                    downloadSpeed = 0L,
                    uploadSpeed = 0L,
                    downloaded = 0L,
                    total = 0L,
                    eta = 0L,
                    status = TorrentStatus.ERROR,
                    seeders = 0,
                    leechers = 0,
                )
            )
        }
    }

    override fun getActiveDownloads(): Flow<List<DownloadProgress>> = flow {
        debugLogger.logDebug("Getting all active downloads", TAG)

        val activeDownloads =
            downloadProgresses.values.filter { progress ->
                progress.status == TorrentStatus.DOWNLOADING || progress.status == TorrentStatus.PENDING
            }

        emit(activeDownloads)
        debugLogger.logInfo("Found ${activeDownloads.size} active downloads", TAG)
    }

    override suspend fun pauseTorrent(torrentId: String) {
        debugLogger.logInfo("Pausing torrent: $torrentId", TAG)

        val torrent = activeTorrents[torrentId]
        if (torrent != null) {
            val updatedTorrent = torrent.copy(status = TorrentStatus.PAUSED)
            activeTorrents[torrentId] = updatedTorrent

            // Update progress status
            downloadProgresses[torrentId]?.let { progress -> downloadProgresses[torrentId] = progress.copy(status = TorrentStatus.PAUSED) }

            debugLogger.logTorrentEvent(TorrentEvent.TorrentPaused(torrentId = torrentId, name = torrent.title))

            debugLogger.logInfo("Torrent paused successfully: $torrentId", TAG)
        } else {
            debugLogger.logWarning("Torrent not found for pause: $torrentId", TAG)
        }
    }

    override suspend fun resumeTorrent(torrentId: String) {
        debugLogger.logInfo("Resuming torrent: $torrentId", TAG)

        val torrent = activeTorrents[torrentId]
        if (torrent != null) {
            val updatedTorrent = torrent.copy(status = TorrentStatus.DOWNLOADING)
            activeTorrents[torrentId] = updatedTorrent

            // Update progress status
            downloadProgresses[torrentId]?.let { progress ->
                downloadProgresses[torrentId] = progress.copy(status = TorrentStatus.DOWNLOADING)
            }

            debugLogger.logTorrentEvent(TorrentEvent.TorrentResumed(torrentId = torrentId, name = torrent.title))

            debugLogger.logInfo("Torrent resumed successfully: $torrentId", TAG)
        } else {
            debugLogger.logWarning("Torrent not found for resume: $torrentId", TAG)
        }
    }

    override suspend fun stopTorrent(torrentId: String) {
        debugLogger.logInfo("Stopping torrent: $torrentId", TAG)

        val torrent = activeTorrents[torrentId]
        if (torrent != null) {
            val updatedTorrent = torrent.copy(status = TorrentStatus.STOPPED)
            activeTorrents[torrentId] = updatedTorrent

            // Update progress status
            downloadProgresses[torrentId]?.let { progress -> downloadProgresses[torrentId] = progress.copy(status = TorrentStatus.STOPPED) }

            debugLogger.logInfo("Torrent stopped successfully: $torrentId", TAG)
        } else {
            debugLogger.logWarning("Torrent not found for stop: $torrentId", TAG)
        }
    }

    override suspend fun removeTorrent(torrentId: String, deleteFiles: Boolean) {
        debugLogger.logInfo("Removing torrent: $torrentId, deleteFiles: $deleteFiles", TAG)

        val torrent = activeTorrents.remove(torrentId)
        downloadProgresses.remove(torrentId)

        if (torrent != null) {
            debugLogger.logTorrentEvent(
                TorrentEvent.TorrentRemoved(torrentId = torrentId, name = torrent.title, filesDeleted = deleteFiles)
            )

            debugLogger.logInfo("Torrent removed successfully: $torrentId", TAG)
        } else {
            debugLogger.logWarning("Torrent not found for removal: $torrentId", TAG)
        }
    }

    override fun getTorrentStatus(torrentId: String): Flow<TorrentStatus> = flow {
        debugLogger.logDebug("Getting torrent status: $torrentId", TAG)

        val torrent = activeTorrents[torrentId]
        if (torrent != null) {
            emit(torrent.status)
        } else {
            debugLogger.logWarning("Torrent not found for status: $torrentId", TAG)
            emit(TorrentStatus.ERROR)
        }
    }

    override fun getAllTorrents(): Flow<List<TorrentHandle>> = flow {
        debugLogger.logDebug("Getting all torrents", TAG)

        val torrents = activeTorrents.values.toList()
        emit(torrents)
        debugLogger.logInfo("Found ${torrents.size} torrents", TAG)
    }

    override suspend fun setDownloadSpeedLimit(limitKBps: Int) {
        debugLogger.logInfo("Setting download speed limit: ${limitKBps}KB/s", TAG)
        // FIXME: Implement actual speed limit
    }

    override suspend fun setUploadSpeedLimit(limitKBps: Int) {
        debugLogger.logInfo("Setting upload speed limit: ${limitKBps}KB/s", TAG)
        // FIXME: Implement actual speed limit
    }

    override fun getStatistics(): Flow<Map<String, Long>> = flow {
        debugLogger.logDebug("Getting torrent statistics", TAG)

        val stats =
            mapOf(
                "activeTorrents" to activeTorrents.size.toLong(),
                "totalDownloaded" to downloadProgresses.values.sumOf { it.downloaded },
                "totalUploaded" to 0L, // Mock value
                "globalDownloadSpeed" to downloadProgresses.values.sumOf { it.downloadSpeed },
                "globalUploadSpeed" to downloadProgresses.values.sumOf { it.uploadSpeed },
            )

        emit(stats)
        debugLogger.logInfo("Torrent statistics retrieved", TAG)
    }

    override suspend fun isAvailable(): Boolean {
        debugLogger.logDebug("Checking torrent engine availability", TAG)
        // For mock implementation, always return true
        return true
    }

    override suspend fun initialize() {
        debugLogger.logInfo("Initializing torrent engine", TAG)

        if (!isInitialized) {
            isInitialized = true

            debugLogger.logTorrentEvent(TorrentEvent.TorrentEngineInitialized)
            debugLogger.logInfo("Torrent engine initialized successfully", TAG)
        } else {
            debugLogger.logWarning("Torrent engine already initialized", TAG)
        }
    }

    override suspend fun shutdown() {
        debugLogger.logInfo("Shutting down torrent engine", TAG)

        if (isInitialized) {
            // Clear all torrents
            activeTorrents.clear()
            downloadProgresses.clear()
            isInitialized = false

            debugLogger.logTorrentEvent(TorrentEvent.TorrentEngineShutdown)
            debugLogger.logInfo("Torrent engine shutdown successfully", TAG)
        } else {
            debugLogger.logWarning("Torrent engine not initialized", TAG)
        }
    }
}
