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

package com.jabook.app.jabook.compose.feature.search.rutracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.compose.data.remote.model.SearchResult
import com.jabook.app.jabook.compose.data.remote.repository.RutrackerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for RuTracker search functionality.
 *
 * Manages search state and communicates with RutrackerRepository.
 */
@HiltViewModel
class RutrackerSearchViewModel
    @Inject
    constructor(
        private val repository: RutrackerRepository,
    ) : ViewModel() {
        private val _searchState = MutableStateFlow<SearchState>(SearchState.Empty)
        val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

        private val _filters = MutableStateFlow(RutrackerSearchFilters())
        val filters: StateFlow<RutrackerSearchFilters> = _filters.asStateFlow()

        private val _sortOrder = MutableStateFlow(RutrackerSortOrder.RELEVANCE)
        val sortOrder: StateFlow<RutrackerSortOrder> = _sortOrder.asStateFlow()

        // Store original results for client-side filtering/sorting
        private var originalResults: List<SearchResult> = emptyList()

        /**
         * Search for audiobooks.
         *
         * @param query Search query
         * @param forumIds Optional forum IDs filter
         */
        fun search(
            query: String,
            forumIds: String? = null,
        ) {
            if (query.isBlank()) {
                _searchState.value = SearchState.Empty
                return
            }

            viewModelScope.launch {
                _searchState.value = SearchState.Loading

                var isFirstEmission = true

                repository
                    .searchAudiobooksFlow(query, forumIds)
                    .collect { result ->
                        val isCachedEmission = isFirstEmission
                        isFirstEmission = false

                        result
                            .onSuccess { results ->
                                originalResults = results
                                val filtered = applyFiltersAndSort(results)
                                _searchState.value =
                                    if (filtered.isEmpty()) {
                                        if (!isCachedEmission) SearchState.Empty else _searchState.value
                                    } else {
                                        SearchState.Success(filtered, isCached = isCachedEmission)
                                    }
                            }.onFailure { error ->
                                val currentState = _searchState.value
                                if (currentState !is SearchState.Success) {
                                    _searchState.value =
                                        SearchState.Error(
                                            error.message,
                                        )
                                }
                            }
                    }
            }
        }

        /**
         * Clear search results.
         */
        fun clearSearch() {
            _searchState.value = SearchState.Empty
            originalResults = emptyList()
        }

        /**
         * Update search filters and reapply to results.
         */
        fun updateFilters(newFilters: RutrackerSearchFilters) {
            _filters.value = newFilters
            reapplyFiltersAndSort()
        }

        /**
         * Update sort order and reapply to results.
         */
        fun updateSortOrder(newSortOrder: RutrackerSortOrder) {
            _sortOrder.value = newSortOrder
            reapplyFiltersAndSort()
        }

        /**
         * Reapply current filters and sort to stored results.
         */
        private fun reapplyFiltersAndSort() {
            if (originalResults.isEmpty()) return

            val filtered = applyFiltersAndSort(originalResults)
            // Preserve isCached state
            val currentIsCached = (_searchState.value as? SearchState.Success)?.isCached ?: false

            _searchState.value =
                if (filtered.isEmpty()) {
                    SearchState.Empty
                } else {
                    SearchState.Success(filtered, isCached = currentIsCached)
                }
        }

        /**
         * Apply filters and sorting to results.
         */
        private fun applyFiltersAndSort(results: List<SearchResult>): List<SearchResult> {
            var filtered = results

            // Apply filters
            val currentFilters = _filters.value
            if (currentFilters.isActive()) {
                filtered =
                    filtered.filter { result ->
                        val passesSeederFilter =
                            currentFilters.minSeeders?.let { min ->
                                result.seeders >= min
                            } ?: true

                        val passesSizeFilter =
                            if (currentFilters.minSizeMb != null || currentFilters.maxSizeMb != null) {
                                val sizeMb = parseSizeToMb(result.size)
                                val passesMin = currentFilters.minSizeMb?.let { sizeMb >= it } ?: true
                                val passesMax = currentFilters.maxSizeMb?.let { sizeMb <= it } ?: true
                                passesMin && passesMax
                            } else {
                                true
                            }

                        passesSeederFilter && passesSizeFilter
                    }
            }

            // Apply sorting
            val sorted =
                when (_sortOrder.value) {
                    RutrackerSortOrder.RELEVANCE -> filtered // Keep original order
                    RutrackerSortOrder.SEEDERS_DESC ->
                        filtered.sortedByDescending { it.seeders }
                    RutrackerSortOrder.SIZE_DESC -> filtered.sortedByDescending { parseSizeToMb(it.size) }
                    RutrackerSortOrder.SIZE_ASC -> filtered.sortedBy { parseSizeToMb(it.size) }
                    RutrackerSortOrder.TITLE_ASC -> filtered.sortedBy { it.title }
                    RutrackerSortOrder.TITLE_DESC -> filtered.sortedByDescending { it.title }
                }

            return sorted
        }

        /**
         * Parse size string to MB for filtering/sorting.
         * Handles formats like "1.5 GB", "500 MB", etc.
         */
        private fun parseSizeToMb(sizeStr: String): Double {
            val pattern = """([\d.]+)\\s*(GB|MB|KB)""".toRegex(RegexOption.IGNORE_CASE)
            val match = pattern.find(sizeStr) ?: return 0.0

            val value = match.groupValues[1].toDoubleOrNull() ?: return 0.0
            val unit = match.groupValues[2].uppercase()

            return when (unit) {
                "GB" -> value * 1024
                "MB" -> value
                "KB" -> value / 1024
                else -> 0.0
            }
        }
    }

/**
 * State for RuTracker search.
 */
sealed class SearchState {
    /** No search performed yet */
    data object Empty : SearchState()

    /** Search in progress */
    data object Loading : SearchState()

    /** Search completed successfully */
    data class Success(
        val results: List<SearchResult>,
        val isCached: Boolean = false,
    ) : SearchState()

    /** Search failed */
    data class Error(
        val message: String?, // Nullable - UI should use stringResource(R.string.unknownError) as fallback
    ) : SearchState()
}
