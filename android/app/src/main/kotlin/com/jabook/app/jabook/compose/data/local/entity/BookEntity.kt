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

/**
 * Room entity for storing book information.
 *
 * This matches the existing database schema for compatibility with
 * the Flutter/native hybrid implementation.
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "author")
    val author: String,
    @ColumnInfo(name = "cover_url")
    val coverUrl: String?,
    @ColumnInfo(name = "description")
    val description: String?,
    @ColumnInfo(name = "total_duration")
    val totalDuration: Long,
    @ColumnInfo(name = "added_date")
    val addedDate: Long,
    @ColumnInfo(name = "last_played_date")
    val lastPlayedDate: Long?,
    @ColumnInfo(name = "current_position")
    val currentPosition: Long = 0,
    @ColumnInfo(name = "current_chapter_index")
    val currentChapterIndex: Int = 0,
    @ColumnInfo(name = "is_downloaded")
    val isDownloaded: Boolean = false,
    @ColumnInfo(name = "download_progress")
    val downloadProgress: Float = 0f,
)

/**
 * Room entity for storing chapter information.
 */
@Entity(
    tableName = "chapters",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = androidx.room.ForeignKey.CASCADE,
        ),
    ],
    indices = [androidx.room.Index(value = ["book_id"])],
)
data class ChapterEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "book_id")
    val bookId: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "chapter_index")
    val chapterIndex: Int,
    @ColumnInfo(name = "file_index")
    val fileIndex: Int,
    @ColumnInfo(name = "duration")
    val duration: Long,
    @ColumnInfo(name = "file_url")
    val fileUrl: String?,
    @ColumnInfo(name = "is_downloaded")
    val isDownloaded: Boolean = false,
)
