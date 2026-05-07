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

package com.jabook.app.jabook.compose.data.repository

import com.jabook.app.jabook.compose.data.local.dao.BookmarkDao
import com.jabook.app.jabook.compose.data.local.entity.toBookmarkEntity
import com.jabook.app.jabook.compose.data.local.entity.toBookmarkItem
import com.jabook.app.jabook.compose.domain.model.BookmarkItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
public class BookmarkRepository
    @Inject
    constructor(
        private val bookmarkDao: BookmarkDao,
    ) {
        public fun observeBookmarks(bookId: String): Flow<List<BookmarkItem>> =
            bookmarkDao
                .getBookmarksForBook(bookId)
                .map { bookmarks -> bookmarks.map { it.toBookmarkItem() } }

        public suspend fun addBookmark(
            bookId: String,
            chapterIndex: Int,
            positionMs: Long,
            noteText: String? = null,
            noteAudioPath: String? = null,
        ): Result<BookmarkItem> =
            withContext(Dispatchers.IO) {
                try {
                    val now = System.currentTimeMillis()
                    val bookmark =
                        BookmarkItem(
                            id = UUID.randomUUID().toString(),
                            bookId = bookId,
                            chapterIndex = chapterIndex.coerceAtLeast(0),
                            positionMs = positionMs.coerceAtLeast(0L),
                            noteText = noteText?.takeIf { it.isNotBlank() },
                            noteAudioPath = noteAudioPath,
                            createdAt = now,
                            updatedAt = now,
                        )
                    bookmarkDao.upsertBookmark(bookmark.toBookmarkEntity())
                    Result.success(bookmark)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

        public suspend fun updateBookmark(bookmark: BookmarkItem): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    bookmarkDao.upsertBookmark(
                        bookmark.copy(updatedAt = System.currentTimeMillis()).toBookmarkEntity(),
                    )
                    Result.success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

        public suspend fun deleteBookmark(bookmarkId: String): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    bookmarkDao.deleteBookmarkById(bookmarkId)
                    Result.success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
    }
