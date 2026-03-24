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

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

/**
 * Tests for PlayerStateTransfer utility.
 * Verifies state saving, restoration, and transfer between players.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlayerStateTransferTest {
    private lateinit var fromPlayer: ExoPlayer
    private lateinit var toPlayer: ExoPlayer
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        fromPlayer = mock()
        toPlayer = mock()

        // Setup fromPlayer state
        whenever(fromPlayer.mediaItemCount).thenReturn(3)
        whenever(fromPlayer.getMediaItemAt(any())).thenReturn(mock<MediaItem>())
        whenever(fromPlayer.currentMediaItemIndex).thenReturn(1)
        whenever(fromPlayer.currentPosition).thenReturn(5000L)
        whenever(fromPlayer.playWhenReady).thenReturn(true)
        whenever(fromPlayer.playbackParameters).thenReturn(
            androidx.media3.common.PlaybackParameters(1.5f, 1.0f),
        )
        whenever(fromPlayer.shuffleModeEnabled).thenReturn(false)
        whenever(fromPlayer.repeatMode).thenReturn(Player.REPEAT_MODE_ALL)
    }

    @After
    fun tearDown() {
        // Cleanup if needed
    }

    @Test
    fun `test save player state`() =
        testScope.runTest {
            // When: Saving player state
            val savedState = PlayerStateTransfer.savePlayerState(fromPlayer)

            // Then: State should be saved correctly
            assert(savedState.currentMediaItemIndex == 1)
            assert(savedState.currentPosition == 5000L)
            assert(savedState.playWhenReady == true)
            assert(savedState.playbackSpeed == 1.5f)
            assert(savedState.shuffleModeEnabled == false)
            assert(savedState.repeatMode == Player.REPEAT_MODE_ALL)
            assert(savedState.mediaItems.size == 3)
        }

    @Test
    fun `test restore player state`() =
        testScope.runTest {
            // Given: Saved state
            val savedState = PlayerStateTransfer.savePlayerState(fromPlayer)

            // When: Restoring state to another player
            PlayerStateTransfer.restorePlayerState(toPlayer, savedState)

            // Then: Player should be configured with saved state
            verify(toPlayer).setMediaItems(any(), any(), any())
            verify(toPlayer).shuffleModeEnabled = false
            verify(toPlayer).repeatMode = Player.REPEAT_MODE_ALL
            verify(toPlayer).setPlaybackSpeed(1.5f)
            verify(toPlayer).playWhenReady = true
            verify(toPlayer).prepare()
        }

    @Test
    fun `test transfer playback between players`() =
        testScope.runTest {
            // When: Transferring playback
            PlayerStateTransfer.transferPlayback(fromPlayer, toPlayer)

            // Then: Source player should be paused
            verify(fromPlayer).pause()

            // And: Destination player should have state restored
            verify(toPlayer).setMediaItems(any(), any(), any())
            verify(toPlayer).prepare()
        }
}
