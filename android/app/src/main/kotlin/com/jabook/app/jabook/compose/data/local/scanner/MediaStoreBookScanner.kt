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

import android.content.Context
import android.provider.MediaStore
import com.jabook.app.jabook.compose.data.local.parser.AudioMetadataParser
import com.jabook.app.jabook.compose.domain.model.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
        @ApplicationContext private val context: Context,
        private val metadataParser: AudioMetadataParser,
    ) : LocalBookScanner {
        override suspend fun scanAudiobooks(): Result<List<ScannedBook>> =
            withContext(Dispatchers.IO) {
                try {
                    val audioFiles = queryAudioFiles()
                    val groupedByAlbum = groupFilesByAlbum(audioFiles)
                    val scannedBooks =
                        groupedByAlbum.mapNotNull { (album, files) ->
                            createScannedBook(album, files)
                        }
                    Result.Success(scannedBooks)
                } catch (e: Exception) {
                    android.util.Log.e("BookScanner", "Scan failed", e)
                    Result.Error(e)
                }
            }

        private fun queryAudioFiles(): List<AudioFileInfo> {
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

            val selection = "${MediaStore.Audio.Media.IS_MUSIC} = 1"

            val audioFiles = mutableListOf<AudioFileInfo>()

            context.contentResolver
                .query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    null,
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                    val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)

                    while (cursor.moveToNext()) {
                        val filePath = cursor.getString(pathColumn)
                        // Only include files that exist
                        if (File(filePath).exists()) {
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
                    .sortedBy { it.displayName }
                    .mapIndexed { index, file ->
                        ScannedChapter(
                            filePath = file.filePath,
                            title = file.title ?: "Chapter ${index + 1}",
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
    }

private data class AudioFileInfo(
    val id: Long,
    val displayName: String,
    val filePath: String,
    val duration: Long,
    val album: String?,
    val artist: String?,
    val title: String?,
)
