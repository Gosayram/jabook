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

package com.jabook.app.jabook.compose.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.compose.data.model.AppTheme
import com.jabook.app.jabook.compose.data.model.BookSortOrder
import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 *
 * Manages user preferences and app settings.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val userPreferencesRepository: UserPreferencesRepository,
    ) : ViewModel() {
        /**
         * User preferences as StateFlow.
         * Automatically updates when preferences change.
         */
        val userPreferences =
            userPreferencesRepository.userData.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null,
            )

        /**
         * Update app theme preference.
         */
        fun updateTheme(theme: AppTheme) {
            viewModelScope.launch {
                userPreferencesRepository.setTheme(theme)
            }
        }

        /**
         * Update book sort order preference.
         */
        fun updateSortOrder(sortOrder: BookSortOrder) {
            viewModelScope.launch {
                userPreferencesRepository.setSortOrder(sortOrder)
            }
        }

        /**
         * Update auto-play next preference.
         */
        fun updateAutoPlayNext(enabled: Boolean) {
            viewModelScope.launch {
                userPreferencesRepository.setAutoPlayNext(enabled)
            }
        }

        /**
         * Update playback speed preference.
         */
        fun updatePlaybackSpeed(speed: Float) {
            viewModelScope.launch {
                userPreferencesRepository.setPlaybackSpeed(speed)
            }
        }
    }
