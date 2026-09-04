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
import com.jabook.app.jabook.compose.core.util.rethrowCancellation
import com.jabook.app.jabook.compose.core.util.safeEnum
import com.jabook.app.jabook.compose.data.model.AppFont
import com.jabook.app.jabook.compose.data.model.AppTheme
import com.jabook.app.jabook.compose.data.model.BookSortOrder
import com.jabook.app.jabook.compose.data.model.LibraryViewMode
import com.jabook.app.jabook.compose.data.model.UserData
import com.jabook.app.jabook.compose.data.preferences.ThemeMode
import com.jabook.app.jabook.compose.data.preferences.UserPreferences
import com.jabook.app.jabook.compose.data.preferences.UserPreferencesSerializer
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [UserPreferencesRepository] backed exclusively by the Proto DataStore
 * ("user_preferences.pb") — replaces the legacy Preferences DataStore implementation.
 *
 * Legacy values are carried over once by
 * [com.jabook.app.jabook.compose.data.preferences.LegacyPreferencesDataMigration],
 * so this class never touches "jabook_preferences".
 */
@Singleton
public class ProtoBackedUserPreferencesRepository
    @Inject
    constructor(
        private val dataStore: DataStore<UserPreferences>,
    ) : UserPreferencesRepository {
        override val userData: Flow<UserData> =
            dataStore.data
                .catch { exception ->
                    if (exception is IOException) {
                        emit(UserPreferencesSerializer.defaultValue)
                    } else {
                        throw exception
                    }
                }.map { preferences ->
                    UserData(
                        theme = AppTheme.entries.firstOrNull { it.name == preferences.themeMode.name } ?: AppTheme.SYSTEM,
                        sortOrder = preferences.librarySortOrder.safeEnum(BookSortOrder.BY_ACTIVITY),
                        viewMode = preferences.viewMode.safeEnum(LibraryViewMode.LIST_COMPACT),
                        autoPlayNext = preferences.autoPlayNext,
                        playbackSpeed = if (preferences.playbackSpeed > 0f) preferences.playbackSpeed else 1.0f,
                        pitchCorrectionEnabled = preferences.pitchCorrectionEnabled,
                        font = preferences.font.safeEnum(AppFont.DEFAULT),
                        normalizeChapterTitles = preferences.normalizeChapterTitles,
                        onboardingCompleted = preferences.onboardingCompleted,
                        storageFallbackEnabled = preferences.storageFallbackEnabled,
                        spotlightCompleted = preferences.spotlightCompleted,
                        hapticsEnabled = preferences.hapticsEnabled,
                        languageCode = preferences.languageCode.ifBlank { "ru" },
                    )
                }

        override suspend fun setTheme(theme: AppTheme) {
            update { runCatching { themeMode = ThemeMode.valueOf(theme.name) } }
        }

        override suspend fun setSortOrder(sortOrder: BookSortOrder) {
            update { librarySortOrder = sortOrder.name }
        }

        override suspend fun setViewMode(viewMode: LibraryViewMode) {
            update { this.viewMode = viewMode.name }
        }

        override suspend fun setAutoPlayNext(enabled: Boolean) {
            update { autoPlayNext = enabled }
        }

        override suspend fun setPlaybackSpeed(speed: Float) {
            update { playbackSpeed = speed }
        }

        override suspend fun setPitchCorrectionEnabled(enabled: Boolean) {
            update { pitchCorrectionEnabled = enabled }
        }

        override suspend fun setFont(font: AppFont) {
            update { this.font = font.name }
        }

        override suspend fun setNormalizeChapterTitles(enabled: Boolean) {
            update { normalizeChapterTitles = enabled }
        }

        override suspend fun setOnboardingCompleted(completed: Boolean): Boolean = update { onboardingCompleted = completed }

        override suspend fun setStorageFallbackEnabled(enabled: Boolean) {
            update { storageFallbackEnabled = enabled }
        }

        override suspend fun setSpotlightCompleted(completed: Boolean) {
            update { spotlightCompleted = completed }
        }

        override suspend fun setHapticsEnabled(enabled: Boolean) {
            update { hapticsEnabled = enabled }
        }

        override suspend fun setLanguage(languageCode: String) {
            update { this.languageCode = languageCode }
        }

        private suspend fun update(transform: UserPreferences.Builder.() -> Unit): Boolean =
            try {
                dataStore.updateData { preferences -> preferences.toBuilder().apply(transform).build() }
                true
            } catch (e: Exception) {
                e.rethrowCancellation()
                LogUtils.e("ProtoPrefs", "Failed to update preference", e)
                false
            }
    }
