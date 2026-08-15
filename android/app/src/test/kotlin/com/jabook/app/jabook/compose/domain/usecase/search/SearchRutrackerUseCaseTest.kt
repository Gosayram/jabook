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

package com.jabook.app.jabook.compose.domain.usecase.search

import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.repository.RutrackerRepository
import com.jabook.app.jabook.compose.domain.model.AppError
import com.jabook.app.jabook.compose.domain.model.Result
import com.jabook.app.jabook.compose.domain.model.RutrackerSearchResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SearchRutrackerUseCaseTest {
    private val rutrackerRepository: RutrackerRepository = mock()
    private val loggerFactory: LoggerFactory = mock()
    private val logger: Logger = mock()

    private val useCase =
        SearchRutrackerUseCase(
            rutrackerRepository = rutrackerRepository,
            loggerFactory = loggerFactory,
        )

    private fun stubLogger() {
        whenever(loggerFactory.get(eq("SearchRutrackerUseCase"))).thenReturn(logger)
    }

    private fun stubLoggerAndRecreate(): SearchRutrackerUseCase {
        stubLogger()
        return SearchRutrackerUseCase(rutrackerRepository, loggerFactory)
    }

    private val testResult =
        RutrackerSearchResult(
            topicId = "12345",
            title = "The Great Gatsby",
            author = "F. Scott Fitzgerald",
            category = "Literature",
            size = "200 MB",
            seeders = 15,
            leechers = 3,
            magnetUrl = "magnet:?xt=urn:btih:abc",
            torrentUrl = "https://rutracker.org/forum/viewtopic.php?t=12345",
            coverUrl = "https://example.com/cover.jpg",
        )

    @Test
    fun `invoke with blank query returns empty success`() =
        runTest {
            val uc = stubLoggerAndRecreate()

            val result = uc("").first()

            assertTrue(result is Result.Success)
            assertEquals(0, (result as Result.Success).data.size)
        }

    @Test
    fun `invoke with whitespace-only query returns empty success`() =
        runTest {
            val uc = stubLoggerAndRecreate()

            val result = uc("   ").first()

            assertTrue(result is Result.Success)
            assertEquals(0, (result as Result.Success).data.size)
        }

    @Test
    fun `invoke with valid query delegates to repository`() =
        runTest {
            whenever(rutrackerRepository.search(eq("gatsby")))
                .thenReturn(flowOf(Result.Success(listOf(testResult))))

            val uc = stubLoggerAndRecreate()
            val result = uc("gatsby").first()

            assertTrue(result is Result.Success)
            assertEquals(1, (result as Result.Success).data.size)
            assertEquals("The Great Gatsby", result.data[0].title)
        }

    @Test
    fun `invoke propagates error from repository`() =
        runTest {
            val error = AppError.NetworkError.NoConnection
            whenever(rutrackerRepository.search(eq("gatsby")))
                .thenReturn(flowOf(Result.Error(error)))

            val uc = stubLoggerAndRecreate()
            val result = uc("gatsby").first()

            assertTrue(result is Result.Error)
            assertEquals("No network connection", (result as Result.Error).error.message)
        }

    @Test
    fun `invoke propagates loading state from repository`() =
        runTest {
            whenever(rutrackerRepository.search(eq("gatsby")))
                .thenReturn(flowOf(Result.Loading()))

            val uc = stubLoggerAndRecreate()
            val result = uc("gatsby").first()

            assertTrue(result is Result.Loading)
        }

    @Test
    fun `invoke returns multiple results from repository`() =
        runTest {
            val results =
                listOf(
                    testResult,
                    testResult.copy(topicId = "99999", title = "Another Book"),
                )
            whenever(rutrackerRepository.search(eq("books")))
                .thenReturn(flowOf(Result.Success(results)))

            val uc = stubLoggerAndRecreate()
            val result = uc("books").first() as Result.Success

            assertEquals(2, result.data.size)
        }

    @Test
    fun `invoke calls repository search with exact query`() =
        runTest {
            whenever(rutrackerRepository.search(eq("exact query")))
                .thenReturn(flowOf(Result.Success(emptyList())))

            val uc = stubLoggerAndRecreate()
            uc("exact query").first()

            verify(rutrackerRepository).search(eq("exact query"))
        }
}
