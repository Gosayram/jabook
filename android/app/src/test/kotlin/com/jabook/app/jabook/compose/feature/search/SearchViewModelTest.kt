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

package com.jabook.app.jabook.compose.feature.search

import app.cash.turbine.test
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.core.logger.NoOpLogger
import com.jabook.app.jabook.compose.data.repository.FavoritesRepository
import com.jabook.app.jabook.compose.data.repository.SearchHistoryRepository
import com.jabook.app.jabook.compose.domain.model.AppError
import com.jabook.app.jabook.compose.domain.model.Result
import com.jabook.app.jabook.compose.domain.model.RutrackerSearchResult
import com.jabook.app.jabook.compose.domain.model.SearchFilters
import com.jabook.app.jabook.compose.domain.usecase.library.SearchBooksUseCase
import com.jabook.app.jabook.compose.domain.usecase.search.SearchRutrackerUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val searchBooksUseCase: SearchBooksUseCase = mock()
    private val searchRutrackerUseCase: SearchRutrackerUseCase = mock()
    private val searchHistoryRepository: SearchHistoryRepository = mock()
    private val favoritesRepository: FavoritesRepository = mock()
    private val loggerFactory: LoggerFactory = mock()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var viewModel: SearchViewModel

    private val resultLowSeeders =
        RutrackerSearchResult(
            topicId = "1",
            title = "Alpha Book",
            author = "Author A",
            category = "Audiobook",
            size = "100 MB",
            seeders = 1,
            leechers = 0,
            magnetUrl = null,
            torrentUrl = "https://example.com/1",
        )

    private val resultHighSeeders =
        RutrackerSearchResult(
            topicId = "2",
            title = "Beta Book",
            author = "Author B",
            category = "Audiobook",
            size = "200 MB",
            seeders = 10,
            leechers = 2,
            magnetUrl = null,
            torrentUrl = "https://example.com/2",
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(loggerFactory.get(any<String>())).thenReturn(NoOpLogger)
        whenever(searchHistoryRepository.getRecentSearches(any())).thenReturn(flowOf(emptyList()))
        whenever(favoritesRepository.favoriteIds).thenReturn(MutableStateFlow(emptyList()))
        viewModel =
            SearchViewModel(
                searchBooksUseCase,
                searchRutrackerUseCase,
                searchHistoryRepository,
                favoritesRepository,
                loggerFactory,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun stubOnlineResults(results: List<RutrackerSearchResult>) {
        whenever(searchRutrackerUseCase.invoke("tolkien"))
            .thenReturn(flowOf(Result.Success(results)))
    }

    @Test
    fun `initial state is idle`() {
        assertEquals(SearchUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `search online with blank query stays idle and skips use case`() =
        runTest(testDispatcher.scheduler) {
            viewModel.searchOnline()
            advanceUntilIdle()

            assertEquals(SearchUiState.Idle, viewModel.uiState.value)
            verify(searchRutrackerUseCase, never()).invoke(any())
        }

    @Test
    fun `search online emits results and saves to history`() =
        runTest(testDispatcher.scheduler) {
            stubOnlineResults(listOf(resultLowSeeders, resultHighSeeders))

            viewModel.onSearchQueryChanged("tolkien")
            viewModel.searchOnline()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is SearchUiState.Success)
            assertEquals(2, (state as SearchUiState.Success).onlineResults.size)
            verify(searchHistoryRepository).saveSearch("tolkien", 2)
        }

    @Test
    fun `search online error surfaces error state`() =
        runTest(testDispatcher.scheduler) {
            whenever(searchRutrackerUseCase.invoke("tolkien"))
                .thenReturn(flowOf(Result.Error(AppError.DataError.NotFound)))

            viewModel.onSearchQueryChanged("tolkien")
            viewModel.searchOnline()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value is SearchUiState.Error)
        }

    @Test
    fun `min seeders filter narrows online results`() =
        runTest(testDispatcher.scheduler) {
            stubOnlineResults(listOf(resultLowSeeders, resultHighSeeders))
            viewModel.onSearchQueryChanged("tolkien")
            viewModel.searchOnline()
            advanceUntilIdle()

            viewModel.updateFilters(SearchFilters(minSeeders = 5))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state is SearchUiState.Success)
            val results = (state as SearchUiState.Success).onlineResults
            assertEquals(1, results.size)
            assertEquals("2", results[0].topicId)
        }

    @Test
    fun `new query resets stale success state to idle`() =
        runTest(testDispatcher.scheduler) {
            stubOnlineResults(listOf(resultHighSeeders))
            viewModel.onSearchQueryChanged("tolkien")
            viewModel.searchOnline()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value is SearchUiState.Success)

            viewModel.onSearchQueryChanged("other")

            assertEquals(SearchUiState.Idle, viewModel.uiState.value)
        }

    @Test
    fun `clear search resets query and state`() =
        runTest(testDispatcher.scheduler) {
            stubOnlineResults(listOf(resultHighSeeders))
            viewModel.onSearchQueryChanged("tolkien")
            viewModel.searchOnline()
            advanceUntilIdle()

            viewModel.clearSearch()

            assertEquals("", viewModel.searchQuery.value)
            assertEquals(SearchUiState.Idle, viewModel.uiState.value)
        }

    @Test
    fun `search query flow reflects changes`() =
        runTest(testDispatcher.scheduler) {
            viewModel.searchQuery.test {
                assertEquals("", awaitItem())
                viewModel.onSearchQueryChanged("tolkien")
                assertEquals("tolkien", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
