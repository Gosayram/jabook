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

package com.jabook.app.jabook.audio

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Synchronizes seek duration settings from DataStore to MediaSession custom commands.
 *
 * Observes user preferences (rewind/forward intervals) and updates:
 * 1. AudioPlayerService skip durations via updateSkipDurations()
 * 2. MediaSession custom commands (will be added in next step)
 *
 * This ensures system media controls (notification, Quick Settings, lock screen)
 * always reflect the current app settings.
 *
 * @param settingsRepository Source of truth for user preferences
 * @param service AudioPlayerService to update
 * @param scope Coroutine scope for collecting settings (typically playerServiceScope)
 */
public class MediaSessionSettingsSync(
    private val settingsRepository: com.jabook.app.jabook.compose.data.preferences.ProtoSettingsRepository,
    private val service: AudioPlayerService,
    private val scope: CoroutineScope,
) {
    /**
     * Starts observing settings and syncing to MediaSession.
     * Should be called once in AudioPlayerService.onCreate().
     */
    public fun start() {
        // Observe skip duration changes
        scope.launch {
            settingsRepository.userPreferences
                .map { prefs ->
                    // Extract intervals with safe defaults
                    val rewindSeconds =
                        if (prefs.rewindDurationSeconds > 0) {
                            prefs.rewindDurationSeconds
                        } else {
                            10 // Default 10s
                        }
                    val forwardSeconds =
                        if (prefs.forwardDurationSeconds > 0) {
                            prefs.forwardDurationSeconds
                        } else {
                            30 // Default 30s
                        }
                    Pair(rewindSeconds, forwardSeconds)
                }.distinctUntilChanged() // Only update when values actually change
                .collect { (rewindSeconds, forwardSeconds) ->
                    android.util.Log.d(
                        "MediaSessionSettingsSync",
                        "Syncing intervals: rewind=${rewindSeconds}s, forward=${forwardSeconds}s",
                    )

                    // Update service skip durations (existing method)
                    service.updateSkipDurations(rewindSeconds, forwardSeconds)

                    // Update MediaSession custom commands
                    service.updateMediaSessionCommands(rewindSeconds, forwardSeconds)
                }
        }

        // Observe audio processing settings changes
        scope.launch {
            settingsRepository.userPreferences
                .map { prefs ->
                    com.jabook.app.jabook.audio.processors.AudioProcessingSettings(
                        normalizeVolume = prefs.normalizeVolume,
                        volumeBoostLevel =
                            try {
                                if (prefs.volumeBoostLevel.isNotEmpty()) {
                                    com.jabook.app.jabook.audio.processors.VolumeBoostLevel
                                        .valueOf(prefs.volumeBoostLevel)
                                } else {
                                    com.jabook.app.jabook.audio.processors.VolumeBoostLevel.Off
                                }
                            } catch (e: Exception) {
                                com.jabook.app.jabook.audio.processors.VolumeBoostLevel.Off
                            },
                        drcLevel =
                            try {
                                if (prefs.drcLevel.isNotEmpty()) {
                                    com.jabook.app.jabook.audio.processors.DRCLevel
                                        .valueOf(prefs.drcLevel)
                                } else {
                                    com.jabook.app.jabook.audio.processors.DRCLevel.Off
                                }
                            } catch (e: Exception) {
                                com.jabook.app.jabook.audio.processors.DRCLevel.Off
                            },
                        speechEnhancer = prefs.speechEnhancer,
                        autoVolumeLeveling = prefs.autoVolumeLeveling,
                        skipSilence = prefs.skipSilence,
                        skipSilenceThresholdNormalized =
                            com.jabook.app.jabook.audio.processors.SkipSilenceThresholdPolicy
                                .toNormalizedAmplitude(prefs.skipSilenceThresholdDb),
                        skipSilenceMinDurationMs =
                            com.jabook.app.jabook.audio.processors.SkipSilenceThresholdPolicy
                                .sanitizeMinSilenceMs(prefs.skipSilenceMinMs),
                        skipSilenceMode =
                            when (prefs.skipSilenceMode) {
                                com.jabook.app.jabook.compose.data.preferences.SkipSilenceMode.SPEED_UP ->
                                    com.jabook.app.jabook.audio.processors.SkipSilenceMode.SPEED_UP
                                else ->
                                    com.jabook.app.jabook.audio.processors.SkipSilenceMode.SKIP
                            },
                        isCrossfadeEnabled = prefs.crossfadeEnabled,
                        crossfadeDurationMs = if (prefs.crossfadeDurationMs > 0) prefs.crossfadeDurationMs else 2000L,
                    )
                }.distinctUntilChanged()
                .collect { settings ->
                    android.util.Log.d(
                        "MediaSessionSettingsSync",
                        "Syncing audio settings: $settings",
                    )
                    service.configureExoPlayer(settings)
                }
        }
    }
}
