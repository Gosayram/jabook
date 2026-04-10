package com.jabook.app.jabook.compose.data.repository

import com.jabook.app.jabook.compose.data.local.dao.DownloadHistoryDao
import com.jabook.app.jabook.compose.data.local.entity.DownloadHistoryEntity
import com.jabook.app.jabook.compose.domain.model.HistorySortOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DownloadHistoryRepositoryTest {
    @Test
    fun `getHistoryWithFilter maps entities to domain items`() =
        runTest {
            val dao: DownloadHistoryDao = mock()
            whenever(dao.getHistory(any()))
                .thenReturn(
                    flowOf(
                        listOf(
                            DownloadHistoryEntity(
                                id = 7,
                                bookId = "book-1",
                                bookTitle = "War and Peace",
                                status = "completed",
                                startedAt = 10L,
                                completedAt = 20L,
                                totalBytes = 1234L,
                                errorMessage = null,
                            ),
                        ),
                    ),
                )

            val repository = DownloadHistoryRepository(dao)
            val result = repository.getHistoryWithFilter("war", HistorySortOrder.TITLE_ASC).first()

            assertEquals(1, result.size)
            assertEquals("book-1", result.first().bookId)
            assertEquals("War and Peace", result.first().bookTitle)
            assertEquals("completed", result.first().status)
        }

    @Test
    fun `clearHistory delegates to dao`() =
        runTest {
            val dao: DownloadHistoryDao = mock()
            val repository = DownloadHistoryRepository(dao)

            repository.clearHistory()

            verify(dao).clearAll()
        }

    @Test
    fun `deleteOlderThan delegates to dao`() =
        runTest {
            val dao: DownloadHistoryDao = mock()
            val repository = DownloadHistoryRepository(dao)

            repository.deleteOlderThan(123L)

            verify(dao).deleteOlderThan(123L)
        }
}
