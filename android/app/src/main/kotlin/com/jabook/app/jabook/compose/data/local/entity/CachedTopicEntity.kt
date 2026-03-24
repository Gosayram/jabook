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

package com.jabook.app.jabook.compose.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.jabook.app.jabook.compose.data.remote.model.SearchResult

/**
 * Normalized entity for storing topic (audiobook) information.
 * Acts as the single source of truth for topic details.
 *
 * Optimization: magnetUrl, torrentUrl, and coverUrl are NOT stored during indexing
 * to reduce index size and improve indexing speed. These fields are retrieved
 * in real-time when opening a topic via getTopicDetails().
 *
 * Fields stored in index (for search):
 * - topicId, title, author, category (required for search)
 * - size, seeders, leechers (for filtering and sorting)
 *
 * Fields NOT stored (retrieved on-demand):
 * - magnetUrl, torrentUrl, coverUrl (retrieved via getTopicDetails())
 */
@Entity(
    tableName = "cached_topics",
    indices = [
        androidx.room.Index(value = ["title"]), // For fast title search
        androidx.room.Index(value = ["author"]), // For fast author search
        androidx.room.Index(value = ["timestamp"]), // For sorting by date
        androidx.room.Index(value = ["seeders"]), // For sorting by popularity
    ],
)
public data class CachedTopicEntity(
    @PrimaryKey
    @ColumnInfo(name = "topic_id")
    val topicId: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "author")
    val author: String,
    @ColumnInfo(name = "category")
    val category: String,
    @ColumnInfo(name = "size")
    val size: String,
    @ColumnInfo(name = "seeders")
    val seeders: Int,
    @ColumnInfo(name = "leechers")
    val leechers: Int,
    // These fields are kept for backward compatibility but NOT filled during indexing
    // They are retrieved on-demand via getTopicDetails() when needed
    @ColumnInfo(name = "magnet_url")
    val magnetUrl: String? = null,
    @ColumnInfo(name = "torrent_url")
    val torrentUrl: String? = null,
    @ColumnInfo(name = "cover_url")
    val coverUrl: String? = null,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long = System.currentTimeMillis(), // When this record was last updated
    @ColumnInfo(name = "index_version")
    val indexVersion: Int = 1, // Version of index when this was indexed
)

/**
 * Maps domain SearchResult to CachedTopicEntity.
 *
 * Optimization: magnetUrl, torrentUrl, and coverUrl are NOT saved during indexing
 * to reduce index size. These will be retrieved on-demand via getTopicDetails().
 *
 * @param indexVersion Version of index when this was indexed (default: 1)
 */
public fun SearchResult.toCachedTopicEntity(indexVersion: Int = 1): CachedTopicEntity {
    val now = System.currentTimeMillis()
    return CachedTopicEntity(
        topicId = topicId,
        title = title,
        author = author,
        category = category.ifBlank { "Аудиокниги" }, // Fallback for blank category during indexing
        size = size,
        seeders = seeders,
        leechers = leechers,
        // Do NOT save these fields during indexing - retrieve on-demand
        magnetUrl = null,
        torrentUrl = null,
        coverUrl = null,
        timestamp = now,
        lastUpdated = now,
        indexVersion = indexVersion,
    )
}

/**
 * Maps CachedTopicEntity back to domain SearchResult.
 *
 * Note: magnetUrl, torrentUrl, and coverUrl will be null from index.
 * These should be retrieved on-demand via getTopicDetails() when needed.
 */
public fun CachedTopicEntity.toSearchResult(): SearchResult =
    SearchResult(
        topicId = topicId,
        title = title,
        author = author,
        category = category,
        size = size,
        seeders = seeders,
        leechers = leechers,
        // These fields are null from index - retrieve via getTopicDetails() when needed
        magnetUrl = magnetUrl,
        torrentUrl = torrentUrl ?: "", // SearchResult requires non-null, use empty string if null
        coverUrl = coverUrl,
    )
