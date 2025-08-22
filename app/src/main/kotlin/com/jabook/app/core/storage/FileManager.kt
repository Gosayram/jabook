package com.jabook.app.core.storage

import com.jabook.app.core.domain.model.Audiobook
import kotlinx.coroutines.flow.Flow
import java.io.File

/** File manager interface for audiobook storage and organization Based on IDEA.md file structure specification */
interface FileManager {
    /** Initialize file manager and create necessary directories */
    suspend fun initialize()

    /** Get root directory for audiobooks /Android/data/com.jabook.app/files/audiobooks/ */
    fun getAudiobooksDirectory(): File

    /** Get directory for specific audiobook /Android/data/com.jabook.app/files/audiobooks/[AuthorName]/[BookTitle]/ */
    fun getAudiobookDirectory(audiobook: Audiobook): File

    /** Get directory for audiobook audio files /Android/data/com.jabook.app/files/audiobooks/[AuthorName]/[BookTitle]/audio/ */
    fun getAudiobookAudioDirectory(audiobook: Audiobook): File

    /** Get path for audiobook metadata file /Android/data/com.jabook.app/files/audiobooks/[AuthorName]/[BookTitle]/metadata.json */
    fun getAudiobookMetadataFile(audiobook: Audiobook): File

    /** Get path for audiobook cover file /Android/data/com.jabook.app/files/audiobooks/[AuthorName]/[BookTitle]/cover.jpg */
    fun getAudiobookCoverFile(audiobook: Audiobook): File

    /** Get temporary directory for downloads /Android/data/com.jabook.app/files/temp/ */
    fun getTempDirectory(): File

    /** Get cache directory for covers and metadata /Android/data/com.jabook.app/files/cache/ */
    fun getCacheDirectory(): File

    /** Get logs directory for debug information /Android/data/com.jabook.app/files/logs/ */
    fun getLogsDirectory(): File

    /** Create directory structure for audiobook */
    suspend fun createAudiobookDirectories(audiobook: Audiobook): Boolean

    /** Check if audiobook is downloaded (has audio files) */
    suspend fun isAudiobookDownloaded(audiobook: Audiobook): Boolean

    /** Get all audio files for audiobook */
    suspend fun getAudiobookAudioFiles(audiobook: Audiobook): List<File>

    /** Get storage space information */
    suspend fun getStorageInfo(): AudiobookStorageInfo

    /** Clean up temporary files */
    suspend fun cleanupTempFiles()

    /** Clean up cache files older than specified days */
    suspend fun cleanupOldCacheFiles(olderThanDays: Int = 30)

    /** Delete audiobook and all its files */
    suspend fun deleteAudiobook(audiobook: Audiobook): Boolean

    /** Move audiobook files to different location */
    suspend fun moveAudiobook(
        audiobook: Audiobook,
        newLocation: File,
    ): Boolean

    /** Get total size of audiobook files */
    suspend fun getAudiobookSize(audiobook: Audiobook): Long

    /** Watch for file system changes */
    fun watchFileSystemChanges(): Flow<FileSystemEvent>

    /** Validate file system integrity */
    suspend fun validateFileSystem(): FileSystemValidationResult

    /** Export audiobook to external storage */
    suspend fun exportAudiobook(
        audiobook: Audiobook,
        destinationPath: String,
    ): Boolean

    /** Import audiobook from external storage */
    suspend fun importAudiobook(
        sourcePath: String,
        audiobook: Audiobook,
    ): Boolean
}

/** Storage information for audiobook files */
data class AudiobookStorageInfo(
    val totalSpace: Long,
    val freeSpace: Long,
    val usedSpace: Long,
    val audiobooksSize: Long,
    val tempSize: Long,
    val cacheSize: Long,
    val logsSize: Long,
) {
    val freeSpacePercentage: Float
        get() = if (totalSpace > 0) (freeSpace.toFloat() / totalSpace.toFloat()) * 100f else 0f

    val usedSpacePercentage: Float
        get() = if (totalSpace > 0) (usedSpace.toFloat() / totalSpace.toFloat()) * 100f else 0f
}

/** File system event for monitoring changes */
sealed class FileSystemEvent {
    data class FileCreated(
        val file: File,
    ) : FileSystemEvent()

    data class FileDeleted(
        val file: File,
    ) : FileSystemEvent()

    data class FileModified(
        val file: File,
    ) : FileSystemEvent()

    data class DirectoryCreated(
        val directory: File,
    ) : FileSystemEvent()

    data class DirectoryDeleted(
        val directory: File,
    ) : FileSystemEvent()

    data class StorageSpaceChanged(
        val storageInfo: AudiobookStorageInfo,
    ) : FileSystemEvent()
}

/** File system validation result */
data class FileSystemValidationResult(
    val isValid: Boolean,
    val issues: List<FileSystemIssue>,
    val fixedIssues: List<FileSystemIssue>,
)

/** File system issue */
data class FileSystemIssue(
    val type: IssueType,
    val description: String,
    val file: File?,
    val severity: IssueSeverity,
    val canAutoFix: Boolean,
)

enum class IssueType {
    MISSING_DIRECTORY,
    MISSING_FILE,
    CORRUPTED_FILE,
    PERMISSION_DENIED,
    DISK_FULL,
    ORPHANED_FILE,
    DUPLICATE_FILE,
    INVALID_METADATA,
}

enum class IssueSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}
