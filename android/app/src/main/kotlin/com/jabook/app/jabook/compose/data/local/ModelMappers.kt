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

package com.jabook.app.jabook.compose.data.local

import com.jabook.app.jabook.compose.data.local.entity.BookEntity
import com.jabook.app.jabook.compose.data.local.entity.ChapterEntity
import com.jabook.app.jabook.compose.data.model.Book
import com.jabook.app.jabook.compose.data.model.Chapter

/**
 * Extension functions for converting between domain models and Room entities.
 *
 * This keeps the domain layer clean and independent of Room.
 */

/**
 * Converts a BookEntity with its chapters to a domain Book model.
 */
fun BookEntity.toDomainModel(chapters: List<ChapterEntity>): Book =
    Book(
        id = id,
        title = title,
        author = author,
        coverUrl = coverUrl,
        description = description,
        totalDuration = totalDuration,
        addedDate = addedDate,
        lastPlayedDate = lastPlayedDate,
        currentPosition = currentPosition,
        currentChapterIndex = currentChapterIndex,
        isDownloaded = isDownloaded,
        downloadProgress = downloadProgress,
        chapters = chapters.map { it.toDomainModel() },
    )

/**
 * Converts a ChapterEntity to a domain Chapter model.
 */
fun ChapterEntity.toDomainModel(): Chapter =
    Chapter(
        id = id,
        bookId = bookId,
        title = title,
        chapterIndex = chapterIndex,
        fileIndex = fileIndex,
        duration = duration,
        fileUrl = fileUrl,
        isDownloaded = isDownloaded,
    )

/**
 * Converts a domain Book to a BookEntity (without chapters).
 */
fun Book.toEntity(): BookEntity =
    BookEntity(
        id = id,
        title = title,
        author = author,
        coverUrl = coverUrl,
        description = description,
        totalDuration = totalDuration,
        addedDate = addedDate,
        lastPlayedDate = lastPlayedDate,
        currentPosition = currentPosition,
        currentChapterIndex = currentChapterIndex,
        isDownloaded = isDownloaded,
        downloadProgress = downloadProgress,
    )

/**
 * Converts a domain Chapter to a ChapterEntity.
 */
fun Chapter.toEntity(): ChapterEntity =
    ChapterEntity(
        id = id,
        bookId = bookId,
        title = title,
        chapterIndex = chapterIndex,
        fileIndex = fileIndex,
        duration = duration,
        fileUrl = fileUrl,
        isDownloaded = isDownloaded,
    )
