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

package com.jabook.app.jabook.compose.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.compose.data.local.entity.FavoriteEntity
import com.jabook.app.jabook.compose.data.model.BookSortOrder
import com.jabook.app.jabook.compose.data.repository.FavoritesRepository
import com.jabook.app.jabook.compose.domain.model.toFavoriteEntity
import com.jabook.app.jabook.compose.domain.usecase.library.GetFavoriteBooksUseCase
import com.jabook.app.jabook.compose.domain.usecase.library.ToggleFavoriteUseCase
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
 * ViewModel for favorites screen.
 * Manages favorites state and operations.
 */
@HiltViewModel
public class FavoritesViewModel
    @Inject
    constructor(
        private val favoritesRepository: FavoritesRepository,
        private val getFavoriteBooksUseCase: GetFavoriteBooksUseCase,
        private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    ) : ViewModel() {
        // Search query state
        private val _searchQuery = MutableStateFlow("")
        public val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

        // Sort order state
        private val _sortOrder = MutableStateFlow(BookSortOrder.RECENTLY_ADDED)
        public val sortOrder: StateFlow<BookSortOrder> = _sortOrder.asStateFlow()

        /**
         * All favorites with search and sort applied.
         * Combines online favorites (FavoriteEntity) and local library favorites (Book with isFavorite=true).
         */
        public val favorites: StateFlow<List<FavoriteEntity>> =
            combine(
                favoritesRepository.allFavorites,
                getFavoriteBooksUseCase(),
                _searchQuery,
                _sortOrder,
            ) { onlineFavorites, localFavoriteBooks, query, order ->
                // Convert local favorite books to FavoriteEntity
                public val localFavorites = localFavoriteBooks.map { it.toFavoriteEntity() }

                // Combine online and local favorites, avoiding duplicates (prefer online if exists)
                public val favoriteIds = onlineFavorites.map { it.topicId }.toSet()
                public val uniqueLocalFavorites = localFavorites.filter { it.topicId !in favoriteIds }
                public val allFavorites = onlineFavorites + uniqueLocalFavorites

                // Apply search filter
                public val filtered =
                    if (query.isBlank()) {
                        allFavorites
                    } else {
                        allFavorites.filter { favorite ->
                            favorite.title.contains(query, ignoreCase = true) ||
                                favorite.author.contains(query, ignoreCase = true)
                        }
                    }

                // Apply sort
                when (order) {
                    BookSortOrder.TITLE_ASC -> filtered.sortedBy { it.title }
                    BookSortOrder.TITLE_DESC -> filtered.sortedByDescending { it.title }
                    BookSortOrder.AUTHOR_ASC -> filtered.sortedBy { it.author }
                    BookSortOrder.AUTHOR_DESC -> filtered.sortedByDescending { it.author }
                    BookSortOrder.RECENTLY_ADDED -> filtered.sortedByDescending { it.addedDate }
                    BookSortOrder.OLDEST_FIRST -> filtered.sortedBy { it.addedDate }
                    BookSortOrder.BY_ACTIVITY -> filtered.sortedByDescending { it.addedDate } // Use addedDate as activity proxy
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            )

        /**
         * Set of favorite topic IDs for quick membership checks.
         * Combines online favorites and local library favorites.
         */
        public val favoriteIds: StateFlow<Set<String>> =
            combine(
                favoritesRepository.favoriteIds,
                getFavoriteBooksUseCase(),
            ) { onlineIds, localBooks ->
                public val onlineSet = onlineIds.toSet()
                public val localSet = localBooks.map { it.id }.toSet()
                onlineSet + localSet
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptySet(),
            )

        private val _isLoading = MutableStateFlow(false)
        public val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        public val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        /**
         * Add or remove an audiobook from favorites.
         */
        public fun toggleFavorite(favorite: FavoriteEntity) {
            viewModelScope.launch {
                _isLoading.value = true
                public val isFavoriteNow = favoritesRepository.isFavorite(favorite.topicId)

                public val result =
                    if (isFavoriteNow) {
                        favoritesRepository.removeFromFavorites(favorite.topicId)
                    } else {
                        favoritesRepository.addToFavorites(favorite)
                    }

                result.onFailure { error ->
                    _errorMessage.value = error.message
                }
                _isLoading.value = false
            }
        }

        /**
         * Remove an audiobook from favorites.
         * Synchronizes with local library (removes isFavorite flag).
         */
        public fun removeFromFavorites(topicId: String) {
            viewModelScope.launch {
                // Remove from FavoriteEntity
                favoritesRepository
                    .removeFromFavorites(topicId)
                    .onFailure { _errorMessage.value = it.message }

                // Also remove from local library if it exists there
                toggleFavoriteUseCase(topicId, false)
            }
        }

        /**
         * Remove multiple audiobooks from favorites.
         */
        public fun removeMultipleFavorites(topicIds: List<String>) {
            viewModelScope.launch {
                favoritesRepository
                    .removeMultipleFavorites(topicIds)
                    .onFailure { _errorMessage.value = it.message }
            }
        }

        /**
         * Clear all favorites.
         */
        public fun clearAllFavorites() : Unit {
            viewModelScope.launch {
                favoritesRepository
                    .clearAllFavorites()
                    .onFailure { _errorMessage.value = it.message }
            }
        }

        /**
         * Clear the error message.
         */
        public fun clearErrorMessage() : Unit {
            _errorMessage.value = null
        }

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
        }
    }
