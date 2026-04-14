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

package com.jabook.app.jabook.compose.feature.player

import com.jabook.app.jabook.audio.processors.VolumeBoostLevel
import com.jabook.app.jabook.compose.data.preferences.SkipSilenceMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerCommandExecutorTest {
    @Test
    fun `execute routes playback commands to expected callbacks`() {
        var played = 0
        var paused = 0
        var seekPosition = -1L

        val executor =
            PlayerCommandExecutor(
                initializePlayer = {},
                play = { played++ },
                pause = { paused++ },
                skipToNext = {},
                skipToPrevious = {},
                seekTo = { seekPosition = it },
                skipToChapter = {},
                initializeVisualizer = {},
                setVisualizerEnabled = {},
                setPlaybackSpeed = {},
                setPitchCorrectionEnabled = {},
                startSleepTimer = {},
                startSleepTimerEndOfChapter = {},
                startSleepTimerEndOfTrack = {},
                cancelSleepTimer = {},
                updateBookSeekSettings = { _, _ -> },
                resetBookSeekSettings = {},
                updateAudioSettings = { _, _, _, _, _, _, _, _ -> },
            )

        executor.execute(PlayerCommand.Play)
        executor.execute(PlayerCommand.Pause)
        executor.execute(PlayerCommand.SeekTo(42_000L))

        assertEquals(1, played)
        assertEquals(1, paused)
        assertEquals(42_000L, seekPosition)
    }

    @Test
    fun `execute routes settings commands with payload`() {
        var rewind: Int? = null
        var forward: Int? = null
        var volumeBoost: VolumeBoostLevel? = null
        var skipSilence: Boolean? = null
        var skipSilenceMode: SkipSilenceMode? = null

        val executor =
            PlayerCommandExecutor(
                initializePlayer = {},
                play = {},
                pause = {},
                skipToNext = {},
                skipToPrevious = {},
                seekTo = {},
                skipToChapter = {},
                initializeVisualizer = {},
                setVisualizerEnabled = {},
                setPlaybackSpeed = {},
                setPitchCorrectionEnabled = {},
                startSleepTimer = {},
                startSleepTimerEndOfChapter = {},
                startSleepTimerEndOfTrack = {},
                cancelSleepTimer = {},
                updateBookSeekSettings = { r, f ->
                    rewind = r
                    forward = f
                },
                resetBookSeekSettings = {},
                updateAudioSettings = { boost, skip, _, _, mode, _, _, _ ->
                    volumeBoost = boost
                    skipSilence = skip
                    skipSilenceMode = mode
                },
            )

        executor.execute(PlayerCommand.UpdateBookSeekSettings(rewindSeconds = 15, forwardSeconds = 45))
        executor.execute(
            PlayerCommand.UpdateAudioSettings(
                volumeBoostLevel = VolumeBoostLevel.Boost200,
                skipSilence = true,
                skipSilenceMode = SkipSilenceMode.SPEED_UP,
            ),
        )

        assertEquals(15, rewind)
        assertEquals(45, forward)
        assertEquals(VolumeBoostLevel.Boost200, volumeBoost)
        assertEquals(true, skipSilence)
        assertEquals(SkipSilenceMode.SPEED_UP, skipSilenceMode)
    }
}
