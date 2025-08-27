package com.jabook.core.logging

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

/**
 * Advanced logging system for JaBook app
 * Provides NDJSON logging with rotation, multiple sinks, and sharing capabilities
 */
class AppLog private constructor(private val context: Context) {
    
    private val TAG = "AppLog"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Log levels
    enum class Level(val value: Int) {
        TRACE(0),
        DEBUG(1),
        INFO(2),
        WARN(3),
        ERROR(4)
    }
    
    // Log entry data class
    data class LogEntry(
        val timestamp: Long,
        val level: Level,
        val subsystem: String,
        val message: String,
        val thread: String = Thread.currentThread().name,
        val cause: String? = null,
        val throwable: Throwable? = null
    )
    
    // Configuration
    data class Config(
        val maxFileSize: Long = 10 * 1024 * 1024, // 10MB
        val maxFiles: Int = 5,
        val logLevel: Level = if (BuildConfig.DEBUG) Level.DEBUG else Level.INFO,
        val enableLogcat: Boolean = true,
        val enableFileLogging: Boolean = true,
        val enableSharing: Boolean = true,
        val logDirectory: String = "logs"
    )
    
    private var config = Config()
    private val isInitialized = AtomicBoolean(false)
    
    // Log file management
    private val logFiles = mutableListOf<File>()
    private val currentLogFile = AtomicLong(0)
    private val logFileMutex = Mutex()
    
    // Log queue for batching
    private val logQueue = mutableListOf<LogEntry>()
    private val logQueueMutex = Mutex()
    private val isWriting = AtomicBoolean(false)
    
    // Subsystem registry
    private val subsystems = mutableSetOf<String>()
    
    init {
        initialize()
    }
    
    /**
     * Initializes the logging system
     */
    private fun initialize() {
        if (isInitialized.get()) return
        
        try {
            // Create log directory
            val logDir = File(context.filesDir, config.logDirectory)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            
            // Load existing log files
            loadLogFiles(logDir)
            
            // Start log writer coroutine
            scope.launch {
                logWriter()
            }
            
            // Register shutdown hook
            Runtime.getRuntime().addShutdownHook(Thread {
                flush()
            })
            
            isInitialized.set(true)
            Log.i(TAG, "Logging system initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize logging system", e)
        }
    }
    
    /**
     * Loads existing log files
     */
    private fun loadLogFiles(logDir: File) {
        logFiles.clear()
        val logPattern = "^jabook-\\d{8}\\.log$".toRegex()
        
        logDir.listFiles { file ->
            file.isFile && logPattern.matches(file.name)
        }?.sortedByDescending { it.lastModified() }?.let { files ->
            logFiles.addAll(files.take(config.maxFiles))
        }
    }
    
    /**
     * Logs a message with the specified level
     */
    fun log(
        level: Level,
        subsystem: String,
        message: String,
        throwable: Throwable? = null,
        vararg args: Any
    ) {
        if (level.value < config.logLevel.value) return
        
        try {
            val formattedMessage = if (args.isNotEmpty()) {
                String.format(message, *args)
            } else {
                message
            }
            
            val entry = LogEntry(
                timestamp = System.currentTimeMillis(),
                level = level,
                subsystem = subsystem,
                message = formattedMessage,
                thread = Thread.currentThread().name,
                cause = throwable?.message,
                throwable = throwable
            )
            
            // Add to subsystem registry
            subsystems.add(subsystem)
            
            // Add to queue for batching
            scope.launch {
                logQueueMutex.withLock {
                    logQueue.add(entry)
                    if (logQueue.size >= 100) {
                        flushQueue()
                    }
                }
            }
            
            // Also log to logcat if enabled
            if (config.enableLogcat) {
                val logTag = if (subsystem.length <= 23) subsystem else subsystem.substring(0, 23)
                val logMessage = "[${entry.thread}] $formattedMessage"
                
                when (level) {
                    Level.TRACE, Level.DEBUG -> Log.d(logTag, logMessage, throwable)
                    Level.INFO -> Log.i(logTag, logMessage, throwable)
                    Level.WARN -> Log.w(logTag, logMessage, throwable)
                    Level.ERROR -> Log.e(logTag, logMessage, throwable)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log message", e)
        }
    }
    
    /**
     * Convenience methods for different log levels
     */
    fun trace(subsystem: String, message: String, throwable: Throwable? = null, vararg args: Any) {
        log(Level.TRACE, subsystem, message, throwable, *args)
    }
    
    fun debug(subsystem: String, message: String, throwable: Throwable? = null, vararg args: Any) {
        log(Level.DEBUG, subsystem, message, throwable, *args)
    }
    
    fun info(subsystem: String, message: String, throwable: Throwable? = null, vararg args: Any) {
        log(Level.INFO, subsystem, message, throwable, *args)
    }
    
    fun warn(subsystem: String, message: String, throwable: Throwable? = null, vararg args: Any) {
        log(Level.WARN, subsystem, message, throwable, *args)
    }
    
    fun error(subsystem: String, message: String, throwable: Throwable? = null, vararg args: Any) {
        log(Level.ERROR, subsystem, message, throwable, *args)
    }
    
    /**
     * Gets logs filtered by criteria
     */
    suspend fun getLogs(
        level: Level? = null,
        subsystem: String? = null,
        since: Long? = null,
        limit: Int = 1000
    ): List<LogEntry> {
        return withContext(Dispatchers.IO) {
            val entries = mutableListOf<LogEntry>()
            
            logFiles.forEach { file ->
                try {
                    file.source().buffer().use { source ->
                        while (true) {
                            val line = source.readUtf8Line() ?: break
                            val entry = parseLogEntry(line) ?: continue
                            
                            // Apply filters
                            if (level != null && entry.level != level) continue
                            if (subsystem != null && entry.subsystem != subsystem) continue
                            if (since != null && entry.timestamp < since) continue
                            
                            entries.add(entry)
                            if (entries.size >= limit) break
                        }
                    }
                    
                    if (entries.size >= limit) break
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read log file: ${file.name}", e)
                }
            }
            
            entries.sortedByDescending { it.timestamp }.take(limit)
        }
    }
    
    /**
     * Clears all log files
     */
    suspend fun clearLogs() {
        withContext(Dispatchers.IO) {
            logFileMutex.withLock {
                logFiles.forEach { file ->
                    try {
                        file.delete()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete log file: ${file.name}", e)
                    }
                }
                logFiles.clear()
                currentLogFile.set(0)
            }
        }
    }
    
    /**
     * Shares logs as a ZIP file
     */
    suspend fun shareLogs(): Intent? {
        if (!config.enableSharing) return null
        
        return withContext(Dispatchers.IO) {
            try {
                val zipFile = createZipArchive()
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, android.net.Uri.fromFile(zipFile))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                intent
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create log archive", e)
                null
            }
        }
    }
    
    /**
     * Gets log statistics
     */
    suspend fun getLogStats(): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            val stats = mutableMapOf<String, Any>()
            
            // Count logs by level
            val levelCounts = mutableMapOf<String, Int>()
            var totalLogs = 0
            
            logFiles.forEach { file ->
                try {
                    file.source().buffer().use { source ->
                        while (true) {
                            val line = source.readUtf8Line() ?: break
                            val entry = parseLogEntry(line) ?: continue
                            
                            val levelKey = entry.level.name
                            levelCounts[levelKey] = (levelCounts[levelKey] ?: 0) + 1
                            totalLogs++
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read log file for stats: ${file.name}", e)
                }
            }
            
            stats["totalLogs"] = totalLogs
            stats["levelCounts"] = levelCounts
            stats["subsystems"] = subsystems.size
            stats["logFiles"] = logFiles.size
            stats["oldestLog"] = logFiles.minOfOrNull { it.lastModified() } ?: 0L
            stats["newestLog"] = logFiles.maxOfOrNull { it.lastModified() } ?: 0L
            
            stats
        }
    }
    
    /**
     * Updates configuration
     */
    fun updateConfig(newConfig: Config) {
        config = newConfig
        info("AppLog", "Configuration updated: $config")
    }
    
    /**
     * Flushes all pending logs
     */
    fun flush() {
        scope.launch {
            flushQueue()
        }
    }
    
    /**
     * Log writer coroutine
     */
    private suspend fun logWriter() {
        while (isActive) {
            try {
                delay(1000) // Write every second
                
                logQueueMutex.withLock {
                    if (logQueue.isNotEmpty() && !isWriting.get()) {
                        isWriting.set(true)
                        flushQueue()
                        isWriting.set(false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in log writer", e)
            }
        }
    }
    
    /**
     * Flushes the log queue to file
     */
    private suspend fun flushQueue() {
        logFileMutex.withLock {
            val entriesToWrite = logQueueMutex.withLock {
                val entries = logQueue.toList()
                logQueue.clear()
                entries
            }
            
            if (entriesToWrite.isEmpty()) return
            
            val currentFile = getCurrentLogFile()
            val writer = currentFile.appendSink().buffer()
            
            entriesToWrite.forEach { entry ->
                writer.writeUtf8(formatLogEntry(entry))
                writer.writeUtf8("\n")
            }
            
            writer.close()
            
            // Check if rotation is needed
            if (currentFile.length() > config.maxFileSize) {
                rotateLogFile()
            }
        }
    }
    
    /**
     * Gets the current log file
     */
    private suspend fun getCurrentLogFile(): File {
        return logFileMutex.withLock {
            val logDir = File(context.filesDir, config.logDirectory)
            val fileName = "jabook-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.log"
            val file = File(logDir, fileName)
            
            if (!file.exists()) {
                file.createNewFile()
                if (logFiles.size >= config.maxFiles) {
                    logFiles.removeAt(logFiles.size - 1).delete()
                }
                logFiles.add(0, file)
            }
            
            file
        }
    }
    
    /**
     * Rotates log files
     */
    private suspend fun rotateLogFile() {
        logFileMutex.withLock {
            val logDir = File(context.filesDir, config.logDirectory)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val newFileName = "jabook-rotated-$timestamp.log"
            
            val currentFile = getCurrentLogFile()
            val rotatedFile = File(logDir, newFileName)
            
            currentFile.renameTo(rotatedFile)
            
            if (logFiles.size >= config.maxFiles) {
                logFiles.removeAt(logFiles.size - 1).delete()
            }
            logFiles.add(0, rotatedFile)
            
            // Create new current file
            getCurrentLogFile()
        }
    }
    
    /**
     * Creates a ZIP archive of all logs
     */
    private suspend fun createZipArchive(): File {
        return withContext(Dispatchers.IO) {
            val zipFile = File(context.cacheDir, "jabook-logs-${System.currentTimeMillis()}.zip")
            
            java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zipOut ->
                logFiles.forEach { file ->
                    if (file.exists()) {
                        zipOut.putNextEntry(java.util.zip.ZipEntry(file.name))
                        file.inputStream().use { input ->
                            input.copyTo(zipOut)
                        }
                        zipOut.closeEntry()
                    }
                }
            }
            
            zipFile
        }
    }
    
    /**
     * Formats a log entry as NDJSON
     */
    private fun formatLogEntry(entry: LogEntry): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(entry.timestamp))
        
        val json = mapOf(
            "ts" to timestamp,
            "level" to entry.level.name.lowercase(),
            "subsystem" to entry.subsystem,
            "msg" to entry.message,
            "thread" to entry.thread
        )
        
        if (entry.cause != null) {
            json.plus("cause" to entry.cause)
        }
        
        return org.json.JSONObject(json).toString()
    }
    
    /**
     * Parses a log entry from NDJSON
     */
    private fun parseLogEntry(line: String): LogEntry? {
        return try {
            val json = org.json.JSONObject(line)
            val level = Level.valueOf(json.getString("level").uppercase())
            
            LogEntry(
                timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .parse(json.getString("ts"))?.time ?: 0L,
                level = level,
                subsystem = json.getString("subsystem"),
                message = json.getString("msg"),
                thread = json.optString("thread", "unknown"),
                cause = json.optString("cause", null)
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        try {
            scope.cancel()
            flush()
            isInitialized.set(false)
            Log.i(TAG, "Logging system cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
    
    companion object {
        @Volatile
        private var instance: AppLog? = null
        
        /**
         * Gets the singleton instance
         */
        fun getInstance(context: Context): AppLog {
            return instance ?: synchronized(this) {
                instance ?: AppLog(context.applicationContext).also { instance = it }
            }
        }
        
        /**
         * Gets the singleton instance (for use in contexts where application context is available)
         */
        fun getInstance(): AppLog {
            return instance ?: throw IllegalStateException("AppLog not initialized")
        }
    }
}