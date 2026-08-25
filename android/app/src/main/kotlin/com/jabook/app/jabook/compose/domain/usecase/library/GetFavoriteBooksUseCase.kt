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
import com.jabook.app.jabook.compose.domain.model.Book
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for retrieving favorite books.
 *
 * Returns all books marked as favorites by the user.
 */
public class GetFavoriteBooksUseCase
    @Inject
    constructor(
        private val booksRepository: BooksRepository,
    ) {
        /**
         * Get all favorite books.
         *
         * @return Flow of favorite books
         */
        public operator fun invoke(): Flow<List<Book>> = booksRepository.getFavoriteBooks()
    }
