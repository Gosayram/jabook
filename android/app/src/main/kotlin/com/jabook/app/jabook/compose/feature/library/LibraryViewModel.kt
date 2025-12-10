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
import com.jabook.app.jabook.compose.data.model.Book
import com.jabook.app.jabook.compose.data.model.BookSortOrder
import com.jabook.app.jabook.compose.data.repository.BooksRepository
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
 * Manages the state of the book library, including:
 * - List of books from repository
 * - Search query
 * - Sort order
 * - Loading and error states
 */
@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        private val booksRepository: BooksRepository,
    ) : ViewModel() {
        // Search query state
        private val _searchQuery = MutableStateFlow("")
        val searchQuery: StateFlow<String> = _searchQuery

        // Sort order state
        private val _sortOrder = MutableStateFlow(BookSortOrder.RECENTLY_PLAYED)
        val sortOrder: StateFlow<BookSortOrder> = _sortOrder

        // Is refreshing state
        private val _isRefreshing = MutableStateFlow(false)
        val isRefreshing: StateFlow<Boolean> = _isRefreshing

        /**
         * UI state combining books data with loading/error states.
         */
        val uiState: StateFlow<LibraryUiState> =
            combine(
                booksRepository.getAllBooks(),
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
         * Refresh the library.
         */
        fun refresh() {
            viewModelScope.launch {
                _isRefreshing.value = true
                try {
                    booksRepository.refresh()
                } finally {
                    _isRefreshing.value = false
                }
            }
        }

        /**
         * Delete a book.
         */
        fun deleteBook(bookId: String) {
            viewModelScope.launch {
                booksRepository.deleteBook(bookId)
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
