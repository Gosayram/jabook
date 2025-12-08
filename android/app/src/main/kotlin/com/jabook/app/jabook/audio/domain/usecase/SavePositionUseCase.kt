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

import com.jabook.app.jabook.audio.core.result.Result
import com.jabook.app.jabook.audio.data.repository.PlaybackPositionRepository
import javax.inject.Inject

/**
 * Use case for saving playback position.
 */
class SavePositionUseCase
    @Inject
    constructor(
        private val positionRepository: PlaybackPositionRepository,
    ) {
        /**
         * Saves the playback position for a book.
         *
         * @param bookId The book ID
         * @param trackIndex The current track index
         * @param position The playback position in milliseconds
         * @return Result indicating success or failure
         */
        suspend operator fun invoke(
            bookId: String,
            trackIndex: Int,
            position: Long,
        ): Result<Unit> = positionRepository.savePosition(bookId, trackIndex, position)
    }
