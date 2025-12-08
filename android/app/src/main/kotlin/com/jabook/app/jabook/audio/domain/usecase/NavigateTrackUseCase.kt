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
import javax.inject.Inject

/**
 * Use case for navigating between tracks in a playlist.
 */
class NavigateTrackUseCase
    @Inject
    constructor() {
        /**
         * Gets the next track index in the playlist.
         *
         * @param playlist The current playlist
         * @return Result with next track index, or null if there is no next track
         */
        operator fun invoke(playlist: Playlist): Result<Int?> {
            val nextIndex = playlist.getNextIndex()
            return if (nextIndex != null) {
                com.jabook.app.jabook.audio.core.result.Result
                    .Success(nextIndex)
            } else {
                com.jabook.app.jabook.audio.core.result.Result
                    .Success(null)
            }
        }

        /**
         * Gets the previous track index in the playlist.
         *
         * @param playlist The current playlist
         * @return Result with previous track index, or null if there is no previous track
         */
        fun getPreviousIndex(playlist: Playlist): Result<Int?> {
            val previousIndex = playlist.getPreviousIndex()
            return if (previousIndex != null) {
                com.jabook.app.jabook.audio.core.result.Result
                    .Success(previousIndex)
            } else {
                com.jabook.app.jabook.audio.core.result.Result
                    .Success(null)
            }
        }

        /**
         * Gets the track index at the specified position.
         *
         * @param playlist The current playlist
         * @param index The desired track index
         * @return Result with track index if valid, or error if out of bounds
         */
        fun getTrackIndex(
            playlist: Playlist,
            index: Int,
        ): Result<Int> =
            if (index in playlist.chapters.indices) {
                com.jabook.app.jabook.audio.core.result.Result
                    .Success(index)
            } else {
                com.jabook.app.jabook.audio.core.result.Result.Error(
                    IndexOutOfBoundsException("Index $index is out of bounds for playlist of size ${playlist.size}"),
                )
            }
    }
