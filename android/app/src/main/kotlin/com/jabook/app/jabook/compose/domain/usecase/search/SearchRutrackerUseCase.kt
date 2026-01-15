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

import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.repository.RutrackerRepository
import com.jabook.app.jabook.compose.domain.model.Result
import com.jabook.app.jabook.compose.domain.model.RutrackerSearchResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * Use case for searching audiobooks on Rutracker.
 *
 * Performs online search and returns results from Rutracker.
 */
public class SearchRutrackerUseCase
    @Inject
    constructor(
        private val rutrackerRepository: RutrackerRepository,
        private val loggerFactory: LoggerFactory,
    ) {
        private val logger = loggerFactory.get("SearchRutrackerUseCase")

        /**
         * Search for audiobooks by query.
         *
         * @param query Search query
         * @return Result with list of search results
         */
        public suspend operator fun invoke(query: String): Flow<Result<List<RutrackerSearchResult>, com.jabook.app.jabook.compose.domain.model.AppError>> {
            if (query.isBlank()) {
                logger.d { "Empty query provided, returning empty results" }
                return kotlinx.coroutines.flow.flowOf(Result.Success(emptyList()))
            }

            logger.d { "🔍 Invoking search for query: '$query'" }
            // Direct call to repository search (returns compatible type Result<List<RutrackerSearchResult>, AppError>)
            return rutrackerRepository
                .search(query)
                .onEach { result ->
                    when (result) {
                        is Result.Success -> {
                            logger.d {
                                "✅ Search completed: ${result.data.size} results for query '$query'"
                            }
                        }
                        is Result.Error -> {
                            logger.e(
                                { "❌ Search failed for query '$query': ${result.error.message}" },
                                result.error.cause,
                            )
                        }
                        is Result.Loading -> {
                            logger.d { "⏳ Search in progress for query '$query'" }
                        }
                    }
                }
        }
    }
