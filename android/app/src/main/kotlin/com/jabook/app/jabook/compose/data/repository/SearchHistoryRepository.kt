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

package com.jabook.app.jabook.compose.data.repository

import com.jabook.app.jabook.compose.data.local.dao.SearchHistoryDao
import com.jabook.app.jabook.compose.data.local.entity.SearchHistoryEntity
import com.jabook.app.jabook.compose.data.local.entity.toSearchHistoryItem
import com.jabook.app.jabook.compose.domain.model.SearchHistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for search history.
 *
 * Manages recent search queries with persistence.
 */
@Singleton
public class SearchHistoryRepository
    @Inject
    constructor(
        private val searchHistoryDao: SearchHistoryDao,
    ) {
        /**
         * Get recent searches as Flow.
         */
        public fun getRecentSearches(limit: Int = 10): Flow<List<SearchHistoryItem>> =
            searchHistoryDao.getRecentSearches(limit).map { history -> history.map { it.toSearchHistoryItem() } }

        /**
         * Save a search query to history.
         * Deduplicates by normalized query: updates timestamp if exists, inserts otherwise.
         */
        public suspend fun saveSearch(
            query: String,
            resultCount: Int = 0,
        ) {
            val trimmed = query.trim().replace(Regex("\\s+"), " ")
            if (trimmed.isBlank()) return
            val normalized = trimmed.lowercase()
            val existing = searchHistoryDao.findByNormalizedQuery(normalized)
            if (existing != null) {
                searchHistoryDao.updateTimestamp(existing.id, System.currentTimeMillis(), resultCount)
            } else {
                searchHistoryDao.insertSearch(
                    SearchHistoryEntity(
                        query = trimmed,
                        normalizedQuery = normalized,
                        timestamp = System.currentTimeMillis(),
                        resultCount = resultCount,
                    ),
                )
            }
            // Trim old history to keep database size manageable
            searchHistoryDao.trimHistory(keepCount = 50)
        }

        /**
         * Delete a specific search from history.
         */
        public suspend fun deleteSearch(id: Long) {
            searchHistoryDao.deleteSearch(id)
        }

        /**
         * Clear all search history.
         */
        public suspend fun clearAll() {
            searchHistoryDao.clearHistory()
        }
    }
