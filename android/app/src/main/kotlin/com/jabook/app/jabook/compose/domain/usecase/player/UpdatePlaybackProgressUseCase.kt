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

package com.jabook.app.jabook.compose.domain.usecase.player

import com.jabook.app.jabook.compose.data.repository.BooksRepository
import com.jabook.app.jabook.compose.domain.model.Result
import javax.inject.Inject

/**
 * Use case for updating playback progress.
 *
 * Updates both the book and chapter playback progress,
 * ensuring proper synchronization of playback state.
 */
class UpdatePlaybackProgressUseCase
    @Inject
    constructor(
        private val booksRepository: BooksRepository,
    ) {
        /**
         * Update playback progress for a book.
         *
         * @param bookId ID of the book
         * @param position Current playback position in milliseconds
         * @param chapterIndex Current chapter index
         * @return Result indicating success or failure
         */
        suspend operator fun invoke(
            bookId: String,
            position: Long,
            chapterIndex: Int,
        ): Result<Unit> =
            try {
                booksRepository.updatePlaybackPosition(
                    bookId = bookId,
                    position = position,
                    chapterIndex = chapterIndex,
                )
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(e)
            }
    }
