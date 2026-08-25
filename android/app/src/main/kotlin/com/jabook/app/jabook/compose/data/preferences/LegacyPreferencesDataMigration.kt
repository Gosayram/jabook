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

package com.jabook.app.jabook.compose.data.preferences

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.jabook.app.jabook.util.LogUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

/**
 * One-time migration from the legacy Preferences DataStore ("jabook_preferences")
 * into the Proto DataStore ("user_preferences.pb").
 *
 * Runs before any read of [UserPreferences] (guaranteed by the DataMigration contract),
 * copies every known legacy key into the proto builder, then deletes the legacy file so
 * the migration never runs twice and no second live preference store remains.
 *
 * Absence of the legacy file is a graceful no-op (fresh installs).
 */
public class LegacyPreferencesDataMigration
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : DataMigration<UserPreferences> {
        public companion object {
            public const val LEGACY_STORE_NAME: String = "jabook_preferences"

            // Keys mirror DataStoreUserPreferencesRepository (removed in favor of this store).
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

        private var legacyStore: DataStore<Preferences>? = null
        private var legacyScope: CoroutineScope? = null

        private fun legacyFile(): File = context.preferencesDataStoreFile(LEGACY_STORE_NAME)

        override suspend fun shouldMigrate(currentData: UserPreferences): Boolean = legacyFile().exists()

        override suspend fun migrate(currentData: UserPreferences): UserPreferences {
            val file = legacyFile()
            if (!file.exists()) return currentData

            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val store =
                PreferenceDataStoreFactory.create(
                    corruptionHandler =
                        androidx.datastore.core.handlers
                            .ReplaceFileCorruptionHandler { emptyPreferences() },
                    scope = scope,
                    produceFile = { file },
                )
            legacyStore = store
            legacyScope = scope

            // Corrupt/unreadable legacy file -> keep proto as-is; cleanUp removes the dead file.
            val preferences: Preferences =
                runCatching { store.data.first() }
                    .onFailure { LogUtils.e("LegacyPrefsMigration", "Failed to read legacy preferences", it) }
                    .getOrNull()
                    ?: return currentData

            return currentData
                .toBuilder()
                .apply {
                    preferences[THEME]?.let { name ->
                        runCatching { themeMode = ThemeMode.valueOf(name) }
                            .onFailure { LogUtils.e("LegacyPrefsMigration", "Unknown legacy theme value: $name", it) }
                    }
                    preferences[SORT_ORDER]?.let { librarySortOrder = it }
                    preferences[VIEW_MODE]?.let { viewMode = it }
                    // Legacy store defaulted these to true; proto3 can't distinguish an
                    // absent key from an explicit `false`, so default only when absent.
                    autoPlayNext = preferences[AUTO_PLAY_NEXT] ?: true
                    preferences[PLAYBACK_SPEED]?.takeIf { it > 0f }?.let { playbackSpeed = it }
                    pitchCorrectionEnabled = preferences[PITCH_CORRECTION_ENABLED] ?: true
                    preferences[FONT]?.let { font = it }
                    preferences[NORMALIZE_CHAPTER_TITLES]?.let { normalizeChapterTitles = it }
                    preferences[ONBOARDING_COMPLETED]?.let { onboardingCompleted = it }
                    preferences[STORAGE_FALLBACK_ENABLED]?.let { storageFallbackEnabled = it }
                    preferences[SPOTLIGHT_COMPLETED]?.let { spotlightCompleted = it }
                    preferences[HAPTICS_ENABLED]?.let { hapticsEnabled = it }
                    preferences[LANGUAGE_CODE]?.takeIf { it.isNotBlank() }?.let { languageCode = it }
                }.build()
        }

        override suspend fun cleanUp() {
            try {
                legacyScope?.cancel()
                legacyStore = null
                legacyScope = null
                legacyFile().delete()
            } catch (e: Exception) {
                if (e !is CorruptionException && e !is kotlinx.coroutines.CancellationException) {
                    LogUtils.e("LegacyPrefsMigration", "Failed to clean up legacy preferences", e)
                }
            }
        }
    }
