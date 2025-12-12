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

package com.jabook.app.jabook.compose.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

// Extension property for Proto DataStore
private val Context.userPreferencesDataStore: DataStore<UserPreferences> by dataStore(
    fileName = "user_preferences.pb",
    serializer = UserPreferencesSerializer,
)

/**
 * Repository for managing user settings/preferences.
 *
 * Uses Proto DataStore for type-safe, structured preferences storage.
 */
interface SettingsRepository {
    /**
     * Get user preferences as Flow.
     */
    val userPreferences: Flow<UserPreferences>

    /**
     * Update theme mode.
     */
    suspend fun updateThemeMode(themeMode: ThemeMode)

    /**
     * Update dynamic colors setting.
     */
    suspend fun updateDynamicColors(enabled: Boolean)

    /**
     * Update playback speed.
     */
    suspend fun updatePlaybackSpeed(speed: Float)

    /**
     * Update audio settings.
     */
    suspend fun updateAudioSettings(
        rewindSeconds: Int? = null,
        forwardSeconds: Int? = null,
        volumeBoost: String? = null,
        drcLevel: String? = null,
        speechEnhancer: Boolean? = null,
        autoVolumeLeveling: Boolean? = null,
        normalizeVolume: Boolean? = null,
    )

    /**
     * Update language.
     */
    suspend fun updateLanguage(languageCode: String)

    /**
     * Update notification settings.
     */
    suspend fun updateNotificationSettings(
        notificationsEnabled: Boolean? = null,
        downloadNotifications: Boolean? = null,
        playerNotifications: Boolean? = null,
    )

    /**
     * Update selected mirror domain.
     */
    suspend fun updateSelectedMirror(domain: String)

    /**
     * Add a custom mirror domain.
     */
    suspend fun addCustomMirror(domain: String)

    /**
     * Remove a custom mirror domain.
     */
    suspend fun removeCustomMirror(domain: String)

    /**
     * Update auto-switch mirror setting.
     */
    suspend fun updateAutoSwitchMirror(enabled: Boolean)

    /**
     * Reset all settings to defaults.
     */
    suspend fun resetToDefaults()
}

/**
 * Implementation of SettingsRepository using Proto DataStore.
 */
@Singleton
class ProtoSettingsRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : SettingsRepository {
        private val dataStore = context.userPreferencesDataStore

        override val userPreferences: Flow<UserPreferences> =
            dataStore.data
                .catch { exception ->
                    if (exception is IOException) {
                        emit(UserPreferencesSerializer.defaultValue)
                    } else {
                        throw exception
                    }
                }

        override suspend fun updateThemeMode(themeMode: ThemeMode) {
            dataStore.updateData { preferences ->
                preferences.toBuilder().setThemeMode(themeMode).build()
            }
        }

        override suspend fun updateDynamicColors(enabled: Boolean) {
            dataStore.updateData { preferences ->
                preferences.toBuilder().setUseDynamicColors(enabled).build()
            }
        }

        override suspend fun updatePlaybackSpeed(speed: Float) {
            dataStore.updateData { preferences ->
                preferences.toBuilder().setPlaybackSpeed(speed).build()
            }
        }

        override suspend fun updateAudioSettings(
            rewindSeconds: Int?,
            forwardSeconds: Int?,
            volumeBoost: String?,
            drcLevel: String?,
            speechEnhancer: Boolean?,
            autoVolumeLeveling: Boolean?,
            normalizeVolume: Boolean?,
        ) {
            dataStore.updateData { preferences ->
                val builder = preferences.toBuilder()
                rewindSeconds?.let { builder.setRewindDurationSeconds(it) }
                forwardSeconds?.let { builder.setForwardDurationSeconds(it) }
                volumeBoost?.let { builder.setVolumeBoostLevel(it) }
                drcLevel?.let { builder.setDrcLevel(it) }
                speechEnhancer?.let { builder.setSpeechEnhancer(it) }
                autoVolumeLeveling?.let { builder.setAutoVolumeLeveling(it) }
                normalizeVolume?.let { builder.setNormalizeVolume(it) }
                builder.build()
            }
        }

        override suspend fun updateLanguage(languageCode: String) {
            dataStore.updateData { preferences ->
                preferences.toBuilder().setLanguageCode(languageCode).build()
            }
        }

        override suspend fun updateNotificationSettings(
            notificationsEnabled: Boolean?,
            downloadNotifications: Boolean?,
            playerNotifications: Boolean?,
        ) {
            dataStore.updateData { preferences ->
                val builder = preferences.toBuilder()
                notificationsEnabled?.let { builder.setNotificationsEnabled(it) }
                downloadNotifications?.let { builder.setDownloadNotifications(it) }
                playerNotifications?.let { builder.setPlayerNotifications(it) }
                builder.build()
            }
        }

        override suspend fun updateSelectedMirror(domain: String) {
            dataStore.updateData { preferences ->
                preferences.toBuilder().setSelectedMirror(domain).build()
            }
        }

        override suspend fun addCustomMirror(domain: String) {
            dataStore.updateData { preferences ->
                val currentMirrors = preferences.customMirrorsList.toMutableList()
                if (!currentMirrors.contains(domain)) {
                    currentMirrors.add(domain)
                }
                preferences
                    .toBuilder()
                    .clearCustomMirrors()
                    .addAllCustomMirrors(currentMirrors)
                    .build()
            }
        }

        override suspend fun removeCustomMirror(domain: String) {
            dataStore.updateData { preferences ->
                val currentMirrors = preferences.customMirrorsList.toMutableList()
                currentMirrors.remove(domain)
                preferences
                    .toBuilder()
                    .clearCustomMirrors()
                    .addAllCustomMirrors(currentMirrors)
                    .build()
            }
        }

        override suspend fun updateAutoSwitchMirror(enabled: Boolean) {
            dataStore.updateData { preferences ->
                preferences.toBuilder().setAutoSwitchMirror(enabled).build()
            }
        }

        override suspend fun resetToDefaults() {
            dataStore.updateData {
                UserPreferencesSerializer.defaultValue
            }
        }
    }
