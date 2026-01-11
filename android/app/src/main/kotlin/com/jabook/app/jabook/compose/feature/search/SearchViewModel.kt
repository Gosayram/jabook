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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.compose.data.local.entity.FavoriteEntity
import com.jabook.app.jabook.compose.data.repository.FavoritesRepository
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.Result
import com.jabook.app.jabook.compose.domain.model.RutrackerSearchResult
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for search.
 */
public sealed interface SearchUiState {
    public data object Idle : SearchUiState

    public data object Loading : SearchUiState

    public data class Success(
        val localResults: List<Book>,
        val onlineResults: List<RutrackerSearchResult>,
    ) : SearchUiState

    public data class Error(
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
public class SearchViewModel
    @Inject
    constructor(
        private val searchBooksUseCase: SearchBooksUseCase,
        private val searchRutrackerUseCase: SearchRutrackerUseCase,
        private val searchHistoryRepository: com.jabook.app.jabook.compose.data.repository.SearchHistoryRepository,
        private val favoritesRepository: FavoritesRepository,
    ) : ViewModel() {
        // Search query state
        private val _searchQuery = MutableStateFlow("")
        public val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

        // Filters and Sort state
        private val _filters = MutableStateFlow(SearchFilters())
        public val filters: StateFlow<SearchFilters> = _filters.asStateFlow()

        private val _sortOrder = MutableStateFlow(SearchSortOrder.RELEVANCE)
        public val sortOrder: StateFlow<SearchSortOrder> = _sortOrder.asStateFlow()

        // Raw results to support client-side filtering
        private val rawOnlineResults = MutableStateFlow<List<RutrackerSearchResult>>(emptyList())

        // UI state - derived from raw results and filters
        private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
        public val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

        /**
         * Local search results with debouncing (300ms).
         */
        public val localResults: StateFlow<List<Book>> =
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
        public val searchHistory: StateFlow<List<com.jabook.app.jabook.compose.data.local.entity.SearchHistoryEntity>> =
            searchHistoryRepository
                .getRecentSearches(limit = 10)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        /**
         * Favorite IDs for checking status.
         */
        public val favoriteIds: StateFlow<Set<String>> =
            favoritesRepository.favoriteIds
                .map { it.toSet() }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptySet(),
                )

        /**
         * Update search query.
         */
        public fun onSearchQueryChanged(query: String) {
            _searchQuery.value = query
        }

        /**
         * Update filters.
         */
        public fun updateFilters(newFilters: SearchFilters) {
            _filters.value = newFilters
            recalculateUiState()
        }

        /**
         * Update sort order.
         */
        public fun updateSortOrder(order: SearchSortOrder) {
            _sortOrder.value = order
            recalculateUiState()
        }

        /**
         * Clear search query.
         */
        public fun clearSearch() {
            _searchQuery.value = ""
            rawOnlineResults.value = emptyList()
            _uiState.value = SearchUiState.Idle
        }

        /**
         * Perform online search on Rutracker.
         */
        public fun searchOnline() {
            val query = _searchQuery.value
            if (query.isBlank()) return

            android.util.Log.d("SearchViewModel", "🔍 Starting online search for query: '$query'")
            viewModelScope.launch {
                _uiState.value = SearchUiState.Loading

                searchRutrackerUseCase(query).collect { result ->
                    when (result) {
                        is Result.Success -> {
                            android.util.Log.d(
                                "SearchViewModel",
                                "✅ Search successful: received ${result.data.size} results for query '$query'",
                            )
                            // Log details about results
                            if (result.data.isNotEmpty()) {
                                val sample = result.data.take(3)
                                sample.forEachIndexed { index, item ->
                                    android.util.Log.d(
                                        "SearchViewModel",
                                        "  Result[$index]: topicId='${item.topicId}', " +
                                            "title='${item.title.take(50)}', " +
                                            "author='${item.author.take(30)}', " +
                                            "coverUrl=${if (item.coverUrl.isNullOrBlank()) "null" else "present"}, " +
                                            "valid=${item.isValid()}",
                                    )
                                }
                            } else {
                                android.util.Log.w("SearchViewModel", "⚠️ Search returned empty results for query '$query'")
                            }
                            rawOnlineResults.value = result.data
                            recalculateUiState()
                        }
                        is Result.Error -> {
                            android.util.Log.e(
                                "SearchViewModel",
                                "❌ Search failed for query '$query': ${result.exception.message}",
                                result.exception,
                            )
                            rawOnlineResults.value = emptyList()
                            _uiState.value =
                                SearchUiState.Error(
                                    result.exception.message ?: "Unknown error",
                                )
                        }
                        is Result.Loading -> {
                            // Already in loading state
                            android.util.Log.d("SearchViewModel", "⏳ Search in progress for query '$query'")
                        }
                    }
                }
            }
        }

        private fun recalculateUiState() {
            val currentRaw = rawOnlineResults.value
            if (currentRaw.isEmpty() && _uiState.value !is SearchUiState.Success) {
                android.util.Log.d("SearchViewModel", "⏭️ Skipping UI state recalculation: no raw results")
                return
            }

            android.util.Log.d(
                "SearchViewModel",
                "🔄 Recalculating UI state: ${currentRaw.size} raw results, " +
                    "filters=${_filters.value}, sortOrder=${_sortOrder.value}",
            )
            val filtered = applyFiltersAndSort(currentRaw)
            android.util.Log.d(
                "SearchViewModel",
                "✅ UI state updated: ${filtered.size} filtered results (from ${currentRaw.size} raw)",
            )

            _uiState.value =
                SearchUiState.Success(
                    localResults = localResults.value, // Note: Local results filtering is separate/implicit via query for now
                    onlineResults = filtered,
                )
        }

        private fun applyFiltersAndSort(results: List<RutrackerSearchResult>): List<RutrackerSearchResult> {
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
        public fun saveSearchToHistory(
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
        public fun deleteSearchHistoryItem(id: Int) {
            viewModelScope.launch {
                searchHistoryRepository.deleteSearch(id.toLong())
            }
        }

        /**
         * Clear all search history.
         */
        public fun clearSearchHistory() {
            viewModelScope.launch {
                searchHistoryRepository.clearAll()
            }
        }

        /**
         * Toggle favorite status for a search result.
         */
        public fun toggleFavorite(result: RutrackerSearchResult) {
            viewModelScope.launch {
                if (favoriteIds.value.contains(result.topicId)) {
                    favoritesRepository.removeFromFavorites(result.topicId)
                } else {
                    val favorite =
                        FavoriteEntity(
                            topicId = result.topicId,
                            title = result.title,
                            author = result.author,
                            category = result.category,
                            size = result.size,
                            seeders = result.seeders,
                            leechers = result.leechers,
                            magnetUrl = result.magnetUrl ?: "",
                            coverUrl = "", // Not available in search result
                            performer = "",
                            genres = "",
                            addedDate =
                                java.time.Instant
                                    .now()
                                    .toString(),
                            addedToFavorites =
                                java.time.Instant
                                    .now()
                                    .toString(),
                            duration = null,
                            bitrate = null,
                            audioCodec = null,
                        )
                    favoritesRepository.addToFavorites(favorite)
                }
            }
        }
    }

/**
 * Creates a BookActionsProvider for local search results.
 */
public fun SearchViewModel.createLocalBookActionsProvider(
    onBookClick: (String) -> Unit,
): com.jabook.app.jabook.compose.domain.model.BookActionsProvider =
    com.jabook.app.jabook.compose.domain.model.BookActionsProvider(
        onBookClick = onBookClick,
        onBookLongPress = {}, // No long press for search results
        onToggleFavorite = { _, _ -> }, // No favorite for local books in search
        favoriteIds = emptySet(),
        showProgress = false, // Don't show progress in search
        showFavoriteButton = false, // Don't show favorite button for local results
    )

/**
 * Creates a BookActionsProvider for online search results (RutrackerSearchResult).
 *
 * Note: onBookClick takes the full RutrackerSearchResult instead of just ID.
 */
public fun SearchViewModel.createOnlineBookActionsProvider(
    onBookClick: (RutrackerSearchResult) -> Unit,
    onToggleFavorite: (RutrackerSearchResult) -> Unit,
): Pair<
    com.jabook.app.jabook.compose.domain.model.BookActionsProvider,
    (
        String,
    ) -> RutrackerSearchResult?,
> {
    // We need a way to map book ID back to RutrackerSearchResult for callbacks
    val currentResults = mutableMapOf<String, RutrackerSearchResult>()

    val provider =
        com.jabook.app.jabook.compose.domain.model.BookActionsProvider(
            onBookClick = { bookId ->
                currentResults[bookId]?.let(onBookClick)
            },
            onBookLongPress = {}, // No long press for online results
            onToggleFavorite = { bookId, _ ->
                currentResults[bookId]?.let(onToggleFavorite)
            },
            favoriteIds = favoriteIds.value,
            showProgress = false,
            showFavoriteButton = true, // Show favorite button for online results
        )

    return provider to { id -> currentResults[id] }
}
