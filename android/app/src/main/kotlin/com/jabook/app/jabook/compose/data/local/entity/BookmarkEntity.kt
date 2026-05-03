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
import androidx.room.PrimaryKey
import com.jabook.app.jabook.compose.domain.model.BookmarkItem

/**
 * Room entity for per-book timeline bookmarks.
 *
 * Supports optional text note and optional short voice note file path.
 */
@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["book_id"]),
        Index(value = ["book_id", "position_ms"]),
    ],
)
public data class BookmarkEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    @ColumnInfo(name = "note_text") val noteText: String? = null,
    @ColumnInfo(name = "note_audio_path") val noteAudioPath: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

public fun BookmarkEntity.toBookmarkItem(): BookmarkItem =
    BookmarkItem(
        id = id,
        bookId = bookId,
        chapterIndex = chapterIndex,
        positionMs = positionMs,
        noteText = noteText,
        noteAudioPath = noteAudioPath,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

public fun BookmarkItem.toBookmarkEntity(): BookmarkEntity =
    BookmarkEntity(
        id = id,
        bookId = bookId,
        chapterIndex = chapterIndex,
        positionMs = positionMs,
        noteText = noteText,
        noteAudioPath = noteAudioPath,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
