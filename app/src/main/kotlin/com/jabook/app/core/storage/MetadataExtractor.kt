package com.jabook.app.core.storage

import com.jabook.app.core.domain.model.Chapter
import java.io.File

/** Interface for extracting metadata from audio files */
interface MetadataExtractor {
  /** Extract basic metadata from audio file */
  suspend fun extractMetadata(audioFile: File): AudioFileMetadata?

  /** Extract metadata from all audio files in directory */
  suspend fun extractMetadataFromDirectory(directory: File): List<AudioFileMetadata>

  /** Extract cover art from audio file */
  suspend fun extractCoverArt(audioFile: File): ByteArray?

  /** Generate audiobook metadata from audio files */
  suspend fun generateAudiobookMetadata(audioFiles: List<File>): ExtractedAudiobookMetadata?

  /** Generate chapters from audio files */
  suspend fun generateChapters(
    audioFiles: List<File>,
    audiobookId: String,
  ): List<Chapter>

  /** Validate audio file integrity */
  suspend fun validateAudioFile(audioFile: File): AudioFileValidationResult

  /** Get supported audio formats */
  fun getSupportedFormats(): Set<String>

  /** Check if file is supported audio format */
  fun isSupportedFormat(file: File): Boolean

  /** Get total duration of audio files */
  suspend fun getTotalDuration(audioFiles: List<File>): Long
}

/** Audio file metadata */
data class AudioFileMetadata(
  val filePath: String,
  val title: String?,
  val artist: String?,
  val album: String?,
  val albumArtist: String?,
  val year: String?,
  val genre: String?,
  val track: String?,
  val duration: Long, // milliseconds
  val bitRate: Int?,
  val sampleRate: Int?,
  val channels: Int?,
  val format: String,
  val fileSize: Long,
  val hasEmbeddedCover: Boolean,
  val creationDate: Long,
  val modificationDate: Long,
)

/** Extracted audiobook metadata */
data class ExtractedAudiobookMetadata(
  val title: String,
  val author: String,
  val narrator: String?,
  val description: String?,
  val genre: String?,
  val year: String?,
  val duration: Long, // milliseconds
  val totalFiles: Int,
  val totalSize: Long,
  val quality: String,
  val format: String,
  val hasCoverArt: Boolean,
  val chapters: List<ChapterMetadata>,
)

/** Chapter metadata */
data class ChapterMetadata(
  val number: Int,
  val title: String,
  val filePath: String,
  val duration: Long, // milliseconds
  val startTime: Long, // milliseconds (for multi-file chapters)
  val endTime: Long, // milliseconds (for multi-file chapters)
)

/** Audio file validation result */
data class AudioFileValidationResult(
  val isValid: Boolean,
  val issues: List<AudioFileIssue>,
  val canAutoFix: Boolean,
)

/** Audio file issue */
data class AudioFileIssue(
  val type: AudioFileIssueType,
  val severity: AudioFileIssueSeverity,
  val description: String,
)

enum class AudioFileIssueType {
  CORRUPTED_FILE,
  MISSING_METADATA,
  INVALID_FORMAT,
  UNSUPPORTED_CODEC,
  INCOMPLETE_DOWNLOAD,
  PERMISSION_DENIED,
  ZERO_DURATION,
  INVALID_BITRATE,
}

enum class AudioFileIssueSeverity {
  INFO,
  WARNING,
  ERROR,
  CRITICAL,
}
