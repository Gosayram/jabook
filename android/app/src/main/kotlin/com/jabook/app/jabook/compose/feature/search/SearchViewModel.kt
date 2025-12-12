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
import com.jabook.app.jabook.compose.domain.model.SearchFilters
import com.jabook.app.jabook.compose.domain.model.SearchSortOrder
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
        private val searchHistoryRepository: com.jabook.app.jabook.compose.data.repository.SearchHistoryRepository,
    ) : ViewModel() {
        // Search query state
        private val _searchQuery = MutableStateFlow("")
        val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

        // Filters and Sort state
        private val _filters = MutableStateFlow(SearchFilters())
        val filters: StateFlow<SearchFilters> = _filters.asStateFlow()

        private val _sortOrder = MutableStateFlow(SearchSortOrder.RELEVANCE)
        val sortOrder: StateFlow<SearchSortOrder> = _sortOrder.asStateFlow()

        // Raw results to support client-side filtering
        private val rawOnlineResults = MutableStateFlow<List<SearchResult>>(emptyList())

        // UI state - derived from raw results and filters
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
         * Recent search history.
         */
        val searchHistory: StateFlow<List<com.jabook.app.jabook.compose.data.local.entity.SearchHistoryEntity>> =
            searchHistoryRepository
                .getRecentSearches(limit = 10)
                .stateIn(
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
         * Update filters.
         */
        fun updateFilters(newFilters: SearchFilters) {
            _filters.value = newFilters
            recalculateUiState()
        }

        /**
         * Update sort order.
         */
        fun updateSortOrder(order: SearchSortOrder) {
            _sortOrder.value = order
            recalculateUiState()
        }

        /**
         * Clear search query.
         */
        fun clearSearch() {
            _searchQuery.value = ""
            rawOnlineResults.value = emptyList()
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
                        rawOnlineResults.value = result.data
                        recalculateUiState()
                    }
                    is Result.Error -> {
                        rawOnlineResults.value = emptyList()
                        _uiState.value =
                            SearchUiState.Error(
                                result.exception.message ?: "Unknown error",
                            )
                    }
                    is Result.Loading -> {
                        // Already in loading state
                    }
                }
            }
        }

        private fun recalculateUiState() {
            val currentRaw = rawOnlineResults.value
            if (currentRaw.isEmpty() && _uiState.value !is SearchUiState.Success) return

            val filtered = applyFiltersAndSort(currentRaw)

            _uiState.value =
                SearchUiState.Success(
                    localResults = localResults.value, // Note: Local results filtering is separate/implicit via query for now
                    onlineResults = filtered,
                )
        }

        private fun applyFiltersAndSort(results: List<SearchResult>): List<SearchResult> {
            var processing = results

            // Apply filters
            val f = _filters.value
            if (f.minSeeders != null) {
                processing = processing.filter { it.seeders >= f.minSeeders }
            }
            if (f.minSize != null || f.maxSize != null) {
                processing =
                    processing.filter { result ->
                        val sizeBytes = parseSize(result.size)
                        val minOk = f.minSize?.let { sizeBytes >= it } ?: true
                        val maxOk = f.maxSize?.let { sizeBytes <= it } ?: true
                        minOk && maxOk
                    }
            }

            // Apply sort
            processing =
                when (_sortOrder.value) {
                    SearchSortOrder.RELEVANCE -> processing // Default order
                    SearchSortOrder.DATE_DESC -> processing // Date not available in SearchResult yet
                    SearchSortOrder.DATE_ASC -> processing // Date not available in SearchResult yet
                    SearchSortOrder.SIZE_DESC -> processing.sortedByDescending { parseSize(it.size) }
                    SearchSortOrder.SIZE_ASC -> processing.sortedBy { parseSize(it.size) }
                    SearchSortOrder.SEEDERS_DESC -> processing.sortedByDescending { it.seeders }
                    SearchSortOrder.TITLE_ASC -> processing.sortedBy { it.title }
                }

            return processing
        }

        private fun parseSize(sizeStr: String): Long {
            // Expected format: "1.23 GB" or "500 MB"
            val parts = sizeStr.trim().split("\\s+".toRegex())
            if (parts.size < 2) return 0L
            val value = parts[0].toDoubleOrNull() ?: 0.0
            val unit = parts[1].uppercase()
            return when {
                unit.contains("GB") -> (value * 1024 * 1024 * 1024).toLong()
                unit.contains("MB") -> (value * 1024 * 1024).toLong()
                unit.contains("KB") -> (value * 1024).toLong()
                else -> value.toLong()
            }
        }

        /**
         * Save search to history.
         */
        fun saveSearchToHistory(
            query: String,
            resultCount: Int = 0,
        ) {
            if (query.isBlank()) return
            viewModelScope.launch {
                searchHistoryRepository.saveSearch(query, resultCount)
            }
        }

        /**
         * Delete specific search history item.
         */
        fun deleteSearchHistoryItem(id: Long) {
            viewModelScope.launch {
                searchHistoryRepository.deleteSearch(id)
            }
        }

        /**
         * Clear all search history.
         */
        fun clearSearchHistory() {
            viewModelScope.launch {
                searchHistoryRepository.clearAll()
            }
        }
    }
