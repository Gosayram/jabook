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
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Entity mapping a search query to a specific topic.
 * Allows multiple queries to point to the same topic without data duplication.
 */
@Entity(
    tableName = "search_query_map",
    primaryKeys = ["query", "topic_id"],
    foreignKeys = [
        ForeignKey(
            entity = CachedTopicEntity::class,
            parentColumns = ["topic_id"],
            childColumns = ["topic_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["topic_id"]),
        Index(value = ["query"]),
    ],
)
public data class SearchQueryEntity(
    @ColumnInfo(name = "query")
    public val query: String,
    @ColumnInfo(name = "topic_id")
    public val topicId: String,
    // To maintain search result order
    @ColumnInfo(name = "rank")
    public val rank: Int,
)
