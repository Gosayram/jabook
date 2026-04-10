package com.jabook.app.jabook.compose.data.repository

import com.jabook.app.jabook.compose.data.local.dao.SearchHistoryDao
import com.jabook.app.jabook.compose.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SearchHistoryRepositoryTest {
    @Test
    fun `getRecentSearches maps entities to domain models`() =
        runTest {
            val dao: SearchHistoryDao = mock()
            whenever(dao.getRecentSearches(10))
                .thenReturn(
                    flowOf(
                        listOf(
                            SearchHistoryEntity(id = 1, query = "tolstoy", timestamp = 100L, resultCount = 3),
                        ),
                    ),
                )

            val repository = SearchHistoryRepository(dao)
            val result = repository.getRecentSearches(limit = 10).first()

            assertEquals(1, result.size)
            assertEquals("tolstoy", result.first().query)
            assertEquals(3, result.first().resultCount)
        }

    @Test
    fun `saveSearch inserts entry and trims history to keepCount 50`() =
        runTest {
            val dao: SearchHistoryDao = mock()
            val repository = SearchHistoryRepository(dao)

            repository.saveSearch(query = "dostoevsky", resultCount = 7)

            val captor = argumentCaptor<SearchHistoryEntity>()
            verify(dao).insertSearch(captor.capture())
            verify(dao).trimHistory(keepCount = 50)

            assertEquals("dostoevsky", captor.firstValue.query)
            assertEquals(7, captor.firstValue.resultCount)
            assertTrue(captor.firstValue.timestamp > 0L)
        }
}
