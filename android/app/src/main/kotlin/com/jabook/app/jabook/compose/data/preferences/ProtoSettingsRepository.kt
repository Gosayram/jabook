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
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.jabook.app.jabook.core.datastore.DataStoreCorruptionPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private fun createUserPreferencesDataStore(context: Context): DataStore<UserPreferences> =
    DataStoreFactory.create(
        serializer = UserPreferencesSerializer,
        corruptionHandler =
            DataStoreCorruptionPolicy.protoHandler(
                storeName = "user_preferences",
                defaultValue = UserPreferencesSerializer.defaultValue,
            ),
        migrations = listOf(UserPreferencesDataMigration()),
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { context.dataStoreFile("user_preferences.pb") },
    )

/**
 * Repository for managing user settings/preferences.
 *
 * Uses Proto DataStore for type-safe, structured preferences storage.
 */
public interface SettingsRepository {
    /**
     * Get user preferences as Flow.
     */
    public val userPreferences: Flow<UserPreferences>

    /**
     * Update theme mode.
     */
    public suspend fun updateThemeMode(themeMode: ThemeMode)

    /**
     * Update dynamic colors setting.
     */
    public suspend fun updateDynamicColors(enabled: Boolean)

    /**
     * Update playback speed.
     */
    public suspend fun updatePlaybackSpeed(speed: Float)

    /**
     * Update audio settings.
     */
    public suspend fun updateAudioSettings(
        rewindSeconds: Int? = null,
        forwardSeconds: Int? = null,
        resumeRewindSeconds: Int? = null,
        sleepTimerShakeExtendEnabled: Boolean? = null,
        volumeBoost: String? = null,
        drcLevel: String? = null,
        speechEnhancer: Boolean? = null,
        autoVolumeLeveling: Boolean? = null,
        normalizeVolume: Boolean? = null,
        skipSilence: Boolean? = null,
        skipSilenceThresholdDb: Float? = null,
        skipSilenceMinMs: Int? = null,
        skipSilenceMode: SkipSilenceMode? = null,
        crossfadeEnabled: Boolean? = null,
        crossfadeDurationMs: Long? = null,
    )

    /**
     * Update language.
     */
    public suspend fun updateLanguage(languageCode: String)

    /**
     * Update notification settings.
     */
    public suspend fun updateNotificationSettings(
        notificationsEnabled: Boolean? = null,
        downloadNotifications: Boolean? = null,
        playerNotifications: Boolean? = null,
    )

    /**
     * Update selected mirror domain.
     */
    public suspend fun updateSelectedMirror(domain: String)

    /**
     * Add a custom mirror domain.
     */
    public suspend fun addCustomMirror(domain: String)

    /**
     * Remove a custom mirror domain.
     */
    public suspend fun removeCustomMirror(domain: String)

    /**
     * Update auto-switch mirror setting.
     */
    public suspend fun updateAutoSwitchMirror(enabled: Boolean)

    /**
     * Update download path.
     */
    public suspend fun updateDownloadPath(path: String)

    /**
     * Update Wi-Fi only download setting.
     */
    public suspend fun updateWifiOnly(enabled: Boolean)

    /**
     * Update download speed limiting.
     */
    public suspend fun updateLimitDownloadSpeed(enabled: Boolean)

    /**
     * Update max download speed in KB/s.
     */
    public suspend fun updateMaxDownloadSpeed(speedKb: Int)

    /**
     * Update max concurrent downloads.
     */
    public suspend fun updateMaxConcurrentDownloads(count: Int)

    /**
     * Update cover loading behavior on cellular network.
     */
    public suspend fun updateAutoLoadCoversOnCellular(enabled: Boolean)

    /**
     * Update library sort order.
     */
    public suspend fun updateLibrarySortOrder(sortOrder: String)

    /**
     * Update equalizer preset.
     */
    public suspend fun updateEqualizerPreset(preset: String)

    /**
     * Update onboarding completion status.
     */
    public suspend fun updateOnboardingCompleted(completed: Boolean)

    /**
     * Reset all settings to defaults.
     */
    public suspend fun resetToDefaults()
}

/**
 * Implementation of SettingsRepository using Proto DataStore.
 */
@Singleton
public class ProtoSettingsRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : SettingsRepository {
        private val dataStore: DataStore<UserPreferences> by lazy { createUserPreferencesDataStore(context) }

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
            resumeRewindSeconds: Int?,
            sleepTimerShakeExtendEnabled: Boolean?,
            volumeBoost: String?,
            drcLevel: String?,
            speechEnhancer: Boolean?,
            autoVolumeLeveling: Boolean?,
            normalizeVolume: Boolean?,
            skipSilence: Boolean?,
            skipSilenceThresholdDb: Float?,
            skipSilenceMinMs: Int?,
            skipSilenceMode: SkipSilenceMode?,
            crossfadeEnabled: Boolean?,
            crossfadeDurationMs: Long?,
        ) {
            dataStore.updateData { preferences ->
                val builder = preferences.toBuilder()
                rewindSeconds?.let { builder.setRewindDurationSeconds(it) }
                forwardSeconds?.let { builder.setForwardDurationSeconds(it) }
                resumeRewindSeconds?.let { builder.setResumeRewindSeconds(it) }
                sleepTimerShakeExtendEnabled?.let { builder.setSleepTimerShakeExtendEnabled(it) }
                volumeBoost?.let { builder.setVolumeBoostLevel(it) }
                drcLevel?.let { builder.setDrcLevel(it) }
                speechEnhancer?.let { builder.setSpeechEnhancer(it) }
                autoVolumeLeveling?.let { builder.setAutoVolumeLeveling(it) }
                normalizeVolume?.let { builder.setNormalizeVolume(it) }
                skipSilence?.let { builder.setSkipSilence(it) }
                skipSilenceThresholdDb?.let { builder.setSkipSilenceThresholdDb(it) }
                skipSilenceMinMs?.let { builder.setSkipSilenceMinMs(it) }
                skipSilenceMode?.let { builder.setSkipSilenceMode(it) }
                crossfadeEnabled?.let { builder.setCrossfadeEnabled(it) }
                crossfadeDurationMs?.let { builder.setCrossfadeDurationMs(it) }
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

        override suspend fun updateDownloadPath(path: String) {
            dataStore.updateData { preferences ->
                preferences.toBuilder().setDownloadPath(path).build()
            }
        }

        override suspend fun updateWifiOnly(enabled: Boolean) {
            dataStore.updateData { preferences ->
                preferences.toBuilder().setWifiOnlyDownload(enabled).build()
            }
        }

        override suspend fun updateLimitDownloadSpeed(enabled: Boolean) {
            dataStore.updateData { preferences ->
                preferences.toBuilder().setLimitDownloadSpeed(enabled).build()
            }
        }

        override suspend fun updateMaxDownloadSpeed(speedKb: Int) {
            dataStore.updateData { preferences ->
                preferences.toBuilder().setMaxDownloadSpeedKb(speedKb).build()
            }
        }

        override suspend fun updateMaxConcurrentDownloads(count: Int) {
            dataStore.updateData { preferences ->
                preferences.toBuilder().setMaxConcurrentDownloads(count).build()
            }
        }

        override suspend fun updateAutoLoadCoversOnCellular(enabled: Boolean) {
            dataStore.updateData { preferences ->
                preferences.toBuilder().setAutoLoadCoversOnCellular(enabled).build()
            }
        }

        override suspend fun updateLibrarySortOrder(sortOrder: String) {
            dataStore.updateData { preferences ->
                preferences.toBuilder().setLibrarySortOrder(sortOrder).build()
            }
        }

        override suspend fun updateEqualizerPreset(preset: String) {
            dataStore.updateData { preferences ->
                preferences.toBuilder().setEqualizerPreset(preset).build()
            }
        }

        override suspend fun updateOnboardingCompleted(completed: Boolean) {
            dataStore.updateData { preferences ->
                preferences.toBuilder().setOnboardingCompleted(completed).build()
            }
        }

        override suspend fun resetToDefaults() {
            dataStore.updateData {
                UserPreferences.getDefaultInstance()
            }
        }
    }
