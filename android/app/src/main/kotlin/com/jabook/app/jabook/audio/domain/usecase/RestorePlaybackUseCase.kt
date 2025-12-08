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

import com.jabook.app.jabook.audio.core.model.PlaybackState
import com.jabook.app.jabook.audio.core.result.Result
import com.jabook.app.jabook.audio.data.repository.PlaybackPositionRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Use case for restoring playback position.
 *
 * Loads saved playback position from local storage.
 */
class RestorePlaybackUseCase
    @Inject
    constructor(
        private val positionRepository: PlaybackPositionRepository,
    ) {
        /**
         * Restores the playback position for a book.
         *
         * @param bookId The book ID
         * @return Result containing the saved playback state, or null if not found
         */
        suspend operator fun invoke(bookId: String): Result<PlaybackState?> =
            try {
                val result = positionRepository.getPosition(bookId).first()
                when (result) {
                    is com.jabook.app.jabook.audio.core.result.Result.Success -> {
                        val entity = result.data
                        if (entity != null) {
                            com.jabook.app.jabook.audio.core.result.Result.Success(
                                PlaybackState(
                                    isPlaying = false, // Start paused
                                    currentPosition = entity.position,
                                    duration = 0L, // Will be set when track loads
                                    currentTrackIndex = entity.trackIndex,
                                    playbackSpeed = 1.0f,
                                    bufferedPosition = 0L,
                                ),
                            )
                        } else {
                            com.jabook.app.jabook.audio.core.result.Result
                                .Success(null)
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
            } catch (e: Exception) {
                com.jabook.app.jabook.audio.core.result.Result
                    .Error(e)
            }
    }
