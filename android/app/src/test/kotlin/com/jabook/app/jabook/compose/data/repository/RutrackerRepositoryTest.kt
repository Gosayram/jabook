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
import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.data.remote.model.Comment
import com.jabook.app.jabook.compose.data.remote.model.TopicDetails
import com.jabook.app.jabook.compose.data.remote.parser.RutrackerParser
import com.jabook.app.jabook.compose.domain.model.Result
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RutrackerRepositoryTest {
    private val api: RutrackerApi = mock()
    private val parser: RutrackerParser = mock()
    private val offlineSearchDao: OfflineSearchDao = mock()

    private lateinit var repository: RutrackerRepositoryImpl

    @Before
    fun setup() {
        repository =
            RutrackerRepositoryImpl(
                api,
                parser,
                offlineSearchDao,
            )
    }

    @Test
    fun `getTopicDetailsPage returns success for page 2 with comments only`() =
        runTest {
            // Arrange
            val topicId = "123"
            val page = 2
            val html = "<html>...</html>"
            val responseBody = html.toByteArray(charset("windows-1251")).toResponseBody(null)
            val response = retrofit2.Response.success(responseBody)

            whenever(api.getTopicDetailsAtPage(topicId, 30)).thenReturn(response)

            // Mock parser to return a TopicDetails that is invalid for page 1 (no torrent) but has comments
            val mockDetails =
                TopicDetails(
                    topicId = topicId,
                    title = "Test Title",
                    category = "Books",
                    seeders = 0,
                    leechers = 0,
                    torrentUrl = "", // Empty for page > 1
                    magnetUrl = null,
                    size = "",
                    author = null,
                    performer = null,
                    coverUrl = null,
                    genres = emptyList(),
                    addedDate = null,
                    duration = null,
                    bitrate = null,
                    audioCodec = null,
                    description = null,
                    relatedBooks = emptyList(),
                    comments =
                        listOf(
                            Comment("1", "User", "Date", "Text"),
                        ),
                )
            whenever(parser.parseTopicDetails(any(), any())).thenReturn(mockDetails)

            // Act
            val result = repository.getTopicDetailsPage(topicId, page)

            // Assert
            assertTrue(result is Result.Success)
            val data = (result as Result.Success).data
            assertEquals("Test Title", data.title)
            assertEquals(1, data.comments.size)
        }
}
