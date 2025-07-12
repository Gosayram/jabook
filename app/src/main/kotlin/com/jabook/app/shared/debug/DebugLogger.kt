package com.jabook.app.shared.debug

import android.content.Context
import android.util.Log
import com.jabook.app.core.torrent.TorrentEvent
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Interface for debug logging to avoid DI issues with object singleton */
interface IDebugLogger {
    fun logInfo(message: String, tag: String = "JaBook")

    fun logDebug(message: String, tag: String = "JaBook")

    fun logWarning(message: String, tag: String = "JaBook")

    fun logError(message: String, error: Throwable? = null, tag: String = "JaBook")

    fun logNetworkRequest(url: String, method: String, headers: Map<String, String> = emptyMap())

    fun logNetworkResponse(url: String, statusCode: Int, responseTime: Long, size: Long = 0)

    fun logTorrentEvent(event: TorrentEvent)

    fun logPlaybackEvent(event: PlaybackEvent)

    fun logUserAction(action: String, context: String = "")

    fun logPerformance(operation: String, duration: Long, additionalInfo: String = "")

    fun exportLogs(): File?

    fun clearLogs()
}

/** Debug logger implementation that delegates to the singleton */
class DebugLoggerImpl(context: Context) : IDebugLogger {
    init {
        DebugLogger.initialize(context)
    }

    override fun logInfo(message: String, tag: String) = DebugLogger.logInfo(message, tag)

    override fun logDebug(message: String, tag: String) = DebugLogger.logDebug(message, tag)

    override fun logWarning(message: String, tag: String) = DebugLogger.logWarning(message, tag)

    override fun logError(message: String, error: Throwable?, tag: String) = DebugLogger.logError(message, error, tag)

    override fun logNetworkRequest(url: String, method: String, headers: Map<String, String>) =
        DebugLogger.logNetworkRequest(url, method, headers)

    override fun logNetworkResponse(url: String, statusCode: Int, responseTime: Long, size: Long) =
        DebugLogger.logNetworkResponse(url, statusCode, responseTime, size)

    override fun logTorrentEvent(event: TorrentEvent) = DebugLogger.logTorrentEvent(event)

    override fun logPlaybackEvent(event: PlaybackEvent) = DebugLogger.logPlaybackEvent(event)

    override fun logUserAction(action: String, context: String) = DebugLogger.logUserAction(action, context)

    override fun logPerformance(operation: String, duration: Long, additionalInfo: String) =
        DebugLogger.logPerformance(operation, duration, additionalInfo)

    override fun exportLogs(): File? = DebugLogger.exportLogs()

    override fun clearLogs() = DebugLogger.clearLogs()
}

/** Debug logger for comprehensive logging Based on IDEA.md architecture specification */
object DebugLogger {
    private const val TAG = "JaBook"
    private const val LOG_FILE_NAME = "jabook_debug.log"
    private const val MAX_LOG_SIZE = 10 * 1024 * 1024 // 10MB

    private var logFile: File? = null
    private var isInitialized = false
    private val logScope = CoroutineScope(Dispatchers.IO)

    fun initialize(context: Context) {
        if (isInitialized) return

        val logsDir = File(context.filesDir, "logs")
        if (!logsDir.exists()) {
            logsDir.mkdirs()
        }

        logFile = File(logsDir, LOG_FILE_NAME)
        isInitialized = true

        logInfo("DebugLogger initialized")
    }

    fun logInfo(message: String, tag: String = TAG) {
        Log.i(tag, message)
        writeToFile(LogLevel.INFO, tag, message)
    }

    fun logDebug(message: String, tag: String = TAG) {
        if (com.jabook.app.BuildConfig.DEBUG) {
            Log.d(tag, message)
            writeToFile(LogLevel.DEBUG, tag, message)
        }
    }

    fun logWarning(message: String, tag: String = TAG) {
        Log.w(tag, message)
        writeToFile(LogLevel.WARNING, tag, message)
    }

    fun logError(message: String, error: Throwable? = null, tag: String = TAG) {
        Log.e(tag, message, error)
        val errorMessage =
            if (error != null) {
                "$message: ${error.message}\n${error.stackTraceToString()}"
            } else {
                message
            }
        writeToFile(LogLevel.ERROR, tag, errorMessage)
    }

    fun logNetworkRequest(url: String, method: String, headers: Map<String, String> = emptyMap()) {
        val headerString = headers.entries.joinToString(", ") { "${it.key}: ${it.value}" }
        val message = "Network Request: $method $url${if (headerString.isNotEmpty()) " | Headers: $headerString" else ""}"
        logDebug(message, "Network")
    }

    fun logNetworkResponse(url: String, statusCode: Int, responseTime: Long, size: Long = 0) {
        val message = "Network Response: $statusCode $url | Time: ${responseTime}ms${if (size > 0) " | Size: ${size}B" else ""}"
        logDebug(message, "Network")
    }

    fun logTorrentEvent(event: TorrentEvent) {
        val message =
            when (event) {
                is TorrentEvent.TorrentAdded -> "Torrent added: ${event.name} (${event.torrentId})"
                is TorrentEvent.TorrentStarted -> "Torrent started: ${event.name} (${event.torrentId})"
                is TorrentEvent.TorrentCompleted -> "Torrent completed: ${event.name} (${event.torrentId})"
                is TorrentEvent.TorrentError -> "Torrent error: ${event.name} (${event.torrentId}) - ${event.error}"
                is TorrentEvent.TorrentPaused -> "Torrent paused: ${event.name} (${event.torrentId})"
                is TorrentEvent.TorrentResumed -> "Torrent resumed: ${event.name} (${event.torrentId})"
                is TorrentEvent.TorrentRemoved -> "Torrent removed: ${event.name} (${event.torrentId}) files deleted: ${event.filesDeleted}"
                is TorrentEvent.TorrentStatusChanged ->
                    "Torrent status changed: ${event.name} (${event.torrentId}) ${event.oldStatus} -> ${event.newStatus}"
                is TorrentEvent.TorrentProgressUpdated ->
                    "Torrent progress: ${event.name} (${event.torrentId}) ${(event.progress * 100).toInt()}% ${event.downloadSpeed}B/s"
                is TorrentEvent.TorrentMetadataReceived ->
                    "Torrent metadata: ${event.name} (${event.torrentId}) ${event.totalSize}B ${event.audioFileCount} audio files"
                is TorrentEvent.AudioFilesExtracted ->
                    "Audio files extracted: ${event.audiobookId} (${event.torrentId}) ${event.audioFiles.size} files"
                is TorrentEvent.TorrentSeeding ->
                    "Torrent seeding: ${event.name} (${event.torrentId}) ${event.uploadSpeed}B/s ratio: ${event.ratio}"
                is TorrentEvent.TorrentStatsUpdated ->
                    "Torrent stats: ${event.activeTorrents} active, ${event.totalDownloaded}B downloaded, ${event.totalUploaded}B uploaded"
                is TorrentEvent.TorrentEngineInitialized -> "Torrent engine initialized"
                is TorrentEvent.TorrentEngineShutdown -> "Torrent engine shutdown"
                is TorrentEvent.TorrentEngineError -> "Torrent engine error: ${event.error}"
            }
        logInfo(message, "Torrent")
    }

    fun logPlaybackEvent(event: PlaybackEvent) {
        val message =
            when (event) {
                is PlaybackEvent.PlaybackStarted -> "Playback started: ${event.audiobookTitle}"
                is PlaybackEvent.PlaybackPaused -> "Playback paused: ${event.audiobookTitle} at ${event.position}ms"
                is PlaybackEvent.PlaybackStopped -> "Playback stopped: ${event.audiobookTitle}"
                is PlaybackEvent.PlaybackSeek ->
                    "Playback seek: ${event.audiobookTitle} from ${event.fromPosition}ms to ${event.toPosition}ms"
                is PlaybackEvent.PlaybackSpeedChanged -> "Playback speed changed: ${event.audiobookTitle} to ${event.speed}x"
                is PlaybackEvent.PlaybackError -> "Playback error: ${event.audiobookTitle} - ${event.error}"
            }
        logInfo(message, "Playback")
    }

    fun logUserAction(action: String, context: String = "") {
        val message = "User Action: $action${if (context.isNotEmpty()) " | Context: $context" else ""}"
        logInfo(message, "UserAction")
    }

    fun logPerformance(operation: String, duration: Long, additionalInfo: String = "") {
        val message = "Performance: $operation took ${duration}ms${if (additionalInfo.isNotEmpty()) " | $additionalInfo" else ""}"
        logDebug(message, "Performance")
    }

    fun exportLogs(): File? {
        return logFile?.takeIf { it.exists() }
    }

    fun clearLogs() {
        logFile?.delete()
        logInfo("Logs cleared")
    }

    private fun writeToFile(level: LogLevel, tag: String, message: String) {
        if (!isInitialized || logFile == null) return

        logScope.launch {
            try {
                val currentFile = logFile!!

                // Check file size and rotate if needed
                if (currentFile.exists() && currentFile.length() > MAX_LOG_SIZE) {
                    val backupFile = File(currentFile.parentFile, "${LOG_FILE_NAME}.bak")
                    if (backupFile.exists()) {
                        backupFile.delete()
                    }
                    currentFile.renameTo(backupFile)
                }

                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                val logEntry = "$timestamp [${level.name}] [$tag] $message\n"

                FileWriter(currentFile, true).use { writer ->
                    writer.write(logEntry)
                    writer.flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write to log file", e)
            }
        }
    }

    enum class LogLevel {
        DEBUG,
        INFO,
        WARNING,
        ERROR,
    }
}

/** Playback events for debugging */
sealed class PlaybackEvent {
    data class PlaybackStarted(val audiobookTitle: String) : PlaybackEvent()

    data class PlaybackPaused(val audiobookTitle: String, val position: Long) : PlaybackEvent()

    data class PlaybackStopped(val audiobookTitle: String) : PlaybackEvent()

    data class PlaybackSeek(val audiobookTitle: String, val fromPosition: Long, val toPosition: Long) : PlaybackEvent()

    data class PlaybackSpeedChanged(val audiobookTitle: String, val speed: Float) : PlaybackEvent()

    data class PlaybackError(val audiobookTitle: String, val error: String) : PlaybackEvent()
}

/** Debug panel interface for development */
interface DebugPanel {
    fun showNetworkLogs()

    fun showTorrentStats()

    fun showPlaybackLogs()

    fun showPerformanceMetrics()

    fun clearCache()

    fun exportLogs(): File?

    fun getAppInfo(): AppInfo
}

/** App information for debugging */
data class AppInfo(
    val appVersion: String,
    val buildType: String,
    val deviceInfo: DeviceInfo,
    val storageInfo: StorageInfo,
    val networkInfo: NetworkInfo,
)

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val architecture: String,
    val totalMemory: Long,
    val availableMemory: Long,
)

data class StorageInfo(val totalSpace: Long, val availableSpace: Long, val usedSpace: Long, val appDataSize: Long)

data class NetworkInfo(val connectionType: String, val isConnected: Boolean, val isMetered: Boolean)

/** Performance metrics tracker */
object PerformanceTracker {
    private val metrics = mutableMapOf<String, MutableList<Long>>()

    fun startMeasurement(operation: String): Long {
        return System.currentTimeMillis()
    }

    fun endMeasurement(operation: String, startTime: Long, additionalInfo: String = "") {
        val duration = System.currentTimeMillis() - startTime

        // Track metrics
        metrics.getOrPut(operation) { mutableListOf() }.add(duration)

        // Log performance
        DebugLogger.logPerformance(operation, duration, additionalInfo)
    }

    fun getAverageTime(operation: String): Long {
        return metrics[operation]?.average()?.toLong() ?: 0
    }

    fun getMetrics(): Map<String, List<Long>> {
        return metrics.toMap()
    }

    fun clearMetrics() {
        metrics.clear()
    }
}

/** Inline function for easy performance measurement */
inline fun <T> measurePerformance(operation: String, additionalInfo: String = "", block: () -> T): T {
    val startTime = PerformanceTracker.startMeasurement(operation)
    return try {
        block()
    } finally {
        PerformanceTracker.endMeasurement(operation, startTime, additionalInfo)
    }
}
