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

package com.jabook.app.jabook.compose.domain.usecase.library

import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.compose.domain.model.Result
import javax.inject.Inject

/**
 * Use case for toggling favorite status of a book.
 *
 * Business logic wrapper for updating book favorite status.
 */
class ToggleFavoriteUseCase
    @Inject
    constructor(
        private val booksDao: BooksDao,
    ) {
        /**
         * Toggle favorite status for a book.
         *
         * @param bookId ID of the book to toggle
         * @param isFavorite New favorite status
         * @return Result indicating success or failure
         */
        suspend operator fun invoke(
            bookId: String,
            isFavorite: Boolean,
        ): Result<Unit> =
            try {
                booksDao.updateFavoriteStatus(bookId, isFavorite)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(e)
            }
    }
