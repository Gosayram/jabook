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

package com.jabook.app.jabook.compose.domain.usecase.search

import android.util.Log
import com.jabook.app.jabook.compose.data.repository.RutrackerRepository
import com.jabook.app.jabook.compose.domain.model.Result
import com.jabook.app.jabook.compose.domain.model.RutrackerSearchResult
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
        companion object {
            private const val TAG = "SearchRutrackerUseCase"
        }

        /**
         * Search for audiobooks by query.
         *
         * @param query Search query
         * @return Result with list of search results
         */
        suspend operator fun invoke(query: String): Result<List<RutrackerSearchResult>> {
            if (query.isBlank()) {
                Log.d(TAG, "Empty query provided, returning empty results")
                return Result.Success(emptyList())
            }

            Log.d(TAG, "🔍 Invoking search for query: '$query'")
            val result = rutrackerRepository.search(query)

            when (result) {
                is Result.Success -> {
                    Log.d(
                        TAG,
                        "✅ Search completed: ${result.data.size} results for query '$query'",
                    )
                }
                is Result.Error -> {
                    Log.e(
                        TAG,
                        "❌ Search failed for query '$query': ${result.exception.message}",
                        result.exception,
                    )
                }
                is Result.Loading -> {
                    Log.d(TAG, "⏳ Search in progress for query '$query'")
                }
            }

            return result
        }
    }
