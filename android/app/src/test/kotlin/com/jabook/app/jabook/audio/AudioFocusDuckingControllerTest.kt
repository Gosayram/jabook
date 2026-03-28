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

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.Mockito.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AudioFocusDuckingControllerTest {
    @Test
    fun `duck applies target volume when suppression is transient audio focus loss`() =
        runTest {
            val (player, currentVolume) = createPlayer(initialVolume = 1.0f)
            val controller =
                AudioFocusDuckingController(
                    getActivePlayer = { player },
                    scope = TestScope(StandardTestDispatcher(testScheduler)),
                )

            controller.onPlaybackSuppressionReasonChanged(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS)

            assertEquals(0.2f, currentVolume(), 0.0001f)
        }

    @Test
    fun `restore animates volume back to pre-duck value on focus gain`() =
        runTest {
            val (player, currentVolume) = createPlayer(initialVolume = 1.0f)
            val controller =
                AudioFocusDuckingController(
                    getActivePlayer = { player },
                    scope = TestScope(StandardTestDispatcher(testScheduler)),
                )

            controller.onPlaybackSuppressionReasonChanged(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS)
            controller.onPlaybackSuppressionReasonChanged(Player.PLAYBACK_SUPPRESSION_REASON_NONE)
            advanceTimeBy(600L)

            assertEquals(1.0f, currentVolume(), 0.0001f)
        }

    @Test
    fun `duck does not increase volume when current volume is already below duck target`() =
        runTest {
            val (player, currentVolume) = createPlayer(initialVolume = 0.1f)
            val controller =
                AudioFocusDuckingController(
                    getActivePlayer = { player },
                    scope = TestScope(StandardTestDispatcher(testScheduler)),
                )

            controller.onPlaybackSuppressionReasonChanged(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS)

            assertEquals(0.1f, currentVolume(), 0.0001f)
        }

    private fun createPlayer(initialVolume: Float): Pair<ExoPlayer, () -> Float> {
        var volume = initialVolume
        val player: ExoPlayer = mock()
        whenever(player.volume).thenAnswer { volume }
        doAnswer { invocation ->
            volume = invocation.getArgument(0)
            null
        }.`when`(player).setVolume(anyFloat())
        return player to { volume }
    }
}
