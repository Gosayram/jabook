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

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.data.remote.repository.RutrackerRepository
import com.jabook.app.jabook.compose.data.repository.BooksRepository
import com.jabook.app.jabook.compose.domain.model.RutrackerSearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI model for search result with library status.
 */
public data class SearchResultUi(
    val result: RutrackerSearchResult,
    val isInLibrary: Boolean = false,
)

/**
 * ViewModel for RuTracker search functionality.
 *
 * Manages search state and communicates with RutrackerRepository.
 */
@HiltViewModel
public class RutrackerSearchViewModel
    @Inject
    constructor(
        private val repository: RutrackerRepository,
        private val booksRepository: BooksRepository,
        private val coverLoader: CoverLoader,
    ) : ViewModel() {
        public companion object {
            private const val TAG = "RutrackerSearchViewModel"
        }

        private val _searchState = MutableStateFlow<SearchState>(SearchState.Empty)
        public val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

        private val _filters = MutableStateFlow(RutrackerSearchFilters())
        public val filters: StateFlow<RutrackerSearchFilters> = _filters.asStateFlow()

        private val _sortOrder = MutableStateFlow(RutrackerSortOrder.RELEVANCE)
        public val sortOrder: StateFlow<RutrackerSortOrder> = _sortOrder.asStateFlow()

        // Store original results for client-side filtering/sorting
        private var originalResults: List<RutrackerSearchResult> = emptyList()

        // Cache of library book source URLs to check "In Library" status against
        private val librarySourceUrls: StateFlow<Set<String>> =
            booksRepository
                .getAllBooks()
                .map { books -> books.mapNotNull { it.sourceUrl }.toSet() }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptySet(),
                )

        /**
         * Search for audiobooks.
         *
         * @param query Search query
         * @param forumIds Optional forum IDs filter (defaults to audiobooks forums only)
         */
        public fun search(
            query: String,
            forumIds: String? = RutrackerApi.AUDIOBOOKS_FORUM_IDS,
        ) {
            if (query.isBlank()) {
                Log.d(TAG, "Empty query, clearing search state")
                _searchState.value = SearchState.Empty
                return
            }

            Log.d(TAG, "🔍 Starting search: query='$query', forumIds=$forumIds")
            viewModelScope.launch {
                _searchState.value = SearchState.Loading

                var isFirstEmission: Boolean = true
                // Combine search results flow with library books flow
                combine(
                    repository.searchAudiobooksFlow(query, forumIds),
                    librarySourceUrls,
                ) { result, libraryUrls ->
                    result to libraryUrls
                }.collect { (result, libraryUrls) ->
                    val isCachedEmission = isFirstEmission
                    isFirstEmission = false

                    result
                        .onSuccess { results ->
                            Log.d(
                                TAG,
                                "✅ Search results received: ${results.size} results " +
                                    "(cached: $isCachedEmission, libraryUrls: ${libraryUrls.size})",
                            )
                            originalResults = results
                            val filtered = applyFiltersAndSort(results)
                            Log.d(
                                TAG,
                                "🔄 After filters/sort: ${filtered.size} results " +
                                    "(from ${results.size} original)",
                            )

                            // Map to UI model
                            val uiResults =
                                filtered.map {
                                    val inLib =
                                        libraryUrls.contains(it.topicId) ||
                                            libraryUrls.any { url -> url.contains(it.topicId) }
                                    SearchResultUi(
                                        result = it,
                                        isInLibrary = inLib,
                                    )
                                }

                            Log.d(
                                TAG,
                                "📊 UI results: ${uiResults.size} items, " +
                                    "${uiResults.count { it.isInLibrary }} in library",
                            )

                            _searchState.value =
                                if (filtered.isEmpty()) {
                                    if (!isCachedEmission) {
                                        Log.w(TAG, "⚠️ No results after filtering, setting Empty state")
                                        SearchState.Empty
                                    } else {
                                        Log.d(TAG, "⏭️ Keeping current state (cached empty)")
                                        _searchState.value
                                    }
                                } else {
                                    Log.d(TAG, "✅ Setting Success state with ${uiResults.size} results")
                                    SearchState.Success(uiResults, isCached = isCachedEmission)
                                }

                            // Trigger background cover loading for items without covers
                            if (filtered.isNotEmpty()) {
                                filtered.forEach { item ->
                                    if (item.coverUrl.isNullOrBlank()) {
                                        coverLoader.loadCover(item.topicId)
                                    }
                                }
                            }
                        }.onFailure { error ->
                            Log.e(
                                TAG,
                                "❌ Search failed for query '$query': ${error.message}",
                                error,
                            )
                            val currentState = _searchState.value
                            if (currentState !is SearchState.Success) {
                                _searchState.value =
                                    SearchState.Error(
                                        error.message,
                                    )
                            } else {
                                Log.d(TAG, "⏭️ Keeping current Success state despite error")
                            }
                        }
                }
            }
        }

        /**
         * Clear search results.
         */
        public fun clearSearch() {
            _searchState.value = SearchState.Empty
            originalResults = emptyList()
        }

        /**
         * Update search filters and reapply to results.
         */
        public fun updateFilters(newFilters: RutrackerSearchFilters) {
            _filters.value = newFilters
            reapplyFiltersAndSort()
        }

        /**
         * Update sort order and reapply to results.
         */
        public fun updateSortOrder(newSortOrder: RutrackerSortOrder) {
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
            val libraryUrls = librarySourceUrls.value

            val uiResults =
                filtered.map {
                    val inLib = libraryUrls.any { url -> url.contains(it.topicId) }
                    SearchResultUi(
                        result = it,
                        isInLibrary = inLib,
                    )
                }

            _searchState.value =
                if (filtered.isEmpty()) {
                    SearchState.Empty
                } else {
                    SearchState.Success(uiResults, isCached = currentIsCached)
                }
        }

        /**
         * Apply filters and sorting to results.
         */
        private fun applyFiltersAndSort(results: List<RutrackerSearchResult>): List<RutrackerSearchResult> {
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
public sealed class SearchState {
    /** No search performed yet */
    public data object Empty : SearchState()

    /** Search in progress */
    public data object Loading : SearchState()

    /** Search completed successfully */
    public data class Success(
        val results: List<SearchResultUi>,
        val isCached: Boolean = false,
    ) : SearchState()

    /** Search failed */
    public data class Error(
        val message: String?, // Nullable - UI should use stringResource(R.string.unknownError) as fallback
    ) : SearchState()
}
