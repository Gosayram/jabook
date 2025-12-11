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

package com.jabook.app.jabook.compose.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.compose.data.remote.model.SearchResult
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.Result
import com.jabook.app.jabook.compose.domain.usecase.library.SearchBooksUseCase
import com.jabook.app.jabook.compose.domain.usecase.search.SearchRutrackerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for search.
 */
sealed interface SearchUiState {
    data object Idle : SearchUiState

    data object Loading : SearchUiState

    data class Success(
        val localResults: List<Book>,
        val onlineResults: List<SearchResult>,
    ) : SearchUiState

    data class Error(
        val message: String,
    ) : SearchUiState
}

/**
 * ViewModel for the Search screen.
 *
 * Manages search query state and results from both local database
 * and online Rutracker search.
 * Implements debouncing to avoid excessive searches while typing.
 */
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        private val searchBooksUseCase: SearchBooksUseCase,
        private val searchRutrackerUseCase: SearchRutrackerUseCase,
    ) : ViewModel() {
        // Search query state
        private val _searchQuery = MutableStateFlow("")
        val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

        // UI state
        private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
        val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

        /**
         * Local search results with debouncing (300ms).
         */
        val localResults: StateFlow<List<Book>> =
            _searchQuery
                .debounce(300)
                .flatMapLatest { query ->
                    if (query.isBlank()) {
                        kotlinx.coroutines.flow.flowOf(emptyList())
                    } else {
                        searchBooksUseCase(query)
                    }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        /**
         * Update search query.
         */
        fun onSearchQueryChanged(query: String) {
            _searchQuery.value = query
        }

        /**
         * Clear search query.
         */
        fun clearSearch() {
            _searchQuery.value = ""
            _uiState.value = SearchUiState.Idle
        }

        /**
         * Perform online search on Rutracker.
         */
        fun searchOnline() {
            val query = _searchQuery.value
            if (query.isBlank()) return

            viewModelScope.launch {
                _uiState.value = SearchUiState.Loading

                when (val result = searchRutrackerUseCase(query)) {
                    is Result.Success -> {
                        _uiState.value =
                            SearchUiState.Success(
                                localResults = localResults.value,
                                onlineResults = result.data,
                            )
                    }
                    is Result.Error -> {
                        _uiState.value =
                            SearchUiState.Error(
                                result.exception.message ?: "Unknown error",
                            )
                    }
                    is Result.Loading -> {
                        // Already in loading state, nothing to do
                    }
                }
            }
        }
    }
