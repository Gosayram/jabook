package com.jabook.app.core.data.repository

import com.jabook.app.core.domain.model.DownloadProgress
import com.jabook.app.core.domain.model.TorrentHandle
import com.jabook.app.core.domain.model.TorrentStatus
import com.jabook.app.core.domain.repository.TorrentRepository
import com.jabook.app.core.torrent.TorrentManager
import com.jabook.app.shared.debug.IDebugLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TorrentRepositoryImpl
  @Inject
  constructor(
    private val torrentManager: TorrentManager,
    private val debugLogger: IDebugLogger,
  ) : TorrentRepository {
    override suspend fun addTorrent(
      magnetUri: String,
      audiobookId: String,
      downloadPath: String,
    ): Flow<TorrentHandle> =
      flowOf(
        TorrentHandle(
          torrentId = "mock_torrent_id",
          magnetUri = magnetUri,
          title = "Mock Torrent",
          status = TorrentStatus.DOWNLOADING,
        ),
      )

    override fun getDownloadProgress(torrentId: String): Flow<DownloadProgress> =
      flowOf(
        DownloadProgress(
          torrentId = torrentId,
          audiobookId = "mock_audiobook",
          progress = 0.5f,
          downloadSpeed = 1024L,
          uploadSpeed = 512L,
          downloaded = 0L,
          total = 1000000L,
          eta = 3600L,
          status = TorrentStatus.DOWNLOADING,
          seeders = 5,
          leechers = 2,
        ),
      )

    override fun getActiveDownloads(): Flow<List<DownloadProgress>> {
      // Map TorrentManager downloadStates to list
      return torrentManager.downloadStates.map { it.values.toList() }
    }

    override suspend fun stopTorrent(torrentId: String) {
      debugLogger.logInfo("Stopping torrent: $torrentId")
      torrentManager.removeTorrent(torrentId, deleteFiles = false)
    }

    override suspend fun pauseTorrent(torrentId: String) {
      debugLogger.logInfo("Pausing torrent: $torrentId")
      torrentManager.pauseTorrent(torrentId)
    }

    override suspend fun resumeTorrent(torrentId: String) {
      debugLogger.logInfo("Resuming torrent: $torrentId")
      torrentManager.resumeTorrent(torrentId)
    }

    override suspend fun removeTorrent(
      torrentId: String,
      deleteFiles: Boolean,
    ) {
      debugLogger.logInfo("Removing torrent: $torrentId, deleteFiles: $deleteFiles")
    }

    override fun getTorrentStatus(torrentId: String): Flow<TorrentStatus> = flowOf(TorrentStatus.DOWNLOADING)

    override fun getAllTorrents(): Flow<List<TorrentHandle>> = flowOf(emptyList())

    override suspend fun setDownloadSpeedLimit(limitKBps: Int) {
      debugLogger.logInfo("Setting download speed limit: $limitKBps KB/s")
    }

    override suspend fun setUploadSpeedLimit(limitKBps: Int) {
      debugLogger.logInfo("Setting upload speed limit: $limitKBps KB/s")
    }

    override fun getStatistics(): Flow<Map<String, Long>> = flowOf(emptyMap())

    override suspend fun isAvailable(): Boolean = true

    override suspend fun initialize() {
      debugLogger.logInfo("TorrentRepository initialized")
    }

    override suspend fun shutdown() {
      debugLogger.logInfo("TorrentRepository shutdown")
    }
  }
