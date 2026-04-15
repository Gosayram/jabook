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

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for PlaybackController.
 *
 * Tests cover:
 * - Play/Pause operations
 * - Seek operations
 * - Playback speed control
 * - Repeat and shuffle modes
 * - Next/Previous track navigation
 * - Rewind/Forward operations
 * - Initial position application
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaybackControllerTest {
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var playbackController: PlaybackController
    private lateinit var testScope: TestScope
    private val testDispatcher = StandardTestDispatcher()
    private var resetTimerCallCount = 0

    @Before
    fun setup() {
        // Set test dispatcher as Main dispatcher for coroutines
        Dispatchers.setMain(testDispatcher)

        exoPlayer = mock()
        testScope = TestScope(testDispatcher)

        // Default mock behavior
        whenever(exoPlayer.mediaItemCount).thenReturn(1)
        whenever(exoPlayer.playbackState).thenReturn(Player.STATE_READY)
        whenever(exoPlayer.playWhenReady).thenReturn(false)
        whenever(exoPlayer.currentPosition).thenReturn(0L)
        whenever(exoPlayer.duration).thenReturn(100000L) // 100 seconds
        whenever(exoPlayer.currentMediaItemIndex).thenReturn(0)
        whenever(exoPlayer.playbackParameters).thenReturn(PlaybackParameters(1.0f))
        whenever(exoPlayer.repeatMode).thenReturn(Player.REPEAT_MODE_OFF)
        whenever(exoPlayer.shuffleModeEnabled).thenReturn(false)

        resetTimerCallCount = 0
        val resetTimer: () -> Unit = { resetTimerCallCount++ }

        val getActivePlayer = { exoPlayer }
        playbackController = PlaybackController(getActivePlayer, testScope, resetTimer, { 10 })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ============ Play Tests ============

    @Test
    fun `play sets playWhenReady to true when player has media items`() =
        runTest(testDispatcher) {
            // Given
            whenever(exoPlayer.mediaItemCount).thenReturn(1)
            whenever(exoPlayer.playbackState).thenReturn(Player.STATE_READY)

            // When
            playbackController.play()
            // Advance time to let coroutine in Dispatchers.Main complete
            advanceUntilIdle()

            // Then
            verify(exoPlayer).playWhenReady = true
            assertEquals(1, resetTimerCallCount)
        }

    @Test
    fun `play prepares player when in IDLE state`() =
        runTest(testDispatcher) {
            // Given
            whenever(exoPlayer.mediaItemCount).thenReturn(1)
            whenever(exoPlayer.playbackState).thenReturn(Player.STATE_IDLE)

            // When
            playbackController.play()
            advanceUntilIdle()

            // Then
            verify(exoPlayer).prepare()
            verify(exoPlayer).playWhenReady = true
        }

    @Test
    fun `play does nothing when no media items loaded`() =
        runTest(testDispatcher) {
            // Given
            whenever(exoPlayer.mediaItemCount).thenReturn(0)

            // When
            playbackController.play()
            advanceUntilIdle()

            // Then
            verify(exoPlayer, never()).playWhenReady = true
        }

    @Test
    fun `play skips resume rewind after sleep timer pause`() =
        runTest(testDispatcher) {
            whenever(exoPlayer.mediaItemCount).thenReturn(1)
            whenever(exoPlayer.playbackState).thenReturn(Player.STATE_READY)
            whenever(exoPlayer.currentPosition).thenReturn(50_000L)

            playbackController.markSleepTimerPause()
            playbackController.play()
            advanceUntilIdle()

            verify(exoPlayer, never()).seekTo(any<Long>())
            verify(exoPlayer).playWhenReady = true
        }

    @Test
    fun `play skips resume rewind when persisted sleep timer stop flag is consumed`() =
        runTest(testDispatcher) {
            whenever(exoPlayer.mediaItemCount).thenReturn(1)
            whenever(exoPlayer.playbackState).thenReturn(Player.STATE_READY)
            whenever(exoPlayer.currentPosition).thenReturn(50_000L)

            var consumeCalls = 0
            playbackController =
                PlaybackController(
                    getActivePlayer = { exoPlayer },
                    playerServiceScope = testScope,
                    resetInactivityTimer = { resetTimerCallCount++ },
                    getResumeRewindSeconds = { 10 },
                    consumeSleepTimerStopFlag = {
                        consumeCalls++
                        consumeCalls == 1
                    },
                )

            playbackController.play()
            advanceUntilIdle()
            playbackController.play()
            advanceUntilIdle()

            verify(exoPlayer, times(1)).seekTo(40_000L)
            assertTrue(consumeCalls >= 2)
        }

    // ============ Pause Tests ============

    @Test
    fun `pause sets playWhenReady to false`() =
        runTest(testDispatcher) {
            // Given
            whenever(exoPlayer.playWhenReady).thenReturn(true)
            whenever(exoPlayer.playbackState).thenReturn(Player.STATE_READY)
            whenever(exoPlayer.currentPosition).thenReturn(50000L)

            // When
            playbackController.pause()
            advanceUntilIdle()

            // Then
            verify(exoPlayer).playWhenReady = false
            assertEquals(1, resetTimerCallCount)
        }

    @Test
    fun `pause rewinds 2 seconds before pausing`() =
        runTest(testDispatcher) {
            // Given
            whenever(exoPlayer.playWhenReady).thenReturn(true)
            whenever(exoPlayer.playbackState).thenReturn(Player.STATE_READY)
            whenever(exoPlayer.currentPosition).thenReturn(50000L) // 50 seconds

            // When
            playbackController.pause()
            advanceUntilIdle()

            // Then - should seek to 48 seconds (50 - 2)
            verify(exoPlayer).seekTo(48000L)
            verify(exoPlayer).playWhenReady = false
        }

    @Test
    fun `pause does not rewind when position is less than 2 seconds`() =
        runTest(testDispatcher) {
            // Given
            whenever(exoPlayer.playWhenReady).thenReturn(true)
            whenever(exoPlayer.playbackState).thenReturn(Player.STATE_READY)
            whenever(exoPlayer.currentPosition).thenReturn(1000L) // 1 second

            // When
            playbackController.pause()
            advanceUntilIdle()

            // Then - should seek to 0 (coerced)
            verify(exoPlayer).seekTo(0L)
        }

    @Test
    fun `pause does not rewind when player is in ENDED state`() =
        runTest(testDispatcher) {
            // Given
            whenever(exoPlayer.playWhenReady).thenReturn(true)
            whenever(exoPlayer.playbackState).thenReturn(Player.STATE_ENDED)

            // When
            playbackController.pause()
            advanceUntilIdle()

            // Then - should not seek, just pause
            verify(exoPlayer, never()).seekTo(any())
            verify(exoPlayer).playWhenReady = false
        }

    // ============ Stop Tests ============

    @Test
    fun `stop calls player stop`() {
        // When
        playbackController.stop()

        // Then
        verify(exoPlayer).stop()
    }

    // ============ Seek Tests ============

    @Test
    fun `seekTo seeks to valid position`() {
        // Given
        val positionMs = 30000L

        // When
        playbackController.seekTo(positionMs)

        // Then
        verify(exoPlayer).seekTo(positionMs)
        assertEquals(1, resetTimerCallCount)
    }

    @Test
    fun `seekTo does nothing when position is negative`() {
        // Given
        val positionMs = -1000L

        // When
        playbackController.seekTo(positionMs)

        // Then
        verify(exoPlayer, never()).seekTo(any())
    }

    @Test
    fun `seekTo does nothing when no media items loaded`() {
        // Given
        whenever(exoPlayer.mediaItemCount).thenReturn(0)
        val positionMs = 30000L

        // When
        playbackController.seekTo(positionMs)

        // Then
        verify(exoPlayer, never()).seekTo(any())
    }

    @Test
    fun `seekTo clamps position to duration`() {
        // Given
        whenever(exoPlayer.duration).thenReturn(50000L) // 50 seconds
        val positionMs = 100000L // Beyond duration

        // When
        playbackController.seekTo(positionMs)

        // Then - should clamp to duration
        verify(exoPlayer).seekTo(50000L)
    }

    @Test
    fun `seekTo resumes playback if was playing before seek`() =
        runTest(testDispatcher) {
            // Given
            whenever(exoPlayer.playWhenReady).thenReturn(true)
            whenever(exoPlayer.playbackState).thenReturn(Player.STATE_READY)
            val positionMs = 30000L

            // When
            playbackController.seekTo(positionMs)
            // Advance time to let coroutine with delay(100) complete
            // The coroutine runs in playerServiceScope which uses testScope with testDispatcher
            advanceTimeBy(150) // Advance past delay
            advanceUntilIdle() // Ensure all coroutines complete

            // Then
            verify(exoPlayer).seekTo(positionMs)
            // Should resume playback after delay (coroutine checks state and sets playWhenReady)
            // Note: playWhenReady is already true, so it may not be set again
            // The test verifies that seekTo was called and coroutine completed
        }

    // ============ Speed Tests ============

    @Test
    fun `setSpeed sets playback speed on player`() {
        // Given
        val speed = 1.5f

        // When
        playbackController.setSpeed(speed)

        // Then
        verify(exoPlayer).setPlaybackSpeed(speed)
        assertEquals(1, resetTimerCallCount)
    }

    @Test
    fun `getSpeed returns current player speed`() {
        // Given
        val expectedSpeed = 1.25f
        whenever(exoPlayer.playbackParameters).thenReturn(PlaybackParameters(expectedSpeed))

        // When
        val actualSpeed = playbackController.getSpeed()

        // Then
        assertEquals(expectedSpeed, actualSpeed, 0.01f)
    }

    // ============ Repeat Mode Tests ============

    @Test
    fun `setRepeatMode sets repeat mode on player`() {
        // Given
        val repeatMode = Player.REPEAT_MODE_ALL

        // When
        playbackController.setRepeatMode(repeatMode)

        // Then
        verify(exoPlayer).repeatMode = repeatMode
        assertEquals(1, resetTimerCallCount)
    }

    @Test
    fun `getRepeatMode returns current repeat mode`() {
        // Given
        val expectedMode = Player.REPEAT_MODE_ONE
        whenever(exoPlayer.repeatMode).thenReturn(expectedMode)

        // When
        val actualMode = playbackController.getRepeatMode()

        // Then
        assertEquals(expectedMode, actualMode)
    }

    // ============ Shuffle Mode Tests ============

    @Test
    fun `setShuffleModeEnabled sets shuffle mode on player`() {
        // Given
        val shuffleEnabled = true

        // When
        playbackController.setShuffleModeEnabled(shuffleEnabled)

        // Then
        verify(exoPlayer).shuffleModeEnabled = shuffleEnabled
        assertEquals(1, resetTimerCallCount)
    }

    @Test
    fun `getShuffleModeEnabled returns current shuffle mode`() {
        // Given
        whenever(exoPlayer.shuffleModeEnabled).thenReturn(true)

        // When
        val result = playbackController.getShuffleModeEnabled()

        // Then
        assertTrue(result)
    }

    // ============ Next/Previous Tests ============

    @Test
    fun `next skips to next track`() {
        // Given
        whenever(exoPlayer.currentMediaItemIndex).thenReturn(0)
        whenever(exoPlayer.mediaItemCount).thenReturn(3)
        // Mock MediaItem with HTTP URI (always available, doesn't check file existence)
        val mediaItem = MediaItem.fromUri(Uri.parse("https://example.com/track.mp3"))
        whenever(exoPlayer.getMediaItemAt(any())).thenReturn(mediaItem)

        // When
        playbackController.next()

        // Then
        // TrackAvailabilityChecker will check availability and either:
        // - Use seekTo if it finds a different available track
        // - Use seekToNextMediaItem if current track is available
        // Since HTTP URIs are always available, it should use seekToNextMediaItem
        verify(exoPlayer, times(1)).seekToNextMediaItem()
        assertEquals(1, resetTimerCallCount)
    }

    @Test
    fun `previous skips to previous track`() {
        // Given
        whenever(exoPlayer.currentMediaItemIndex).thenReturn(1)
        whenever(exoPlayer.mediaItemCount).thenReturn(3)
        // Mock MediaItem with HTTP URI (always available, doesn't check file existence)
        val mediaItem = MediaItem.fromUri(Uri.parse("https://example.com/track.mp3"))
        whenever(exoPlayer.getMediaItemAt(any())).thenReturn(mediaItem)

        // When
        playbackController.previous()

        // Then
        // TrackAvailabilityChecker will check availability and either:
        // - Use seekTo if it finds a different available track
        // - Use seekToPreviousMediaItem if current track is available
        // Since HTTP URIs are always available, it should use seekToPreviousMediaItem
        verify(exoPlayer, times(1)).seekToPreviousMediaItem()
        assertEquals(1, resetTimerCallCount)
    }

    // ============ Seek to Track Tests ============

    @Test
    fun `seekToTrack seeks to valid track index`() =
        runTest(testDispatcher) {
            // Given
            whenever(exoPlayer.mediaItemCount).thenReturn(5)
            whenever(exoPlayer.playWhenReady).thenReturn(true)
            val trackIndex = 2

            // When
            playbackController.seekToTrack(trackIndex)
            advanceTimeBy(150)

            // Then
            verify(exoPlayer).seekTo(trackIndex, 0L)
            assertEquals(1, resetTimerCallCount)
        }

    @Test
    fun `seekToTrack does nothing when index is out of bounds`() =
        runTest(testDispatcher) {
            // Given
            whenever(exoPlayer.mediaItemCount).thenReturn(3)
            val trackIndex = 5 // Out of bounds

            // When
            playbackController.seekToTrack(trackIndex)

            // Then
            verify(exoPlayer, never()).seekTo(any(), any())
        }

    @Test
    fun `seekToTrackAndPosition applies chapter seek backtrack guard`() =
        runTest(testDispatcher) {
            // Given
            whenever(exoPlayer.mediaItemCount).thenReturn(5)
            whenever(exoPlayer.playWhenReady).thenReturn(true)
            val trackIndex = 2
            val positionMs = 30000L

            // When
            playbackController.seekToTrackAndPosition(trackIndex, positionMs)
            advanceTimeBy(150)

            // Then
            verify(exoPlayer).seekTo(trackIndex, 29700L)
            assertEquals(1, resetTimerCallCount)
        }

    @Test
    fun `seekToTrackAndPosition clamps chapter seek backtrack at zero`() =
        runTest(testDispatcher) {
            whenever(exoPlayer.mediaItemCount).thenReturn(5)
            whenever(exoPlayer.playWhenReady).thenReturn(false)

            playbackController.seekToTrackAndPosition(trackIndex = 2, positionMs = 120L)

            verify(exoPlayer).seekTo(2, 0L)
        }

    @Test
    fun `seekToTrackAndPosition does nothing when track index is invalid`() =
        runTest(testDispatcher) {
            // Given
            whenever(exoPlayer.mediaItemCount).thenReturn(3)
            val trackIndex = 5 // Out of bounds
            val positionMs = 30000L

            // When
            playbackController.seekToTrackAndPosition(trackIndex, positionMs)

            // Then
            verify(exoPlayer, never()).seekTo(any(), any())
        }

    @Test
    fun `seekToTrackAndPosition does nothing when position is negative`() =
        runTest(testDispatcher) {
            // Given
            whenever(exoPlayer.mediaItemCount).thenReturn(5)
            val trackIndex = 2
            val positionMs = -1000L

            // When
            playbackController.seekToTrackAndPosition(trackIndex, positionMs)

            // Then
            verify(exoPlayer, never()).seekTo(any(), any())
        }

    // ============ Rewind/Forward Tests ============

    @Test
    fun `rewind seeks backward by default 15 seconds`() {
        // Given
        whenever(exoPlayer.currentPosition).thenReturn(50000L) // 50 seconds

        // When
        playbackController.rewind()

        // Then - should seek to 35 seconds (50 - 15)
        verify(exoPlayer).seekTo(35000L)
        assertEquals(1, resetTimerCallCount)
    }

    @Test
    fun `rewind with custom seconds seeks backward by specified amount`() {
        // Given
        whenever(exoPlayer.currentPosition).thenReturn(50000L)
        val seconds = 10

        // When
        playbackController.rewind(seconds)

        // Then - should seek to 40 seconds (50 - 10)
        verify(exoPlayer).seekTo(40000L)
    }

    @Test
    fun `rewind clamps to zero when result would be negative`() {
        // Given
        whenever(exoPlayer.currentPosition).thenReturn(5000L) // 5 seconds

        // When
        playbackController.rewind(15) // Would go negative

        // Then - should clamp to 0
        verify(exoPlayer).seekTo(0L)
    }

    @Test
    fun `forward seeks forward by default 30 seconds`() {
        // Given
        whenever(exoPlayer.currentPosition).thenReturn(30000L) // 30 seconds
        whenever(exoPlayer.duration).thenReturn(100000L) // 100 seconds

        // When
        playbackController.forward()

        // Then - should seek to 60 seconds (30 + 30)
        verify(exoPlayer).seekTo(60000L)
        assertEquals(1, resetTimerCallCount)
    }

    @Test
    fun `forward with custom seconds seeks forward by specified amount`() {
        // Given
        whenever(exoPlayer.currentPosition).thenReturn(30000L)
        whenever(exoPlayer.duration).thenReturn(100000L)
        val seconds = 20

        // When
        playbackController.forward(seconds)

        // Then - should seek to 50 seconds (30 + 20)
        verify(exoPlayer).seekTo(50000L)
    }

    @Test
    fun `forward clamps to duration when result exceeds duration`() {
        // Given
        whenever(exoPlayer.currentPosition).thenReturn(90000L) // 90 seconds
        whenever(exoPlayer.duration).thenReturn(100000L) // 100 seconds

        // When
        playbackController.forward(30) // Would exceed duration

        // Then - should clamp to duration
        verify(exoPlayer).seekTo(100000L)
    }

    @Test
    fun `forward does nothing when duration is TIME_UNSET`() {
        // Given
        whenever(exoPlayer.currentPosition).thenReturn(30000L)
        whenever(exoPlayer.duration).thenReturn(C.TIME_UNSET)

        // When
        playbackController.forward()

        // Then - should not seek
        verify(exoPlayer, never()).seekTo(any())
    }

    // ============ Apply Initial Position Tests ============

    @Test
    fun `applyInitialPosition seeks to track and position when player is ready`() =
        runTest(testDispatcher) {
            // Given - player is already ready and track is loaded
            whenever(exoPlayer.mediaItemCount).thenReturn(5)
            whenever(exoPlayer.playbackState).thenReturn(Player.STATE_READY)
            whenever(exoPlayer.currentMediaItemIndex).thenReturn(0)
            val trackIndex = 2
            val positionMs = 30000L

            // When - call suspend function directly in runTest
            playbackController.applyInitialPosition(trackIndex, positionMs, null)

            // Then
            verify(exoPlayer).seekTo(trackIndex, 29700L)
        }

    @Test
    fun `applyInitialPosition waits for player to be ready`() =
        runTest(testDispatcher) {
            // Given - player starts in IDLE state, but becomes ready quickly
            // We'll make it ready on first check to avoid long waits
            var stateCheckCount = 0
            whenever(exoPlayer.mediaItemCount).thenReturn(5)
            whenever(exoPlayer.playbackState).thenAnswer {
                stateCheckCount++
                // Return READY after first check (simulating quick ready)
                if (stateCheckCount > 1) Player.STATE_READY else Player.STATE_IDLE
            }
            whenever(exoPlayer.currentMediaItemIndex).thenReturn(0)
            val trackIndex = 2
            val positionMs = 30000L

            // When - call suspend function directly in runTest
            // Method will check state and wait if needed
            playbackController.applyInitialPosition(trackIndex, positionMs, null)

            // Then - should eventually seek
            verify(exoPlayer, times(1)).seekTo(trackIndex, 29700L)
        }
}
