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

package com.jabook.app.jabook.compose.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jabook.app.jabook.compose.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for search history operations.
 */
@Dao
interface SearchHistoryDao {
    /**
     * Get recent searches ordered by timestamp.
     *
     * @param limit Maximum number of results (default 10)
     */
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSearches(limit: Int = 10): Flow<List<SearchHistoryEntity>>

    /**
     * Insert a search query into history.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: SearchHistoryEntity)

    /**
     * Delete a specific search from history.
     */
    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteSearch(id: Long)

    /**
     * Clear all search history.
     */
    @Query("DELETE FROM search_history")
    suspend fun clearHistory()

    /**
     * Delete old searches, keeping only the most recent.
     *
     * @param keepCount Number of recent searches to keep
     */
    @Query(
        """
        DELETE FROM search_history 
        WHERE id NOT IN (
            SELECT id FROM search_history 
            ORDER BY timestamp DESC 
            LIMIT :keepCount
        )
        """,
    )
    suspend fun trimHistory(keepCount: Int = 50)
}
