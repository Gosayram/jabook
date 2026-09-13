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

package com.jabook.app.jabook.compose.feature.search.rutracker

import com.jabook.app.jabook.compose.core.logger.NoOpLoggerFactory
import com.jabook.app.jabook.compose.data.repository.BooksRepository
import com.jabook.app.jabook.compose.data.repository.RutrackerRepository
import com.jabook.app.jabook.compose.domain.model.AppError
import com.jabook.app.jabook.compose.domain.model.Result
import com.jabook.app.jabook.compose.domain.model.RutrackerSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RutrackerSearchViewModelTest {
    private val repository: RutrackerRepository = mock()
    private val booksRepository: BooksRepository = mock()
    private val coverLoader: CoverLoader = mock()
    private val coverEvents = MutableSharedFlow<CoverLoader.CoverLoadedEvent>()
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: RutrackerSearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(booksRepository.getAllBooks()).thenReturn(flowOf(emptyList()))
        whenever(coverLoader.coverLoadedEvents).thenReturn(coverEvents)
        viewModel = RutrackerSearchViewModel(repository, booksRepository, coverLoader, NoOpLoggerFactory)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `failed remote search is shown as an error instead of empty results`() =
        runTest(testDispatcher.scheduler) {
            whenever(repository.searchAudiobooksFlow(any(), any())).thenReturn(
                flowOf(Result.Error(AppError.NetworkError.HttpError(code = 400))),
            )

            viewModel.search("query")
            advanceUntilIdle()

            val state = viewModel.searchState.value
            assertTrue(state is SearchState.Error)
            assertEquals("HTTP error 400", (state as SearchState.Error).message)
        }

    @Test
    fun `new search cancels the previous search`() =
        runTest(testDispatcher.scheduler) {
            val first = MutableSharedFlow<Result<List<RutrackerSearchResult>, AppError>>(replay = 1)
            val second = MutableSharedFlow<Result<List<RutrackerSearchResult>, AppError>>(replay = 1)
            whenever(repository.searchAudiobooksFlow(eq("first"), any())).thenReturn(first)
            whenever(repository.searchAudiobooksFlow(eq("second"), any())).thenReturn(second)

            viewModel.search("first")
            runCurrent()
            viewModel.search("second")
            runCurrent()
            second.emit(Result.Success(listOf(searchResult("second"))))
            runCurrent()
            first.emit(Result.Success(listOf(searchResult("first"))))
            runCurrent()

            val state = viewModel.searchState.value as SearchState.Success
            assertEquals(
                "second",
                state.results
                    .single()
                    .result.topicId,
            )
        }

    @Test
    fun `cover event from old results does not replace a new loading search`() =
        runTest(testDispatcher.scheduler) {
            val first = MutableSharedFlow<Result<List<RutrackerSearchResult>, AppError>>(replay = 1)
            val second = MutableSharedFlow<Result<List<RutrackerSearchResult>, AppError>>()
            whenever(repository.searchAudiobooksFlow(eq("first"), any())).thenReturn(first)
            whenever(repository.searchAudiobooksFlow(eq("second"), any())).thenReturn(second)

            viewModel.search("first")
            runCurrent()
            first.emit(Result.Success(listOf(searchResult("first"))))
            runCurrent()
            viewModel.search("second")
            runCurrent()
            coverEvents.emit(CoverLoader.CoverLoadedEvent("first", "https://example.com/cover.jpg"))
            runCurrent()

            assertTrue(viewModel.searchState.value is SearchState.Loading)
        }

    @Test
    fun `size sorting parses whitespace between the value and unit`() =
        runTest(testDispatcher.scheduler) {
            whenever(repository.searchAudiobooksFlow(any(), any())).thenReturn(
                flowOf(Result.Success(listOf(searchResult("large", "1 GB"), searchResult("small", "500 MB")))),
            )

            viewModel.updateSortOrder(RutrackerSortOrder.SIZE_ASC)
            viewModel.search("query")
            advanceUntilIdle()

            val state = viewModel.searchState.value as SearchState.Success
            assertEquals(
                "small",
                state.results
                    .first()
                    .result.topicId,
            )
        }

    private fun searchResult(
        topicId: String,
        size: String = "1 MB",
    ): RutrackerSearchResult =
        RutrackerSearchResult(
            topicId = topicId,
            title = topicId,
            author = "author",
            category = "category",
            size = size,
            seeders = 1,
            leechers = 0,
            magnetUrl = null,
            torrentUrl = "https://example.com/$topicId.torrent",
        )
}
