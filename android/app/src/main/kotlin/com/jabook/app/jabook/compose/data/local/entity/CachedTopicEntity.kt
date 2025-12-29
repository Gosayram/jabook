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

package com.jabook.app.jabook.compose.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.jabook.app.jabook.compose.data.remote.model.SearchResult

/**
 * Normalized entity for storing topic (audiobook) information.
 * Acts as the single source of truth for topic details.
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
data class CachedTopicEntity(
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
    @ColumnInfo(name = "magnet_url")
    val magnetUrl: String?,
    @ColumnInfo(name = "torrent_url")
    val torrentUrl: String,
    @ColumnInfo(name = "cover_url")
    val coverUrl: String?,
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
 * @param indexVersion Version of index when this was indexed (default: 1)
 */
fun SearchResult.toCachedTopicEntity(indexVersion: Int = 1): CachedTopicEntity {
    val now = System.currentTimeMillis()
    return CachedTopicEntity(
        topicId = topicId,
        title = title,
        author = author,
        category = category,
        size = size,
        seeders = seeders,
        leechers = leechers,
        magnetUrl = magnetUrl,
        torrentUrl = torrentUrl,
        coverUrl = coverUrl,
        timestamp = now,
        lastUpdated = now,
        indexVersion = indexVersion,
    )
}

/**
 * Maps CachedTopicEntity back to domain SearchResult.
 */
fun CachedTopicEntity.toSearchResult(): SearchResult =
    SearchResult(
        topicId = topicId,
        title = title,
        author = author,
        category = category,
        size = size,
        seeders = seeders,
        leechers = leechers,
        magnetUrl = magnetUrl,
        torrentUrl = torrentUrl,
        coverUrl = coverUrl,
    )
