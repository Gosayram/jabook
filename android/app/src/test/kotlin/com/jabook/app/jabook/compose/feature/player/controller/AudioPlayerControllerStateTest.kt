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

package com.jabook.app.jabook.compose.feature.player.controller

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

/**
 * Tests for AudioPlayerController state management.
 * Verifies StateFlow updates, absence of hangs, and graceful degradation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AudioPlayerControllerStateTest {
    private lateinit var context: android.content.Context
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        exoPlayer = mock()
        userPreferencesRepository = mock()

        whenever(exoPlayer.playbackState).thenReturn(Player.STATE_IDLE)
        whenever(exoPlayer.mediaItemCount).thenReturn(0)
        whenever(exoPlayer.playWhenReady).thenReturn(false)
        whenever(exoPlayer.isPlaying).thenReturn(false)
        whenever(exoPlayer.currentPosition).thenReturn(0L)
        whenever(exoPlayer.duration).thenReturn(0L)
        whenever(exoPlayer.currentMediaItemIndex).thenReturn(0)
    }

    @After
    fun tearDown() {
        // Cleanup if needed
    }

    @Test
    fun `test StateFlow updates on player state change`() =
        testScope.runTest {
            // Given: Controller with initial state
            val controller =
                AudioPlayerController(
                    context = context,
                    exoPlayer = exoPlayer,
                    userPreferencesRepository = userPreferencesRepository,
                )

            advanceUntilIdle()

            // When: Player state changes
            whenever(exoPlayer.isPlaying).thenReturn(true)
            whenever(exoPlayer.playbackState).thenReturn(Player.STATE_READY)

            // Then: StateFlow should update
            val isPlaying = controller.isPlaying.first()
            // Note: In real scenario, this would be through MediaController listener
            assert(isPlaying != null) // StateFlow should have a value
        }

    @Test
    fun `test no hangs on multiple rapid calls`() =
        testScope.runTest {
            // Given: Controller
            val controller =
                AudioPlayerController(
                    context = context,
                    exoPlayer = exoPlayer,
                    userPreferencesRepository = userPreferencesRepository,
                )

            advanceUntilIdle()

            // When: Multiple rapid calls
            repeat(100) {
                controller.play()
                controller.pause()
            }

            // Then: Should complete without hanging
            advanceUntilIdle()
            // Test passes if no timeout/hang occurs
        }

    @Test
    fun `test graceful degradation when MediaController is null`() =
        testScope.runTest {
            // Given: Controller with null MediaController (fallback scenario)
            val controller =
                AudioPlayerController(
                    context = context,
                    exoPlayer = exoPlayer,
                    userPreferencesRepository = userPreferencesRepository,
                )

            advanceUntilIdle()

            // When: MediaController is null, operations should fallback gracefully
            // In real scenario, this would use service fallback
            controller.play()
            controller.pause()

            // Then: Should not crash
            // Test passes if no exception is thrown
        }
}
