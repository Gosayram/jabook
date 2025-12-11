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

package com.jabook.app.jabook.compose.domain.model

import com.jabook.app.jabook.compose.data.local.entity.ChapterEntity
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Domain model representing a chapter within an audiobook.
 *
 * Each chapter corresponds to a single audio file or segment of the book.
 *
 * @property id Unique identifier
 * @property bookId Parent book identifier
 * @property title Chapter title
 * @property chapterIndex Display order in the book
 * @property fileIndex Index in the audio playlist
 * @property duration Chapter duration
 * @property fileUrl URL or path to audio file
 * @property position Current playback position within this chapter
 * @property isCompleted Whether chapter has been fully played
 * @property isDownloaded Whether audio file is downloaded
 */
data class Chapter(
    val id: String,
    val bookId: String,
    val title: String,
    val chapterIndex: Int,
    val fileIndex: Int,
    val duration: Duration,
    val fileUrl: String?,
    val position: Duration,
    val isCompleted: Boolean,
    val isDownloaded: Boolean,
) {
    /**
     * Remaining duration in this chapter.
     */
    val remainingDuration: Duration
        get() = duration - position

    /**
     * Progress within this chapter (0.0 to 1.0).
     */
    val progress: Float
        get() =
            if (duration.inWholeMilliseconds > 0) {
                (position.inWholeMilliseconds.toFloat() / duration.inWholeMilliseconds.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }

    /**
     * Whether chapter has been started.
     */
    val isStarted: Boolean
        get() = position.inWholeMilliseconds > 0

    /**
     * Display number (1-indexed) for UI.
     */
    val displayNumber: Int
        get() = chapterIndex + 1

    companion object {
        /**
         * Creates a sample Chapter for preview/testing.
         */
        fun preview() =
            Chapter(
                id = "1",
                bookId = "book1",
                title = "Chapter 1",
                chapterIndex = 0,
                fileIndex = 0,
                duration = 1800.milliseconds,
                fileUrl = null,
                position = 900.milliseconds,
                isCompleted = false,
                isDownloaded = false,
            )
    }
}

/**
 * Extension function to convert ChapterEntity to domain Chapter model.
 */
fun ChapterEntity.toChapter() =
    Chapter(
        id = id,
        bookId = bookId,
        title = title,
        chapterIndex = chapterIndex,
        fileIndex = fileIndex,
        duration = duration.milliseconds,
        fileUrl = fileUrl,
        position = position.milliseconds,
        isCompleted = isCompleted,
        isDownloaded = isDownloaded,
    )

/**
 * Extension function to convert domain Chapter to ChapterEntity.
 */
fun Chapter.toEntity() =
    ChapterEntity(
        id = id,
        bookId = bookId,
        title = title,
        chapterIndex = chapterIndex,
        fileIndex = fileIndex,
        duration = duration.inWholeMilliseconds,
        fileUrl = fileUrl,
        position = position.inWholeMilliseconds,
        isCompleted = isCompleted,
        isDownloaded = isDownloaded,
    )

/**
 * Extension function to convert list of ChapterEntities to list of Chapters.
 */
fun List<ChapterEntity>.toChapters(): List<Chapter> = map { it.toChapter() }
