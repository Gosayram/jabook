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
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for storing favorite audiobooks.
 * Favorites are audiobooks marked by users from search results for quick access.
 */
@Entity(
    tableName = "favorites",
    indices = [Index(value = ["added_to_favorites"])],
)
public data class FavoriteEntity(
    @PrimaryKey
    @ColumnInfo(name = "topic_id") val topicId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "author") val author: String,
    @ColumnInfo(name = "category") val category: String,
    @ColumnInfo(name = "size") val size: String,
    @ColumnInfo(name = "seeders") val seeders: Int = 0,
    @ColumnInfo(name = "leechers") val leechers: Int = 0,
    @ColumnInfo(name = "magnet_url") val magnetUrl: String,
    @ColumnInfo(name = "cover_url") val coverUrl: String? = null,
    @ColumnInfo(name = "performer") val performer: String? = null,
    // JSON array of genres as comma-separated string
    @ColumnInfo(name = "genres") val genres: String? = null,
    // ISO 8601 timestamps
    @ColumnInfo(name = "added_date") val addedDate: String,
    @ColumnInfo(name = "added_to_favorites") val addedToFavorites: String,
    // Optional metadata
    @ColumnInfo(name = "duration") val duration: String? = null,
    @ColumnInfo(name = "bitrate") val bitrate: String? = null,
    @ColumnInfo(name = "audio_codec") val audioCodec: String? = null,
)
