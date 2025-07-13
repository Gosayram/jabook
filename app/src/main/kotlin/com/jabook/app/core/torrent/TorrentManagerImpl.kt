package com.jabook.app.core.torrent

import com.jabook.app.core.domain.model.DownloadProgress
import com.jabook.app.core.domain.model.TorrentHandle
import com.jabook.app.shared.debug.IDebugLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TorrentManagerImpl @Inject constructor(private val debugLogger: IDebugLogger) : TorrentManager {

    private val _downloadStates = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    override val downloadStates: StateFlow<Map<String, DownloadProgress>> = _downloadStates.asStateFlow()

    override suspend fun addTorrent(magnetUri: String): TorrentHandle {
        debugLogger.logInfo("Adding torrent: $magnetUri")
        return TorrentHandle(
            torrentId = "mock_torrent_${System.currentTimeMillis()}",
            magnetUri = magnetUri,
            title = "Mock Torrent",
            status = com.jabook.app.core.domain.model.TorrentStatus.DOWNLOADING,
        )
    }

    override suspend fun addTorrentFile(torrentFilePath: String): TorrentHandle {
        debugLogger.logInfo("Adding torrent file: $torrentFilePath")
        return TorrentHandle(
            torrentId = "mock_torrent_file_${System.currentTimeMillis()}",
            magnetUri = "magnet:?xt=urn:btih:mock",
            title = "Mock Torrent File",
            status = com.jabook.app.core.domain.model.TorrentStatus.DOWNLOADING,
        )
    }

    override suspend fun pauseTorrent(torrentId: String) {
        debugLogger.logInfo("Pausing torrent: $torrentId")
        updateStatus(torrentId, com.jabook.app.core.domain.model.TorrentStatus.PAUSED)
    }

    override suspend fun resumeTorrent(torrentId: String) {
        debugLogger.logInfo("Resuming torrent: $torrentId")
        updateStatus(torrentId, com.jabook.app.core.domain.model.TorrentStatus.DOWNLOADING)
    }

    override suspend fun removeTorrent(torrentId: String, deleteFiles: Boolean) {
        debugLogger.logInfo("Removing torrent: $torrentId, deleteFiles: $deleteFiles")
        _downloadStates.value = _downloadStates.value - torrentId
    }

    override fun getTorrentProgress(torrentId: String): Flow<DownloadProgress> {
        return flowOf(
            DownloadProgress(
                torrentId = torrentId,
                audiobookId = "mock_audiobook",
                progress = 0.5f,
                downloadSpeed = 1024L,
                uploadSpeed = 512L,
                downloaded = 0L,
                total = 1000000L,
                eta = 3600L,
                status = com.jabook.app.core.domain.model.TorrentStatus.DOWNLOADING,
                seeders = 5,
                leechers = 2,
            ),
        )
    }

    override fun getAllTorrents(): Flow<List<TorrentHandle>> {
        return flowOf(emptyList())
    }

    override suspend fun setDownloadLocation(torrentId: String, path: String) {
        debugLogger.logInfo("Setting download location for $torrentId: $path")
    }

    override suspend fun setDownloadLimits(downloadLimit: Long, uploadLimit: Long) {
        debugLogger.logInfo("Setting download limits: down=$downloadLimit, up=$uploadLimit")
    }

    private fun updateStatus(torrentId: String, status: com.jabook.app.core.domain.model.TorrentStatus) {
        val current = _downloadStates.value[torrentId] ?: return
        _downloadStates.value = _downloadStates.value + (torrentId to current.copy(status = status))
    }
}
