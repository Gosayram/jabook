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

package com.jabook.app.jabook.compose.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jabook.app.jabook.compose.data.local.JabookDatabase
import com.jabook.app.jabook.compose.data.local.entity.BookEntity
import com.jabook.app.jabook.compose.data.local.entity.BookmarkEntity
import com.jabook.app.jabook.compose.data.local.entity.ChapterEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BooksDaoScanUpsertTest {
    private lateinit var database: JabookDatabase
    private lateinit var booksDao: BooksDao
    private lateinit var chaptersDao: ChaptersDao
    private lateinit var bookmarkDao: BookmarkDao

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    JabookDatabase::class.java,
                ).build()
        booksDao = database.booksDao()
        chaptersDao = database.chaptersDao()
        bookmarkDao = database.bookmarkDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `scan refreshes file metadata without resetting book or chapter playback state`() =
        runBlocking {
            booksDao.insertBook(
                BookEntity(
                    id = "book",
                    title = "Old title",
                    author = "Old author",
                    coverUrl = "cover",
                    description = "description",
                    totalDuration = 500L,
                    currentPosition = 123L,
                    totalProgress = 0.4f,
                    currentChapterIndex = 2,
                    addedDate = 10L,
                    lastPlayedDate = 20L,
                    isFavorite = true,
                    preferredSpeed = 1.5f,
                ),
            )
            chaptersDao.insertAll(
                listOf(
                    ChapterEntity(
                        id = "first-chapter",
                        bookId = "book",
                        title = "First old chapter",
                        chapterIndex = 0,
                        fileIndex = 0,
                        duration = 500L,
                        fileUrl = "first.mp3",
                    ),
                    ChapterEntity(
                        id = "second-chapter",
                        bookId = "book",
                        title = "Second old chapter",
                        chapterIndex = 2,
                        fileIndex = 2,
                        duration = 500L,
                        fileUrl = "second.mp3",
                        position = 123L,
                        isCompleted = true,
                        lufsValue = -18.0,
                    ),
                    ChapterEntity(
                        id = "obsolete-chapter",
                        bookId = "book",
                        title = "Obsolete chapter",
                        chapterIndex = 4,
                        fileIndex = 4,
                        duration = 500L,
                        fileUrl = "obsolete.mp3",
                    ),
                ),
            )
            bookmarkDao.upsertBookmark(bookmark(id = "first-bookmark", chapterIndex = 0))
            bookmarkDao.upsertBookmark(bookmark(id = "second-bookmark", chapterIndex = 2))

            booksDao.upsertScannedBooksWithChapters(
                books =
                    listOf(
                        BookEntity(
                            id = "book",
                            title = "New title",
                            author = "New author",
                            coverUrl = null,
                            description = null,
                            totalDuration = 1_000L,
                            addedDate = 30L,
                            downloadStatus = "DOWNLOADED",
                            isDownloaded = true,
                        ),
                    ),
                chapters =
                    listOf(
                        ChapterEntity(
                            id = "new-chapter",
                            bookId = "book",
                            title = "New first chapter",
                            chapterIndex = 0,
                            fileIndex = 0,
                            duration = 1_000L,
                            fileUrl = "new.mp3",
                            isDownloaded = true,
                        ),
                        ChapterEntity(
                            id = "shifted-first-chapter",
                            bookId = "book",
                            title = "First chapter",
                            chapterIndex = 1,
                            fileIndex = 1,
                            duration = 1_000L,
                            fileUrl = "first.mp3",
                            isDownloaded = true,
                        ),
                        ChapterEntity(
                            id = "shifted-second-chapter",
                            bookId = "book",
                            title = "Second chapter",
                            chapterIndex = 3,
                            fileIndex = 3,
                            duration = 1_000L,
                            fileUrl = "second.mp3",
                            isDownloaded = true,
                        ),
                    ),
            )

            val book = requireNotNull(booksDao.getBookById("book"))
            val chapter = requireNotNull(chaptersDao.getChapterById("second-chapter"))

            assertEquals("New title", book.title)
            assertEquals(1_000L, book.totalDuration)
            assertEquals(123L, book.currentPosition)
            assertEquals(0.4f, book.totalProgress, 0f)
            assertEquals(3, book.currentChapterIndex)
            assertEquals("cover", book.coverUrl)
            assertEquals("description", book.description)
            assertEquals(10L, book.addedDate)
            assertEquals(20L, book.lastPlayedDate)
            assertTrue(book.isFavorite)
            assertEquals(1.5f, requireNotNull(book.preferredSpeed), 0f)
            assertEquals("Second chapter", chapter.title)
            assertEquals(3, chapter.chapterIndex)
            assertEquals("second.mp3", chapter.fileUrl)
            assertEquals(123L, chapter.position)
            assertTrue(chapter.isCompleted)
            assertEquals(-18.0, chapter.lufsValue ?: 0.0, 0.0)
            assertEquals(3, chaptersDao.getChaptersByBookId("book").size)
            assertEquals(null, chaptersDao.getChapterById("obsolete-chapter"))
            assertEquals(
                listOf(1, 3),
                bookmarkDao.getBookmarksForBookSync("book").map { it.chapterIndex },
            )
        }

    @Test
    fun `scan resets playback when the current chapter no longer exists`() =
        runBlocking {
            booksDao.insertBook(
                BookEntity(
                    id = "book",
                    title = "Book",
                    author = "Author",
                    coverUrl = null,
                    description = null,
                    totalDuration = 1_000L,
                    currentPosition = 123L,
                    totalProgress = 0.4f,
                    currentChapterIndex = 2,
                    addedDate = 1L,
                ),
            )
            chaptersDao.insertAll(
                listOf(
                    ChapterEntity("first", "book", "First", 0, 0, 500L, "first.mp3"),
                    ChapterEntity("current", "book", "Current", 2, 2, 500L, "current.mp3"),
                ),
            )

            booksDao.upsertScannedBooksWithChapters(
                books =
                    listOf(
                        BookEntity(
                            id = "book",
                            title = "Book",
                            author = "Author",
                            coverUrl = null,
                            description = null,
                            totalDuration = 500L,
                            addedDate = 1L,
                        ),
                    ),
                chapters = listOf(ChapterEntity("first-new", "book", "First", 0, 0, 500L, "first.mp3")),
            )

            val book = requireNotNull(booksDao.getBookById("book"))
            assertEquals(0L, book.currentPosition)
            assertEquals(0f, book.totalProgress, 0f)
            assertEquals(0, book.currentChapterIndex)
        }

    private fun bookmark(
        id: String,
        chapterIndex: Int,
    ): BookmarkEntity =
        BookmarkEntity(
            id = id,
            bookId = "book",
            chapterIndex = chapterIndex,
            positionMs = 100L,
            createdAt = 1L,
            updatedAt = 1L,
        )
}
