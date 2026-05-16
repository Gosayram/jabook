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

import com.jabook.app.jabook.audio.data.local.dao.LufsCacheDao
import com.jabook.app.jabook.audio.data.local.database.entity.LufsCacheEntity
import com.jabook.app.jabook.util.LogUtils
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that manages LUFS analysis cache with automatic invalidation.
 *
 * Before returning a cached value, [getLufsForFile] checks whether the
 * underlying file has changed (size or last-modified timestamp). If the file
 * was replaced — e.g. a new version of an audiobook was downloaded — the stale
 * entry is deleted and `null` is returned so the caller can trigger a fresh
 * analysis.
 *
 * **Thread-safety:** All DAO operations are `suspend` functions. Call from a
 * coroutine scope (e.g. `Dispatchers.IO`).
 *
 * P-02: LUFS cache with content-hash-based invalidation.
 */
@Singleton
public class LufsCacheRepository
    @Inject
    constructor(
        private val lufsCacheDao: LufsCacheDao,
    ) {
        /**
         * Returns the cached LUFS value for [filePath], or `null` if:
         * - no cache entry exists, or
         * - the file has changed since it was analysed (stale → deleted).
         *
         * @param filePath Absolute path to the audio file.
         * @return The cached LUFS value, or `null` if unavailable/stale.
         */
        public suspend fun getLufsForFile(filePath: String): Float? {
            val cached = lufsCacheDao.getForPath(filePath) ?: return null
            val file = File(filePath)

            // File no longer exists — clean up stale entry
            if (!file.exists()) {
                lufsCacheDao.delete(filePath)
                LogUtils.d(TAG) { "Cache miss (file deleted): $filePath" }
                return null
            }

            // Invalidate if file was modified since analysis
            if (file.length() != cached.fileSize ||
                file.lastModified() != cached.fileLastModified
            ) {
                lufsCacheDao.delete(filePath)
                LogUtils.d(TAG) { "Cache invalidated (file changed): $filePath" }
                return null
            }

            return cached.lufsValue
        }

        /**
         * Returns all cached LUFS entries for a book.
         *
         * Entries are **not** validated against disk — callers that need guaranteed
         * freshness should use [getLufsForFile] instead.
         */
        public suspend fun getLufsForBook(bookId: String): List<LufsCacheEntity> = lufsCacheDao.getForBook(bookId)

        /**
         * Stores a LUFS analysis result in the cache.
         *
         * @param filePath  Absolute path to the audio file.
         * @param bookId    Parent book identifier.
         * @param lufsValue Measured loudness in LUFS.
         */
        public suspend fun put(
            filePath: String,
            bookId: String,
            lufsValue: Float,
        ) {
            val file = File(filePath)
            val entity =
                LufsCacheEntity(
                    filePath = filePath,
                    bookId = bookId,
                    lufsValue = lufsValue,
                    fileSize = file.length(),
                    fileLastModified = file.lastModified(),
                )
            lufsCacheDao.upsert(entity)
            LogUtils.d(TAG) { "Cached LUFS=$lufsValue for $filePath" }
        }

        /**
         * Invalidates cache entries for files belonging to [bookId] that have
         * changed on disk since they were last analysed.
         *
         * @return The number of entries invalidated.
         */
        public suspend fun invalidateStaleForBook(bookId: String): Int {
            val entries = lufsCacheDao.getForBook(bookId)
            var invalidated = 0

            for (entry in entries) {
                val file = File(entry.filePath)
                if (!file.exists() ||
                    file.length() != entry.fileSize ||
                    file.lastModified() != entry.fileLastModified
                ) {
                    lufsCacheDao.delete(entry.filePath)
                    invalidated++
                }
            }

            if (invalidated > 0) {
                LogUtils.d(TAG) { "Invalidated $invalidated LUFS entries for book=$bookId" }
            }

            return invalidated
        }

        /**
         * Removes all LUFS cache entries for a book.
         */
        public suspend fun deleteForBook(bookId: String) {
            lufsCacheDao.deleteForBook(bookId)
            LogUtils.d(TAG) { "Deleted LUFS cache for book=$bookId" }
        }

        private companion object {
            private const val TAG = "LufsCacheRepo"
        }
    }
