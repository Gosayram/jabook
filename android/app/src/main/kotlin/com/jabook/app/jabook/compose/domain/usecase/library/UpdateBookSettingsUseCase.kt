// Copyright 2026 Jabook Contributors
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

package com.jabook.app.jabook.compose.domain.usecase.library

import com.jabook.app.jabook.compose.data.repository.BooksRepository
import javax.inject.Inject

/**
 * Use case to update per-book playback settings (seek intervals).
 */
public class UpdateBookSettingsUseCase
    @Inject
    constructor(
        private val booksRepository: BooksRepository,
    ) {
        /**
         * Updates the seek settings for a specific book.
         *
         * @param bookId The ID of the book to update
         * @param rewindDurationSeconds Rewind duration in seconds, or null to use global default
         * @param forwardDurationSeconds Forward duration in seconds, or null to use global default
         */
        suspend operator fun invoke(
            bookId: String,
            rewindDurationSeconds: Int?,
            forwardDurationSeconds: Int?,
        ) {
            booksRepository.updateBookSettings(bookId, rewindDurationSeconds, forwardDurationSeconds)
        }

        /**
         * Reset settings for a specific book (set to null).
         */
        suspend fun resetForBook(bookId: String) {
            booksRepository.updateBookSettings(bookId, null, null)
        }

        /**
         * Reset settings for ALL books.
         */
        suspend fun resetAll() {
            booksRepository.resetAllBookSettings()
        }
    }
