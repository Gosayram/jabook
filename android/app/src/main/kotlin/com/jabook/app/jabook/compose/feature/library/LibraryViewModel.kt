// Copyright 2025 Jabook Contributors
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
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.jabook.app.jabook.compose.data.model.BookSortOrder
import com.jabook.app.jabook.compose.data.worker.LibraryScanWorker
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.usecase.library.DeleteBookUseCase
import com.jabook.app.jabook.compose.domain.usecase.library.GetFavoriteBooksUseCase
import com.jabook.app.jabook.compose.domain.usecase.library.GetInProgressBooksUseCase
import com.jabook.app.jabook.compose.domain.usecase.library.GetLibraryUseCase
import com.jabook.app.jabook.compose.domain.usecase.library.GetRecentlyPlayedBooksUseCase
import com.jabook.app.jabook.compose.domain.usecase.library.SearchBooksUseCase
import com.jabook.app.jabook.compose.domain.usecase.library.ToggleFavoriteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        private val getLibraryUseCase: GetLibraryUseCase,
        private val searchBooksUseCase: SearchBooksUseCase,
        private val getFavoriteBooksUseCase: GetFavoriteBooksUseCase,
        private val getRecentlyPlayedBooksUseCase: GetRecentlyPlayedBooksUseCase,
        private val getInProgressBooksUseCase: GetInProgressBooksUseCase,
        private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
        private val deleteBookUseCase: DeleteBookUseCase,
        private val workManager: WorkManager,
    ) : ViewModel() {
        // Search query state
        private val _searchQuery = MutableStateFlow("")
        val searchQuery: StateFlow<String> = _searchQuery

        // Sort order state
        private val _sortOrder = MutableStateFlow(BookSortOrder.RECENTLY_PLAYED)
        val sortOrder: StateFlow<BookSortOrder> = _sortOrder

        /**
         * UI state combining books data with loading/error states.
         */
        val uiState: StateFlow<LibraryUiState> =
            combine(
                getLibraryUseCase(),
                _searchQuery,
                _sortOrder,
            ) { books, query, order ->
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

                    val sortedBooks =
                        when (order) {
                            BookSortOrder.RECENTLY_PLAYED ->
                                filteredBooks.sortedByDescending { it.lastPlayedDate ?: 0 }
                            BookSortOrder.RECENTLY_ADDED -> filteredBooks.sortedByDescending { it.addedDate }
                            BookSortOrder.TITLE_ASC -> filteredBooks.sortedBy { it.title }
                            BookSortOrder.TITLE_DESC -> filteredBooks.sortedByDescending { it.title }
                            BookSortOrder.AUTHOR_ASC -> filteredBooks.sortedBy { it.author }
                            BookSortOrder.AUTHOR_DESC -> filteredBooks.sortedByDescending { it.author }
                        }

                    if (sortedBooks.isEmpty()) {
                        LibraryUiState.Empty
                    } else {
                        LibraryUiState.Success(sortedBooks)
                    }
                } catch (e: Exception) {
                    LibraryUiState.Error(e.message ?: "Unknown error")
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = LibraryUiState.Loading,
            )

        /**
         * Update search query.
         */
        fun onSearchQueryChanged(query: String) {
            _searchQuery.value = query
        }

        /**
         * Update sort order.
         */
        fun onSortOrderChanged(order: BookSortOrder) {
            _sortOrder.value = order
        }

        /**
         * Get favorite books reactively.
         */
        val favoriteBooks: StateFlow<List<Book>> =
            getFavoriteBooksUseCase()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        /**
         * Get recently played books.
         */
        val recentlyPlayed: StateFlow<List<Book>> =
            getRecentlyPlayedBooksUseCase(limit = 10)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        /**
         * Get in-progress books.
         */
        val inProgress: StateFlow<List<Book>> =
            getInProgressBooksUseCase()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        /**
         * Toggle favorite status of a book.
         */
        fun toggleFavorite(
            bookId: String,
            isFavorite: Boolean,
        ) {
            viewModelScope.launch {
                toggleFavoriteUseCase(bookId, isFavorite)
            }
        }

        /**
         * Delete a book.
         */
        fun deleteBook(bookId: String) {
            viewModelScope.launch {
                deleteBookUseCase(bookId)
                // Result handling can be added if needed for user feedback
            }
        }

        // Library scan state
        private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
        val scanState: StateFlow<ScanState> = _scanState

        /**
         * Start library scan for local audiobooks.
         */
        fun startLibraryScan() {
            val scanRequest =
                OneTimeWorkRequestBuilder<LibraryScanWorker>()
                    .setConstraints(
                        Constraints
                            .Builder()
                            .setRequiresStorageNotLow(true)
                            .build(),
                    ).build()

            workManager.enqueue(scanRequest)

            // Observe work progress
            viewModelScope.launch {
                workManager.getWorkInfoByIdFlow(scanRequest.id).collect { workInfo ->
                    _scanState.value =
                        when (workInfo?.state) {
                            WorkInfo.State.RUNNING -> {
                                val status = workInfo.progress.getString("status") ?: "Scanning..."
                                ScanState.Scanning(status)
                            }
                            WorkInfo.State.SUCCEEDED -> {
                                val count = workInfo.outputData.getInt("booksFound", 0)
                                ScanState.Completed(count)
                            }
                            WorkInfo.State.FAILED -> {
                                val error = workInfo.outputData.getString("error") ?: "Unknown error"
                                ScanState.Failed(error)
                            }
                            else -> ScanState.Idle
                        }
                }
            }
        }
    }

/**
 * UI state for the Library screen.
 */
sealed interface LibraryUiState {
    /**
     * Loading state - initial load or refreshing.
     */
    data object Loading : LibraryUiState

    /**
     * Success state with books.
     */
    data class Success(
        val books: List<Book>,
    ) : LibraryUiState

    /**
     * Empty state - no books found.
     */
    data object Empty : LibraryUiState

    /**
     * Error state.
     */
    data class Error(
        val message: String,
    ) : LibraryUiState
}

/**
 * State of library scanning operation.
 */
sealed interface ScanState {
    data object Idle : ScanState

    data class Scanning(
        val message: String,
    ) : ScanState

    data class Completed(
        val booksFound: Int,
    ) : ScanState

    data class Failed(
        val error: String,
    ) : ScanState
}
