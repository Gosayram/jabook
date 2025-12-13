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
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.toBooks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Use case for retrieving books that are in progress.
 *
 * Returns books that have been started but not completed,
 * useful for "Continue Reading" features.
 */
class GetInProgressBooksUseCase
    @Inject
    constructor(
        private val booksDao: BooksDao,
    ) {
        /**
         * Get books that are currently in progress.
         *
         * Books are considered in progress if:
         * - They have a position > 0
         * - Their progress < 98%
         *
         * @return Flow of in-progress books
         */
        operator fun invoke(): Flow<List<Book>> = booksDao.getInProgressBooksFlow().map { it.toBooks() }
    }
