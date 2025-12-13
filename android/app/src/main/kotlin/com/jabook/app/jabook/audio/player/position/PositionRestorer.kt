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

import com.jabook.app.jabook.audio.core.model.PlaybackState
import com.jabook.app.jabook.audio.core.result.Result
import com.jabook.app.jabook.audio.domain.usecase.RestorePlaybackUseCase
import javax.inject.Inject

/**
 * Handles restoration of playback position.
 *
 * Loads saved playback position from local storage and applies it to the player.
 */
class PositionRestorer
    @Inject
    constructor(
        private val restorePlaybackUseCase: RestorePlaybackUseCase,
    ) {
        /**
         * Restores the playback position for a book.
         *
         * @param bookId The book ID
         * @return Result containing the saved playback state, or null if not found
         */
        suspend fun restorePosition(bookId: String): Result<PlaybackState?> = restorePlaybackUseCase(bookId)

        /**
         * Applies the restored playback state to the player.
         *
         * @param playerManager The ExoPlayer manager
         * @param playbackState The restored playback state
         */
        fun applyRestoredState(
            playerManager: com.jabook.app.jabook.audio.player.exoplayer.ExoPlayerManager,
            playbackState: PlaybackState,
        ) {
            // Seek to the saved position
            playerManager.seekTo(playbackState.currentTrackIndex, playbackState.currentPosition)

            // Set playback speed if different from default
            if (playbackState.playbackSpeed != 1.0f) {
                playerManager.setPlaybackSpeed(playbackState.playbackSpeed)
            }

            // Note: isPlaying state should be controlled by the service, not restored automatically
        }
    }
