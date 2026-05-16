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

import android.content.Context
import android.content.Intent
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for [BookCompletionCoordinator].
 *
 * Covers all completion sources (STATE_ENDED, DISCONTINUITY, POSITION_CHECK, etc.)
 * and verifies idempotency, index normalization, and side-effect correctness.
 */
@RunWith(RobolectricTestRunner::class)
class BookCompletionCoordinatorTest {
    private lateinit var coordinator: BookCompletionCoordinator
    private lateinit var player: Player
    private lateinit var context: Context

    // Mutable state for lambdas
    private var isBookCompleted = false
    private var lastCompletedTrackIndex = -1
    private var positionSaved = false
    private var markedBookId: String? = null

    private val totalTracks = 10
    private val currentBookId = "book-123"

    @Before
    fun setup() {
        context = mock()
        `when`(context.packageName).thenReturn("com.jabook.app.jabook")
        player = mock()
        `when`(player.currentPosition).thenReturn(50_000L)
        `when`(player.duration).thenReturn(60_000L)

        isBookCompleted = false
        lastCompletedTrackIndex = -1
        positionSaved = false
        markedBookId = null

        coordinator =
            BookCompletionCoordinator(
                context = context,
                getIsBookCompleted = { isBookCompleted },
                setIsBookCompleted = { isBookCompleted = it },
                getActualPlaylistSize = { totalTracks },
                getLastCompletedTrackIndex = { lastCompletedTrackIndex },
                setLastCompletedTrackIndex = { lastCompletedTrackIndex = it },
                getCurrentBookId = { currentBookId },
                markBookCompleted = { markedBookId = it },
                saveCurrentPosition = { positionSaved = true },
            )
    }

    // ---- STATE_ENDED source ----

    @Test
    fun `notifyCompletion from STATE_ENDED on last track completes book`() {
        val result =
            coordinator.notifyCompletion(
                player = player,
                currentIndex = totalTracks - 1,
                source = BookCompletionCoordinator.Source.STATE_ENDED,
            )

        assertTrue(result)
        assertTrue(isBookCompleted)
        assertEquals(totalTracks - 1, lastCompletedTrackIndex)
        assertEquals(currentBookId, markedBookId)
        assertTrue(positionSaved)
        verify(player).pause()
        verify(player).seekTo(totalTracks - 1, 50_000L)
        verify(context).sendBroadcast(any(Intent::class.java))
    }

    @Test
    fun `notifyCompletion from STATE_ENDED on non-last track does not complete`() {
        val result =
            coordinator.notifyCompletion(
                player = player,
                currentIndex = 5,
                source = BookCompletionCoordinator.Source.STATE_ENDED,
            )

        assertFalse(result)
        assertFalse(isBookCompleted)
        verify(player, never()).pause()
    }

    // ---- Idempotency ----

    @Test
    fun `notifyCompletion is idempotent - second call returns false`() {
        val first =
            coordinator.notifyCompletion(
                player = player,
                currentIndex = totalTracks - 1,
                source = BookCompletionCoordinator.Source.STATE_ENDED,
            )
        assertTrue(first)

        // Second call should be a no-op
        val second =
            coordinator.notifyCompletion(
                player = player,
                currentIndex = totalTracks - 1,
                source = BookCompletionCoordinator.Source.POSITION_CHECK,
            )
        assertFalse(second)

        // Verify pause was called only once (from the first completion)
        verify(player, times(1)).pause()
    }

    // ---- DISCONTINUITY source ----

    @Test
    fun `notifyCompletion from DISCONTINUITY on last track completes book`() {
        val result =
            coordinator.notifyCompletion(
                player = player,
                currentIndex = totalTracks - 1,
                source = BookCompletionCoordinator.Source.DISCONTINUITY,
            )

        assertTrue(result)
        assertTrue(isBookCompleted)
    }

    // ---- POSITION_CHECK source ----

    @Test
    fun `notifyCompletion from POSITION_CHECK on last track completes book`() {
        val result =
            coordinator.notifyCompletion(
                player = player,
                currentIndex = totalTracks - 1,
                source = BookCompletionCoordinator.Source.POSITION_CHECK,
            )

        assertTrue(result)
        assertTrue(isBookCompleted)
    }

    // ---- Index normalization ----

    @Test
    fun `notifyCompletion normalizes out-of-bounds index to last track`() {
        // Index 0 is out of bounds for completion when it's not the actual last track
        // BookCompletionIndexPolicy should resolve it using saved index or last track
        `when`(player.currentPosition).thenReturn(59_000L)
        `when`(player.duration).thenReturn(60_000L)

        val result =
            coordinator.notifyCompletion(
                player = player,
                currentIndex = 0, // Invalid for completion
                source = BookCompletionCoordinator.Source.DISCONTINUITY,
            )

        // With index 0, the policy should resolve to totalTracks - 1 if position is valid
        assertTrue(result)
        assertTrue(isBookCompleted)
        assertEquals(totalTracks - 1, lastCompletedTrackIndex)
    }

    // ---- Edge cases ----

    @Test
    fun `notifyCompletion with zero totalTracks returns false`() {
        val emptyCoordinator =
            BookCompletionCoordinator(
                context = context,
                getIsBookCompleted = { false },
                setIsBookCompleted = { },
                getActualPlaylistSize = { 0 },
                getLastCompletedTrackIndex = { -1 },
                setLastCompletedTrackIndex = { },
                getCurrentBookId = { null },
                markBookCompleted = { },
                saveCurrentPosition = { },
            )

        val result =
            emptyCoordinator.notifyCompletion(
                player = player,
                currentIndex = 0,
                source = BookCompletionCoordinator.Source.STATE_ENDED,
            )

        assertFalse(result)
    }

    @Test
    fun `notifyCompletion with single track playlist completes on track 0`() {
        val singleCoordinator =
            BookCompletionCoordinator(
                context = context,
                getIsBookCompleted = { false },
                setIsBookCompleted = { isBookCompleted = it },
                getActualPlaylistSize = { 1 },
                getLastCompletedTrackIndex = { -1 },
                setLastCompletedTrackIndex = { lastCompletedTrackIndex = it },
                getCurrentBookId = { currentBookId },
                markBookCompleted = { markedBookId = it },
                saveCurrentPosition = { positionSaved = true },
            )

        val result =
            singleCoordinator.notifyCompletion(
                player = player,
                currentIndex = 0,
                source = BookCompletionCoordinator.Source.POSITION_CHECK,
            )

        assertTrue(result)
        assertTrue(isBookCompleted)
        assertEquals(0, lastCompletedTrackIndex)
    }

    @Test
    fun `notifyCompletion pauses playback and seeks to preserve index`() {
        coordinator.notifyCompletion(
            player = player,
            currentIndex = totalTracks - 1,
            source = BookCompletionCoordinator.Source.STATE_ENDED,
        )

        verify(player).pause()
        verify(player).playWhenReady = false
        verify(player).seekTo(totalTracks - 1, 50_000L)
    }

    @Test
    fun `notifyCompletion falls back gracefully on seek error`() {
        `when`(player.seekTo(anyInt(), anyLong())).thenThrow(RuntimeException("seek failed"))

        val result =
            coordinator.notifyCompletion(
                player = player,
                currentIndex = totalTracks - 1,
                source = BookCompletionCoordinator.Source.STATE_ENDED,
            )

        // Should still complete despite seek error
        assertTrue(result)
        assertTrue(isBookCompleted)
    }

    @Test
    fun `notifyCompletion from all sources can complete book`() {
        val sources = BookCompletionCoordinator.Source.entries
        for (source in sources) {
            // Reset state
            isBookCompleted = false
            lastCompletedTrackIndex = -1
            positionSaved = false

            val result =
                coordinator.notifyCompletion(
                    player = player,
                    currentIndex = totalTracks - 1,
                    source = source,
                )

            assertTrue("Source $source should complete", result)
            assertTrue("Source $source: isBookCompleted", isBookCompleted)
            assertTrue("Source $source: positionSaved", positionSaved)
        }
    }

    @Test
    fun `notifyCompletion broadcasts BOOK_COMPLETED intent`() {
        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)

        coordinator.notifyCompletion(
            player = player,
            currentIndex = totalTracks - 1,
            source = BookCompletionCoordinator.Source.SMART_COMPLETION,
        )

        verify(context).sendBroadcast(intentCaptor.capture())
        val captured = intentCaptor.value
        assertEquals(
            "com.jabook.app.jabook.BOOK_COMPLETED",
            captured.action,
        )
        assertEquals(
            totalTracks - 1,
            captured.getIntExtra("last_track_index", -1),
        )
        assertEquals("com.jabook.app.jabook", captured.`package`)
    }

    @Test
    fun `notifyCompletion handles null bookId gracefully`() {
        val nullBookCoordinator =
            BookCompletionCoordinator(
                context = context,
                getIsBookCompleted = { false },
                setIsBookCompleted = { isBookCompleted = it },
                getActualPlaylistSize = { totalTracks },
                getLastCompletedTrackIndex = { -1 },
                setLastCompletedTrackIndex = { lastCompletedTrackIndex = it },
                getCurrentBookId = { null },
                markBookCompleted = { markedBookId = it },
                saveCurrentPosition = { positionSaved = true },
            )

        val result =
            nullBookCoordinator.notifyCompletion(
                player = player,
                currentIndex = totalTracks - 1,
                source = BookCompletionCoordinator.Source.STATE_ENDED,
            )

        assertTrue(result)
        assertTrue(isBookCompleted)
        // markedBookId should remain null since getCurrentBookId returns null
        assertEquals(null, markedBookId)
    }
}
