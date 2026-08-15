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

package com.jabook.app.jabook.compose.feature.library

import android.app.Application
import androidx.work.WorkManager
import app.cash.turbine.test
import com.jabook.app.jabook.audio.domain.usecase.ListeningStatsSummary
import com.jabook.app.jabook.audio.domain.usecase.ListeningStatsUseCase
import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.compose.data.local.dao.ScanPathDao
import com.jabook.app.jabook.compose.data.model.BookSortOrder
import com.jabook.app.jabook.compose.data.model.DownloadStatus
import com.jabook.app.jabook.compose.data.model.LibraryViewMode
import com.jabook.app.jabook.compose.data.model.UserData
import com.jabook.app.jabook.compose.data.repository.FavoritesRepository
import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.usecase.library.DeleteBookUseCase
import com.jabook.app.jabook.compose.domain.usecase.library.GetFavoriteBooksUseCase
import com.jabook.app.jabook.compose.domain.usecase.library.GetInProgressBooksUseCase
import com.jabook.app.jabook.compose.domain.usecase.library.GetLibraryUseCase
import com.jabook.app.jabook.compose.domain.usecase.library.GetRecentlyPlayedBooksUseCase
import com.jabook.app.jabook.compose.domain.usecase.library.SearchBooksUseCase
import com.jabook.app.jabook.compose.domain.usecase.library.ToggleFavoriteUseCase
import com.jabook.app.jabook.compose.domain.usecase.player.GetChaptersUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val getLibraryUseCase: GetLibraryUseCase = mock()
    private val searchBooksUseCase: SearchBooksUseCase = mock()
    private val getFavoriteBooksUseCase: GetFavoriteBooksUseCase = mock()
    private val getRecentlyPlayedBooksUseCase: GetRecentlyPlayedBooksUseCase = mock()
    private val getInProgressBooksUseCase: GetInProgressBooksUseCase = mock()
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase = mock()
    private val getChaptersUseCase: GetChaptersUseCase = mock()
    private val deleteBookUseCase: DeleteBookUseCase = mock()
    private val favoritesRepository: FavoritesRepository = mock()
    private val workManager: WorkManager = mock()
    private val userPreferencesRepository: UserPreferencesRepository = mock()
    private val booksDao: BooksDao = mock()
    private val scanPathDao: ScanPathDao = mock()
    private val application: Application = mock()
    private val listeningStatsUseCase: ListeningStatsUseCase = mock()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val userDataFlow = MutableStateFlow(UserData())

    private lateinit var viewModel: LibraryViewModel

    private val bookAlpha =
        Book(
            id = "1",
            title = "Alpha Book",
            author = "Author A",
            coverUrl = null,
            description = "desc",
            totalDuration = 1.hours,
            currentPosition = 900000L.milliseconds,
            progress = 0.25f,
            currentChapterIndex = 0,
            downloadStatus = DownloadStatus.NOT_DOWNLOADED,
            downloadProgress = 0f,
            localPath = null,
            addedDate = 1000L,
            lastPlayedDate = 3000L,
            isFavorite = false,
            sourceUrl = null,
        )

    private val bookBeta =
        Book(
            id = "2",
            title = "Beta Novel",
            author = "Author B",
            coverUrl = null,
            description = "desc",
            totalDuration = 2.hours,
            currentPosition = 2.hours,
            progress = 1.0f,
            currentChapterIndex = 5,
            downloadStatus = DownloadStatus.DOWNLOADED,
            downloadProgress = 1.0f,
            localPath = "/books/beta",
            addedDate = 2000L,
            lastPlayedDate = 5000L,
            isFavorite = true,
            sourceUrl = null,
        )

    private val bookGamma =
        Book(
            id = "3",
            title = "Gamma Guide",
            author = "Author C",
            coverUrl = null,
            description = "desc",
            totalDuration = 3.hours,
            currentPosition = 0.milliseconds,
            progress = 0f,
            currentChapterIndex = 0,
            downloadStatus = DownloadStatus.NOT_DOWNLOADED,
            downloadProgress = 0f,
            localPath = null,
            addedDate = 3000L,
            lastPlayedDate = null,
            isFavorite = false,
            sourceUrl = null,
        )

    private val allBooks = listOf(bookAlpha, bookBeta, bookGamma)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(userPreferencesRepository.userData).thenReturn(userDataFlow)
        whenever(listeningStatsUseCase.observeSummary(any(), any()))
            .thenReturn(flowOf(ListeningStatsSummary(0L, 0L, 0, 0)))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(books: List<Book> = allBooks): LibraryViewModel {
        whenever(getLibraryUseCase(any())).thenReturn(flowOf(books))
        whenever(getFavoriteBooksUseCase()).thenReturn(flowOf(books.filter { it.isFavorite }))
        whenever(getRecentlyPlayedBooksUseCase(any())).thenReturn(flowOf(books.filter { it.lastPlayedDate != null }))
        whenever(getInProgressBooksUseCase()).thenReturn(flowOf(books.filter { it.isStarted && !it.isCompleted }))
        return LibraryViewModel(
            getLibraryUseCase,
            searchBooksUseCase,
            getFavoriteBooksUseCase,
            getRecentlyPlayedBooksUseCase,
            getInProgressBooksUseCase,
            toggleFavoriteUseCase,
            getChaptersUseCase,
            deleteBookUseCase,
            favoritesRepository,
            workManager,
            userPreferencesRepository,
            booksDao,
            scanPathDao,
            application,
            listeningStatsUseCase,
        )
    }

    // --- Initial load ---

    @Test
    fun `initial state is Loading`() =
        runTest {
            viewModel = createViewModel()
            assertEquals(LibraryUiState.Loading, viewModel.uiState.value)
        }

    @Test
    fun `initial load emits success with all books`() =
        runTest {
            viewModel = createViewModel()

            viewModel.uiState.test {
                testDispatcher.scheduler.advanceUntilIdle()
                val state = expectMostRecentItem()
                assertTrue(state is LibraryUiState.Success)
                assertEquals(3, (state as LibraryUiState.Success).books.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `initial load with empty library emits Empty state`() =
        runTest {
            viewModel = createViewModel(books = emptyList())

            viewModel.uiState.test {
                testDispatcher.scheduler.advanceUntilIdle()
                val state = expectMostRecentItem()
                assertTrue(state is LibraryUiState.Empty)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- Search filtering ---

    @Test
    fun `search query filters books by title`() =
        runTest {
            viewModel = createViewModel()

            viewModel.uiState.test {
                testDispatcher.scheduler.advanceUntilIdle()
                expectMostRecentItem()

                viewModel.onSearchQueryChanged("Alpha")
                testDispatcher.scheduler.advanceUntilIdle()
                val state = expectMostRecentItem()
                assertTrue(state is LibraryUiState.Success)
                val books = (state as LibraryUiState.Success).books
                assertEquals(1, books.size)
                assertEquals("Alpha Book", books[0].title)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `search query filters books by author`() =
        runTest {
            viewModel = createViewModel()

            viewModel.uiState.test {
                testDispatcher.scheduler.advanceUntilIdle()
                expectMostRecentItem()

                viewModel.onSearchQueryChanged("Author C")
                testDispatcher.scheduler.advanceUntilIdle()
                val state = expectMostRecentItem()
                assertTrue(state is LibraryUiState.Success)
                val books = (state as LibraryUiState.Success).books
                assertEquals(1, books.size)
                assertEquals("Gamma Guide", books[0].title)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `search query is case insensitive`() =
        runTest {
            viewModel = createViewModel()

            viewModel.uiState.test {
                testDispatcher.scheduler.advanceUntilIdle()
                expectMostRecentItem()

                viewModel.onSearchQueryChanged("alpha")
                testDispatcher.scheduler.advanceUntilIdle()
                val state = expectMostRecentItem()
                assertTrue(state is LibraryUiState.Success)
                assertEquals(1, (state as LibraryUiState.Success).books.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `search query with no matches emits Empty`() =
        runTest {
            viewModel = createViewModel()

            viewModel.uiState.test {
                testDispatcher.scheduler.advanceUntilIdle()
                expectMostRecentItem()

                viewModel.onSearchQueryChanged("ZZZZZ")
                testDispatcher.scheduler.advanceUntilIdle()
                val state = expectMostRecentItem()
                assertTrue(state is LibraryUiState.Empty)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `clearing search query restores all books`() =
        runTest {
            viewModel = createViewModel()

            viewModel.uiState.test {
                testDispatcher.scheduler.advanceUntilIdle()
                expectMostRecentItem()

                viewModel.onSearchQueryChanged("Alpha")
                testDispatcher.scheduler.advanceUntilIdle()
                val filtered = expectMostRecentItem() as LibraryUiState.Success
                assertEquals(1, filtered.books.size)

                viewModel.onSearchQueryChanged("")
                testDispatcher.scheduler.advanceUntilIdle()
                val restored = expectMostRecentItem() as LibraryUiState.Success
                assertEquals(3, restored.books.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- Sort modes ---

    @Test
    fun `sort order change triggers library reload`() =
        runTest {
            viewModel = createViewModel()

            viewModel.uiState.test {
                testDispatcher.scheduler.advanceUntilIdle()
                expectMostRecentItem()

                viewModel.onSortOrderChanged(BookSortOrder.TITLE_ASC)
                testDispatcher.scheduler.advanceUntilIdle()

                verify(getLibraryUseCase).invoke(BookSortOrder.TITLE_ASC)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `sort order change persists to preferences`() =
        runTest {
            viewModel = createViewModel()

            viewModel.uiState.test {
                testDispatcher.scheduler.advanceUntilIdle()
                expectMostRecentItem()

                viewModel.onSortOrderChanged(BookSortOrder.AUTHOR_ASC)
                testDispatcher.scheduler.advanceUntilIdle()

                verify(userPreferencesRepository).setSortOrder(BookSortOrder.AUTHOR_ASC)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- Quick filter lists (use case delegation) ---

    @Test
    fun `favorite books use case is invoked`() =
        runTest {
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            verify(getFavoriteBooksUseCase).invoke()
        }

    @Test
    fun `in progress books use case is invoked`() =
        runTest {
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            verify(getInProgressBooksUseCase).invoke()
        }

    @Test
    fun `recently played use case is invoked`() =
        runTest {
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            verify(getRecentlyPlayedBooksUseCase).invoke(any())
        }

    // --- Book deletion ---

    @Test
    fun `delete book calls delete use case`() =
        runTest {
            viewModel = createViewModel()

            viewModel.uiState.test {
                testDispatcher.scheduler.advanceUntilIdle()
                expectMostRecentItem()

                viewModel.deleteBook("1")
                testDispatcher.scheduler.advanceUntilIdle()

                verify(deleteBookUseCase).invoke("1")
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- Favorite toggle ---

    @Test
    fun `toggle favorite calls use case`() =
        runTest {
            viewModel = createViewModel()

            viewModel.uiState.test {
                testDispatcher.scheduler.advanceUntilIdle()
                expectMostRecentItem()

                viewModel.toggleFavorite("1", true)
                testDispatcher.scheduler.advanceUntilIdle()

                verify(toggleFavoriteUseCase).invoke("1", true)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `toggle favorite remove calls repository remove`() =
        runTest {
            viewModel = createViewModel()

            viewModel.uiState.test {
                testDispatcher.scheduler.advanceUntilIdle()
                expectMostRecentItem()

                viewModel.toggleFavorite("2", false)
                testDispatcher.scheduler.advanceUntilIdle()

                verify(toggleFavoriteUseCase).invoke("2", false)
                verify(favoritesRepository).removeFromFavorites("2")
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- Book properties dialog ---

    @Test
    fun `show book properties sets selected book`() =
        runTest {
            viewModel = createViewModel()

            viewModel.uiState.test {
                testDispatcher.scheduler.advanceUntilIdle()
                expectMostRecentItem()

                viewModel.showBookProperties("1")
                testDispatcher.scheduler.advanceUntilIdle()

                val selected = viewModel.selectedBookForProperties.value
                assertEquals("1", selected?.id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `hide book properties clears selected book`() =
        runTest {
            viewModel = createViewModel()

            viewModel.uiState.test {
                testDispatcher.scheduler.advanceUntilIdle()
                expectMostRecentItem()

                viewModel.showBookProperties("1")
                testDispatcher.scheduler.advanceUntilIdle()
                viewModel.hideBookProperties()

                assertNull(viewModel.selectedBookForProperties.value)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- View mode ---

    @Test
    fun `view mode change persists to preferences`() =
        runTest {
            viewModel = createViewModel()

            viewModel.uiState.test {
                testDispatcher.scheduler.advanceUntilIdle()
                expectMostRecentItem()

                viewModel.onViewModeChanged(LibraryViewMode.GRID_COMPACT)
                testDispatcher.scheduler.advanceUntilIdle()

                verify(userPreferencesRepository).setViewMode(LibraryViewMode.GRID_COMPACT)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- Spotlight ---

    @Test
    fun `complete spotlight persists and updates state`() =
        runTest {
            viewModel = createViewModel()

            viewModel.uiState.test {
                testDispatcher.scheduler.advanceUntilIdle()
                expectMostRecentItem()

                viewModel.completeSpotlight()
                testDispatcher.scheduler.advanceUntilIdle()

                verify(userPreferencesRepository).setSpotlightCompleted(true)
                assertTrue(viewModel.spotlightCompleted.value)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
