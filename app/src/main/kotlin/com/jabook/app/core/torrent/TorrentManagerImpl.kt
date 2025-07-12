package com.jabook.app.core.torrent

import com.jabook.app.core.domain.model.DownloadProgress
import com.jabook.app.core.domain.model.TorrentHandle
import com.jabook.app.core.domain.model.TorrentStatus
import com.jabook.app.core.storage.FileManager
import com.jabook.app.shared.debug.IDebugLogger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Implementation of torrent manager using LibTorrent4j Handles downloading audiobooks via torrent protocol
 *
 * FIXME: Replace mock implementation with actual LibTorrent4j integration
 */
@Singleton
class TorrentManagerImpl @Inject constructor(private val fileManager: FileManager, private val debugLogger: IDebugLogger) : TorrentManager {

    companion object {
        private const val TAG = "TorrentManager"
        private const val MAX_DOWNLOAD_SPEED = 5 * 1024 * 1024L // 5 MB/s default
        private const val MAX_UPLOAD_SPEED = 1 * 1024 * 1024L // 1 MB/s default
        private const val PROGRESS_UPDATE_INTERVAL = 1000L // 1 second
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // State management
    private var isInitialized = false
    private var downloadSpeedLimit = MAX_DOWNLOAD_SPEED
    private var uploadSpeedLimit = MAX_UPLOAD_SPEED

    // Torrent storage
    private val activeTorrents = ConcurrentHashMap<String, TorrentHandle>()
    private val torrentProgress = ConcurrentHashMap<String, MutableStateFlow<DownloadProgress>>()
    private val torrentConfigs = ConcurrentHashMap<String, TorrentConfig>()

    // State flows
    private val _allTorrents = MutableStateFlow<List<TorrentHandle>>(emptyList())
    val allTorrents: StateFlow<List<TorrentHandle>> = _allTorrents.asStateFlow()

    init {
        // Initialize progress monitoring
        scope.launch {
            while (isActive) {
                updateAllTorrentProgress()
                delay(PROGRESS_UPDATE_INTERVAL)
            }
        }
    }

    override suspend fun addTorrent(magnetUri: String): TorrentHandle {
        debugLogger.logInfo("Adding torrent: $magnetUri", TAG)

        return withContext(Dispatchers.IO) {
            try {
                // Generate unique torrent ID
                val torrentId = UUID.randomUUID().toString()

                // Create torrent handle
                val torrentHandle =
                    TorrentHandle(
                        torrentId = torrentId,
                        magnetUri = magnetUri,
                        title = extractTorrentName(magnetUri),
                        status = TorrentStatus.PENDING,
                    )

                // Store torrent
                activeTorrents[torrentId] = torrentHandle

                // Create progress flow
                val totalSize = estimateTorrentSize(magnetUri)
                val initialProgress =
                    DownloadProgress(
                        torrentId = torrentId,
                        audiobookId = "unknown",
                        progress = 0f,
                        downloadSpeed = 0L,
                        uploadSpeed = 0L,
                        downloaded = 0L,
                        total = totalSize,
                        eta = 0L,
                        status = TorrentStatus.PENDING,
                        seeders = 0,
                        leechers = 0,
                    )

                torrentProgress[torrentId] = MutableStateFlow(initialProgress)

                // Log torrent event
                debugLogger.logTorrentEvent(
                    TorrentEvent.TorrentAdded(
                        torrentId = torrentId,
                        name = torrentHandle.title,
                        magnetUri = magnetUri,
                        audiobookId = "unknown", // Will be set later when linked to audiobook
                    )
                )

                // Update state
                updateTorrentsState()

                // Start download automatically
                startTorrentDownload(torrentId)

                debugLogger.logInfo("Torrent added successfully: $torrentId", TAG)
                torrentHandle
            } catch (e: Exception) {
                debugLogger.logError("Failed to add torrent", e, TAG)
                throw e
            }
        }
    }

    override suspend fun addTorrentFile(torrentFilePath: String): TorrentHandle {
        debugLogger.logInfo("Adding torrent from file: $torrentFilePath", TAG)

        return withContext(Dispatchers.IO) {
            try {
                // For now, treat file torrent similar to magnet
                // FIXME: Implement actual torrent file parsing
                val magnetUri = "magnet:?xt=urn:btih:${UUID.randomUUID()}" // Mock magnet from file
                addTorrent(magnetUri)
            } catch (e: Exception) {
                debugLogger.logError("Failed to add torrent from file", e, TAG)
                throw e
            }
        }
    }

    override suspend fun pauseTorrent(torrentId: String) {
        debugLogger.logInfo("Pausing torrent: $torrentId", TAG)

        withContext(Dispatchers.IO) {
            activeTorrents[torrentId]?.let { torrent ->
                val updatedTorrent = torrent.copy(status = TorrentStatus.PAUSED)
                activeTorrents[torrentId] = updatedTorrent

                // Update progress status
                torrentProgress[torrentId]?.let { progressFlow ->
                    val currentProgress = progressFlow.value
                    progressFlow.value = currentProgress.copy(status = TorrentStatus.PAUSED)
                }

                debugLogger.logTorrentEvent(TorrentEvent.TorrentPaused(torrentId = torrentId, name = torrent.title))

                updateTorrentsState()
                debugLogger.logInfo("Torrent paused: $torrentId", TAG)
            } ?: run { debugLogger.logWarning("Torrent not found for pause: $torrentId", TAG) }
        }
    }

    override suspend fun resumeTorrent(torrentId: String) {
        debugLogger.logInfo("Resuming torrent: $torrentId", TAG)

        withContext(Dispatchers.IO) {
            activeTorrents[torrentId]?.let { torrent ->
                val updatedTorrent = torrent.copy(status = TorrentStatus.DOWNLOADING)
                activeTorrents[torrentId] = updatedTorrent

                // Update progress status
                torrentProgress[torrentId]?.let { progressFlow ->
                    val currentProgress = progressFlow.value
                    progressFlow.value = currentProgress.copy(status = TorrentStatus.DOWNLOADING)
                }

                debugLogger.logTorrentEvent(TorrentEvent.TorrentResumed(torrentId = torrentId, name = torrent.title))

                updateTorrentsState()
                debugLogger.logInfo("Torrent resumed: $torrentId", TAG)
            } ?: run { debugLogger.logWarning("Torrent not found for resume: $torrentId", TAG) }
        }
    }

    override suspend fun removeTorrent(torrentId: String, deleteFiles: Boolean) {
        debugLogger.logInfo("Removing torrent: $torrentId, deleteFiles: $deleteFiles", TAG)

        withContext(Dispatchers.IO) {
            activeTorrents.remove(torrentId)?.let { torrent ->
                torrentProgress.remove(torrentId)
                torrentConfigs.remove(torrentId)

                // Delete files if requested
                if (deleteFiles) {
                    try {
                        val saveDir = fileManager.getTempDirectory()
                        // For mock implementation, we don't actually have files to delete
                        debugLogger.logInfo("Would delete torrent files for: $torrentId", TAG)
                    } catch (e: Exception) {
                        debugLogger.logError("Failed to delete torrent files", e, TAG)
                    }
                }

                debugLogger.logTorrentEvent(
                    TorrentEvent.TorrentRemoved(torrentId = torrentId, name = torrent.title, filesDeleted = deleteFiles)
                )

                updateTorrentsState()
                debugLogger.logInfo("Torrent removed: $torrentId", TAG)
            } ?: run { debugLogger.logWarning("Torrent not found for removal: $torrentId", TAG) }
        }
    }

    override fun getTorrentProgress(torrentId: String): Flow<DownloadProgress> {
        return torrentProgress[torrentId]?.asStateFlow()
            ?: run {
                debugLogger.logWarning("Progress flow not found for torrent: $torrentId", TAG)
                MutableStateFlow(
                        DownloadProgress(
                            torrentId = torrentId,
                            audiobookId = "unknown",
                            progress = 0f,
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
                    .asStateFlow()
            }
    }

    override fun getAllTorrents(): Flow<List<TorrentHandle>> = allTorrents

    override suspend fun setDownloadLocation(torrentId: String, path: String) {
        debugLogger.logInfo("Setting download location for torrent $torrentId: $path", TAG)

        activeTorrents[torrentId]?.let { torrent ->
            // TorrentHandle doesn't have savePath field, so we'll store it separately
            // For now, just log the operation
            debugLogger.logInfo("Download location set for torrent $torrentId: $path", TAG)
            // FIXME: Store path in torrentConfigs or extend TorrentHandle
        }
    }

    override suspend fun setDownloadLimits(downloadLimit: Long, uploadLimit: Long) {
        debugLogger.logInfo("Setting download/upload limits: ${downloadLimit}/${uploadLimit} bytes/s", TAG)

        downloadSpeedLimit = downloadLimit
        uploadSpeedLimit = uploadLimit

        // FIXME: Apply limits to actual LibTorrent session
    }

    // Private helper methods

    private suspend fun startTorrentDownload(torrentId: String) {
        activeTorrents[torrentId]?.let { torrent ->
            val updatedTorrent = torrent.copy(status = TorrentStatus.DOWNLOADING)
            activeTorrents[torrentId] = updatedTorrent

            debugLogger.logTorrentEvent(TorrentEvent.TorrentStarted(torrentId = torrentId, name = torrent.title))

            updateTorrentsState()

            // Start mock download simulation
            simulateDownload(torrentId)
        }
    }

    private fun simulateDownload(torrentId: String) {
        scope.launch {
            val torrent = activeTorrents[torrentId] ?: return@launch
            val progressFlow = torrentProgress[torrentId] ?: return@launch
            val initialProgress = progressFlow.value

            var downloadedBytes = 0L
            val totalBytes = initialProgress.total
            val downloadSpeed = kotlin.random.Random.nextLong(512 * 1024, 2 * 1024 * 1024) // 512KB-2MB/s

            while (downloadedBytes < totalBytes && activeTorrents.containsKey(torrentId)) {
                val currentTorrent = activeTorrents[torrentId] ?: break

                if (currentTorrent.status == TorrentStatus.PAUSED) {
                    delay(1000)
                    continue
                }

                // Simulate download progress
                downloadedBytes = minOf(downloadedBytes + downloadSpeed, totalBytes)
                val progress = downloadedBytes.toFloat() / totalBytes.toFloat()
                val eta = if (downloadSpeed > 0) (totalBytes - downloadedBytes) / downloadSpeed else 0L

                // Update progress
                val updatedProgress =
                    DownloadProgress(
                        torrentId = torrentId,
                        audiobookId = initialProgress.audiobookId,
                        progress = progress,
                        downloadSpeed = downloadSpeed,
                        uploadSpeed = kotlin.random.Random.nextLong(0, 512 * 1024), // 0-512KB/s upload
                        downloaded = downloadedBytes,
                        total = totalBytes,
                        eta = eta,
                        status = if (progress >= 1.0f) TorrentStatus.COMPLETED else TorrentStatus.DOWNLOADING,
                        seeders = kotlin.random.Random.nextInt(1, 50),
                        leechers = kotlin.random.Random.nextInt(0, 20),
                    )

                progressFlow.value = updatedProgress

                // Update torrent handle
                val updatedTorrent = currentTorrent.copy(status = updatedProgress.status)

                activeTorrents[torrentId] = updatedTorrent

                // Check if completed
                if (progress >= 1.0f) {
                    debugLogger.logTorrentEvent(
                        TorrentEvent.TorrentCompleted(
                            torrentId = torrentId,
                            name = torrent.title,
                            audiobookId = initialProgress.audiobookId,
                            downloadPath = fileManager.getTempDirectory().absolutePath,
                            totalSize = totalBytes,
                            downloadTimeMs = System.currentTimeMillis(),
                        )
                    )

                    updateTorrentsState()
                    break
                }

                updateTorrentsState()
                delay(PROGRESS_UPDATE_INTERVAL)
            }
        }
    }

    private suspend fun updateAllTorrentProgress() {
        // This method can be used for real-time updates when using actual LibTorrent
        // For now, the mock simulation handles progress updates
    }

    private fun updateTorrentsState() {
        _allTorrents.value = activeTorrents.values.toList()
    }

    private fun extractTorrentName(magnetUri: String): String {
        // Extract name from magnet URI
        return try {
            val nameStart = magnetUri.indexOf("dn=")
            if (nameStart != -1) {
                val nameEnd = magnetUri.indexOf("&", nameStart)
                val name =
                    if (nameEnd != -1) {
                        magnetUri.substring(nameStart + 3, nameEnd)
                    } else {
                        magnetUri.substring(nameStart + 3)
                    }
                java.net.URLDecoder.decode(name, "UTF-8")
            } else {
                "Unknown Torrent"
            }
        } catch (e: Exception) {
            "Unknown Torrent"
        }
    }

    private fun estimateTorrentSize(magnetUri: String): Long {
        // Mock size estimation - in real implementation this would come from metadata
        return kotlin.random.Random.nextLong(100 * 1024 * 1024, 2 * 1024 * 1024 * 1024) // 100MB - 2GB
    }
}
