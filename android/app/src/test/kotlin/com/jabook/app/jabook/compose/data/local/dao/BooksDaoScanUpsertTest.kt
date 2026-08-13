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
            chaptersDao.insertChapter(
                ChapterEntity(
                    id = "chapter",
                    bookId = "book",
                    title = "Old chapter",
                    chapterIndex = 0,
                    fileIndex = 0,
                    duration = 500L,
                    fileUrl = "old.mp3",
                    position = 123L,
                    isCompleted = true,
                    lufsValue = -18.0,
                ),
            )

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
                            id = "chapter",
                            bookId = "book",
                            title = "New chapter",
                            chapterIndex = 1,
                            fileIndex = 1,
                            duration = 1_000L,
                            fileUrl = "new.mp3",
                            isDownloaded = true,
                        ),
                    ),
            )

            val book = requireNotNull(booksDao.getBookById("book"))
            val chapter = requireNotNull(chaptersDao.getChapterById("chapter"))

            assertEquals("New title", book.title)
            assertEquals(1_000L, book.totalDuration)
            assertEquals(123L, book.currentPosition)
            assertEquals(0.4f, book.totalProgress, 0f)
            assertEquals(2, book.currentChapterIndex)
            assertEquals("cover", book.coverUrl)
            assertEquals("description", book.description)
            assertEquals(10L, book.addedDate)
            assertEquals(20L, book.lastPlayedDate)
            assertTrue(book.isFavorite)
            assertEquals(1.5f, requireNotNull(book.preferredSpeed), 0f)
            assertEquals("New chapter", chapter.title)
            assertEquals(1, chapter.chapterIndex)
            assertEquals("new.mp3", chapter.fileUrl)
            assertEquals(123L, chapter.position)
            assertTrue(chapter.isCompleted)
            assertEquals(-18.0, chapter.lufsValue ?: 0.0, 0.0)
        }
}
