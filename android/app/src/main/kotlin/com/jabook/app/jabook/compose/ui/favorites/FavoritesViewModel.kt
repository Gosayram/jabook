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

package com.jabook.app.jabook.compose.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.compose.data.local.entity.FavoriteEntity
import com.jabook.app.jabook.compose.data.repository.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for favorites screen.
 * Manages favorites state and operations.
 */
@HiltViewModel
class FavoritesViewModel
    @Inject
    constructor(
        private val favoritesRepository: FavoritesRepository,
    ) : ViewModel() {
        /**
         * All favorites sorted by date added (newest first).
         */
        val favorites: StateFlow<List<FavoriteEntity>> =
            favoritesRepository.allFavorites
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        /**
         * Set of favorite topic IDs for quick membership checks.
         */
        val favoriteIds: StateFlow<Set<String>> =
            favoritesRepository.favoriteIds
                .map { it.toSet() }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptySet(),
                )

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        /**
         * Add or remove an audiobook from favorites.
         */
        fun toggleFavorite(favorite: FavoriteEntity) {
            viewModelScope.launch {
                _isLoading.value = true
                val isFavoriteNow = favoritesRepository.isFavorite(favorite.topicId)

                val result =
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
         */
        fun removeFromFavorites(topicId: String) {
            viewModelScope.launch {
                favoritesRepository
                    .removeFromFavorites(topicId)
                    .onFailure { _errorMessage.value = it.message }
            }
        }

        /**
         * Remove multiple audiobooks from favorites.
         */
        fun removeMultipleFavorites(topicIds: List<String>) {
            viewModelScope.launch {
                favoritesRepository
                    .removeMultipleFavorites(topicIds)
                    .onFailure { _errorMessage.value = it.message }
            }
        }

        /**
         * Clear all favorites.
         */
        fun clearAllFavorites() {
            viewModelScope.launch {
                favoritesRepository
                    .clearAllFavorites()
                    .onFailure { _errorMessage.value = it.message }
            }
        }

        /**
         * Clear the error message.
         */
        fun clearErrorMessage() {
            _errorMessage.value = null
        }
    }
