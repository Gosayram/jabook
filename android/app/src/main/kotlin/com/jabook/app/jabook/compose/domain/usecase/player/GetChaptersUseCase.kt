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
import com.jabook.app.jabook.compose.domain.model.Chapter
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for retrieving chapters of a book.
 *
 * Returns a reactive list of chapters for playback navigation.
 */
class GetChaptersUseCase
    @Inject
    constructor(
        private val booksRepository: BooksRepository,
    ) {
        /**
         * Get all chapters for a specific book.
         *
         * @param bookId ID of the book
         * @return Flow of chapters ordered by chapter index
         */
        operator fun invoke(bookId: String): Flow<List<Chapter>> = booksRepository.getChapters(bookId)
    }
