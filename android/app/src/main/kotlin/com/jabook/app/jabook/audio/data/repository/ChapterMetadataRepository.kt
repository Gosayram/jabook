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

package com.jabook.app.jabook.audio.data.repository

import com.jabook.app.jabook.audio.core.result.Result
import com.jabook.app.jabook.audio.core.result.asResult
import com.jabook.app.jabook.audio.data.local.dao.ChapterMetadataDao
import com.jabook.app.jabook.audio.data.local.database.entity.ChapterMetadataEntity
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing chapter metadata.
 *
 * P-89: Includes an in-memory LRU cache to avoid repeated Room queries
 * on every `onMediaItemTransition`. The cache holds chapters for the
 * current + 2 adjacent books.
 *
 * Provides offline-first access to chapter metadata with reactive Flow API.
 */
@Singleton
public class ChapterMetadataRepository
    @Inject
    constructor(
        private val chapterDao: ChapterMetadataDao,
    ) {
        /** LRU cache: bookId → chapters. Size 3 (current + 2 adjacent). */
        private val metadataCache = HashMap<String, List<ChapterMetadataEntity>>(CACHE_SIZE)

        private val accessOrder = ArrayDeque<String>(CACHE_SIZE)

        /**
         * Gets all chapters for a book (Flow-based, for reactive UI).
         * Returns Flow<Result<List<ChapterMetadataEntity>>> for reactive updates.
         */
        public fun getChapters(bookId: String): Flow<Result<List<ChapterMetadataEntity>>> = chapterDao.getChapters(bookId).asResult()

        /**
         * P-89: Gets chapters with in-memory LRU cache.
         *
         * Use this for hot paths like `onMediaItemTransition` where
         * a Room query on every call is wasteful. Returns cached data
         * if available, otherwise loads from Room and caches the result.
         *
         * @param bookId Book identifier
         * @return List of chapters (from cache or Room)
         */
        public suspend fun getCachedChapters(bookId: String): List<ChapterMetadataEntity> {
            metadataCache[bookId]?.let { cached ->
                touchKey(bookId)
                return cached
            }

            val chapters =
                try {
                    val loaded =
                        chapterDao
                            .getChapters(bookId)
                            .first()
                    putCache(bookId, loaded)
                    LogUtils.d(TAG, "Cache miss for book=$bookId, loaded ${loaded.size} chapters")
                    loaded
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LogUtils.e(TAG, "Failed to load chapters for book=$bookId", e)
                    emptyList()
                }

            return chapters
        }

        /**
         * Gets a specific chapter by ID.
         */
        public fun getChapter(id: String): Flow<Result<ChapterMetadataEntity?>> = chapterDao.getChapter(id).asResult()

        /**
         * Saves chapter metadata.
         */
        public suspend fun saveChapter(
            bookId: String,
            fileIndex: Int,
            title: String,
            filePath: String?,
            startTime: Int = 0,
            endTime: Long? = null,
            duration: Long? = null,
        ): Result<Unit> =
            try {
                val id: String = "${bookId}_$fileIndex"
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
                invalidateCache(bookId)
                Result.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.Error(e)
            }

        /**
         * Saves multiple chapters.
         */
        public suspend fun saveChapters(
            bookId: String,
            chapters: List<ChapterMetadataEntity>,
        ): Result<Unit> =
            try {
                require(chapters.all { it.bookId == bookId }) {
                    val wrongBookIds = chapters.map { it.bookId }.distinct().filter { it != bookId }
                    "saveChapters expects chapters for bookId=$bookId, got mismatched bookIds=$wrongBookIds"
                }
                chapterDao.replaceChaptersForBook(bookId = bookId, chapters = chapters)
                invalidateCache(bookId)
                Result.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.Error(e)
            }

        /**
         * Deletes all chapters for a book.
         */
        public suspend fun deleteChapters(bookId: String): Result<Unit> =
            try {
                chapterDao.deleteChapters(bookId)
                invalidateCache(bookId)
                Result.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.Error(e)
            }

        /**
         * Deletes a specific chapter.
         */
        public suspend fun deleteChapter(id: String): Result<Unit> =
            try {
                chapterDao.deleteChapter(id)
                Result.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.Error(e)
            }

        /**
         * P-89: Invalidates the cache for a specific book.
         * Call when chapter metadata is modified externally.
         */
        public fun invalidateCache(bookId: String) {
            metadataCache.remove(bookId)
            accessOrder.remove(bookId)
            LogUtils.d(TAG, "Cache invalidated for book=$bookId")
        }

        /**
         * P-89: Clears the entire cache.
         */
        public fun clearCache() {
            metadataCache.clear()
            accessOrder.clear()
            LogUtils.d(TAG, "Cache cleared")
        }

        private fun putCache(
            bookId: String,
            chapters: List<ChapterMetadataEntity>,
        ) {
            if (metadataCache.size >= CACHE_SIZE) {
                val evictKey = accessOrder.removeFirstOrNull()
                if (evictKey != null) metadataCache.remove(evictKey)
            }
            metadataCache[bookId] = chapters
            touchKey(bookId)
        }

        private fun touchKey(bookId: String) {
            accessOrder.remove(bookId)
            accessOrder.addLast(bookId)
        }

        private companion object {
            private const val TAG = "ChapterMetadataRepo"
            private const val CACHE_SIZE = 3
        }
    }
