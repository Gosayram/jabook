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

package com.jabook.app.jabook.audio.service

import com.jabook.app.jabook.audio.player.position.PositionSaver
import javax.inject.Inject

/**
 * Manages service lifecycle operations.
 *
 * Handles saving state when service is destroyed and restoring when service is created.
 */
class ServiceLifecycleManager
    @Inject
    constructor(
        private val positionSaver: PositionSaver,
    ) {
        /**
         * Called when service is being destroyed.
         * Saves current playback state.
         */
        suspend fun onServiceDestroyed(
            bookId: String,
            trackIndex: Int,
            position: Long,
        ) {
            // Stop periodic saving
            positionSaver.stopPeriodicSaving()

            // Save position immediately
            positionSaver.saveImmediately(bookId, trackIndex, position)
        }

        /**
         * Called when service is created.
         * Prepares for playback restoration.
         */
        fun onServiceCreated() {
            // Service is ready, position restoration will be handled by RestorePlaybackUseCase
        }
    }
