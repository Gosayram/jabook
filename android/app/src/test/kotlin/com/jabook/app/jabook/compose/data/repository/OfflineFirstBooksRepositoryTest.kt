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
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.core.logger.NoOpLogger
import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.compose.data.local.dao.ChaptersDao
import com.jabook.app.jabook.compose.data.local.dao.ScanPathDao
import com.jabook.app.jabook.compose.data.local.scanner.LocalBookScanner
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OfflineFirstBooksRepositoryTest {
    @Test
    fun `updateChapterOrder delegates to transactional DAO path`() =
        runTest {
            val booksDao = mock<BooksDao>()
            val chaptersDao = mock<ChaptersDao>()
            val scanPathDao = mock<ScanPathDao>()
            val playerPersistenceManager = mock<PlayerPersistenceManager>()
            val localBookScanner = mock<LocalBookScanner>()
            val loggerFactory = mock<LoggerFactory>()
            whenever(loggerFactory.get(eq("OfflineFirstBooksRepository"))).thenReturn(NoOpLogger)

            val repository =
                OfflineFirstBooksRepository(
                    booksDao = booksDao,
                    chaptersDao = chaptersDao,
                    scanPathDao = scanPathDao,
                    playerPersistenceManager = playerPersistenceManager,
                    localBookScanner = localBookScanner,
                    loggerFactory = loggerFactory,
                )

            val orderedIds = listOf("c2", "c1", "c3")
            repository.updateChapterOrder(bookId = "book-1", newOrderedIds = orderedIds)

            verify(chaptersDao).reorderChaptersByIds(
                bookId = eq("book-1"),
                newOrderedIds = eq(orderedIds),
            )
        }
}
