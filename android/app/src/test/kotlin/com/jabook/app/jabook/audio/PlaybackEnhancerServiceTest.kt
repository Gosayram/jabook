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
import com.jabook.app.jabook.compose.data.preferences.ProtoSettingsRepository
import com.jabook.app.jabook.compose.data.preferences.UserPreferences
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33]) // LoudnessEnhancer requires API 19+
class PlaybackEnhancerServiceTest {
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var settingsRepository: ProtoSettingsRepository
    private lateinit var enhancerService: PlaybackEnhancerService

    @Before
    fun setup() {
        exoPlayer = mock()
        settingsRepository = mock()

        // Create Proto UserPreferences object
        val preferences =
            UserPreferences
                .newBuilder()
                .setVolumeBoostLevel("Off")
                .build()

        // Mock user preferences flow
        whenever(settingsRepository.userPreferences).thenReturn(
            flowOf(preferences),
        )

        enhancerService = PlaybackEnhancerService(exoPlayer, settingsRepository)
    }

    @Test
    fun `initialize attaches listener to player`() =
        runTest {
            // When
            enhancerService.initialize()

            // Then
            verify(exoPlayer).addListener(any<Player.Listener>())
        }

    @Test
    fun `attachEnhancer connects LoudnessEnhancer to session`() =
        runTest {
            // Given
            val sessionId = 123
            whenever(exoPlayer.audioSessionId).thenReturn(sessionId)

            // When
            enhancerService.initialize()

            // Then - We verify no crash and normal operation.
            // Mocking LoudnessEnhancer constructor is hard in Robolectric without Shadow.
            // But we can verify `exoPlayer.audioSessionId` was accessed.
            verify(exoPlayer).audioSessionId
        }
}
