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

package com.jabook.app.jabook.compose.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jabook.app.jabook.compose.core.util.rethrowCancellation
import com.jabook.app.jabook.compose.core.util.safeEnum
import com.jabook.app.jabook.compose.data.model.AppFont
import com.jabook.app.jabook.compose.data.model.AppTheme
import com.jabook.app.jabook.compose.data.model.BookSortOrder
import com.jabook.app.jabook.compose.data.model.LibraryViewMode
import com.jabook.app.jabook.compose.data.model.UserData
import com.jabook.app.jabook.util.LogUtils
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
public class DataStoreUserPreferencesRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : UserPreferencesRepository {
        public companion object {
            private val THEME = stringPreferencesKey("theme")
            private val SORT_ORDER = stringPreferencesKey("sort_order")
            private val VIEW_MODE = stringPreferencesKey("view_mode")
            private val AUTO_PLAY_NEXT = booleanPreferencesKey("auto_play_next")
            private val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
            private val PITCH_CORRECTION_ENABLED = booleanPreferencesKey("pitch_correction_enabled")
            private val FONT = stringPreferencesKey("font")
            private val NORMALIZE_CHAPTER_TITLES = booleanPreferencesKey("normalize_chapter_titles")
            private val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
            private val STORAGE_FALLBACK_ENABLED = booleanPreferencesKey("storage_fallback_enabled")
            private val SPOTLIGHT_COMPLETED = booleanPreferencesKey("spotlight_completed")
            private val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
            private val LANGUAGE_CODE = stringPreferencesKey("language_code")
        }

        override val userData: Flow<UserData> =
            dataStore.data.map { preferences ->
                UserData(
                    theme = preferences[THEME].safeEnum(AppTheme.SYSTEM),
                    sortOrder = preferences[SORT_ORDER].safeEnum(BookSortOrder.BY_ACTIVITY),
                    viewMode = preferences[VIEW_MODE].safeEnum(LibraryViewMode.LIST_COMPACT),
                    autoPlayNext = preferences[AUTO_PLAY_NEXT] ?: true,
                    playbackSpeed = preferences[PLAYBACK_SPEED] ?: 1.0f,
                    pitchCorrectionEnabled = preferences[PITCH_CORRECTION_ENABLED] ?: true,
                    font = preferences[FONT].safeEnum(AppFont.DEFAULT),
                    normalizeChapterTitles = preferences[NORMALIZE_CHAPTER_TITLES] ?: false,
                    onboardingCompleted = preferences[ONBOARDING_COMPLETED] ?: false,
                    storageFallbackEnabled = preferences[STORAGE_FALLBACK_ENABLED] ?: false,
                    spotlightCompleted = preferences[SPOTLIGHT_COMPLETED] ?: false,
                    hapticsEnabled = preferences[HAPTICS_ENABLED] ?: true,
                    languageCode = preferences[LANGUAGE_CODE] ?: "ru",
                )
            }

        override suspend fun setTheme(theme: AppTheme) {
            try {
                dataStore.edit { preferences ->
                    preferences[THEME] = theme.name
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                LogUtils.e("DataStorePrefs", "Failed to update preference", e)
            }
        }

        override suspend fun setSortOrder(sortOrder: BookSortOrder) {
            try {
                dataStore.edit { preferences ->
                    preferences[SORT_ORDER] = sortOrder.name
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                LogUtils.e("DataStorePrefs", "Failed to update preference", e)
            }
        }

        override suspend fun setViewMode(viewMode: LibraryViewMode) {
            try {
                dataStore.edit { preferences ->
                    preferences[VIEW_MODE] = viewMode.name
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                LogUtils.e("DataStorePrefs", "Failed to update preference", e)
            }
        }

        override suspend fun setAutoPlayNext(enabled: Boolean) {
            try {
                dataStore.edit { preferences ->
                    preferences[AUTO_PLAY_NEXT] = enabled
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                LogUtils.e("DataStorePrefs", "Failed to update preference", e)
            }
        }

        override suspend fun setPlaybackSpeed(speed: Float) {
            try {
                dataStore.edit { preferences ->
                    preferences[PLAYBACK_SPEED] = speed
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                LogUtils.e("DataStorePrefs", "Failed to update preference", e)
            }
        }

        override suspend fun setPitchCorrectionEnabled(enabled: Boolean) {
            try {
                dataStore.edit { preferences ->
                    preferences[PITCH_CORRECTION_ENABLED] = enabled
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                LogUtils.e("DataStorePrefs", "Failed to update preference", e)
            }
        }

        override suspend fun setFont(font: AppFont) {
            try {
                dataStore.edit { preferences ->
                    preferences[FONT] = font.name
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                LogUtils.e("DataStorePrefs", "Failed to update preference", e)
            }
        }

        override suspend fun setNormalizeChapterTitles(enabled: Boolean) {
            try {
                dataStore.edit { preferences ->
                    preferences[NORMALIZE_CHAPTER_TITLES] = enabled
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                LogUtils.e("DataStorePrefs", "Failed to update preference", e)
            }
        }

        override suspend fun setOnboardingCompleted(completed: Boolean) {
            try {
                dataStore.edit { preferences ->
                    preferences[ONBOARDING_COMPLETED] = completed
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                LogUtils.e("DataStorePrefs", "Failed to update preference", e)
            }
        }

        override suspend fun setStorageFallbackEnabled(enabled: Boolean) {
            try {
                dataStore.edit { preferences ->
                    preferences[STORAGE_FALLBACK_ENABLED] = enabled
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                LogUtils.e("DataStorePrefs", "Failed to update preference", e)
            }
        }

        override suspend fun setSpotlightCompleted(completed: Boolean) {
            try {
                dataStore.edit { preferences ->
                    preferences[SPOTLIGHT_COMPLETED] = completed
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                LogUtils.e("DataStorePrefs", "Failed to update preference", e)
            }
        }

        override suspend fun setHapticsEnabled(enabled: Boolean) {
            try {
                dataStore.edit { preferences ->
                    preferences[HAPTICS_ENABLED] = enabled
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                LogUtils.e("DataStorePrefs", "Failed to update preference", e)
            }
        }

        override suspend fun setLanguage(languageCode: String) {
            try {
                dataStore.edit { preferences ->
                    preferences[LANGUAGE_CODE] = languageCode
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                LogUtils.e("DataStorePrefs", "Failed to update preference", e)
            }
        }
    }
