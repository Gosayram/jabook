// Copyright 2026 Jabook Contributors
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

import android.content.Context
import android.provider.MediaStore
import com.jabook.app.jabook.compose.data.local.parser.AudioMetadataParser
import com.jabook.app.jabook.compose.data.model.ScanProgress
import com.jabook.app.jabook.compose.domain.model.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of LocalBookScanner using MediaStore API.
 *
 * Groups audio files by album tag to identify audiobooks.
 */
@Singleton
class MediaStoreBookScanner
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val metadataParser: AudioMetadataParser,
        private val scanPathDao: com.jabook.app.jabook.compose.data.local.dao.ScanPathDao,
        private val encodingDetector: com.jabook.app.jabook.compose.data.local.parser.EncodingDetector,
    ) : LocalBookScanner {
        private val _scanProgress = kotlinx.coroutines.flow.MutableStateFlow<ScanProgress>(ScanProgress.Idle)
        override val scanProgress: kotlinx.coroutines.flow.StateFlow<ScanProgress> = _scanProgress.asStateFlow()

        override suspend fun scanAudiobooks(): Result<List<ScannedBook>> =
            withContext(Dispatchers.IO) {
                try {
                    _scanProgress.value = ScanProgress.Discovery(0)

                    val allowedPaths = scanPathDao.getAllPathsList().map { it.path }
                    val audioFiles = queryAudioFiles(allowedPaths)
                    _scanProgress.value = ScanProgress.Discovery(audioFiles.size)

                    val groupedByAlbum = groupFilesByAlbum(audioFiles)
                    val totalAlbums = groupedByAlbum.size

                    val scannedBooks =
                        groupedByAlbum.entries.mapIndexedNotNull { index, (album, files) ->
                            _scanProgress.value = ScanProgress.Parsing(album, index + 1, totalAlbums)
                            createScannedBook(album, files)
                        }

                    _scanProgress.value = ScanProgress.Saving
                    Result.Success(scannedBooks)
                } catch (e: Exception) {
                    android.util.Log.e("BookScanner", "Scan failed", e)
                    _scanProgress.value = ScanProgress.Error(e.message ?: "Unknown error")
                    Result.Error(e)
                }
            }

        private fun queryAudioFiles(allowedPaths: List<String>): List<AudioFileInfo> {
            val projection =
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.DATA, // file path
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.TITLE,
                )

            // We query all music/audio files and filter in code for flexibility
            // Ideally we would add selection for paths, but LIKE with many paths is complex in SQL
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1 OR ${MediaStore.Audio.Media.IS_AUDIOBOOK} = 1"

            val audioFiles = mutableListOf<AudioFileInfo>()

            try {
                context.contentResolver
                    .query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        null,
                        null,
                    )?.use { cursor ->
                        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                        val nameColumn =
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                        val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                        val durationColumn =
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                        val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                        val artistColumn =
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                        val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)

                        val defaultFolders = listOf("Audiobooks", "Podcasts", "Books")

                        while (cursor.moveToNext()) {
                            val filePath = cursor.getString(pathColumn)
                            // Filter Logic
                            val shouldInclude =
                                if (allowedPaths.isEmpty()) {
                                    // Default: Look for "Audiobooks", "Podcasts", "Books" in path
                                    defaultFolders.any {
                                        filePath.contains(it, ignoreCase = true)
                                    }
                                } else {
                                    // Custom: Must start with one of the allowed paths
                                    allowedPaths.any { filePath.startsWith(it) }
                                }

                            if (shouldInclude && File(filePath).exists()) {
                                audioFiles.add(
                                    AudioFileInfo(
                                        id = cursor.getLong(idColumn),
                                        displayName = cursor.getString(nameColumn),
                                        filePath = filePath,
                                        duration = cursor.getLong(durationColumn),
                                        album = cursor.getString(albumColumn),
                                        artist = cursor.getString(artistColumn),
                                        title = cursor.getString(titleColumn),
                                    ),
                                )
                            }
                        }
                    }
            } catch (e: Exception) {
                // Handle IllegalArgumentException for IS_AUDIOBOOK on older APIs if needed
                // But MediaStore should just ignore valid columns?
                // Actually IS_AUDIOBOOK was added in API 29.
                // If running on older API, this might throw IllegalArgumentException "Invalid column IS_AUDIOBOOK".
                // We should safeguard the selection string.
                android.util.Log.e("BookScanner", "Error querying MediaStore", e)
                return emptyList()
            }

            return audioFiles
        }

        private fun groupFilesByAlbum(files: List<AudioFileInfo>): Map<String, List<AudioFileInfo>> =
            files
                .filter { it.album != null && it.album.isNotBlank() }
                .groupBy { it.album!! }

        private suspend fun createScannedBook(
            album: String,
            files: List<AudioFileInfo>,
        ): ScannedBook? {
            // Parse metadata from first file for book-level info
            val firstFile = files.firstOrNull() ?: return null
            val metadata = metadataParser.parseMetadata(firstFile.filePath)

            val chapters =
                files
                    .sortedWith(createChapterComparator())
                    .mapIndexed { index, file ->
                        // Apply encoding detector to chapter titles
                        // Use filename without extension if no title tag
                        val rawTitle =
                            file.title?.takeIf { it.isNotBlank() }
                                ?: java.io.File(file.displayName).nameWithoutExtension
                        val (fixedTitle, detectedEncoding) = encodingDetector.fixGarbledText(rawTitle)

                        if (detectedEncoding != null) {
                            android.util.Log.d(
                                "BookScanner",
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

            return ScannedBook(
                directory = File(firstFile.filePath).parent ?: "",
                title = metadata?.album ?: album,
                author = metadata?.albumArtist ?: metadata?.artist ?: firstFile.artist ?: "Unknown",
                chapters = chapters,
                totalDuration = chapters.sumOf { it.duration },
                coverArt = metadata?.coverArt,
            )
        }

        private data class ChapterInfo(
            val partNumber: Int = 0,
            val chapterNumber: Int = 0,
            val hasNumber: Boolean = false,
        ) {
            fun toSortKey(): Int = partNumber * 1000 + chapterNumber
        }

        private fun createChapterComparator(): Comparator<AudioFileInfo> =
            compareBy<AudioFileInfo> { file ->
                val filename = file.displayName.lowercase()
                when {
                    filename.contains("пролог") || filename.contains("prologue") -> 0
                    extractChapterInfo(file.displayName).hasNumber -> 1
                    filename.contains("эпилог") || filename.contains("epilogue") -> 3
                    else -> 2
                }
            }.thenBy { file ->
                val info = extractChapterInfo(file.displayName)
                if (info.hasNumber) info.toSortKey() else Int.MAX_VALUE
            }.thenBy { file ->
                file.displayName.lowercase()
            }

        private fun extractChapterInfo(filename: String): ChapterInfo {
            val clean = filename.lowercase()

            val partMatch =
                Regex("""част[\u044cяи]\s*(\d+)""").find(clean)
                    ?: Regex("""part\s*(\d+)""").find(clean)
            val partNum = partMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

            val patterns =
                listOf(
                    Regex("""глава\s*(\d+)""", RegexOption.IGNORE_CASE),
                    Regex("""chapter\s*(\d+)""", RegexOption.IGNORE_CASE),
                    Regex("""(\d+)\s*[-._]"""),
                    Regex("""^(\d+)"""),
                )

            var chapterNum = 0
            var found = false
            for (pattern in patterns) {
                pattern.find(clean)?.let {
                    chapterNum = it.groupValues[1].toIntOrNull() ?: 0
                    found = true
                    return@let
                }
            }

            return ChapterInfo(partNum, chapterNum, found)
        }
    }
