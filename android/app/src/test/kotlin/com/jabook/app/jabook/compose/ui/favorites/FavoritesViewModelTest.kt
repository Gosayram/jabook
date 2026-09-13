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

package com.jabook.app.jabook.compose.ui.favorites

import app.cash.turbine.test
import com.jabook.app.jabook.compose.data.model.BookSortOrder
import com.jabook.app.jabook.compose.data.repository.FavoritesRepository
import com.jabook.app.jabook.compose.domain.model.FavoriteItem
import com.jabook.app.jabook.compose.domain.usecase.library.GetFavoriteBooksUseCase
import com.jabook.app.jabook.compose.domain.usecase.library.ToggleFavoriteUseCase
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
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {
    private val favoritesRepository: FavoritesRepository = mock()
    private val getFavoriteBooksUseCase: GetFavoriteBooksUseCase = mock()
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase = mock()

    private val testDispatcher = StandardTestDispatcher()

    private val allFavoritesFlow = MutableStateFlow<List<FavoriteItem>>(emptyList())

    private lateinit var viewModel: FavoritesViewModel

    private val favoriteAlpha =
        FavoriteItem(
            topicId = "t1",
            title = "Alpha Book",
            author = "Author A",
            category = "Audiobook",
            size = "100 MB",
            magnetUrl = "magnet:?xt=urn:btih:t1",
            addedDate = "2024-01-01",
            addedToFavorites = "2024-01-01",
        )

    private val favoriteBeta =
        FavoriteItem(
            topicId = "t2",
            title = "Beta Novel",
            author = "Author B",
            category = "Audiobook",
            size = "200 MB",
            magnetUrl = "magnet:?xt=urn:btih:t2",
            addedDate = "2024-02-01",
            addedToFavorites = "2024-02-01",
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(favoritesRepository.allFavorites).thenReturn(allFavoritesFlow)
        whenever(favoritesRepository.favoriteIds).thenReturn(MutableStateFlow(emptyList()))
        whenever(getFavoriteBooksUseCase.invoke()).thenReturn(flowOf(emptyList()))
        viewModel = FavoritesViewModel(favoritesRepository, getFavoriteBooksUseCase, toggleFavoriteUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggle favorite adds when not favorite`() =
        runTest(testDispatcher.scheduler) {
            whenever(favoritesRepository.isFavorite("t1")).thenReturn(false)
            whenever(favoritesRepository.addToFavorites(favoriteAlpha)).thenReturn(Result.success(Unit))

            viewModel.toggleFavorite(favoriteAlpha)
            advanceUntilIdle()

            verify(favoritesRepository).addToFavorites(favoriteAlpha)
            verify(favoritesRepository, never()).removeFromFavorites("t1")
        }

    @Test
    fun `toggle favorite removes when already favorite`() =
        runTest(testDispatcher.scheduler) {
            whenever(favoritesRepository.isFavorite("t1")).thenReturn(true)
            whenever(favoritesRepository.removeFromFavorites("t1")).thenReturn(Result.success(Unit))

            viewModel.toggleFavorite(favoriteAlpha)
            advanceUntilIdle()

            verify(favoritesRepository).removeFromFavorites("t1")
            verify(favoritesRepository, never()).addToFavorites(favoriteAlpha)
        }

    @Test
    fun `search query filters favorites by title`() =
        runTest(testDispatcher.scheduler) {
            allFavoritesFlow.value = listOf(favoriteAlpha, favoriteBeta)

            viewModel.onSearchQueryChanged("beta")

            viewModel.favorites.test {
                advanceUntilIdle()
                val favorites = expectMostRecentItem()
                assertEquals(1, favorites.size)
                assertEquals("Beta Novel", favorites[0].title)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `title ascending sort reorders favorites`() =
        runTest(testDispatcher.scheduler) {
            allFavoritesFlow.value = listOf(favoriteBeta, favoriteAlpha)

            viewModel.onSortOrderChanged(BookSortOrder.TITLE_ASC)

            viewModel.favorites.test {
                advanceUntilIdle()
                val favorites = expectMostRecentItem()
                assertEquals(listOf("Alpha Book", "Beta Novel"), favorites.map { it.title })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `remove from favorites syncs local library flag`() =
        runTest(testDispatcher.scheduler) {
            viewModel.removeFromFavorites("t1")
            advanceUntilIdle()

            verify(favoritesRepository).removeFromFavorites("t1")
            verify(toggleFavoriteUseCase).invoke("t1", false)
        }

    @Test
    fun `remove multiple favorites delegates to repository`() =
        runTest(testDispatcher.scheduler) {
            viewModel.removeMultipleFavorites(listOf("t1", "t2"))
            advanceUntilIdle()

            verify(favoritesRepository).removeMultipleFavorites(listOf("t1", "t2"))
        }
}
