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
import com.jabook.app.jabook.compose.data.local.entity.BookmarkEntity
import com.jabook.app.jabook.compose.domain.model.BookmarkItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BookmarkRepositoryTest {
    private lateinit var fakeDao: FakeBookmarkDao
    private lateinit var repository: BookmarkRepository

    @Before
    fun setUp() {
        fakeDao = FakeBookmarkDao()
        repository = BookmarkRepository(fakeDao)
    }

    @Test
    fun `addBookmark returns success and inserts bookmark`() =
        runTest {
            val result =
                repository.addBookmark(
                    bookId = "book-1",
                    chapterIndex = 0,
                    positionMs = 60000L,
                    noteText = "Chapter start",
                )

            assertTrue(result.isSuccess)
            val bookmark = result.getOrNull()!!
            assertEquals("book-1", bookmark.bookId)
            assertEquals(0, bookmark.chapterIndex)
            assertEquals(60000L, bookmark.positionMs)
            assertEquals("Chapter start", bookmark.noteText)
            assertEquals(1, fakeDao.upserted.size)
        }

    @Test
    fun `addBookmark rejects duplicate within threshold`() =
        runTest {
            fakeDao.bookmarks["book-1"] =
                listOf(
                    BookmarkEntity(
                        id = "existing",
                        bookId = "book-1",
                        chapterIndex = 0,
                        positionMs = 1000L,
                        createdAt = 0L,
                        updatedAt = 0L,
                    ),
                )

            val result =
                repository.addBookmark(
                    bookId = "book-1",
                    chapterIndex = 0,
                    positionMs = 2000L,
                )

            assertTrue(result.isFailure)
            assertEquals(0, fakeDao.upserted.size)
        }

    @Test
    fun `addBookmark allows bookmark outside duplicate threshold`() =
        runTest {
            fakeDao.bookmarks["book-1"] =
                listOf(
                    BookmarkEntity(
                        id = "existing",
                        bookId = "book-1",
                        chapterIndex = 0,
                        positionMs = 1000L,
                        createdAt = 0L,
                        updatedAt = 0L,
                    ),
                )

            val result =
                repository.addBookmark(
                    bookId = "book-1",
                    chapterIndex = 0,
                    positionMs = 10000L,
                )

            assertTrue(result.isSuccess)
            assertEquals(1, fakeDao.upserted.size)
        }

    @Test
    fun `addBookmark normalizes negative position to zero`() =
        runTest {
            val result =
                repository.addBookmark(
                    bookId = "book-1",
                    chapterIndex = 0,
                    positionMs = -100L,
                )

            assertTrue(result.isSuccess)
            assertEquals(0L, result.getOrNull()!!.positionMs)
        }

    @Test
    fun `addBookmark normalizes negative chapter index to zero`() =
        runTest {
            val result =
                repository.addBookmark(
                    bookId = "book-1",
                    chapterIndex = -1,
                    positionMs = 5000L,
                )

            assertTrue(result.isSuccess)
            assertEquals(0, result.getOrNull()!!.chapterIndex)
        }

    @Test
    fun `addBookmark calculates normalizedPosition when chapterDurationMs provided`() =
        runTest {
            val result =
                repository.addBookmark(
                    bookId = "book-1",
                    chapterIndex = 0,
                    positionMs = 3000L,
                    chapterDurationMs = 10000L,
                )

            assertTrue(result.isSuccess)
            assertEquals(0.3f, result.getOrNull()!!.normalizedPosition, 0.001f)
        }

    @Test
    fun `addBookmark clamps normalizedPosition to 1_0`() =
        runTest {
            val result =
                repository.addBookmark(
                    bookId = "book-1",
                    chapterIndex = 0,
                    positionMs = 50000L,
                    chapterDurationMs = 10000L,
                )

            assertTrue(result.isSuccess)
            assertEquals(1.0f, result.getOrNull()!!.normalizedPosition)
        }

    @Test
    fun `addBookmark returns zero normalizedPosition when chapterDurationMs is zero`() =
        runTest {
            val result =
                repository.addBookmark(
                    bookId = "book-1",
                    chapterIndex = 0,
                    positionMs = 5000L,
                )

            assertTrue(result.isSuccess)
            assertEquals(0f, result.getOrNull()!!.normalizedPosition)
        }

    @Test
    fun `addBookmark strips blank noteText`() =
        runTest {
            val result =
                repository.addBookmark(
                    bookId = "book-1",
                    chapterIndex = 0,
                    positionMs = 0L,
                    noteText = "   ",
                )

            assertTrue(result.isSuccess)
            assertEquals(null, result.getOrNull()!!.noteText)
        }

    @Test
    fun `updateBookmark returns success`() =
        runTest {
            val bookmark =
                BookmarkItem(
                    id = "bm-1",
                    bookId = "book-1",
                    chapterIndex = 2,
                    positionMs = 120000L,
                    normalizedPosition = 0.5f,
                    noteText = "Updated note",
                    createdAt = 1000L,
                    updatedAt = 2000L,
                )

            val result = repository.updateBookmark(bookmark)

            assertTrue(result.isSuccess)
            assertEquals(1, fakeDao.upserted.size)
        }

    @Test
    fun `deleteBookmark returns success`() =
        runTest {
            val result = repository.deleteBookmark("bm-1")

            assertTrue(result.isSuccess)
            assertEquals("bm-1", fakeDao.deletedIds.single())
        }

    @Test
    fun `observeBookmarks maps entities to domain models`() =
        runTest {
            fakeDao.bookmarks["book-1"] =
                listOf(
                    BookmarkEntity(
                        id = "bm-1",
                        bookId = "book-1",
                        chapterIndex = 0,
                        positionMs = 5000L,
                        normalizedPosition = 0.1f,
                        noteText = "Note",
                        createdAt = 100L,
                        updatedAt = 200L,
                    ),
                )

            val bookmarks = repository.observeBookmarks("book-1").first()

            assertEquals(1, bookmarks.size)
            assertEquals("bm-1", bookmarks[0].id)
            assertEquals("Note", bookmarks[0].noteText)
            assertEquals(0.1f, bookmarks[0].normalizedPosition)
        }

    @Test
    fun `addBookmark returns failure when DAO throws`() =
        runTest {
            fakeDao.throwOnUpsert = true

            val result =
                repository.addBookmark(
                    bookId = "book-1",
                    chapterIndex = 0,
                    positionMs = 0L,
                )

            assertTrue(result.isFailure)
        }

    @Test
    fun `deleteBookmark returns failure when DAO throws`() =
        runTest {
            fakeDao.throwOnDelete = true

            val result = repository.deleteBookmark("bm-1")

            assertTrue(result.isFailure)
        }

    private class FakeBookmarkDao : BookmarkDao {
        var bookmarks: MutableMap<String, List<BookmarkEntity>> = mutableMapOf()
        var upserted: MutableList<BookmarkEntity> = mutableListOf()
        var deletedIds: MutableList<String> = mutableListOf()
        var throwOnUpsert: Boolean = false
        var throwOnDelete: Boolean = false

        private val flows: MutableMap<String, MutableStateFlow<List<BookmarkEntity>>> = mutableMapOf()

        override fun getBookmarksForBookInternal(bookId: String): kotlinx.coroutines.flow.Flow<List<BookmarkEntity>> {
            val flow =
                flows.getOrPut(bookId) {
                    MutableStateFlow(bookmarks[bookId] ?: emptyList())
                }
            return flow
        }

        override fun getBookmarksForBook(bookId: String): kotlinx.coroutines.flow.Flow<List<BookmarkEntity>> =
            getBookmarksForBookInternal(bookId)

        override suspend fun getBookmarksForBookSync(bookId: String): List<BookmarkEntity> = bookmarks[bookId] ?: emptyList()

        override suspend fun upsertBookmark(bookmark: BookmarkEntity) {
            if (throwOnUpsert) throw RuntimeException("DB error")
            upserted.add(bookmark)
        }

        override suspend fun deleteBookmarkById(bookmarkId: String): Int {
            if (throwOnDelete) throw RuntimeException("DB error")
            deletedIds.add(bookmarkId)
            return 1
        }

        override suspend fun deleteBookmarksForBook(bookId: String): Int {
            bookmarks.remove(bookId)
            return 1
        }
    }
}
