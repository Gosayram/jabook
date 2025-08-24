package com.jabook.app.core.storage

import android.content.Context
import com.jabook.app.core.compat.StorageCompat
import com.jabook.app.shared.debug.DebugLogger
import com.jabook.app.shared.utils.FileUtils
import com.jabook.app.shared.utils.ValidationUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/** Implementation of StorageManager for audiobook file management */
@Singleton
class StorageManagerImpl
  @Inject
  constructor(
    private val context: Context,
  ) : StorageManager {
    private val baseDirectory = File(StorageCompat.getExternalStorageDirectory(context), "JaBook")

    override suspend fun getAudiobooksDirectory(): File =
      withContext(Dispatchers.IO) {
        val dir = File(baseDirectory, "audiobooks")
        if (!dir.exists()) {
          dir.mkdirs()
        }
        dir
      }

    override suspend fun getTempDirectory(): File =
      withContext(Dispatchers.IO) {
        val dir = File(baseDirectory, "temp")
        if (!dir.exists()) {
          dir.mkdirs()
        }
        dir
      }

    override suspend fun getCacheDirectory(): File =
      withContext(Dispatchers.IO) {
        val dir = File(baseDirectory, "cache")
        if (!dir.exists()) {
          dir.mkdirs()
        }
        dir
      }

    override suspend fun getLogsDirectory(): File =
      withContext(Dispatchers.IO) {
        val dir = File(baseDirectory, "logs")
        if (!dir.exists()) {
          dir.mkdirs()
        }
        dir
      }

    override suspend fun createAudiobookDirectory(
      author: String,
      title: String,
    ): File =
      withContext(Dispatchers.IO) {
        val sanitizedAuthor = FileUtils.sanitizeFileName(author)
        val sanitizedTitle = FileUtils.sanitizeFileName(title)

        val authorDir = File(getAudiobooksDirectory(), sanitizedAuthor)
        val audiobookDir = File(authorDir, sanitizedTitle)

        if (!audiobookDir.exists()) {
          audiobookDir.mkdirs()
        }

        DebugLogger.logInfo("Created audiobook directory: ${audiobookDir.absolutePath}", "StorageManager")
        audiobookDir
      }

    override suspend fun extractAudiobookFiles(
      archivePath: String,
      destination: File,
    ): List<AudioFile> =
      withContext(Dispatchers.IO) {
        val audioFiles = mutableListOf<AudioFile>()

        try {
          val archiveFile = File(archivePath)
          if (!archiveFile.exists()) {
            DebugLogger.logError("Archive file not found: $archivePath", null, "StorageManager")
            return@withContext audioFiles
          }

          if (!destination.exists()) {
            destination.mkdirs()
          }

          when (FileUtils.getFileExtension(archivePath)) {
            "zip" -> extractZipFile(archiveFile, destination, audioFiles)
            "rar" -> {
              DebugLogger.logWarning("RAR extraction is not supported", "StorageManager")
            }
            "7z" -> {
              DebugLogger.logWarning("7z extraction is not supported", "StorageManager")
            }
            else -> {
              DebugLogger.logWarning("Unsupported archive format: $archivePath", "StorageManager")
            }
          }

          DebugLogger.logInfo("Extracted ${audioFiles.size} audio files from $archivePath", "StorageManager")
        } catch (e: Exception) {
          DebugLogger.logError("Failed to extract archive: $archivePath", e, "StorageManager")
        }

        audioFiles
      }

    override suspend fun detectAudioFiles(directory: File): List<AudioFile> =
      withContext(Dispatchers.IO) {
        val audioFiles = mutableListOf<AudioFile>()

        try {
          if (!directory.exists() || !directory.isDirectory) {
            return@withContext audioFiles
          }

          directory.listFiles()?.forEach { file ->
            if (file.isFile && ValidationUtils.isValidAudioFile(file.name)) {
              val audioFile = createAudioFile(file)
              if (audioFile != null) {
                audioFiles.add(audioFile)
              }
            } else if (file.isDirectory) {
              // Recursively search subdirectories
              audioFiles.addAll(detectAudioFiles(file))
            }
          }

          // Sort by filename for consistent ordering
          audioFiles.sortBy { it.name }

          DebugLogger.logInfo("Detected ${audioFiles.size} audio files in ${directory.absolutePath}", "StorageManager")
        } catch (e: Exception) {
          DebugLogger.logError("Failed to detect audio files in directory: ${directory.absolutePath}", e, "StorageManager")
        }

        audioFiles
      }

    override suspend fun deleteAudiobook(
      author: String,
      title: String,
    ): Boolean =
      withContext(Dispatchers.IO) {
        return@withContext try {
          val sanitizedAuthor = FileUtils.sanitizeFileName(author)
          val sanitizedTitle = FileUtils.sanitizeFileName(title)

          val authorDir = File(getAudiobooksDirectory(), sanitizedAuthor)
          val audiobookDir = File(authorDir, sanitizedTitle)

          if (audiobookDir.exists()) {
            val deleted = audiobookDir.deleteRecursively()

            // Clean up empty author directory
            if (deleted && authorDir.exists() && authorDir.listFiles().isNullOrEmpty()) {
              authorDir.delete()
            }

            DebugLogger.logInfo("Deleted audiobook directory: ${audiobookDir.absolutePath}", "StorageManager")
            deleted
          } else {
            DebugLogger.logWarning("Audiobook directory not found: ${audiobookDir.absolutePath}", "StorageManager")
            false
          }
        } catch (e: Exception) {
          DebugLogger.logError("Failed to delete audiobook: $author - $title", e, "StorageManager")
          false
        }
      }

    override suspend fun getStorageInfo(): StorageInfo =
      withContext(Dispatchers.IO) {
        return@withContext try {
          val totalSpace = baseDirectory.totalSpace
          val usableSpace = baseDirectory.usableSpace
          val freeSpace = baseDirectory.freeSpace

          val audiobooksSize = calculateDirectorySize(getAudiobooksDirectory())
          val tempSize = calculateDirectorySize(getTempDirectory())
          val cacheSize = calculateDirectorySize(getCacheDirectory())
          val logsSize = calculateDirectorySize(getLogsDirectory())

          StorageInfo(
            totalSpace = totalSpace,
            availableSpace = freeSpace,
            usedSpace = totalSpace - freeSpace,
            audiobooksSize = audiobooksSize,
            tempSize = tempSize,
            cacheSize = cacheSize,
            logsSize = logsSize,
          )
        } catch (e: Exception) {
          DebugLogger.logError("Failed to get storage info", e, "StorageManager")
          StorageInfo(0, 0, 0, 0, 0, 0, 0)
        }
      }

    override suspend fun cleanupTempFiles(): Long =
      withContext(Dispatchers.IO) {
        return@withContext try {
          val tempDir = getTempDirectory()
          val sizeBeforeCleanup = calculateDirectorySize(tempDir)

          tempDir.listFiles()?.forEach { file -> file.deleteRecursively() }

          DebugLogger.logInfo("Cleaned up temp files, freed $sizeBeforeCleanup bytes", "StorageManager")
          sizeBeforeCleanup
        } catch (e: Exception) {
          DebugLogger.logError("Failed to cleanup temp files", e, "StorageManager")
          0
        }
      }

    override suspend fun cleanupOldLogs(daysOld: Int): Long =
      withContext(Dispatchers.IO) {
        return@withContext try {
          val logsDir = getLogsDirectory()
          val cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
          var freedSpace = 0L

          logsDir.listFiles()?.forEach { file ->
            if (file.lastModified() < cutoffTime) {
              freedSpace += file.length()
              file.delete()
            }
          }

          DebugLogger.logInfo("Cleaned up old logs, freed $freedSpace bytes", "StorageManager")
          freedSpace
        } catch (e: Exception) {
          DebugLogger.logError("Failed to cleanup old logs", e, "StorageManager")
          0
        }
      }

    override fun getAudiobookMetadata(audiobookDirectory: File): AudiobookMetadata? {
      return try {
        val metadataFile = File(audiobookDirectory, "metadata.json")
        if (metadataFile.exists()) {
          val jsonString = metadataFile.readText()
          return Json.decodeFromString<AudiobookMetadata>(jsonString)
        } else {
          null
        }
      } catch (e: Exception) {
        DebugLogger.logError("Failed to get audiobook metadata", e, "StorageManager")
        null
      }
    }

    override suspend fun saveAudiobookMetadata(
      directory: File,
      metadata: AudiobookMetadata,
    ) = withContext(Dispatchers.IO) {
      try {
        val metadataFile = File(directory, "metadata.json")
        val jsonString = Json.encodeToString(metadata)
        metadataFile.writeText(jsonString)
        DebugLogger.logInfo("Saved audiobook metadata to ${metadataFile.absolutePath}", "StorageManager")
      } catch (e: Exception) {
        DebugLogger.logError("Failed to save audiobook metadata", e, "StorageManager")
      }
    }

    override suspend fun downloadCoverImage(
      url: String,
      destination: File,
    ): Boolean =
      withContext(Dispatchers.IO) {
        return@withContext try {
          val connection = URL(url).openConnection()
          connection.connect()

          connection.getInputStream().use { input -> FileOutputStream(destination).use { output -> input.copyTo(output) } }

          DebugLogger.logInfo("Downloaded cover image to ${destination.absolutePath}", "StorageManager")
          true
        } catch (e: Exception) {
          DebugLogger.logError("Failed to download cover image: $url", e, "StorageManager")
          false
        }
      }

    // Private helper methods

    private fun extractZipFile(
      archiveFile: File,
      destination: File,
      audioFiles: MutableList<AudioFile>,
    ) {
      try {
        ZipFile(archiveFile).use { zipFile ->
          zipFile.entries().asSequence().forEach { entry ->
            processZipEntry(zipFile, entry, destination, audioFiles)
          }
        }
      } catch (e: Exception) {
        DebugLogger.logError("Failed to extract ZIP file", e, "StorageManager")
      }
    }

    private fun processZipEntry(
      zipFile: ZipFile,
      entry: java.util.zip.ZipEntry,
      destination: File,
      audioFiles: MutableList<AudioFile>,
    ) {
      val entryFile = File(destination, entry.name)

      if (entry.isDirectory) {
        entryFile.mkdirs()
      } else {
        entryFile.parentFile?.mkdirs()

        zipFile.getInputStream(entry).use { input -> FileOutputStream(entryFile).use { output -> input.copyTo(output) } }

        if (ValidationUtils.isValidAudioFile(entryFile.name)) {
          val audioFile = createAudioFile(entryFile)
          if (audioFile != null) {
            audioFiles.add(audioFile)
          }
        }
      }
    }

    private fun extractRarFile(
      archiveFile: File,
      destination: File,
      audioFiles: MutableList<AudioFile>,
    ) {
      DebugLogger.logWarning("RAR extraction is not supported", "StorageManager")
    }

    private fun extract7zFile(
      archiveFile: File,
      destination: File,
      audioFiles: MutableList<AudioFile>,
    ) {
      DebugLogger.logWarning("7z extraction is not supported", "StorageManager")
    }

    private fun createAudioFile(file: File): AudioFile? {
      return try {
        if (!file.exists() || !file.isFile) {
          return null
        }

        val format = AudioFormat.fromExtension(FileUtils.getFileExtension(file.name))

        AudioFile(
          path = file.absolutePath,
          name = file.name,
          size = file.length(),
          format = format,
          chapterNumber = extractChapterNumber(file.name),
          duration = 0L, // Placeholder - would need audio parsing library
          bitrate = 0,   // Placeholder - would need audio parsing library
          title = file.nameWithoutExtension,
        )
      } catch (e: Exception) {
        DebugLogger.logError("Failed to create AudioFile for ${file.name}", e, "StorageManager")
        null
      }
    }

    private fun extractChapterNumber(fileName: String): Int? =
      try {
        val numbers = fileName.filter { it.isDigit() }
        if (numbers.isNotEmpty()) {
          numbers.toInt()
        } else {
          null
        }
      } catch (e: Exception) {
        null
      }

    private fun calculateDirectorySize(directory: File): Long {
      return try {
        if (!directory.exists() || !directory.isDirectory) {
          return 0
        }

        directory
          .walkTopDown()
          .filter { it.isFile }
          .map { it.length() }
          .sum()
      } catch (e: Exception) {
        DebugLogger.logError("Failed to calculate directory size", e, "StorageManager")
        0
      }
    }
  }
