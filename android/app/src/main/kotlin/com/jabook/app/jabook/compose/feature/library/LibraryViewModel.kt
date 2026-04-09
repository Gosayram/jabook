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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.jabook.app.jabook.compose.data.model.BookSortOrder
import com.jabook.app.jabook.compose.data.model.LibraryViewMode
import com.jabook.app.jabook.compose.data.repository.FavoritesRepository
import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import com.jabook.app.jabook.compose.data.worker.LibraryScanWorker
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.toFavoriteEntity
import com.jabook.app.jabook.compose.domain.usecase.library.DeleteBookUseCase
import com.jabook.app.jabook.compose.domain.usecase.library.GetFavoriteBooksUseCase
import com.jabook.app.jabook.compose.domain.usecase.library.GetInProgressBooksUseCase
import com.jabook.app.jabook.compose.domain.usecase.library.GetLibraryUseCase
import com.jabook.app.jabook.compose.domain.usecase.library.GetRecentlyPlayedBooksUseCase
import com.jabook.app.jabook.compose.domain.usecase.library.SearchBooksUseCase
import com.jabook.app.jabook.compose.domain.usecase.library.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Library screen.
 *
 * Uses domain layer use cases to manage library state following
 * Clean Architecture principles.
 *
 * Manages:
 * - Book list via GetLibraryUseCase
 * - Search and sorting (in-memory)
 * - Book deletion via DeleteBookUseCase
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
public class LibraryViewModel
    @Inject
    constructor(
        private val getLibraryUseCase: GetLibraryUseCase,
        private val searchBooksUseCase: SearchBooksUseCase,
        private val getFavoriteBooksUseCase: GetFavoriteBooksUseCase,
        private val getRecentlyPlayedBooksUseCase: GetRecentlyPlayedBooksUseCase,
        private val getInProgressBooksUseCase: GetInProgressBooksUseCase,
        private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
        private val deleteBookUseCase: DeleteBookUseCase,
        private val favoritesRepository: FavoritesRepository,
        private val workManager: WorkManager,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val scanPathDao: com.jabook.app.jabook.compose.data.local.dao.ScanPathDao,
        private val application: android.app.Application,
    ) : ViewModel() {
        // Search query state
        private val _searchQuery = MutableStateFlow("")
        public val searchQuery: StateFlow<String> = _searchQuery

        // Sort order state
        private val _sortOrder = MutableStateFlow(BookSortOrder.BY_ACTIVITY)
        public val sortOrder: StateFlow<BookSortOrder> = _sortOrder

        // View mode state
        private val _viewMode = MutableStateFlow(LibraryViewMode.LIST_COMPACT)
        public val viewMode: StateFlow<LibraryViewMode> = _viewMode

        // Selected book for properties dialog
        private val _selectedBookForProperties = MutableStateFlow<Book?>(null)
        public val selectedBookForProperties: StateFlow<Book?> = _selectedBookForProperties

        init {
            // Load saved settings from preferences
            viewModelScope.launch {
                userPreferencesRepository.userData.collect { userData ->
                    _viewMode.value = userData.viewMode
                    _sortOrder.value = userData.sortOrder
                }
            }
        }

        /**
         * UI state combining books data with loading/error states.
         */
        public val uiState: StateFlow<LibraryUiState> =
            _sortOrder
                .flatMapLatest { order ->
                    combine(
                        getLibraryUseCase(order),
                        _searchQuery,
                    ) { books, query ->
                        try {
                            val filteredBooks =
                                if (query.isBlank()) {
                                    books
                                } else {
                                    books.filter { book ->
                                        book.title.contains(query, ignoreCase = true) ||
                                            book.author.contains(query, ignoreCase = true)
                                    }
                                }

                            if (filteredBooks.isEmpty()) {
                                LibraryUiState.Empty
                            } else {
                                LibraryUiState.Success(filteredBooks)
                            }
                        } catch (e: Exception) {
                            LibraryUiState.Error(e.message ?: "Unknown error")
                        }
                    }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = LibraryUiState.Loading,
                )

        /**
         * Update search query.
         */
        public fun onSearchQueryChanged(query: String) {
            _searchQuery.value = query
        }

        /**
         * Update sort order.
         */
        public fun onSortOrderChanged(order: BookSortOrder) {
            _sortOrder.value = order
            viewModelScope.launch {
                userPreferencesRepository.setSortOrder(order)
            }
        }

        /**
         * Update view mode.
         */
        public fun onViewModeChanged(mode: LibraryViewMode) {
            viewModelScope.launch {
                _viewMode.value = mode
                userPreferencesRepository.setViewMode(mode)
            }
        }

        /**
         * Get favorite books reactively.
         */
        public val favoriteBooks: StateFlow<List<Book>> =
            getFavoriteBooksUseCase()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        /**
         * Get recently played books.
         */
        public val recentlyPlayed: StateFlow<List<Book>> =
            getRecentlyPlayedBooksUseCase(limit = 10)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        /**
         * Get in-progress books.
         */
        public val inProgress: StateFlow<List<Book>> =
            getInProgressBooksUseCase()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        /**
         * Toggle favorite status of a book.
         * Synchronizes with FavoriteEntity for unified favorites system.
         */
        public fun toggleFavorite(
            bookId: String,
            isFavorite: Boolean,
        ) {
            viewModelScope.launch {
                // CRITICAL: Get book data BEFORE updating, as we need current book info
                // Use current sort order to get book from the same Flow that UI uses
                val currentBooks = getLibraryUseCase(_sortOrder.value).first()
                val book = currentBooks.find { it.id == bookId }

                // Update local book favorite status in database
                // This will trigger Flow update automatically
                toggleFavoriteUseCase(bookId, isFavorite)

                // Synchronize with FavoriteEntity for unified favorites system
                if (isFavorite) {
                    // Add to FavoriteEntity if book exists
                    if (book != null) {
                        val favoriteEntity = book.toFavoriteEntity()
                        favoritesRepository.addToFavorites(favoriteEntity)
                    } else {
                        // If book not found in current list, try to get it from database directly
                        // This can happen if book is filtered out by search
                        val allBooks = getLibraryUseCase(BookSortOrder.BY_ACTIVITY).first()
                        val foundBook = allBooks.find { it.id == bookId }
                        if (foundBook != null) {
                            val favoriteEntity = foundBook.toFavoriteEntity()
                            favoritesRepository.addToFavorites(favoriteEntity)
                        }
                    }
                } else {
                    // Remove from FavoriteEntity
                    favoritesRepository.removeFromFavorites(bookId)
                }
            }
        }

        /**
         * Delete a book.
         */
        public fun deleteBook(bookId: String) {
            viewModelScope.launch {
                deleteBookUseCase(bookId)
                // Result handling can be added if needed for user feedback
            }
        }

        /**
         * Show book properties dialog.
         */
        public fun showBookProperties(bookId: String) {
            viewModelScope.launch {
                // Find book from current UI state
                val book =
                    (uiState.value as? LibraryUiState.Success)?.books?.find {
                        it.id == bookId
                    }
                _selectedBookForProperties.value = book
            }
        }

        /**
         * Hide book properties dialog.
         */
        public fun hideBookProperties() {
            _selectedBookForProperties.value = null
        }

        // Library scan state
        private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
        public val scanState: StateFlow<ScanState> = _scanState

        // Track current scan work for cancellation
        private var currentScanWorkId: java.util.UUID? = null

        /**
         * Start library scan for local audiobooks.
         */
        public fun startLibraryScan() {
            viewModelScope.launch {
                // Check if scan folders are configured
                val scanFolders = scanPathDao.getAllPathsList()
                if (scanFolders.isEmpty()) {
                    // No folders configured - skip scan and show completion with flag
                    _scanState.value =
                        ScanState.Completed(
                            booksFound = 0,
                            noFoldersConfigured = true,
                        )
                    return@launch
                }

                // Folders configured - proceed with scan
                val scanRequest =
                    OneTimeWorkRequestBuilder<LibraryScanWorker>()
                        .setConstraints(
                            Constraints
                                .Builder()
                                .setRequiresStorageNotLow(true)
                                .setRequiresBatteryNotLow(true)
                                .build(),
                        ).build()

                // Track work ID for cancellation
                currentScanWorkId = scanRequest.id
                workManager.enqueueUniqueWork(
                    LibraryScanWorker.WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    scanRequest,
                )

                // Observe work progress
                workManager.getWorkInfoByIdFlow(scanRequest.id).collect { workInfo ->
                    _scanState.value =
                        when (workInfo?.state) {
                            WorkInfo.State.RUNNING -> {
                                val status =
                                    workInfo.progress.getString("status")
                                        ?: application.getString(com.jabook.app.jabook.R.string.scanningLibrary)
                                ScanState.Scanning(status)
                            }
                            WorkInfo.State.SUCCEEDED -> {
                                val count = workInfo.outputData.getInt("booksFound", 0)
                                ScanState.Completed(count)
                            }
                            WorkInfo.State.FAILED -> {
                                val error =
                                    workInfo.outputData.getString("error")
                                        ?: application.getString(com.jabook.app.jabook.R.string.libraryUnknownError)
                                ScanState.Failed(error)
                            }
                            else -> ScanState.Idle
                        }
                }
            }
        }

        /**
         * Cancel the currently running library scan.
         */
        public fun cancelLibraryScan() {
            workManager.cancelUniqueWork(LibraryScanWorker.WORK_NAME)
            currentScanWorkId = null
            _scanState.value = ScanState.Idle
        }
    }

/**
 * UI state for the Library screen.
 */
public sealed interface LibraryUiState {
    /**
     * Loading state - initial load or refreshing.
     */
    public data object Loading : LibraryUiState

    /**
     * Success state with books.
     */
    public data class Success(
        val books: List<Book>,
    ) : LibraryUiState

    /**
     * Empty state - no books found.
     */
    public data object Empty : LibraryUiState

    /**
     * Error state.
     */
    public data class Error(
        val message: String,
    ) : LibraryUiState
}

/**
 * State of library scanning operation.
 */
public sealed interface ScanState {
    public data object Idle : ScanState

    public data class Scanning(
        val message: String,
    ) : ScanState

    public data class Completed(
        val booksFound: Int,
        val noFoldersConfigured: Boolean = false,
    ) : ScanState

    public data class Failed(
        val error: String,
    ) : ScanState
}

/**
 * Creates a BookActionsProvider from the current ViewModel state.
 *
 * This extension function consolidates all book actions into a single provider
 * that can be passed to UI components, ensuring consistent behavior across
 * all display modes.
 *
 * @param onBookClick Optional override for book click action
 * @return BookActionsProvider configured with current ViewModel state
 */
public fun LibraryViewModel.createBookActionsProvider(
    onBookClick: (String) -> Unit,
): com.jabook.app.jabook.compose.domain.model.BookActionsProvider {
    val favoriteIds = favoriteBooks.value.map { it.id }.toSet()

    return com.jabook.app.jabook.compose.domain.model.BookActionsProvider(
        onBookClick = onBookClick,
        onBookLongPress = { bookId -> showBookProperties(bookId) },
        onToggleFavorite = ::toggleFavorite,
        favoriteIds = favoriteIds,
        showProgress = true,
        showFavoriteButton = true,
    )
}
