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

package com.jabook.app.jabook.audio.player.position

import com.jabook.app.jabook.audio.core.result.Result
import com.jabook.app.jabook.audio.domain.usecase.SavePositionUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles periodic saving of playback position.
 *
 * Implements adaptive periodic saving strategy:
 * - Close to start/end (first/last 10%): every 5 seconds
 * - In the middle: every 30 seconds
 * - On critical events: immediate save
 */
class PositionSaver
    @Inject
    constructor(
        private val savePositionUseCase: SavePositionUseCase,
        private val coroutineScope: CoroutineScope,
    ) {
        private var saveJob: Job? = null
        private var isSavingEnabled = false

        /**
         * Starts periodic position saving.
         *
         * @param bookId The book ID
         * @param getTrackIndex Function to get current track index
         * @param getPosition Function to get current position
         * @param getDuration Function to get current track duration
         */
        fun startPeriodicSaving(
            bookId: String,
            getTrackIndex: () -> Int,
            getPosition: () -> Long,
            getDuration: () -> Long,
        ) {
            if (isSavingEnabled) {
                return // Already saving
            }

            isSavingEnabled = true
            saveJob =
                coroutineScope.launch {
                    while (isSavingEnabled) {
                        val position = getPosition()
                        val duration = getDuration()
                        val trackIndex = getTrackIndex()

                        // Validate position before saving
                        if (position >= 0 && duration > 0 && trackIndex >= 0) {
                            val progress = position.toFloat() / duration.toFloat()

                            // Determine save interval based on position
                            val interval =
                                when {
                                    progress <= 0.1f || progress >= 0.9f -> 5000L // 5 seconds for start/end
                                    else -> 30000L // 30 seconds for middle
                                }

                            // Save position
                            savePositionUseCase(bookId, trackIndex, position)

                            delay(interval)
                        } else {
                            // Invalid state, wait before retry
                            delay(5000L)
                        }
                    }
                }
        }

        /**
         * Stops periodic position saving.
         */
        fun stopPeriodicSaving() {
            isSavingEnabled = false
            saveJob?.cancel()
            saveJob = null
        }

        /**
         * Saves position immediately.
         *
         * @param bookId The book ID
         * @param trackIndex The current track index
         * @param position The current position
         */
        suspend fun saveImmediately(
            bookId: String,
            trackIndex: Int,
            position: Long,
        ): Result<Unit> = savePositionUseCase(bookId, trackIndex, position)
    }
