package com.jabook.app.core.storage

import android.content.Context
import android.os.StatFs
import android.text.TextUtils
import com.jabook.app.core.domain.model.Audiobook
import com.jabook.app.shared.debug.IDebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/** Implementation of file manager for audiobook storage Handles directory structure according to IDEA.md specification */
@Singleton
class FileManagerImpl @Inject constructor(@ApplicationContext private val context: Context, private val debugLogger: IDebugLogger) :
    FileManager {

    companion object {
        private const val TAG = "FileManager"
        private const val AUDIOBOOKS_DIR = "audiobooks"
        private const val TEMP_DIR = "temp"
        private const val CACHE_DIR = "cache"
        private const val LOGS_DIR = "logs"
        private const val AUDIO_DIR = "audio"
        private const val METADATA_FILE = "metadata.json"
        private const val COVER_FILE = "cover.jpg"

        // Supported audio file extensions
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "flac", "wav", "wma", "opus")

        // Pattern for safe file names (remove invalid characters)
        private val SAFE_FILENAME_PATTERN = Pattern.compile("[^a-zA-Z0-9._\\-\\s]")
    }

    private val appDataDir: File by lazy { context.getExternalFilesDir(null) ?: context.filesDir }

    override suspend fun initialize() {
        debugLogger.logInfo("Initializing FileManager", TAG)

        try {
            withContext(Dispatchers.IO) {
                // Create main directories
                val directories = listOf(getAudiobooksDirectory(), getTempDirectory(), getCacheDirectory(), getLogsDirectory())

                directories.forEach { dir ->
                    if (!dir.exists()) {
                        val created = dir.mkdirs()
                        debugLogger.logInfo("Created directory: ${dir.absolutePath} - success: $created", TAG)
                    }
                }

                // Create .nomedia file in each directory to prevent media scanning
                directories.forEach { dir ->
                    val nomediaFile = File(dir, ".nomedia")
                    if (!nomediaFile.exists()) {
                        try {
                            nomediaFile.createNewFile()
                            debugLogger.logDebug("Created .nomedia file in: ${dir.absolutePath}", TAG)
                        } catch (e: Exception) {
                            debugLogger.logWarning("Failed to create .nomedia file in: ${dir.absolutePath}", TAG)
                        }
                    }
                }
            }

            debugLogger.logInfo("FileManager initialized successfully", TAG)
        } catch (e: Exception) {
            debugLogger.logError("Failed to initialize FileManager", e, TAG)
            throw e
        }
    }

    override fun getAudiobooksDirectory(): File {
        return File(appDataDir, AUDIOBOOKS_DIR)
    }

    override fun getAudiobookDirectory(audiobook: Audiobook): File {
        val safeAuthor = sanitizeFileName(audiobook.author)
        val safeTitle = sanitizeFileName(audiobook.title)
        return File(getAudiobooksDirectory(), "$safeAuthor/$safeTitle")
    }

    override fun getAudiobookAudioDirectory(audiobook: Audiobook): File {
        return File(getAudiobookDirectory(audiobook), AUDIO_DIR)
    }

    override fun getAudiobookMetadataFile(audiobook: Audiobook): File {
        return File(getAudiobookDirectory(audiobook), METADATA_FILE)
    }

    override fun getAudiobookCoverFile(audiobook: Audiobook): File {
        return File(getAudiobookDirectory(audiobook), COVER_FILE)
    }

    override fun getTempDirectory(): File {
        return File(appDataDir, TEMP_DIR)
    }

    override fun getCacheDirectory(): File {
        return File(appDataDir, CACHE_DIR)
    }

    override fun getLogsDirectory(): File {
        return File(appDataDir, LOGS_DIR)
    }

    override suspend fun createAudiobookDirectories(audiobook: Audiobook): Boolean {
        debugLogger.logInfo("Creating directories for audiobook: ${audiobook.title}", TAG)

        return withContext(Dispatchers.IO) {
            try {
                val audioBookDir = getAudiobookDirectory(audiobook)
                val audioDir = getAudiobookAudioDirectory(audiobook)

                val mainDirCreated = audioBookDir.mkdirs() || audioBookDir.exists()
                val audioDirCreated = audioDir.mkdirs() || audioDir.exists()

                if (mainDirCreated && audioDirCreated) {
                    debugLogger.logInfo("Successfully created directories for: ${audiobook.title}", TAG)
                    true
                } else {
                    debugLogger.logError("Failed to create directories for: ${audiobook.title}", null, TAG)
                    false
                }
            } catch (e: Exception) {
                debugLogger.logError("Exception creating directories for: ${audiobook.title}", e, TAG)
                false
            }
        }
    }

    override suspend fun isAudiobookDownloaded(audiobook: Audiobook): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val audioFiles = getAudiobookAudioFiles(audiobook)
                val hasAudioFiles = audioFiles.isNotEmpty()

                debugLogger.logDebug("Audiobook ${audiobook.title} downloaded: $hasAudioFiles (${audioFiles.size} files)", TAG)
                hasAudioFiles
            } catch (e: Exception) {
                debugLogger.logError("Failed to check if audiobook is downloaded: ${audiobook.title}", e, TAG)
                false
            }
        }
    }

    override suspend fun getAudiobookAudioFiles(audiobook: Audiobook): List<File> {
        return withContext(Dispatchers.IO) {
            try {
                val audioDir = getAudiobookAudioDirectory(audiobook)
                if (!audioDir.exists()) {
                    return@withContext emptyList()
                }

                val audioFiles =
                    audioDir.listFiles { file -> file.isFile && file.extension.lowercase() in AUDIO_EXTENSIONS }?.toList() ?: emptyList()

                // Sort by filename to maintain consistent order
                audioFiles.sortedBy { it.name }
            } catch (e: Exception) {
                debugLogger.logError("Failed to get audio files for: ${audiobook.title}", e, TAG)
                emptyList()
            }
        }
    }

    override suspend fun getStorageInfo(): AudiobookStorageInfo {
        return withContext(Dispatchers.IO) {
            try {
                val stat = StatFs(appDataDir.absolutePath)
                val bytesAvailable = stat.blockCountLong * stat.blockSizeLong
                val bytesFree = stat.availableBlocksLong * stat.blockSizeLong
                val bytesUsed = bytesAvailable - bytesFree

                val audiobooksSize = calculateDirectorySize(getAudiobooksDirectory())
                val tempSize = calculateDirectorySize(getTempDirectory())
                val cacheSize = calculateDirectorySize(getCacheDirectory())
                val logsSize = calculateDirectorySize(getLogsDirectory())

                AudiobookStorageInfo(
                    totalSpace = bytesAvailable,
                    freeSpace = bytesFree,
                    usedSpace = bytesUsed,
                    audiobooksSize = audiobooksSize,
                    tempSize = tempSize,
                    cacheSize = cacheSize,
                    logsSize = logsSize,
                )
            } catch (e: Exception) {
                debugLogger.logError("Failed to get storage info", e, TAG)
                AudiobookStorageInfo(0, 0, 0, 0, 0, 0, 0)
            }
        }
    }

    override suspend fun cleanupTempFiles() {
        debugLogger.logInfo("Cleaning up temporary files", TAG)

        withContext(Dispatchers.IO) {
            try {
                val tempDir = getTempDirectory()
                if (tempDir.exists()) {
                    val deletedFiles = deleteDirectory(tempDir)
                    tempDir.mkdirs() // Recreate the directory
                    debugLogger.logInfo("Cleaned up $deletedFiles temporary files", TAG)
                }
            } catch (e: Exception) {
                debugLogger.logError("Failed to cleanup temp files", e, TAG)
            }
        }
    }

    override suspend fun cleanupOldCacheFiles(olderThanDays: Int) {
        debugLogger.logInfo("Cleaning up cache files older than $olderThanDays days", TAG)

        withContext(Dispatchers.IO) {
            try {
                val cacheDir = getCacheDirectory()
                if (!cacheDir.exists()) return@withContext

                val cutoffTime = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)
                var deletedFiles = 0

                cacheDir.walkTopDown().forEach { file ->
                    if (file.isFile && file.lastModified() < cutoffTime) {
                        if (file.delete()) {
                            deletedFiles++
                        }
                    }
                }

                debugLogger.logInfo("Cleaned up $deletedFiles old cache files", TAG)
            } catch (e: Exception) {
                debugLogger.logError("Failed to cleanup old cache files", e, TAG)
            }
        }
    }

    override suspend fun deleteAudiobook(audiobook: Audiobook): Boolean {
        debugLogger.logInfo("Deleting audiobook: ${audiobook.title}", TAG)

        return withContext(Dispatchers.IO) {
            try {
                val audiobookDir = getAudiobookDirectory(audiobook)
                if (audiobookDir.exists()) {
                    val deletedFiles = deleteDirectory(audiobookDir)
                    debugLogger.logInfo("Deleted audiobook ${audiobook.title} - $deletedFiles files removed", TAG)
                    true
                } else {
                    debugLogger.logWarning("Audiobook directory does not exist: ${audiobook.title}", TAG)
                    false
                }
            } catch (e: Exception) {
                debugLogger.logError("Failed to delete audiobook: ${audiobook.title}", e, TAG)
                false
            }
        }
    }

    override suspend fun moveAudiobook(audiobook: Audiobook, newLocation: File): Boolean {
        debugLogger.logInfo("Moving audiobook ${audiobook.title} to: ${newLocation.absolutePath}", TAG)

        return withContext(Dispatchers.IO) {
            try {
                val currentDir = getAudiobookDirectory(audiobook)
                if (!currentDir.exists()) {
                    debugLogger.logError("Source directory does not exist: ${currentDir.absolutePath}", null, TAG)
                    return@withContext false
                }

                val success = currentDir.renameTo(newLocation)

                if (success) {
                    debugLogger.logInfo("Successfully moved audiobook: ${audiobook.title}", TAG)
                } else {
                    debugLogger.logError("Failed to move audiobook: ${audiobook.title}", null, TAG)
                }

                success
            } catch (e: Exception) {
                debugLogger.logError("Exception moving audiobook: ${audiobook.title}", e, TAG)
                false
            }
        }
    }

    override suspend fun getAudiobookSize(audiobook: Audiobook): Long {
        return withContext(Dispatchers.IO) {
            try {
                val audiobookDir = getAudiobookDirectory(audiobook)
                calculateDirectorySize(audiobookDir)
            } catch (e: Exception) {
                debugLogger.logError("Failed to get audiobook size: ${audiobook.title}", e, TAG)
                0L
            }
        }
    }

    override fun watchFileSystemChanges(): Flow<FileSystemEvent> = flow {
        // TODO: Implement file system monitoring with FileObserver
        // For now, return empty flow
        debugLogger.logInfo("File system monitoring not implemented yet", TAG)
    }

    override suspend fun validateFileSystem(): FileSystemValidationResult {
        debugLogger.logInfo("Validating file system", TAG)

        return withContext(Dispatchers.IO) {
            try {
                val issues = mutableListOf<FileSystemIssue>()
                val fixedIssues = mutableListOf<FileSystemIssue>()

                // Check main directories
                val mainDirs = listOf(getAudiobooksDirectory(), getTempDirectory(), getCacheDirectory(), getLogsDirectory())

                mainDirs.forEach { dir ->
                    if (!dir.exists()) {
                        val issue =
                            FileSystemIssue(
                                type = IssueType.MISSING_DIRECTORY,
                                description = "Missing directory: ${dir.absolutePath}",
                                file = dir,
                                severity = IssueSeverity.HIGH,
                                canAutoFix = true,
                            )

                        if (dir.mkdirs()) {
                            fixedIssues.add(issue)
                        } else {
                            issues.add(issue)
                        }
                    }
                }

                // Check storage space
                val storageInfo = getStorageInfo()
                if (storageInfo.freeSpacePercentage < 5.0f) {
                    issues.add(
                        FileSystemIssue(
                            type = IssueType.DISK_FULL,
                            description = "Low disk space: ${storageInfo.freeSpacePercentage}% free",
                            file = null,
                            severity = IssueSeverity.CRITICAL,
                            canAutoFix = false,
                        )
                    )
                }

                FileSystemValidationResult(isValid = issues.isEmpty(), issues = issues, fixedIssues = fixedIssues)
            } catch (e: Exception) {
                debugLogger.logError("Failed to validate file system", e, TAG)
                FileSystemValidationResult(
                    isValid = false,
                    issues =
                        listOf(
                            FileSystemIssue(
                                type = IssueType.PERMISSION_DENIED,
                                description = "Failed to validate file system: ${e.message}",
                                file = null,
                                severity = IssueSeverity.CRITICAL,
                                canAutoFix = false,
                            )
                        ),
                    fixedIssues = emptyList(),
                )
            }
        }
    }

    override suspend fun exportAudiobook(audiobook: Audiobook, destinationPath: String): Boolean {
        debugLogger.logInfo("Exporting audiobook ${audiobook.title} to: $destinationPath", TAG)

        return withContext(Dispatchers.IO) {
            try {
                val sourceDir = getAudiobookDirectory(audiobook)
                val destDir = File(destinationPath)

                if (!sourceDir.exists()) {
                    debugLogger.logError("Source audiobook directory does not exist: ${audiobook.title}", null, TAG)
                    return@withContext false
                }

                val success = copyDirectory(sourceDir, destDir)

                if (success) {
                    debugLogger.logInfo("Successfully exported audiobook: ${audiobook.title}", TAG)
                } else {
                    debugLogger.logError("Failed to export audiobook: ${audiobook.title}", null, TAG)
                }

                success
            } catch (e: Exception) {
                debugLogger.logError("Exception exporting audiobook: ${audiobook.title}", e, TAG)
                false
            }
        }
    }

    override suspend fun importAudiobook(sourcePath: String, audiobook: Audiobook): Boolean {
        debugLogger.logInfo("Importing audiobook from: $sourcePath", TAG)

        return withContext(Dispatchers.IO) {
            try {
                val sourceDir = File(sourcePath)
                val destDir = getAudiobookDirectory(audiobook)

                if (!sourceDir.exists()) {
                    debugLogger.logError("Source directory does not exist: $sourcePath", null, TAG)
                    return@withContext false
                }

                // Create destination directories
                if (!createAudiobookDirectories(audiobook)) {
                    debugLogger.logError("Failed to create destination directories for: ${audiobook.title}", null, TAG)
                    return@withContext false
                }

                val success = copyDirectory(sourceDir, destDir)

                if (success) {
                    debugLogger.logInfo("Successfully imported audiobook: ${audiobook.title}", TAG)
                } else {
                    debugLogger.logError("Failed to import audiobook: ${audiobook.title}", null, TAG)
                }

                success
            } catch (e: Exception) {
                debugLogger.logError("Exception importing audiobook: ${audiobook.title}", e, TAG)
                false
            }
        }
    }

    // Helper methods

    private fun sanitizeFileName(filename: String): String {
        if (TextUtils.isEmpty(filename)) return "Unknown"

        // Replace invalid characters with underscores
        val sanitized = SAFE_FILENAME_PATTERN.matcher(filename).replaceAll("_")

        // Limit length and trim whitespace
        return sanitized.trim().take(100).ifEmpty { "Unknown" }
    }

    private fun calculateDirectorySize(directory: File): Long {
        if (!directory.exists()) return 0L

        return try {
            directory.walkTopDown().sumOf { file -> if (file.isFile) file.length() else 0L }
        } catch (e: Exception) {
            debugLogger.logWarning("Failed to calculate directory size: ${directory.absolutePath}", TAG)
            0L
        }
    }

    private fun deleteDirectory(directory: File): Int {
        if (!directory.exists()) return 0

        var deletedFiles = 0

        try {
            directory.walkBottomUp().forEach { file ->
                if (file.delete()) {
                    deletedFiles++
                }
            }
        } catch (e: Exception) {
            debugLogger.logError("Failed to delete directory: ${directory.absolutePath}", e, TAG)
        }

        return deletedFiles
    }

    private fun copyDirectory(source: File, destination: File): Boolean {
        if (!source.exists()) return false

        try {
            if (source.isDirectory) {
                if (!destination.exists()) {
                    destination.mkdirs()
                }

                source.listFiles()?.forEach { file ->
                    val destFile = File(destination, file.name)
                    if (!copyDirectory(file, destFile)) {
                        return false
                    }
                }
            } else {
                source.copyTo(destination, overwrite = true)
            }

            return true
        } catch (e: Exception) {
            debugLogger.logError("Failed to copy directory: ${source.absolutePath}", e, TAG)
            return false
        }
    }
}
