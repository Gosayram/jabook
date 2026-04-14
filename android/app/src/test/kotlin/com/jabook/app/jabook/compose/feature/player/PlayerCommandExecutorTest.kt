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
import com.jabook.app.jabook.compose.feature.player.testdoubles.FakePlayerCommandCallbacks
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerCommandExecutorTest {
    @Test
    fun `execute routes playback commands to expected callbacks`() {
        val fakeCallbacks = FakePlayerCommandCallbacks()
        val executor = fakeCallbacks.buildExecutor()

        executor.execute(PlayerCommand.Play)
        executor.execute(PlayerCommand.Pause)
        executor.execute(PlayerCommand.SeekTo(42_000L))

        assertEquals(1, fakeCallbacks.playCalls)
        assertEquals(1, fakeCallbacks.pauseCalls)
        assertEquals(42_000L, fakeCallbacks.lastSeekPositionMs)
    }

    @Test
    fun `execute routes settings commands with payload`() {
        val fakeCallbacks = FakePlayerCommandCallbacks()
        val executor = fakeCallbacks.buildExecutor()

        executor.execute(PlayerCommand.UpdateBookSeekSettings(rewindSeconds = 15, forwardSeconds = 45))
        executor.execute(
            PlayerCommand.UpdateAudioSettings(
                volumeBoostLevel = VolumeBoostLevel.Boost200,
                skipSilence = true,
                skipSilenceMode = SkipSilenceMode.SPEED_UP,
            ),
        )

        assertEquals(15, fakeCallbacks.lastRewindSeconds)
        assertEquals(45, fakeCallbacks.lastForwardSeconds)
        assertEquals(VolumeBoostLevel.Boost200, fakeCallbacks.lastVolumeBoostLevel)
        assertEquals(true, fakeCallbacks.lastSkipSilence)
        assertEquals(SkipSilenceMode.SPEED_UP, fakeCallbacks.lastSkipSilenceMode)
    }
}
