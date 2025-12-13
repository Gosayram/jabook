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

package com.jabook.app.jabook.audio.data.repository

import com.jabook.app.jabook.audio.core.result.Result
import com.jabook.app.jabook.audio.core.result.asResult
import com.jabook.app.jabook.audio.data.local.dao.ChapterMetadataDao
import com.jabook.app.jabook.audio.data.local.database.entity.ChapterMetadataEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing chapter metadata.
 *
 * Provides offline-first access to chapter metadata with reactive Flow API.
 */
@Singleton
class ChapterMetadataRepository
    @Inject
    constructor(
        private val chapterDao: ChapterMetadataDao,
    ) {
        /**
         * Gets all chapters for a book.
         * Returns Flow<Result<List<ChapterMetadataEntity>>> for reactive updates.
         */
        fun getChapters(bookId: String): Flow<Result<List<ChapterMetadataEntity>>> = chapterDao.getChapters(bookId).asResult()

        /**
         * Gets a specific chapter by ID.
         */
        fun getChapter(id: String): Flow<Result<ChapterMetadataEntity?>> = chapterDao.getChapter(id).asResult()

        /**
         * Saves chapter metadata.
         */
        suspend fun saveChapter(
            bookId: String,
            fileIndex: Int,
            title: String,
            filePath: String?,
            startTime: Long = 0L,
            endTime: Long? = null,
            duration: Long? = null,
        ): Result<Unit> =
            try {
                val id = "${bookId}_$fileIndex"
                val entity =
                    ChapterMetadataEntity(
                        id = id,
                        bookId = bookId,
                        fileIndex = fileIndex,
                        title = title,
                        filePath = filePath,
                        startTime = startTime,
                        endTime = endTime,
                        duration = duration,
                        lastUpdated = System.currentTimeMillis(),
                    )
                chapterDao.upsertChapter(entity)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(e)
            }

        /**
         * Saves multiple chapters.
         */
        suspend fun saveChapters(chapters: List<ChapterMetadataEntity>): Result<Unit> =
            try {
                chapterDao.upsertChapters(chapters)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(e)
            }

        /**
         * Deletes all chapters for a book.
         */
        suspend fun deleteChapters(bookId: String): Result<Unit> =
            try {
                chapterDao.deleteChapters(bookId)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(e)
            }

        /**
         * Deletes a specific chapter.
         */
        suspend fun deleteChapter(id: String): Result<Unit> =
            try {
                chapterDao.deleteChapter(id)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(e)
            }
    }
