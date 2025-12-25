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

                repository
                    .searchAudiobooks(query, forumIds)
                    .onSuccess { results ->
                        _searchState.value =
                            if (results.isEmpty()) {
                                SearchState.Empty
                            } else {
                                SearchState.Success(results)
                            }
                    }.onFailure { error ->
                        _searchState.value =
                            SearchState.Error(
                                error.message, // Nullable - UI will localize if null
                            )
                    }
            }
        }

        /**
         * Clear search results.
         */
        fun clearSearch() {
            _searchState.value = SearchState.Empty
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
    ) : SearchState()

    /** Search failed */
    data class Error(
        val message: String?, // Nullable - UI should use stringResource(R.string.unknownError) as fallback
    ) : SearchState()
}
