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

import com.jabook.app.jabook.audio.PlayerPersistenceManager
import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.core.logger.NoOpLogger
import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.compose.data.local.dao.ChaptersDao
import com.jabook.app.jabook.compose.data.local.dao.DownloadQueueDao
import com.jabook.app.jabook.compose.data.local.dao.ScanPathDao
import com.jabook.app.jabook.compose.data.local.scanner.LocalBookScanner
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OfflineFirstBooksRepositoryTest {
    @Test
    fun `updatePreferredPlaybackSpeed rejects non-finite speeds without writing`() =
        runTest {
            val booksDao = mock<BooksDao>()
            val chaptersDao = mock<ChaptersDao>()
            val scanPathDao = mock<ScanPathDao>()
            val playerPersistenceManager = mock<PlayerPersistenceManager>()
            val localBookScanner = mock<LocalBookScanner>()
            val logger = mock<Logger>()
            val loggerFactory = mock<LoggerFactory>()
            whenever(loggerFactory.get(eq("OfflineFirstBooksRepository"))).thenReturn(logger)

            val repository =
                OfflineFirstBooksRepository(
                    booksDao = booksDao,
                    downloadQueueDao = mock(),
                    chaptersDao = chaptersDao,
                    scanPathDao = scanPathDao,
                    playerPersistenceManager = playerPersistenceManager,
                    localBookScanner = localBookScanner,
                    loggerFactory = loggerFactory,
                )

            for (invalidSpeed in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f, -1.5f)) {
                repository.updatePreferredPlaybackSpeed(bookId = "book-1", speed = invalidSpeed)
            }

            verifyNoInteractions(booksDao)
        }

    @Test
    fun `setFavorite delegates to DAO updateFavoriteStatus`() =
        runTest {
            val booksDao = mock<BooksDao>()
            val chaptersDao = mock<ChaptersDao>()
            val scanPathDao = mock<ScanPathDao>()
            val playerPersistenceManager = mock<PlayerPersistenceManager>()
            val localBookScanner = mock<LocalBookScanner>()
            val loggerFactory = mock<LoggerFactory>()
            whenever(loggerFactory.get(eq("OfflineFirstBooksRepository"))).thenReturn(NoOpLogger)

            val downloadQueueDao = mock<DownloadQueueDao>()
            val repository =
                OfflineFirstBooksRepository(
                    booksDao = booksDao,
                    downloadQueueDao = downloadQueueDao,
                    chaptersDao = chaptersDao,
                    scanPathDao = scanPathDao,
                    playerPersistenceManager = playerPersistenceManager,
                    localBookScanner = localBookScanner,
                    loggerFactory = loggerFactory,
                )

            repository.setFavorite(bookId = "book-1", isFavorite = true)

            verify(booksDao).updateFavoriteStatus(
                bookId = eq("book-1"),
                isFavorite = eq(true),
            )
        }

    @Test
    fun `deleteBook removes download queue entry along with the book`() =
        runTest {
            val booksDao = mock<BooksDao>()
            val downloadQueueDao = mock<DownloadQueueDao>()
            val loggerFactory = mock<LoggerFactory>()
            whenever(loggerFactory.get(eq("OfflineFirstBooksRepository"))).thenReturn(NoOpLogger)

            val repository =
                OfflineFirstBooksRepository(
                    booksDao = booksDao,
                    downloadQueueDao = downloadQueueDao,
                    chaptersDao = mock(),
                    scanPathDao = mock(),
                    playerPersistenceManager = mock(),
                    localBookScanner = mock(),
                    loggerFactory = loggerFactory,
                )

            repository.deleteBook("book-1")

            verify(downloadQueueDao).deleteByBookId("book-1")
            verify(booksDao).deleteById("book-1")
        }
}
