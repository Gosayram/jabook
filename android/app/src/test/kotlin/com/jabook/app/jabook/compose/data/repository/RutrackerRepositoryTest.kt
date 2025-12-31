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

import com.jabook.app.jabook.compose.data.local.dao.OfflineSearchDao
import com.jabook.app.jabook.compose.data.local.entity.CachedTopicEntity
import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.data.remote.parser.RutrackerParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RutrackerRepositoryTest {

    private val api: RutrackerApi = mock()
    private val parser: RutrackerParser = mock()
    private val offlineSearchDao: OfflineSearchDao = mock()


    private lateinit var repository: RutrackerRepositoryImpl

    @Before
    fun setup() {
        repository = RutrackerRepositoryImpl(
            api,
            parser,
            offlineSearchDao,
        )
    }

    @Test
    fun `search with !index command calls getSampleTopics`() = runTest {
        // Arrange
        val sampleTopics = listOf(
            CachedTopicEntity(
                topicId = "1",
                title = "Test Title",
                author = "Test Author",
                category = "Books",
                seeders = 10,
                leechers = 0,
                size = "100 MB",
                timestamp = 1000L,
                indexVersion = 1,
                magnetUrl = "magnet:?xt=urn:btih:123"
            )
        )
        whenever(offlineSearchDao.getSampleTopics(10)).thenReturn(sampleTopics)
        whenever(offlineSearchDao.getTopicCount()).thenReturn(100)


        // Act
        val result = repository.search("!index")

        // Assert
        verify(offlineSearchDao).getSampleTopics(10)
        assertTrue(result is com.jabook.app.jabook.compose.domain.model.Result.Success)
        val data = (result as com.jabook.app.jabook.compose.domain.model.Result.Success).data
        assertEquals(1, data.size)
        assertEquals("[DEBUG] Test Title", data[0].title)
    }

    @Test
    fun `search with normal query builds correct SQL`() = runTest {
        // Arrange
        val query = "Harry Potter"
        whenever(offlineSearchDao.getTopicCount()).thenReturn(100)
        whenever(offlineSearchDao.searchIndexedTopicsRaw(any())).thenReturn(emptyList())

        // Act
        repository.search(query)

        // Assert
        val captor = argumentCaptor<androidx.sqlite.db.SupportSQLiteQuery>()
        verify(offlineSearchDao).searchIndexedTopicsRaw(captor.capture())

        val sqlQuery = captor.firstValue
        val sql = sqlQuery.sql
        
        // Expect logic: SELECT * FROM cached_topics WHERE (title LIKE ? OR author LIKE ?) AND (title LIKE ? OR author LIKE ?) ...
        assertTrue(sql.contains("SELECT * FROM cached_topics WHERE"))
        assertTrue(sql.contains("(title LIKE ? OR author LIKE ?)"))
        assertTrue(sql.contains("AND"))
        assertTrue(sql.contains("ORDER BY seeders DESC, timestamp DESC LIMIT 200"))
    }
}
