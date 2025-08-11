package com.jabook.app.shared.debug

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import com.jabook.app.BuildConfig
import com.jabook.app.core.torrent.TorrentEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private const val LOG_FOLDER_NAME = "JabookLogs"
    private const val LOG_FILE_NAME = "debug_log.txt"
    private const val MAX_LOG_SIZE = 10 * 1024 * 1024 // 10MB

    private var logFile: File? = null
    private var isInitialized = false
    private val logScope = CoroutineScope(Dispatchers.IO)
    private var appContext: Context? = null

    fun initialize(context: Context) {
        if (isInitialized) return

        appContext = context.applicationContext
        try {
            val externalStorage = Environment.getExternalStorageDirectory()
            val version = BuildConfig.VERSION_NAME
            val logDir = File(externalStorage, "$LOG_FOLDER_NAME/$version")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            logFile = File(logDir, LOG_FILE_NAME)
            logInfo("DebugLogger initialized (public external storage, versioned)")
        } catch (e: Exception) {
            // Fallback: use internal storage if external is not available
            logFile = File(context.filesDir, LOG_FILE_NAME)
            logInfo("DebugLogger fallback to internal storage: ${e.message}")
        }
        isInitialized = true
        val storageType =
            if (logFile?.path?.contains("files") == true) {
                "internal"
            } else {
                "external"
            }
        logInfo("DebugLogger init complete. Using $storageType storage", TAG)
    }

    fun logInfo(message: String, tag: String = TAG) {
        Log.i(tag, message)
        writeToFile(LogLevel.INFO, tag, message)
    }

    fun logDebug(message: String, tag: String = TAG) {
        Log.d(tag, message)
        writeToFile(LogLevel.DEBUG, tag, message)
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
        val message = TorrentEventFormatter.formatTorrentEventMessage(event)
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
        if (!isInitialized) return

        val context = appContext ?: return
        val prefs: SharedPreferences = context.getSharedPreferences("jabook_prefs", Context.MODE_PRIVATE)
        val logFolderUriString = prefs.getString("log_folder_uri", null)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val logEntry = "$timestamp [${level.name}] [$tag] $message\n"

        if (logFolderUriString != null) {
            try {
                val logFolderUri = Uri.parse(logFolderUriString)
                val contentResolver = context.contentResolver
                val logFileName = LOG_FILE_NAME

                // Find or create the log file in the selected folder
                val logFileUri = findOrCreateLogFile(contentResolver, logFolderUri, logFileName)
                if (logFileUri != null) {
                    // Append log entry to the file
                    contentResolver.openOutputStream(logFileUri, "wa")?.use { outputStream ->
                        outputStream.write(logEntry.toByteArray())
                        outputStream.flush()
                    }
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write log to SAF folder", e)
            }
        }

        // Fallback: write to file in public or internal storage
        val file = logFile ?: return
        logScope.launch {
            try {
                val currentFile = file
                if (currentFile.exists() && currentFile.length() > MAX_LOG_SIZE) {
                    val backupFile = File(currentFile.parentFile, "${LOG_FILE_NAME}.bak")
                    if (backupFile.exists()) {
                        backupFile.delete()
                    }
                    currentFile.renameTo(backupFile)
                }
                FileWriter(currentFile, true).use { writer ->
                    writer.write(logEntry)
                    writer.flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write to log file", e)
            }
        }
    }

    /**
     * Find or create a log file in the SAF folder. Returns the file Uri.
     */
    private fun findOrCreateLogFile(contentResolver: ContentResolver, folderUri: Uri, fileName: String): Uri? {
        // Try to find the file first
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, DocumentsContract.getTreeDocumentId(folderUri))
        val cursor = contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )
        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(1)
                if (name == fileName) {
                    val documentId = it.getString(0)
                    return DocumentsContract.buildDocumentUriUsingTree(folderUri, documentId)
                }
            }
        }
        // If not found, create the file
        val values = ContentValues().apply {
            put(DocumentsContract.Document.COLUMN_DISPLAY_NAME, fileName)
            put(DocumentsContract.Document.COLUMN_MIME_TYPE, "text/plain")
        }
        return DocumentsContract.createDocument(contentResolver, folderUri, "text/plain", fileName)
    }

    enum class LogLevel {
        DEBUG,
        INFO,
        WARNING,
        ERROR,
    }

    private val values: List<String> = emptyList() // Unused property
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

    fun startMeasurement(@Suppress("UNUSED_PARAMETER") operation: String): Long {
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
