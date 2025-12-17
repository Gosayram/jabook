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
class MediaSessionSettingsSync(
    private val settingsRepository: com.jabook.app.jabook.compose.data.preferences.ProtoSettingsRepository,
    private val service: AudioPlayerService,
    private val scope: CoroutineScope,
) {
    /**
     * Starts observing settings and syncing to MediaSession.
     * Should be called once in AudioPlayerService.onCreate().
     */
    fun start() {
        scope.launch {
            settingsRepository.userPreferences
                .map { prefs ->
                    // Extract intervals with safe defaults
                    val rewindSeconds =
                        if (prefs.rewindDurationSeconds > 0) {
                            prefs.rewindDurationSeconds.toInt()
                        } else {
                            10 // Default 10s
                        }
                    val forwardSeconds =
                        if (prefs.forwardDurationSeconds > 0) {
                            prefs.forwardDurationSeconds.toInt()
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

                    // TODO: Update MediaSession custom commands when implemented
                    // service.updateMediaSessionCommands(rewindSeconds, forwardSeconds)
                }
        }
    }
}
