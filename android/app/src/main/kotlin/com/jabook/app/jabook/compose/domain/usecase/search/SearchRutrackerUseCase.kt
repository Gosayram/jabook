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

package com.jabook.app.jabook.compose.domain.usecase.search

import com.jabook.app.jabook.compose.data.remote.model.SearchResult
import com.jabook.app.jabook.compose.data.repository.RutrackerRepository
import com.jabook.app.jabook.compose.domain.model.Result
import javax.inject.Inject

/**
 * Use case for searching audiobooks on Rutracker.
 *
 * Performs online search and returns results from Rutracker.
 */
class SearchRutrackerUseCase
    @Inject
    constructor(
        private val rutrackerRepository: RutrackerRepository,
    ) {
        /**
         * Search for audiobooks by query.
         *
         * @param query Search query
         * @return Result with list of search results
         */
        suspend operator fun invoke(query: String): Result<List<SearchResult>> {
            if (query.isBlank()) {
                return Result.Success(emptyList())
            }

            return rutrackerRepository.search(query)
        }
    }
