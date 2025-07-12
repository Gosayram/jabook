package com.jabook.app.core.storage

import android.content.Context
import com.jabook.app.core.domain.model.Audiobook
import com.jabook.app.shared.debug.IDebugLogger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Singleton
class FileManagerImpl @Inject constructor(private val context: Context, private val debugLogger: IDebugLogger) : FileManager {

    override suspend fun initialize() {
        debugLogger.logInfo("FileManager initialized")
    }

    override fun getAudiobooksDirectory(): File {
        return File(context.filesDir, "audiobooks")
    }

    override fun getTempDirectory(): File {
        return File(context.cacheDir, "temp")
    }

    override fun getCacheDirectory(): File {
        return context.cacheDir
    }

    override fun getLogsDirectory(): File {
        return File(context.filesDir, "logs")
    }

    override fun getAudiobookDirectory(audiobook: Audiobook): File {
        return File(getAudiobooksDirectory(), "${audiobook.author}/${audiobook.title}")
    }

    override fun getAudiobookAudioDirectory(audiobook: Audiobook): File {
        return File(getAudiobookDirectory(audiobook), "audio")
    }

    override fun getAudiobookMetadataFile(audiobook: Audiobook): File {
        return File(getAudiobookDirectory(audiobook), "metadata.json")
    }

    override fun getAudiobookCoverFile(audiobook: Audiobook): File {
        return File(getAudiobookDirectory(audiobook), "cover.jpg")
    }

    override suspend fun createAudiobookDirectories(audiobook: Audiobook): Boolean {
        return try {
            getAudiobookAudioDirectory(audiobook).mkdirs()
            true
        } catch (e: Exception) {
            debugLogger.logError("Failed to create audiobook directories", e)
            false
        }
    }

    override suspend fun isAudiobookDownloaded(audiobook: Audiobook): Boolean {
        return getAudiobookAudioDirectory(audiobook).exists()
    }

    override suspend fun getAudiobookAudioFiles(audiobook: Audiobook): List<File> {
        val audioDir = getAudiobookAudioDirectory(audiobook)
        return if (audioDir.exists()) {
            audioDir.listFiles { file -> file.extension in listOf("mp3", "m4a", "flac", "ogg") }?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }

    override suspend fun cleanupTempFiles() {
        try {
            getTempDirectory().deleteRecursively()
            debugLogger.logInfo("Temp files cleaned up")
        } catch (e: Exception) {
            debugLogger.logError("Failed to cleanup temp files", e)
        }
    }

    override suspend fun cleanupOldCacheFiles(olderThanDays: Int) {
        try {
            val cutoffTime = System.currentTimeMillis() - (olderThanDays * 24 * 60 * 60 * 1000L)
            getCacheDirectory().listFiles()?.forEach { file ->
                if (file.lastModified() < cutoffTime) {
                    file.deleteRecursively()
                }
            }
            debugLogger.logInfo("Old cache files cleaned up")
        } catch (e: Exception) {
            debugLogger.logError("Failed to cleanup old cache files", e)
        }
    }

    override suspend fun deleteAudiobook(audiobook: Audiobook): Boolean {
        return try {
            getAudiobookDirectory(audiobook).deleteRecursively()
            true
        } catch (e: Exception) {
            debugLogger.logError("Failed to delete audiobook", e)
            false
        }
    }

    override suspend fun moveAudiobook(audiobook: Audiobook, newLocation: File): Boolean {
        return try {
            getAudiobookDirectory(audiobook).copyRecursively(newLocation)
            getAudiobookDirectory(audiobook).deleteRecursively()
            true
        } catch (e: Exception) {
            debugLogger.logError("Failed to move audiobook", e)
            false
        }
    }

    override suspend fun getAudiobookSize(audiobook: Audiobook): Long {
        return try {
            getAudiobookDirectory(audiobook).walkTopDown().sumOf { file -> if (file.isFile) file.length() else 0L }
        } catch (e: Exception) {
            debugLogger.logError("Failed to get audiobook size", e)
            0L
        }
    }

    override suspend fun getStorageInfo(): AudiobookStorageInfo {
        val audiobooksDir = getAudiobooksDirectory()
        val freeSpace = audiobooksDir.freeSpace
        val totalSpace = audiobooksDir.totalSpace
        val usedSpace = totalSpace - freeSpace

        return AudiobookStorageInfo(
            totalSpace = totalSpace,
            freeSpace = freeSpace,
            usedSpace = usedSpace,
            audiobooksSize =
                if (audiobooksDir.exists()) {
                    audiobooksDir.walkTopDown().sumOf { file -> if (file.isFile) file.length() else 0L }
                } else 0L,
            tempSize = getTempDirectory().walkTopDown().sumOf { file -> if (file.isFile) file.length() else 0L },
            cacheSize = getCacheDirectory().walkTopDown().sumOf { file -> if (file.isFile) file.length() else 0L },
            logsSize = getLogsDirectory().walkTopDown().sumOf { file -> if (file.isFile) file.length() else 0L },
        )
    }

    override suspend fun exportAudiobook(audiobook: Audiobook, destinationPath: String): Boolean {
        return try {
            val destination = File(destinationPath)
            getAudiobookDirectory(audiobook).copyRecursively(destination)
            true
        } catch (e: Exception) {
            debugLogger.logError("Failed to export audiobook", e)
            false
        }
    }

    override suspend fun importAudiobook(sourcePath: String, audiobook: Audiobook): Boolean {
        return try {
            val source = File(sourcePath)
            val destination = getAudiobookDirectory(audiobook)
            createAudiobookDirectories(audiobook)
            source.copyRecursively(destination)
            true
        } catch (e: Exception) {
            debugLogger.logError("Failed to import audiobook", e)
            false
        }
    }

    override fun watchFileSystemChanges(): Flow<FileSystemEvent> {
        return flowOf() // Empty flow for now
    }

    override suspend fun validateFileSystem(): FileSystemValidationResult {
        return FileSystemValidationResult(isValid = true, issues = emptyList(), fixedIssues = emptyList())
    }
}
