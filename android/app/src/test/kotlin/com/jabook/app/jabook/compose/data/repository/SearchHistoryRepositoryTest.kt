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

import com.jabook.app.jabook.compose.data.local.dao.SearchHistoryDao
import com.jabook.app.jabook.compose.data.local.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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
                            SearchHistoryEntity(id = 1, query = "tolstoy", normalizedQuery = "tolstoy", timestamp = 100L, resultCount = 3),
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
    fun `saveSearch inserts new entry when no duplicate exists`() =
        runTest {
            val dao: SearchHistoryDao = mock()
            whenever(dao.findByNormalizedQuery("dostoevsky")).thenReturn(null)
            val repository = SearchHistoryRepository(dao)

            repository.saveSearch(query = "dostoevsky", resultCount = 7)

            val captor = argumentCaptor<SearchHistoryEntity>()
            verify(dao).insertSearch(captor.capture())
            verify(dao).trimHistory(keepCount = 50)

            assertEquals("dostoevsky", captor.firstValue.query)
            assertEquals("dostoevsky", captor.firstValue.normalizedQuery)
            assertEquals(7, captor.firstValue.resultCount)
            assertTrue(captor.firstValue.timestamp > 0L)
        }

    @Test
    fun `saveSearch updates timestamp when duplicate exists`() =
        runTest {
            val dao: SearchHistoryDao = mock()
            val existing = SearchHistoryEntity(id = 5, query = "Tolstoy", normalizedQuery = "tolstoy", timestamp = 100L, resultCount = 3)
            whenever(dao.findByNormalizedQuery("tolstoy")).thenReturn(existing)
            val repository = SearchHistoryRepository(dao)

            repository.saveSearch(query = "  Tolstoy  ", resultCount = 10)

            verify(dao, never()).insertSearch(org.mockito.kotlin.any())
            verify(dao).updateTimestamp(eq(5), org.mockito.kotlin.any(), eq(10))
            verify(dao).trimHistory(keepCount = 50)
        }

    @Test
    fun `saveSearch normalizes query with multiple spaces`() =
        runTest {
            val dao: SearchHistoryDao = mock()
            whenever(dao.findByNormalizedQuery("war and peace")).thenReturn(null)
            val repository = SearchHistoryRepository(dao)

            repository.saveSearch(query = "  War   and   Peace  ", resultCount = 1)

            val captor = argumentCaptor<SearchHistoryEntity>()
            verify(dao).insertSearch(captor.capture())

            assertEquals("War and Peace", captor.firstValue.query)
            assertEquals("war and peace", captor.firstValue.normalizedQuery)
        }
}
