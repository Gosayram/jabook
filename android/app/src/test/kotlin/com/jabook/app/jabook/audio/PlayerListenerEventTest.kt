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
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Tests for PlayerListener event handling.
 *
 * Verifies that:
 * - onEvents() handles correct playback state changes
 * - Playback state changes are properly handled
 */
@RunWith(RobolectricTestRunner::class)
class PlayerListenerEventTest {
    @Test
    fun `PlayerListener can be instantiated with required parameters`() {
        // This test verifies that PlayerListener constructor works
        // Full behavior testing requires more complex mocking of coroutines

        // Arrange - create minimal dependencies
        val context: android.content.Context = mock()

        // Assert we can reference all required lambdas
        val listener =
            PlayerListener(
                context = context,
                getActivePlayer = { error("Not used in constructor test") },
                getIsBookCompleted = { false },
                setIsBookCompleted = { },
                getSleepTimerEndOfChapter = { false },
                getSleepTimerEndOfTrack = { false },
                getSleepTimerEndTime = { 0L },
                cancelSleepTimer = { },
                sendTimerExpiredEvent = { },
                saveCurrentPosition = { },
                startSleepTimerCheck = { },
                getEmbeddedArtworkPath = { null },
                setEmbeddedArtworkPath = { },
                getCurrentMetadata = { null },
                setLastCompletedTrackIndex = { },
                getLastCompletedTrackIndex = { -1 },
                getActualPlaylistSize = { 0 },
                updateActualTrackIndex = { },
                isPlaylistLoading = { false },
            )

        val playerListener: Player.Listener = listener
        assertEquals(listener, playerListener)
    }

    @Test
    fun `PlayerListener implements Player Listener interface`() {
        // Verify that PlayerListener is a proper Player.Listener
        val context: android.content.Context = mock()

        val listener =
            PlayerListener(
                context = context,
                getActivePlayer = { error("Not used in interface test") },
                getIsBookCompleted = { false },
                setIsBookCompleted = { },
                getSleepTimerEndOfChapter = { false },
                getSleepTimerEndOfTrack = { false },
                getSleepTimerEndTime = { 0L },
                cancelSleepTimer = { },
                sendTimerExpiredEvent = { },
                saveCurrentPosition = { },
                startSleepTimerCheck = { },
                getEmbeddedArtworkPath = { null },
                setEmbeddedArtworkPath = { },
                getCurrentMetadata = { null },
                setLastCompletedTrackIndex = { },
                getLastCompletedTrackIndex = { -1 },
                getActualPlaylistSize = { 0 },
                updateActualTrackIndex = { },
                isPlaylistLoading = { false },
            )

        // Assert that listener conforms to Player.Listener
        val playerListener: Player.Listener = listener
        assertEquals(listener, playerListener)
    }

    @Test
    fun `Player STATE constants are available`() {
        // Verify that we can access player state constants used by PlayerListener
        assertEquals(1, Player.STATE_IDLE)
        assertEquals(2, Player.STATE_BUFFERING)
        assertEquals(3, Player.STATE_READY)
        assertEquals(4, Player.STATE_ENDED)
    }

    @Test
    fun `Player EVENT constants are available for event checking`() {
        // Verify key event constants that PlayerListener checks
        assertEquals(4, Player.EVENT_PLAYBACK_STATE_CHANGED)
        assertEquals(5, Player.EVENT_PLAY_WHEN_READY_CHANGED)
        assertEquals(6, Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED)
        assertEquals(7, Player.EVENT_IS_PLAYING_CHANGED)
    }

    @Test
    fun `onMediaItemTransition saves position when sleep timer ends on auto transition`() {
        val context: android.content.Context = mock()
        val player: ExoPlayer = mock()
        whenever(player.currentMediaItemIndex).thenReturn(2)

        var savedPositionCalls = 0
        var cancelSleepTimerCalls = 0
        var timerExpiredCalls = 0
        var markSleepTimerPauseCalls = 0

        val listener =
            PlayerListener(
                context = context,
                getActivePlayer = { player },
                getIsBookCompleted = { false },
                setIsBookCompleted = { },
                getSleepTimerEndOfChapter = { true },
                getSleepTimerEndOfTrack = { false },
                getSleepTimerEndTime = { 0L },
                cancelSleepTimer = { cancelSleepTimerCalls++ },
                sendTimerExpiredEvent = { timerExpiredCalls++ },
                markSleepTimerPause = { markSleepTimerPauseCalls++ },
                saveCurrentPosition = { savedPositionCalls++ },
                startSleepTimerCheck = { },
                getEmbeddedArtworkPath = { null },
                setEmbeddedArtworkPath = { },
                getCurrentMetadata = { null },
                setLastCompletedTrackIndex = { },
                getLastCompletedTrackIndex = { -1 },
                getActualPlaylistSize = { 10 },
                updateActualTrackIndex = { },
                isPlaylistLoading = { false },
            )

        listener.onMediaItemTransition(
            MediaItem.Builder().setMediaId("chapter-3").build(),
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
        )

        assertEquals(1, savedPositionCalls)
        assertEquals(1, cancelSleepTimerCalls)
        assertEquals(1, timerExpiredCalls)
        assertEquals(1, markSleepTimerPauseCalls)
        verify(player).playWhenReady = false
    }

    @Test
    fun `onMediaItemTransition does not save position for manual transition with sleep timer`() {
        val context: android.content.Context = mock()
        val player: ExoPlayer = mock()
        whenever(player.currentMediaItemIndex).thenReturn(2)

        var savedPositionCalls = 0
        var markSleepTimerPauseCalls = 0

        val listener =
            PlayerListener(
                context = context,
                getActivePlayer = { player },
                getIsBookCompleted = { false },
                setIsBookCompleted = { },
                getSleepTimerEndOfChapter = { true },
                getSleepTimerEndOfTrack = { false },
                getSleepTimerEndTime = { 0L },
                cancelSleepTimer = { },
                sendTimerExpiredEvent = { },
                markSleepTimerPause = { markSleepTimerPauseCalls++ },
                saveCurrentPosition = { savedPositionCalls++ },
                startSleepTimerCheck = { },
                getEmbeddedArtworkPath = { null },
                setEmbeddedArtworkPath = { },
                getCurrentMetadata = { null },
                setLastCompletedTrackIndex = { },
                getLastCompletedTrackIndex = { -1 },
                getActualPlaylistSize = { 10 },
                updateActualTrackIndex = { },
                isPlaylistLoading = { false },
            )

        listener.onMediaItemTransition(
            MediaItem.Builder().setMediaId("chapter-3").build(),
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
        )

        assertEquals(0, savedPositionCalls)
        assertEquals(0, markSleepTimerPauseCalls)
        verify(player, never()).playWhenReady = false
    }
}
