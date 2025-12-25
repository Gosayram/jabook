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
import androidx.room.Transaction
import com.jabook.app.jabook.compose.data.local.entity.CachedTopicEntity
import com.jabook.app.jabook.compose.data.local.entity.SearchQueryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineSearchDao {
    /**
     * Insert or update cached topics.
     * Uses OnConflictStrategy.REPLACE to update existing topics with fresh data.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTopics(topics: List<CachedTopicEntity>)

    /**
     * Insert search query mappings.
     * Should be called inside a transaction after clearing old mappings for the query.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueryMappings(mappings: List<SearchQueryEntity>)

    /**
     * Delete all mappings for a specific query.
     */
    @Query("DELETE FROM search_query_map WHERE `query` = :query")
    suspend fun deleteMappingsForQuery(query: String)

    /**
     * Transaction to save search results:
     * 1. Upsert topics (update cache)
     * 2. Clear old results for this query
     * 3. Insert new mappings
     */
    @Transaction
    suspend fun saveSearchResults(
        query: String,
        topics: List<CachedTopicEntity>,
    ) {
        upsertTopics(topics)
        deleteMappingsForQuery(query)

        val mappings =
            topics.mapIndexed { index, topic ->
                SearchQueryEntity(
                    query = query,
                    topicId = topic.topicId,
                    rank = index,
                )
            }
        insertQueryMappings(mappings)
    }

    /**
     * Get search results for a specific query.
     * Joins CachedTopicEntity with SearchQueryEntity to return ordered topics.
     */
    @Transaction
    @Query(
        """
        SELECT T.* FROM cached_topics T
        INNER JOIN search_query_map S ON T.topic_id = S.topic_id
        WHERE S.`query` = :query
        ORDER BY S.rank ASC
    """,
    )
    fun getResultsForQuery(query: String): Flow<List<CachedTopicEntity>>

    /**
     * Get a single topic by ID.
     * Useful for quick lookup from other screens.
     */
    @Query("SELECT * FROM cached_topics WHERE topic_id = :topicId")
    suspend fun getTopicById(topicId: String): CachedTopicEntity?

    /**
     * Clear old cache entries (optional maintenance).
     */
    @Query("DELETE FROM cached_topics WHERE timestamp < :threshold")
    suspend fun clearOldCache(threshold: Long)
}
