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

package com.jabook.app.jabook.compose.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jabook.app.jabook.compose.data.model.AppTheme
import com.jabook.app.jabook.compose.data.model.BookSortOrder
import com.jabook.app.jabook.compose.data.model.UserData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of UserPreferencesRepository using DataStore.
 *
 * Provides reactive access to user preferences with automatic persistence.
 */
@Singleton
class DataStoreUserPreferencesRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : UserPreferencesRepository {
        companion object {
            private val THEME = stringPreferencesKey("theme")
            private val SORT_ORDER = stringPreferencesKey("sort_order")
            private val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
            private val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        }

        override val userData: Flow<UserData> =
            dataStore.data.map { preferences ->
                UserData(
                    theme =
                        preferences[THEME]?.let { themeName ->
                            try {
                                AppTheme.valueOf(themeName)
                            } catch (e: IllegalArgumentException) {
                                AppTheme.SYSTEM
                            }
                        } ?: AppTheme.SYSTEM,
                    sortOrder =
                        preferences[SORT_ORDER]?.let { sortName ->
                            try {
                                BookSortOrder.valueOf(sortName)
                            } catch (e: IllegalArgumentException) {
                                BookSortOrder.RECENTLY_PLAYED
                            }
                        } ?: BookSortOrder.RECENTLY_PLAYED,
                    autoPlayNext = preferences[AUTO_PLAY_NEXT] ?: true,
                    playbackSpeed = preferences[PLAYBACK_SPEED] ?: 1.0f,
                )
            }

        override suspend fun setTheme(theme: AppTheme) {
            dataStore.edit { preferences ->
                preferences[THEME] = theme.name
            }
        }

        override suspend fun setSortOrder(sortOrder: BookSortOrder) {
            dataStore.edit { preferences ->
                preferences[SORT_ORDER] = sortOrder.name
            }
        }

        override suspend fun setAutoPlayNext(enabled: Boolean) {
            dataStore.edit { preferences ->
                preferences[AUTO_PLAY_NEXT] = enabled
            }
        }

        override suspend fun setPlaybackSpeed(speed: Float) {
            dataStore.edit { preferences ->
                preferences[PLAYBACK_SPEED] = speed
            }
        }
    }
