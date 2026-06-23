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
import com.jabook.app.jabook.compose.core.theme.getAllAccentSwatches
import com.jabook.app.jabook.core.datastore.DataStoreCorruptionPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
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
@Singleton
public class SettingsRepository(
    private val dataStore: DataStore<UserPreferences>
) {
    /**
     * Get user preferences as Flow.
     */
    public val userPreferences: Flow<UserPreferences> =
        dataStore.data.catch { emit(UserPreferencesSerializer.defaultValue) }

    /**
     * Last persisted player snapshot for process-death fallback.
     */
    public val playerStateSnapshot: Flow<PlayerStateSnapshotPreference?> =
        userPreferences.map { preferences ->
            val bookId = preferences.playerSnapshotBookId
            if (bookId.isBlank()) {
                null
            } else {
                PlayerStateSnapshotPreference(
                    bookId = bookId,
                    positionMs = preferences.playerSnapshotPositionMs.coerceAtLeast(0L),
                    chapterIndex = preferences.playerSnapshotChapterIndex.coerceAtLeast(0),
                    playbackSpeed = preferences.playerSnapshotPlaybackSpeed.coerceAtLeast(0f),
                    sleepTimerMode = preferences.playerSnapshotSleepMode,
                )
            }
        }

    /**
     * Sleep timer state from DataStore (P-12 migration from SharedPreferences).
     */
    public val sleepTimerState: Flow<SleepTimerState> =
        userPreferences.map { preferences -> preferences.sleepTimerState }

    /**
     * Update theme mode.
     */
    public suspend fun updateThemeMode(themeMode: ThemeMode) {
        dataStore.updateData { preferences ->
            preferences.toBuilder().setThemeMode(themeMode).build()
        }
    }

    /**
     * Update dynamic colors setting.
     */
    public suspend fun updateDynamicColors(enabled: Boolean) {
        dataStore.updateData { preferences ->
            preferences.toBuilder().setUseDynamicColors(enabled).build()
        }
    }

    public suspend fun updateAccentSwatchIndex(index: Int) {
        dataStore.updateData { preferences ->
            preferences.toBuilder().setAccentSwatchIndex(index).build()
        }
    }

    public suspend fun updatePlayerCoverMode(mode: Int) {
        dataStore.updateData { preferences ->
            preferences.toBuilder().setPlayerCoverMode(mode).build()
        }
    }

    /**
     * Update playback speed.
     */
    public suspend fun updatePlaybackSpeed(speed: Float) {
        dataStore.updateData { preferences ->
            preferences.toBuilder().setPlaybackSpeed(speed).build()
        }
    }

    /**
     * Update audio settings.
     */
    public suspend fun updateAudioSettings(
        rewindSeconds: Int? = null,
        forwardSeconds: Int? = null,
        resumeRewindSeconds: Int? = null,
        resumeRewindMode: ResumeRewindMode? = null,
        resumeRewindAggressiveness: Float? = null,
        sleepTimerShakeExtendEnabled: Boolean? = null,
        holdToBoostSpeed: Float? = null,
        autoPipEnabled: Boolean? = null,
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
    ) {
        val prefs = userPreferences.map { preferences ->
            val builder = preferences.toBuilder()
            if (rewindSeconds != null) builder.setRewindDurationSeconds(rewindSeconds)
            if (forwardSeconds != null) builder.setForwardDurationSeconds(forwardSeconds)
            if (resumeRewindSeconds != null) builder.setResumeRewindSeconds(resumeRewindSeconds)
            if (resumeRewindMode != null) builder.setResumeRewindMode(resumeRewindMode)
            if (resumeRewindAggressiveness != null) builder.setResumeRewindAggressiveness(resumeRewindAggressiveness)
            if (sleepTimerShakeExtendEnabled != null) builder.setSleepTimerShakeExtendEnabled(sleepTimerShakeExtendEnabled)
            if (holdToBoostSpeed != null) builder.setHoldToBoostSpeed(holdToBoostSpeed)
            if (autoPipEnabled != null) builder.setAutoPipEnabled(autoPipEnabled)
            if (volumeBoost != null) builder.setVolumeBoostLevel(volumeBoost)
            if (drcLevel != null) builder.setDrcLevel(drcLevel)
            if (speechEnhancer != null) builder.setSpeechEnhancer(speechEnhancer)
            if (autoVolumeLeveling != null) builder.setAutoVolumeLeveling(autoVolumeLeveling)
            if (normalizeVolume != null) builder.setNormalizeVolume(normalizeVolume)
            if (skipSilence != null) builder.setSkipSilence(skipSilence)
            if (skipSilenceThresholdDb != null) builder.setSkipSilenceThresholdDb(skipSilenceThresholdDb)
            if (skipSilenceMinMs != null) builder.setSkipSilenceMinMs(skipSilenceMinMs)
            if (skipSilenceMode != null) builder.setSkipSilenceMode(skipSilenceMode)
            if (crossfadeEnabled != null) builder.setCrossfadeEnabled(crossfadeEnabled)
            if (crossfadeDurationMs != null) builder.setCrossfadeDurationMs(crossfadeDurationMs)
            builder.build()
        }.first()
        
        dataStore.updateData { prefs }
    }

    /**
     * Update language.
     */
    public suspend fun updateLanguage(languageCode: String) {
        dataStore.updateData { preferences ->
            preferences.toBuilder().setLanguageCode(languageCode).build()
        }
    }

    /**
     * Update notification settings.
     */
    public suspend fun updateNotificationSettings(
        notificationsEnabled: Boolean? = null,
        downloadNotifications: Boolean? = null,
        playerNotifications: Boolean? = null,
    ) {
        val prefs = userPreferences.map { preferences ->
            val builder = preferences.toBuilder()
            if (notificationsEnabled != null) builder.setNotificationsEnabled(notificationsEnabled)
            if (downloadNotifications != null) builder.setDownloadNotifications(downloadNotifications)
            if (playerNotifications != null) builder.setPlayerNotifications(playerNotifications)
            builder.build()
        }.first()
        
        dataStore.updateData { prefs }
    }

    /**
     * Update selected mirror domain.
     */
    public suspend fun updateSelectedMirror(domain: String) {
        dataStore.updateData { preferences ->
            preferences.toBuilder().setSelectedMirror(domain).build()
        }
    }

    /**
     * Add a custom mirror domain.
     */
    public suspend fun addCustomMirror(domain: String) {
        dataStore.updateData { preferences ->
            val builder = preferences.toBuilder()
            if (!builder.customMirrorsList.contains(domain)) {
                builder.addCustomMirrors(domain)
            }
            builder.build()
        }
    }

    /**
     * Remove a custom mirror domain.
     */
    public suspend fun removeCustomMirror(domain: String) {
        dataStore.updateData { preferences ->
            val filtered = preferences.customMirrorsList.filterNot { it == domain }
            preferences
                .toBuilder()
                .clearCustomMirrors()
                .addAllCustomMirrors(filtered)
                .build()
        }
    }

    /**
     * Update auto-switch mirror setting.
     */
    public suspend fun updateAutoSwitchMirror(enabled: Boolean) {
        dataStore.updateData { preferences ->
            preferences.toBuilder().setAutoSwitchMirror(enabled).build()
        }
    }

    /**
     * Update download path.
     */
    public suspend fun updateDownloadPath(path: String) {
        dataStore.updateData { preferences ->
            preferences.toBuilder().setDownloadPath(path).build()
        }
    }

    /**
     * Update Wi-Fi only download setting.
     */
    public suspend fun updateWifiOnly(enabled: Boolean) {
        dataStore.updateData { preferences ->
            preferences.toBuilder().setWifiOnlyDownload(enabled).build()
        }
    }

    /**
     * Update download speed limiting.
     */
    public suspend fun updateLimitDownloadSpeed(enabled: Boolean) {
        dataStore.updateData { preferences ->
            preferences.toBuilder().setLimitDownloadSpeed(enabled).build()
        }
    }

    /**
     * Update max download speed in KB/s.
     */
    public suspend fun updateMaxDownloadSpeed(speedKb: Int) {
        dataStore.updateData { preferences ->
            preferences.toBuilder().setMaxDownloadSpeedKb(speedKb).build()
        }
    }

    /**
     * Update max concurrent downloads.
     */
    public suspend fun updateMaxConcurrentDownloads(count: Int) {
        dataStore.updateData { preferences ->
            preferences.toBuilder().setMaxConcurrentDownloads(count).build()
        }
    }

    /**
     * Update cover loading behavior on cellular network.
     */
    public suspend fun updateAutoLoadCoversOnCellular(enabled: Boolean) {
        dataStore.updateData { preferences ->
            preferences.toBuilder().setAutoLoadCoversOnCellular(enabled).build()
        }
    }

    /**
     * Update library sort order.
     */
    public suspend fun updateLibrarySortOrder(sortOrder: String) {
        dataStore.updateData { preferences ->
            preferences.toBuilder().setLibrarySortOrder(sortOrder).build()
        }
    }

    /**
     * Mark spotlight coachmarks as completed.
     */
    public suspend fun updateSpotlightCompleted(completed: Boolean) {
        dataStore.updateData { preferences ->
            preferences.toBuilder().setSpotlightCompleted(completed).build()
        }
    }

    /**
     * Update haptics enabled setting.
     */
    public suspend fun updateHapticsEnabled(enabled: Boolean) {
        dataStore.updateData { preferences ->
            preferences.toBuilder().setHapticsEnabled(enabled).build()
        }
    }

    /**
     * Update equalizer preset.
     */
    public suspend fun updateEqualizerPreset(preset: String) {
        dataStore.updateData { preferences ->
            preferences.toBuilder().setEqualizerPreset(preset).build()
        }
    }

    /**
     * Update onboarding completion status.
     */
    public suspend fun updateOnboardingCompleted(completed: Boolean) {
        dataStore.updateData { preferences ->
            preferences.toBuilder().setOnboardingCompleted(completed).build()
        }
    }

/**
     * Persist player state snapshot for process death restore fallback.
     */
    public suspend fun updatePlayerStateSnapshot(snapshot: PlayerStateSnapshotPreference)

    /**
     * Clear persisted player state snapshot.
     */
    public suspend fun clearPlayerStateSnapshot()

    /**
     * Update sleep timer state.
     */
    public suspend fun updateSleepTimerState(state: SleepTimerState)

    /**
     * Clear sleep timer state.
     */
    public suspend fun clearSleepTimerState()

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

        override val playerStateSnapshot: Flow<PlayerStateSnapshotPreference?> =
            userPreferences.map { preferences ->
                val bookId = preferences.playerSnapshotBookId
                if (bookId.isBlank()) {
                    null
                } else {
                    PlayerStateSnapshotPreference(
                        bookId = bookId,
                        positionMs = preferences.playerSnapshotPositionMs.coerceAtLeast(0L),
                        chapterIndex = preferences.playerSnapshotChapterIndex.coerceAtLeast(0),
                        playbackSpeed = preferences.playerSnapshotPlaybackSpeed.coerceAtLeast(0f),
                        sleepTimerMode = preferences.playerSnapshotSleepMode,
                    )
                }
            }

/**
         * Sleep timer state from DataStore (P-12 migration from SharedPreferences).
         */
        override val sleepTimerState: Flow<SleepTimerState> =
            userPreferences.map { preferences ->
                preferences.sleepTimerState
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

        override suspend fun updateAccentSwatchIndex(index: Int) {
            val safeIndex = index.coerceIn(0, getAllAccentSwatches().size - 1)
            dataStore.updateData { preferences ->
                preferences.toBuilder().setAccentSwatchIndex(safeIndex).build()
            }
        }

        override suspend fun updatePlayerCoverMode(mode: Int) {
            val safeMode = mode.coerceIn(0, 1)
            dataStore.updateData { preferences ->
                preferences.toBuilder().setPlayerCoverMode(safeMode).build()
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
            resumeRewindMode: ResumeRewindMode?,
            resumeRewindAggressiveness: Float?,
            sleepTimerShakeExtendEnabled: Boolean?,
            holdToBoostSpeed: Float?,
            autoPipEnabled: Boolean?,
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
                resumeRewindMode?.let { builder.setResumeRewindMode(it) }
                resumeRewindAggressiveness?.let { builder.setResumeRewindAggressiveness(it) }
                sleepTimerShakeExtendEnabled?.let { builder.setSleepTimerShakeExtendEnabled(it) }
                holdToBoostSpeed?.let { builder.setHoldToBoostSpeed(it) }
                autoPipEnabled?.let { builder.setAutoPipEnabled(it) }
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

        override suspend fun updateSpotlightCompleted(completed: Boolean) {
            dataStore.updateData { preferences ->
                preferences.toBuilder().setSpotlightCompleted(completed).build()
            }
        }

        override suspend fun updateHapticsEnabled(enabled: Boolean) {
            dataStore.updateData { preferences ->
                preferences.toBuilder().setHapticsEnabled(enabled).build()
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

        override suspend fun updatePlayerStateSnapshot(snapshot: PlayerStateSnapshotPreference) {
            dataStore.updateData { preferences ->
                preferences
                    .toBuilder()
                    .setPlayerSnapshotBookId(snapshot.bookId)
                    .setPlayerSnapshotPositionMs(snapshot.positionMs.coerceAtLeast(0L))
                    .setPlayerSnapshotChapterIndex(snapshot.chapterIndex.coerceAtLeast(0))
                    .setPlayerSnapshotPlaybackSpeed(snapshot.playbackSpeed.coerceAtLeast(0f))
                    .setPlayerSnapshotSleepMode(snapshot.sleepTimerMode)
                    .build()
            }
        }

        override suspend fun clearPlayerStateSnapshot() {
            dataStore.updateData { preferences ->
                preferences
                    .toBuilder()
                    .clearPlayerSnapshotBookId()
                    .setPlayerSnapshotPositionMs(0L)
                    .setPlayerSnapshotChapterIndex(0)
                    .setPlayerSnapshotPlaybackSpeed(1.0f)
                    .clearPlayerSnapshotSleepMode()
                    .build()
            }
        }

        override suspend fun updateSleepTimerState(state: SleepTimerState) {
            dataStore.updateData { preferences ->
                preferences
                    .toBuilder()
                    .setSleepTimerState(state)
                    .build()
            }
        }

        override suspend fun clearSleepTimerState() {
            dataStore.updateData { preferences ->
                preferences
                    .toBuilder()
                    .clearSleepTimerState()
                    .build()
            }
        }

        override suspend fun resetToDefaults() {
            dataStore.updateData {
                UserPreferences.getDefaultInstance()
            }
        }
    }
