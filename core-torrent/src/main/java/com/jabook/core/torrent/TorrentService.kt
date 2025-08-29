package com.jabook.core.torrent

import android.content.Context
import android.util.Log
import com.jabook.core.endpoints.EndpointResolver
import kotlinx.coroutines.*
import org.libtorrent4j.AddTorrentParams
import org.libtorrent4j.AlertListener
import org.libtorrent4j.SessionManager
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.Vectors
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.TorrentFinishedAlert
import org.libtorrent4j.alerts.TorrentRemovedAlert
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Torrent service for JaBook app
 * Handles torrent downloads with sequential reading for audio streaming
 */
class TorrentService(
    private val context: Context,
    private val endpointResolver: EndpointResolver
) {
    
    private val TAG = "TorrentService"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // LibTorrent4j session
    private var sessionManager: SessionManager? = null
    
    // Active torrents
    private val activeTorrents = ConcurrentHashMap<String, TorrentHandle>()
    private val torrentListeners = ConcurrentHashMap<String, TorrentListener>()
    
    // Service state
    private val isInitialized = AtomicBoolean(false)
    
    /**
     * Torrent listener interface
     */
    interface TorrentListener {
        fun onProgress(torrentId: String, progress: Float, downloadSpeed: Long, uploadSpeed: Long)
        fun onFinished(torrentId: String)
        fun onError(torrentId: String, error: String)
        fun onPaused(torrentId: String)
    }
    
    /**
     * Torrent info data class
     */
    data class TorrentInfo(
        val id: String,
        val name: String,
        val size: Long,
        val progress: Float,
        val downloadSpeed: Long,
        val uploadSpeed: Long,
        val priority: Int,
        val sequential: Boolean,
        val paused: Boolean,
        val finished: Boolean
    )
    
    init {
        initialize()
    }
    
    /**
     * Initializes the torrent service
     */
    private fun initialize() {
        if (isInitialized.get()) return
        
        try {
            // Initialize libtorrent4j session
            sessionManager = SessionManager().apply {
                // Set session parameters using the correct API
                settings().downloadRateLimit(-1) // No limit
                settings().uploadRateLimit(-1) // No limit
                settings().activeDownloads(5)
                settings().activeSeeding(3)
                settings().setDhtEnabled(true)
                settings().setLsdEnabled(true)
                settings().setPeXEnabled(true)
                settings().setMaxPerTorrent(10)
                
                // Set alert listener using the correct API
                sessionManager?.addListener(object : AlertListener {
                    override fun alert(alert: Alert<*>) {
                        when (alert.type()) {
                            AlertType.ADD_TORRENT -> {
                                val addedAlert = alert as org.libtorrent4j.AddTorrentAlert
                                val torrentId = addedAlert.handle().infoHash().toHex()
                                Log.i(TAG, "Torrent added: $torrentId")
                            }
                            AlertType.TORRENT_FINISHED -> {
                                val finishedAlert = alert as TorrentFinishedAlert
                                val torrentId = finishedAlert.handle().infoHash().toHex()
                                Log.i(TAG, "Torrent finished: $torrentId")
                                notifyFinished(torrentId)
                            }
                            AlertType.TORRENT_REMOVED -> {
                                val removedAlert = alert as TorrentRemovedAlert
                                val torrentId = removedAlert.handle().infoHash().toHex()
                                Log.i(TAG, "Torrent removed: $torrentId")
                                activeTorrents.remove(torrentId)
                            }
                            AlertType.ERROR_ALERT -> {
                                Log.e(TAG, "Libtorrent error: ${alert.message()}")
                            }
                            else -> {
                                // Log other alerts for debugging
                                if (alert.severity() >= AlertType.ERROR_ALERT.severity()) {
                                    Log.w(TAG, "Libtorrent alert: ${alert.type()} - ${alert.message()}")
                                }
                            }
                        }
                    }
                    
                    override fun types(): IntArray {
                        return intArrayOf(
                            AlertType.ADD_TORRENT.ordinal,
                            AlertType.TORRENT_FINISHED.ordinal,
                            AlertType.TORRENT_REMOVED.ordinal,
                            AlertType.ERROR_ALERT.ordinal
                        )
                    }
                })
                
                // Start session
                start()
            }
            
            isInitialized.set(true)
            Log.i(TAG, "Torrent service initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize torrent service", e)
        }
    }
    
    /**
     * Adds a torrent by magnet URL
     */
    suspend fun addTorrentByMagnet(magnetUrl: String, sequential: Boolean = true): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized.get()) {
                    return@withContext Result.failure(Exception("Torrent service not initialized"))
                }
                
                val session = sessionManager ?: return@withContext Result.failure(Exception("Session not available"))
                
                // Create torrent handle
                val params = AddTorrentParams()
                    .url(magnetUrl)
                    .setSavePath(getTorrentsDirectory().absolutePath)
                    .setPaused(false)
                    .setSequentialDownload(sequential)
                    .setPriority(1) // PRIORITY_NORMAL = 1
                
                val handle = session.addTorrent(params)
                val torrentId = handle.infoHash().toHex()
                
                // Store active torrent
                activeTorrents[torrentId] = handle
                
                Log.i(TAG, "Added torrent by magnet: $torrentId")
                Result.success(torrentId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add torrent by magnet", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Adds a torrent by torrent file
     */
    suspend fun addTorrentByFile(torrentFile: File, sequential: Boolean = true): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (!isInitialized.get()) {
                    return@withContext Result.failure(Exception("Torrent service not initialized"))
                }
                
                val session = sessionManager ?: return@withContext Result.failure(Exception("Session not available"))
                
                // Load torrent info
                val libtorrentInfo = org.libtorrent4j.TorrentInfo(torrentFile)
                val infoHash = libtorrentInfo.infoHash()
                
                // Create torrent handle
                val params = AddTorrentParams()
                    .ti(libtorrentInfo)
                    .setSavePath(getTorrentsDirectory().absolutePath)
                    .setPaused(false)
                    .setSequentialDownload(sequential)
                    .setPriority(1) // PRIORITY_NORMAL = 1
                
                val handle = session.addTorrent(params)
                val torrentId = infoHash.toHex()
                
                // Store active torrent
                activeTorrents[torrentId] = handle
                
                Log.i(TAG, "Added torrent by file: $torrentId")
                Result.success(torrentId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add torrent by file", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Removes a torrent
     */
    suspend fun removeTorrent(torrentId: String, deleteFiles: Boolean = false): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val handle = activeTorrents[torrentId]
                if (handle == null) {
                    return@withContext Result.failure(Exception("Torrent not found: $torrentId"))
                }
                
                val session = sessionManager ?: return@withContext Result.failure(Exception("Session not available"))
                
                // Remove torrent
                sessionManager?.removeTorrent(handle, deleteFiles)
                
                Log.i(TAG, "Removed torrent: $torrentId")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove torrent: $torrentId", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Pauses a torrent
     */
    suspend fun pauseTorrent(torrentId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val handle = activeTorrents[torrentId]
                if (handle == null) {
                    return@withContext Result.failure(Exception("Torrent not found: $torrentId"))
                }
                
                handle.pause()
                notifyPaused(torrentId)
                
                Log.i(TAG, "Paused torrent: $torrentId")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pause torrent: $torrentId", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Resumes a torrent
     */
    suspend fun resumeTorrent(torrentId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val handle = activeTorrents[torrentId]
                if (handle == null) {
                    return@withContext Result.failure(Exception("Torrent not found: $torrentId"))
                }
                
                handle.resume()
                
                Log.i(TAG, "Resumed torrent: $torrentId")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resume torrent: $torrentId", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Gets torrent info
     */
    suspend fun getTorrentInfo(torrentId: String): Result<TorrentInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val handle = activeTorrents[torrentId]
                if (handle == null) {
                    return@withContext Result.failure(Exception("Torrent not found: $torrentId"))
                }
                
                val status = handle.status()
                val torrentInfoData = TorrentInfo(
                    id = handle.infoHash().toHex(),
                    name = handle.name().toString(),
                    size = status.totalWanted(),
                    progress = status.progress(),
                    downloadSpeed = status.downloadRate().toLong(),
                    uploadSpeed = status.uploadRate().toLong(),
                    priority = 1,
                    sequential = false,
                    paused = status.paused(),
                    finished = status.finished()
                )
                
                Result.success(torrentInfoData)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get torrent info: $torrentId", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Gets all active torrents
     */
    suspend fun getAllTorrents(): List<TorrentInfo> {
        return withContext(Dispatchers.IO) {
            try {
                activeTorrents.values.map { handle ->
                    val status = handle.status()
                    TorrentInfo(
                        id = handle.infoHash().toHex(),
                        name = handle.name().toString(),
                        size = status.totalWanted(),
                        progress = status.progress(),
                        downloadSpeed = status.downloadRate().toLong(),
                        uploadSpeed = status.uploadRate().toLong(),
                        priority = 1,
                        sequential = false,
                        paused = status.paused(),
                        finished = status.finished()
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get all torrents", e)
                emptyList()
            }
        }
    }
    
    /**
     * Sets torrent listener
     */
    fun setTorrentListener(torrentId: String, listener: TorrentListener) {
        torrentListeners[torrentId] = listener
    }
    
    /**
     * Removes torrent listener
     */
    fun removeTorrentListener(torrentId: String) {
        torrentListeners.remove(torrentId)
    }
    
    /**
     * Starts progress monitoring
     */
    fun startProgressMonitoring() {
        scope.launch {
            while (isActive) {
                try {
                    monitorProgress()
                    delay(1000) // Update every second
                } catch (e: Exception) {
                    Log.e(TAG, "Error in progress monitoring", e)
                }
            }
        }
    }
    
    /**
     * Monitors torrent progress
     */
    private suspend fun monitorProgress() {
        activeTorrents.forEach { (torrentId, handle) ->
            try {
                val status = handle.status()
                val listener = torrentListeners[torrentId]
                
                if (listener != null) {
                    listener.onProgress(
                        torrentId = torrentId,
                        progress = status.progress(),
                        downloadSpeed = status.downloadRate().toLong(),
                        uploadSpeed = status.uploadRate().toLong()
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to monitor torrent: $torrentId", e)
            }
        }
    }
    
    /**
     * Notifies listeners about finished torrent
     */
    private fun notifyFinished(torrentId: String) {
        val listener = torrentListeners[torrentId]
        listener?.onFinished(torrentId)
    }
    
    /**
     * Notifies listeners about paused torrent
     */
    private fun notifyPaused(torrentId: String) {
        val listener = torrentListeners[torrentId]
        listener?.onPaused(torrentId)
    }
    
    /**
     * Gets torrents directory
     */
    private fun getTorrentsDirectory(): File {
        val filesDir = context.filesDir
        val torrentsDir = File(filesDir, "torrents")
        if (!torrentsDir.exists()) {
            torrentsDir.mkdirs()
        }
        return torrentsDir
    }
    
    /**
     * Gets torrent files directory
     */
    fun getTorrentFilesDirectory(torrentId: String): File {
        val torrentsDir = getTorrentsDirectory()
        return File(torrentsDir, torrentId)
    }
    
    /**
     * Checks if torrent has finished downloading
     */
    suspend fun isTorrentFinished(torrentId: String): Boolean {
        return try {
            val info = getTorrentInfo(torrentId)
            info.isSuccess && info.getOrNull()?.finished == true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Gets torrent file path for streaming
     */
    suspend fun getTorrentFilePath(torrentId: String, fileName: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val torrentDir = getTorrentFilesDirectory(torrentId)
                val file = File(torrentDir, fileName)
                
                if (file.exists()) {
                    Result.success(file.absolutePath)
                } else {
                    Result.failure(Exception("File not found: $fileName"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get torrent file path", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        try {
            scope.cancel()
            
            // Stop all torrents
            activeTorrents.values.forEach { handle ->
                handle.pause()
            }
            
            // Close session
            sessionManager?.stop()
            sessionManager = null
            
            isInitialized.set(false)
            Log.i(TAG, "Torrent service cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}