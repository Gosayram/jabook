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

import com.jabook.app.jabook.audio.core.model.Playlist
import com.jabook.app.jabook.audio.core.result.Result
import com.jabook.app.jabook.audio.data.repository.ChapterMetadataRepository
import com.jabook.app.jabook.audio.data.repository.PlaylistRepository
import com.jabook.app.jabook.audio.domain.mapper.ChapterMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Use case for loading a playlist for a book.
 *
 * Implements offline-first approach: reads from local storage first.
 */
class LoadPlaylistUseCase
    @Inject
    constructor(
        private val playlistRepository: PlaylistRepository,
        private val chapterRepository: ChapterMetadataRepository,
    ) {
        /**
         * Loads a playlist for a book.
         *
         * @param bookId The book ID
         * @return Flow of Result containing the playlist
         */
        operator fun invoke(bookId: String): Flow<Result<Playlist>> =
            chapterRepository.getChapters(bookId).map { result ->
                when (result) {
                    is com.jabook.app.jabook.audio.core.result.Result.Success -> {
                        val chapters = ChapterMapper.toDomainList(result.data)
                        val playlistEntity = playlistRepository.getPlaylist(bookId).first()
                        when (playlistEntity) {
                            is com.jabook.app.jabook.audio.core.result.Result.Success -> {
                                val entity = playlistEntity.data
                                if (entity != null) {
                                    com.jabook.app.jabook.audio.core.result.Result.Success(
                                        Playlist(
                                            bookId = bookId,
                                            bookTitle = entity.bookTitle,
                                            chapters = chapters,
                                            currentIndex = entity.currentIndex,
                                        ),
                                    )
                                } else {
                                    // No saved playlist, create new one
                                    com.jabook.app.jabook.audio.core.result.Result.Success(
                                        Playlist(
                                            bookId = bookId,
                                            bookTitle = "",
                                            chapters = chapters,
                                            currentIndex = 0,
                                        ),
                                    )
                                }
                            }
                            is com.jabook.app.jabook.audio.core.result.Result.Error -> {
                                com.jabook.app.jabook.audio.core.result.Result
                                    .Error(playlistEntity.exception)
                            }
                            is com.jabook.app.jabook.audio.core.result.Result.Loading -> {
                                com.jabook.app.jabook.audio.core.result.Result.Loading
                            }
                        }
                    }
                    is com.jabook.app.jabook.audio.core.result.Result.Error -> {
                        com.jabook.app.jabook.audio.core.result.Result
                            .Error(result.exception)
                    }
                    is com.jabook.app.jabook.audio.core.result.Result.Loading -> {
                        com.jabook.app.jabook.audio.core.result.Result.Loading
                    }
                }
            }
    }
