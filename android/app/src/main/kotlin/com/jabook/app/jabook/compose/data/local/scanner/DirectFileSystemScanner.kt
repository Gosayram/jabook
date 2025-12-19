// Copyright 2025 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.jabook.app.jabook.compose.data.local.scanner

import com.jabook.app.jabook.compose.data.local.parser.AudioMetadataParser
import com.jabook.app.jabook.compose.domain.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Direct file system scanner that ignores .nomedia files.
 *
 * This scanner is used for custom user paths where .nomedia might be present
 * to hide images from gallery, but audio files should still be visible in Jabook.
 *
 * Use case: User has .nomedia in audiobook folder to prevent cover images
 * from appearing in gallery, but wants audio files to be scanned.
 */
@Singleton
class DirectFileSystemScanner
    @Inject
    constructor(
        private val metadataParser: AudioMetadataParser,
        private val scanPathDao: com.jabook.app.jabook.compose.data.local.dao.ScanPathDao,
        private val bookIdentifier: BookIdentifier,
        private val encodingDetector: com.jabook.app.jabook.compose.data.local.parser.EncodingDetector,
    ) : LocalBookScanner {
        override suspend fun scanAudiobooks(): Result<List<ScannedBook>> =
            withContext(Dispatchers.IO) {
                try {
                    val customPaths = scanPathDao.getAllPathsList().map { it.path }

                    if (customPaths.isEmpty()) {
                        return@withContext Result.Success(emptyList())
                    }

                    val audioFiles = mutableListOf<AudioFileInfo>()

                    // Scan each custom path recursively
                    for (path in customPaths) {
                        val directory = File(path)
                        if (directory.exists() && directory.isDirectory) {
                            scanDirectory(directory, audioFiles) // Now suspend
                        }
                    }

                    // Group by album and create scanned books
                    val groupedByAlbum = groupFilesByAlbum(audioFiles)

                    android.util.Log.d(
                        "DirectFileScanner",
                        "Scanning stats: ${audioFiles.size} audio files found, grouped into ${groupedByAlbum.size} books",
                    )

                    val scannedBooks =
                        groupedByAlbum.mapNotNull { (groupingKey, files) ->
                            createScannedBook(groupingKey, files)
                        }

                    android.util.Log.i(
                        "DirectFileScanner",
                        "Scan complete: ${scannedBooks.size} books successfully created",
                    )

                    Result.Success(scannedBooks)
                } catch (e: Exception) {
                    android.util.Log.e("DirectFileScanner", "Scan failed", e)
                    Result.Error(e)
                }
            }

        /**
         * Recursively scan directory for audio files.
         * IGNORES .nomedia files - this is intentional for user's use case!
         */
        private suspend fun scanDirectory(
            directory: File,
            result: MutableList<AudioFileInfo>,
        ) {
            try {
                directory.listFiles()?.forEach { file ->
                    when {
                        file.isDirectory -> {
                            // Recursively scan subdirectories
                            // NOTE: We intentionally IGNORE .nomedia files here!
                            scanDirectory(file, result)
                        }
                        file.isFile && file.isAudioFile() -> {
                            // Found audio file - add to results
                            val audioInfo = createAudioFileInfo(file)
                            if (audioInfo != null) {
                                result.add(audioInfo)
                                android.util.Log.v(
                                    "DirectFileScanner",
                                    "Found audio file: ${file.name} (album: ${audioInfo.album ?: "none"})",
                                )
                            }
                        }
                    }
                }
            } catch (e: SecurityException) {
                android.util.Log.w("DirectFileScanner", "Cannot access directory: ${directory.path}", e)
            }
        }

        /**
         * Check if file is an audio file based on extension.
         */
        private fun File.isAudioFile(): Boolean {
            val extension = this.extension.lowercase()
            return extension in AUDIO_EXTENSIONS
        }

        /**
         * Create AudioFileInfo from File by parsing metadata.
         */
        private suspend fun createAudioFileInfo(file: File): AudioFileInfo? =
            try {
                val metadata = metadataParser.parseMetadata(file.absolutePath)

                AudioFileInfo(
                    filePath = file.absolutePath,
                    displayName = file.name,
                    duration = metadata?.duration ?: 0L,
                    album = metadata?.album,
                    artist = metadata?.artist ?: metadata?.albumArtist,
                    title = metadata?.title,
                )
            } catch (e: Exception) {
                android.util.Log.w("DirectFileScanner", "Failed to parse: ${file.name}", e)
                null
            }

        /**
         * Group audio files by album name, with directory fallback.
         *
         * Uses composite key (album/directory + artist) to avoid duplicates.
         */
        private fun groupFilesByAlbum(files: List<AudioFileInfo>): Map<String, List<AudioFileInfo>> {
            val totalFiles = files.size
            android.util.Log.d("DirectFileScanner", "Grouping $totalFiles files into books...")

            // Group by composite key (album or directory + artist)
            val grouped =
                files.groupBy { fileInfo ->
                    val directory = java.io.File(fileInfo.filePath).parent ?: ""
                    bookIdentifier.generateGroupingKey(
                        directory = directory,
                        album = fileInfo.album,
                        artist = fileInfo.artist,
                    )
                }

            // Log files without album metadata
            val filesWithoutAlbum = files.count { it.album.isNullOrBlank() }
            if (filesWithoutAlbum > 0) {
                android.util.Log.w(
                    "DirectFileScanner",
                    "$filesWithoutAlbum files have no album metadata (will group by directory)",
                )
            }

            android.util.Log.d(
                "DirectFileScanner",
                "Grouped into ${grouped.size} books (before: $totalFiles files)",
            )

            return grouped
        }

        /**
         * Create ScannedBook from grouped files.
         */
        private suspend fun createScannedBook(
            groupingKey: String,
            files: List<AudioFileInfo>,
        ): ScannedBook? {
            val firstFile = files.firstOrNull() ?: return null
            val metadata = metadataParser.parseMetadata(firstFile.filePath)

            val directory = File(firstFile.filePath).parent ?: ""

            // Generate unique book ID
            val bookId =
                bookIdentifier.generateBookId(
                    directory = directory,
                    album = metadata?.album,
                    artist = metadata?.albumArtist ?: metadata?.artist,
                )

            val chapters =
                files
                    .sortedBy { it.displayName }
                    .mapIndexed { index, file ->
                        // Apply encoding detector to chapter titles
                        val rawTitle = file.title ?: "Chapter ${index + 1}"
                        val (fixedTitle, detectedEncoding) = encodingDetector.fixGarbledText(rawTitle)

                        if (detectedEncoding != null) {
                            android.util.Log.d(
                                "DirectFileScanner",
                                "📖 Chapter encoding fix: '$rawTitle' -> '$fixedTitle' ($detectedEncoding)",
                            )
                        }

                        ScannedChapter(
                            filePath = file.filePath,
                            title = fixedTitle,
                            index = index,
                            duration = file.duration,
                        )
                    }

            val book =
                ScannedBook(
                    directory = directory,
                    title = metadata?.album ?: File(directory).name,
                    author = metadata?.albumArtist ?: metadata?.artist ?: firstFile.artist ?: "Unknown",
                    chapters = chapters,
                    totalDuration = chapters.sumOf { it.duration },
                    coverArt = metadata?.coverArt,
                )

            android.util.Log.d(
                "DirectFileScanner",
                "Created book '${book.title}' by ${book.author} (${chapters.size} chapters, ID: $bookId)",
            )

            return book
        }

        companion object {
            /**
             * Supported audio file extensions.
             */
            private val AUDIO_EXTENSIONS =
                setOf(
                    "mp3",
                    "m4a",
                    "m4b",
                    "ogg",
                    "opus",
                    "flac",
                    "wav",
                    "aac",
                    "wma",
                    "oga",
                )
        }
    }
