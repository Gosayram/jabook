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

import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaybackSpeedTest {
    private lateinit var playbackController: PlaybackController
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var testScope: TestScope

    @Before
    fun setup() {
        exoPlayer = mock()
        testScope = TestScope()

        // Mock getActivePlayer
        val getActivePlayer = { exoPlayer }
        val resetTimer = {}

        playbackController = PlaybackController(getActivePlayer, testScope, resetTimer, { 10 })
    }

    @Test
    fun `setSpeed sets playback speed on player`() {
        // Given
        val speed = 1.5f
        whenever(exoPlayer.playbackParameters).thenReturn(PlaybackParameters(1.0f))

        // When
        playbackController.setSpeed(speed)

        // Then
        verify(exoPlayer).setPlaybackSpeed(speed)
    }

    @Test
    fun `getSpeed returns current player speed`() {
        // Given
        val expectedSpeed = 1.25f
        whenever(exoPlayer.playbackParameters).thenReturn(PlaybackParameters(expectedSpeed))

        // When
        val actualSpeed = playbackController.getSpeed()

        // Then
        assert(actualSpeed == expectedSpeed)
    }
}
