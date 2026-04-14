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

package com.jabook.app.jabook.compose.feature.player.testdoubles

import com.jabook.app.jabook.audio.processors.VolumeBoostLevel
import com.jabook.app.jabook.compose.data.preferences.SkipSilenceMode
import com.jabook.app.jabook.compose.feature.player.PlayerCommandExecutor

/**
 * Fake callbacks receiver for [PlayerCommandExecutor] tests.
 *
 * Uses deterministic in-memory counters/fields and has no Android dependencies.
 */
internal class FakePlayerCommandCallbacks {
    var initializePlayerCalls: Int = 0
    var playCalls: Int = 0
    var pauseCalls: Int = 0
    var skipToNextCalls: Int = 0
    var skipToPreviousCalls: Int = 0
    var lastSeekPositionMs: Long = -1L
    var lastSkipToChapterIndex: Int = -1
    var initializeVisualizerCalls: Int = 0
    var lastVisualizerEnabled: Boolean? = null
    var lastPlaybackSpeed: Float? = null
    var lastPitchCorrectionEnabled: Boolean? = null
    var lastSleepTimerMinutes: Int? = null
    var startSleepTimerEndOfChapterCalls: Int = 0
    var startSleepTimerEndOfTrackCalls: Int = 0
    var cancelSleepTimerCalls: Int = 0
    var lastRewindSeconds: Int? = null
    var lastForwardSeconds: Int? = null
    var resetBookSeekSettingsCalls: Int = 0
    var lastVolumeBoostLevel: VolumeBoostLevel? = null
    var lastSkipSilence: Boolean? = null
    var lastSkipSilenceThresholdDb: Float? = null
    var lastSkipSilenceMinMs: Int? = null
    var lastSkipSilenceMode: SkipSilenceMode? = null
    var lastNormalizeVolume: Boolean? = null
    var lastSpeechEnhancer: Boolean? = null
    var lastAutoVolumeLeveling: Boolean? = null

    fun buildExecutor(): PlayerCommandExecutor =
        PlayerCommandExecutor(
            initializePlayer = { initializePlayerCalls++ },
            play = { playCalls++ },
            pause = { pauseCalls++ },
            skipToNext = { skipToNextCalls++ },
            skipToPrevious = { skipToPreviousCalls++ },
            seekTo = { lastSeekPositionMs = it },
            skipToChapter = { lastSkipToChapterIndex = it },
            initializeVisualizer = { initializeVisualizerCalls++ },
            setVisualizerEnabled = { lastVisualizerEnabled = it },
            setPlaybackSpeed = { lastPlaybackSpeed = it },
            setPitchCorrectionEnabled = { lastPitchCorrectionEnabled = it },
            startSleepTimer = { lastSleepTimerMinutes = it },
            startSleepTimerEndOfChapter = { startSleepTimerEndOfChapterCalls++ },
            startSleepTimerEndOfTrack = { startSleepTimerEndOfTrackCalls++ },
            cancelSleepTimer = { cancelSleepTimerCalls++ },
            updateBookSeekSettings = { rewind, forward ->
                lastRewindSeconds = rewind
                lastForwardSeconds = forward
            },
            resetBookSeekSettings = { resetBookSeekSettingsCalls++ },
            updateAudioSettings = { boost, skip, threshold, minMs, mode, normalize, speech, leveling ->
                lastVolumeBoostLevel = boost
                lastSkipSilence = skip
                lastSkipSilenceThresholdDb = threshold
                lastSkipSilenceMinMs = minMs
                lastSkipSilenceMode = mode
                lastNormalizeVolume = normalize
                lastSpeechEnhancer = speech
                lastAutoVolumeLeveling = leveling
            },
        )
}
