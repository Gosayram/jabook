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
 * This entity stores comprehensive book metadata including playback state,
 * download status, and user preferences.
 *
 * @property id Unique identifier for the book
 * @property title Book title
 * @property author Book author
 * @property coverUrl URL to book cover image
 * @property description Book description/synopsis
 * @property totalDuration Total duration of all chapters in milliseconds
 * @property currentPosition Current playback position in milliseconds
 * @property totalProgress Overall progress as a percentage (0.0 to 1.0)
 * @property currentChapterIndex Index of the currently playing chapter
 * @property downloadStatus Download status (NOT_DOWNLOADED, DOWNLOADING, DOWNLOADED, FAILED)
 * @property downloadProgress Download progress (0.0 to 1.0)
 * @property localPath Local file system path where book files are stored
 * @property addedDate Timestamp when book was added to library (milliseconds since epoch)
 * @property lastPlayedDate Timestamp when book was last played (milliseconds since epoch)
 * @property isFavorite Whether user has marked this book as favorite
 * @property sourceUrl Source URL where book was obtained from (e.g., rutracker link)
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
    @ColumnInfo(name = "current_position")
    val currentPosition: Long = 0,
    @ColumnInfo(name = "total_progress")
    val totalProgress: Float = 0f,
    @ColumnInfo(name = "current_chapter_index")
    val currentChapterIndex: Int = 0,
    @ColumnInfo(name = "download_status")
    val downloadStatus: String = "NOT_DOWNLOADED",
    @ColumnInfo(name = "download_progress")
    val downloadProgress: Float = 0f,
    @ColumnInfo(name = "local_path")
    val localPath: String? = null,
    @ColumnInfo(name = "added_date")
    val addedDate: Long,
    @ColumnInfo(name = "last_played_date")
    val lastPlayedDate: Long? = null,
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
    @ColumnInfo(name = "source_url")
    val sourceUrl: String? = null,
    // Legacy field for backwards compatibility
    @ColumnInfo(name = "is_downloaded")
    val isDownloaded: Boolean = false,
)

/**
 * Room entity for storing chapter information.
 *
 * Each chapter belongs to a book and represents a single audio file or segment.
 *
 * @property id Unique identifier for the chapter
 * @property bookId Foreign key reference to the parent book
 * @property title Chapter title
 * @property chapterIndex Display order of the chapter in the book
 * @property fileIndex Index of the audio file in the playlist
 * @property duration Chapter duration in milliseconds
 * @property fileUrl URL or path to the chapter's audio file
 * @property position Current playback position within this chapter (milliseconds)
 * @property isCompleted Whether this chapter has been fully played
 * @property isDownloaded Whether this chapter's audio file is downloaded locally
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
    @ColumnInfo(name = "position")
    val position: Long = 0,
    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,
    @ColumnInfo(name = "is_downloaded")
    val isDownloaded: Boolean = false,
)
