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

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import com.jabook.app.jabook.compose.data.local.entity.CachedTopicEntity
import com.jabook.app.jabook.compose.data.local.entity.SearchQueryEntity

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
    suspend fun getResultsForQuery(query: String): List<CachedTopicEntity>

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

    /**
     * Delete all cached topics.
     */
    @Query("DELETE FROM cached_topics")
    suspend fun deleteAllTopics()

    /**
     * Delete all search query mappings.
     */
    @Query("DELETE FROM search_query_map")
    suspend fun deleteAllMappings()

    /**
     * Get count of cached topics.
     */
    @Query("SELECT COUNT(*) FROM cached_topics")
    suspend fun getTopicCount(): Int

    /**
     * Fast search in indexed topics by title and author.
     * Uses LIKE for pattern matching (case-insensitive on most SQLite versions).
     *
     * @param query Search query (will search in title and author)
     * @param limit Maximum number of results
     * @return List of matching topics
     */
    @Query(
        """
        SELECT * FROM cached_topics
        WHERE (title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%')
          AND category IS NOT NULL AND category != ''
        ORDER BY 
          CASE WHEN title LIKE :query || '%' THEN 1 ELSE 2 END,
          seeders DESC, 
          timestamp DESC
        LIMIT :limit
    """,
    )
    suspend fun searchIndexedTopics(
        query: String,
        limit: Int = 100,
    ): List<CachedTopicEntity>

    /**
     * Get all indexed topics (for browsing).
     *
     * @param limit Maximum number of results
     * @param offset Pagination offset
     * @return List of topics ordered by seeders and timestamp
     */
    @Query(
        """
        SELECT * FROM cached_topics
        ORDER BY seeders DESC, timestamp DESC
        LIMIT :limit OFFSET :offset
    """,
    )
    suspend fun getAllIndexedTopics(
        limit: Int = 100,
        offset: Int = 0,
    ): List<CachedTopicEntity>

    /**
     * Get topics that need updating (older than threshold or different index version).
     *
     * @param maxAgeMs Maximum age in milliseconds (topics older than this need update)
     * @param currentIndexVersion Current index version (topics with different version need update)
     * @param limit Maximum number of topics to return
     * @return List of topics that need updating
     */
    @Query(
        """
        SELECT * FROM cached_topics
        WHERE last_updated < :maxAgeMs OR index_version != :currentIndexVersion
        ORDER BY last_updated ASC
        LIMIT :limit
    """,
    )
    suspend fun getTopicsNeedingUpdate(
        maxAgeMs: Long,
        currentIndexVersion: Int,
        limit: Int = 1000,
    ): List<CachedTopicEntity>

    /**
     * Get topic IDs that exist in database (for incremental update check).
     *
     * @param topicIds List of topic IDs to check
     * @return Set of topic IDs that exist in database
     */
    @Query(
        """
        SELECT topic_id FROM cached_topics
        WHERE topic_id IN (:topicIds)
    """,
    )
    suspend fun getExistingTopicIds(topicIds: List<String>): List<String>

    /**
     * Get index metadata (oldest and newest timestamps, total count).
     */
    @RewriteQueriesToDropUnusedColumns
    @Query(
        """
        SELECT 
            COUNT(*) as count,
            MIN(timestamp) as oldest,
            MAX(timestamp) as newest,
            MIN(last_updated) as oldest_updated,
            MAX(last_updated) as newest_updated
        FROM cached_topics
    """,
    )
    suspend fun getIndexMetadata(): IndexMetadata?
}

/**
 * Index metadata for monitoring.
 */
data class IndexMetadata(
    @ColumnInfo(name = "count")
    val count: Int,
    @ColumnInfo(name = "oldest")
    val oldest: Long?,
    @ColumnInfo(name = "newest")
    val newest: Long?,
    @ColumnInfo(name = "oldest_updated")
    val oldestUpdated: Long?,
    @ColumnInfo(name = "newest_updated")
    val newestUpdated: Long?,
)
