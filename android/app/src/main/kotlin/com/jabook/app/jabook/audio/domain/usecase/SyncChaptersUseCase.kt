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

package com.jabook.app.jabook.audio.domain.usecase

import com.jabook.app.jabook.audio.core.model.Chapter
import com.jabook.app.jabook.audio.core.result.Result
import com.jabook.app.jabook.audio.data.repository.ChapterMetadataRepository
import com.jabook.app.jabook.audio.data.repository.PlaylistRepository
import com.jabook.app.jabook.audio.domain.mapper.ChapterMapper
import javax.inject.Inject

/**
 * Use case for synchronizing chapters metadata.
 *
 * Saves chapters to local storage for offline-first access.
 */
class SyncChaptersUseCase
    @Inject
    constructor(
        private val chapterRepository: ChapterMetadataRepository,
        private val playlistRepository: PlaylistRepository,
    ) {
        /**
         * Synchronizes chapters metadata for a book.
         *
         * @param bookId The book ID
         * @param bookTitle The book title
         * @param chapters List of chapters to sync
         * @param filePaths List of file paths for the playlist
         * @return Result indicating success or failure
         */
        suspend operator fun invoke(
            bookId: String,
            bookTitle: String,
            chapters: List<Chapter>,
            filePaths: List<String>,
        ): Result<Unit> {
            return try {
                // Save chapters metadata
                val entities = ChapterMapper.toEntityList(chapters, bookId)
                val saveChaptersResult = chapterRepository.saveChapters(entities)
                if (saveChaptersResult is com.jabook.app.jabook.audio.core.result.Result.Error) {
                    return saveChaptersResult
                }

                // Save playlist
                val savePlaylistResult =
                    playlistRepository.savePlaylist(
                        bookId = bookId,
                        bookTitle = bookTitle,
                        filePaths = filePaths,
                        currentIndex = 0,
                    )
                if (savePlaylistResult is com.jabook.app.jabook.audio.core.result.Result.Error) {
                    return savePlaylistResult
                }

                com.jabook.app.jabook.audio.core.result.Result
                    .Success(Unit)
            } catch (e: Exception) {
                com.jabook.app.jabook.audio.core.result.Result
                    .Error(e)
            }
        }
    }
