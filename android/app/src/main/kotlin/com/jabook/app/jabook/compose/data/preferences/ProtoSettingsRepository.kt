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

import androidx.datastore.core.DataStore
import com.jabook.app.jabook.compose.core.theme.getAllAccentSwatches
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Contract for application settings backed by Proto DataStore. */
public interface SettingsRepository {
    public val userPreferences: Flow<UserPreferences>
    public val playerStateSnapshot: Flow<PlayerStateSnapshotPreference?>
    public val sleepTimerState: Flow<SleepTimerState>

    public suspend fun updateThemeMode(themeMode: ThemeMode)

    public suspend fun updateDynamicColors(enabled: Boolean)

    public suspend fun updateAccentSwatchIndex(index: Int)

    public suspend fun updatePlayerCoverMode(mode: Int)

    public suspend fun updateAudioSettings(
        rewindSeconds: Int? = null,
        forwardSeconds: Int? = null,
        resumeRewindSeconds: Int? = null,
        resumeRewindMode: ResumeRewindMode? = null,
        resumeRewindAggressiveness: Float? = null,
        sleepTimerShakeExtendEnabled: Boolean? = null,
        holdToBoostSpeed: Float? = null,
        autoPipEnabled: Boolean? = null,
        headsetAutoplayEnabled: Boolean? = null,
        volumeBoost: String? = null,
        drcLevel: String? = null,
        speechCompressorLevel: String? = null,
        speechEnhancer: Boolean? = null,
        autoVolumeLeveling: Boolean? = null,
        normalizeVolume: Boolean? = null,
        skipSilence: Boolean? = null,
        skipSilenceThresholdDb: Float? = null,
        skipSilenceMinMs: Int? = null,
        skipSilenceMode: SkipSilenceMode? = null,
        crossfadeEnabled: Boolean? = null,
        crossfadeDurationMs: Long? = null,
        noiseGateLevel: String? = null,
        singleClickAction: Int? = null,
        doubleClickAction: Int? = null,
        tripleClickAction: Int? = null,
        longPressAction: Int? = null,
        notificationActionSlots: List<Int>? = null,
    )

    public suspend fun updateNotificationSettings(
        notificationsEnabled: Boolean?,
        downloadNotifications: Boolean?,
        playerNotifications: Boolean?,
    )

    /**
     * Bulk restore for backup import — one proto rewrite instead of N.
     * Mirrors are replaced (not appended) with [customMirrors].
     */
    public suspend fun applyBackupSettings(
        wifiOnly: Boolean,
        autoLoadCoversOnCellular: Boolean,
        downloadPath: String,
        selectedMirror: String,
        autoSwitchMirror: Boolean,
        limitDownloadSpeed: Boolean,
        maxDownloadSpeedKb: Int,
        maxConcurrentDownloads: Int,
        rewindSeconds: Int,
        forwardSeconds: Int,
        dynamicColors: Boolean,
        notificationsEnabled: Boolean,
        downloadNotifications: Boolean,
        playerNotifications: Boolean,
        customMirrors: List<String>,
    )

    public suspend fun updateSelectedMirror(domain: String)

    public suspend fun addCustomMirror(domain: String)

    public suspend fun removeCustomMirror(domain: String)

    public suspend fun updateAutoSwitchMirror(enabled: Boolean)

    public suspend fun updateDownloadPath(path: String)

    public suspend fun updateWifiOnly(enabled: Boolean)

    public suspend fun updateLimitDownloadSpeed(enabled: Boolean)

    public suspend fun updateMaxDownloadSpeed(speedKb: Int)

    public suspend fun updateMaxConcurrentDownloads(count: Int)

    public suspend fun updateAutoLoadCoversOnCellular(enabled: Boolean)

    public suspend fun updateLibrarySortOrder(sortOrder: String)

    public suspend fun updateSpotlightCompleted(completed: Boolean)

    public suspend fun updateEqualizerPreset(preset: String)

    public val bassBoostStrength: Flow<Int>

    public suspend fun updateBassBoostStrength(strength: Int)

    public val audioVisualizerMode: Flow<Int>

    public suspend fun updateAudioVisualizerMode(mode: Int)

    public val customEqBands: Flow<List<Int>>

    public suspend fun updateCustomEqBands(bands: List<Int>)

    public suspend fun updatePlayerStateSnapshot(snapshot: PlayerStateSnapshotPreference)

    public suspend fun clearPlayerStateSnapshot()

    public suspend fun updateSleepTimerState(state: SleepTimerState)

    public suspend fun clearSleepTimerState()

    public suspend fun resetToDefaults()
}

/**
 * Implementation of SettingsRepository using Proto DataStore.
 *
 * The [DataStore] instance is provided by DI (see DataModule) so that it is shared
 * with other consumers of the same "user_preferences.pb" file.
 */
@Singleton
public class ProtoSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<UserPreferences>,
    ) : SettingsRepository {
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

        override suspend fun updateAudioSettings(
            rewindSeconds: Int?,
            forwardSeconds: Int?,
            resumeRewindSeconds: Int?,
            resumeRewindMode: ResumeRewindMode?,
            resumeRewindAggressiveness: Float?,
            sleepTimerShakeExtendEnabled: Boolean?,
            holdToBoostSpeed: Float?,
            autoPipEnabled: Boolean?,
            headsetAutoplayEnabled: Boolean?,
            volumeBoost: String?,
            drcLevel: String?,
            speechCompressorLevel: String?,
            speechEnhancer: Boolean?,
            autoVolumeLeveling: Boolean?,
            normalizeVolume: Boolean?,
            skipSilence: Boolean?,
            skipSilenceThresholdDb: Float?,
            skipSilenceMinMs: Int?,
            skipSilenceMode: SkipSilenceMode?,
            crossfadeEnabled: Boolean?,
            crossfadeDurationMs: Long?,
            noiseGateLevel: String?,
            singleClickAction: Int?,
            doubleClickAction: Int?,
            tripleClickAction: Int?,
            longPressAction: Int?,
            notificationActionSlots: List<Int>?,
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
                headsetAutoplayEnabled?.let { builder.setHeadsetAutoplayEnabled(it) }
                volumeBoost?.let { builder.setVolumeBoostLevel(it) }
                drcLevel?.let { builder.setDrcLevel(it) }
                speechCompressorLevel?.let { builder.setSpeechCompressorLevel(it) }
                speechEnhancer?.let { builder.setSpeechEnhancer(it) }
                autoVolumeLeveling?.let { builder.setAutoVolumeLeveling(it) }
                normalizeVolume?.let { builder.setNormalizeVolume(it) }
                skipSilence?.let { builder.setSkipSilence(it) }
                skipSilenceThresholdDb?.let { builder.setSkipSilenceThresholdDb(it) }
                skipSilenceMinMs?.let { builder.setSkipSilenceMinMs(it) }
                skipSilenceMode?.let { builder.setSkipSilenceMode(it) }
                crossfadeEnabled?.let { builder.setCrossfadeEnabled(it) }
                crossfadeDurationMs?.let { builder.setCrossfadeDurationMs(it) }
                noiseGateLevel?.let { builder.setNoiseGateLevel(it) }
                singleClickAction?.let { builder.setSingleClickAction(it) }
                doubleClickAction?.let { builder.setDoubleClickAction(it) }
                tripleClickAction?.let { builder.setTripleClickAction(it) }
                longPressAction?.let { builder.setLongPressAction(it) }
                notificationActionSlots?.let { slots ->
                    builder.clearNotificationActionSlots()
                    builder.addAllNotificationActionSlots(slots)
                }
                builder.build()
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

        override suspend fun applyBackupSettings(
            wifiOnly: Boolean,
            autoLoadCoversOnCellular: Boolean,
            downloadPath: String,
            selectedMirror: String,
            autoSwitchMirror: Boolean,
            limitDownloadSpeed: Boolean,
            maxDownloadSpeedKb: Int,
            maxConcurrentDownloads: Int,
            rewindSeconds: Int,
            forwardSeconds: Int,
            dynamicColors: Boolean,
            notificationsEnabled: Boolean,
            downloadNotifications: Boolean,
            playerNotifications: Boolean,
            customMirrors: List<String>,
        ) {
            // Single rewrite: each individual setter would fsync the whole proto.
            dataStore.updateData { preferences ->
                preferences
                    .toBuilder()
                    .setWifiOnlyDownload(wifiOnly)
                    .setAutoLoadCoversOnCellular(autoLoadCoversOnCellular)
                    .setDownloadPath(downloadPath)
                    .setSelectedMirror(selectedMirror)
                    .setAutoSwitchMirror(autoSwitchMirror)
                    .setLimitDownloadSpeed(limitDownloadSpeed)
                    .setMaxDownloadSpeedKb(maxDownloadSpeedKb)
                    .setMaxConcurrentDownloads(maxConcurrentDownloads)
                    .setRewindDurationSeconds(rewindSeconds)
                    .setForwardDurationSeconds(forwardSeconds)
                    .setUseDynamicColors(dynamicColors)
                    .setNotificationsEnabled(notificationsEnabled)
                    .setDownloadNotifications(downloadNotifications)
                    .setPlayerNotifications(playerNotifications)
                    .clearCustomMirrors()
                    .addAllCustomMirrors(customMirrors)
                    .build()
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

        override val bassBoostStrength: Flow<Int> =
            userPreferences.map { it.bassBoostStrength }

        override suspend fun updateBassBoostStrength(strength: Int) {
            val safeStrength = strength.coerceIn(0, 100)
            dataStore.updateData { preferences ->
                preferences.toBuilder().setBassBoostStrength(safeStrength).build()
            }
        }

        override val audioVisualizerMode: Flow<Int> =
            userPreferences.map { it.audioVisualizerMode }

        override suspend fun updateAudioVisualizerMode(mode: Int) {
            dataStore.updateData { preferences ->
                preferences.toBuilder().setAudioVisualizerMode(mode).build()
            }
        }

        override suspend fun updateEqualizerPreset(preset: String) {
            dataStore.updateData { preferences ->
                preferences.toBuilder().setEqualizerPreset(preset).build()
            }
        }

        override val customEqBands: Flow<List<Int>> =
            userPreferences.map { it.customEqBandsList }

        override suspend fun updateCustomEqBands(bands: List<Int>) {
            dataStore.updateData { preferences ->
                preferences
                    .toBuilder()
                    .clearCustomEqBands()
                    .addAllCustomEqBands(bands)
                    .build()
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
                // Keep reset behavior consistent with a fresh installation and corruption recovery.
                UserPreferencesSerializer.defaultValue
            }
        }
    }
