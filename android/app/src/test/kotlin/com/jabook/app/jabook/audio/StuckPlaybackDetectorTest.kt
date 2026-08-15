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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StuckPlaybackDetectorTest {
    private lateinit var player: Player
    private lateinit var unrecoverableEvents: MutableList<Unit>
    private var nowMs: Long = 10_000L

    @Before
    fun setUp() {
        player = mock()
        unrecoverableEvents = mutableListOf()
    }

    private fun createDetector(): StuckPlaybackDetector =
        StuckPlaybackDetector(
            player = player,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
            onUnrecoverable = { unrecoverableEvents.add(Unit) },
            nowMsProvider = { nowMs },
        )

    // --- checkPlaybackState directly (unit test without coroutine loop) ---

    @Test
    fun `checkBufferingStuck detects stuck after threshold`() {
        whenever(player.playbackState).thenReturn(Player.STATE_BUFFERING)
        whenever(player.isPlaying).thenReturn(false)
        whenever(player.currentPosition).thenReturn(5_000L)

        val detector = createDetector()

        // First check: buffering starts
        nowMs = 10_000L
        detector.checkPlaybackStateForTest()

        // Second check: still buffering, past threshold
        nowMs = 10_000L + StuckPlaybackDetector.STUCK_THRESHOLD_MS + 1
        detector.checkPlaybackStateForTest()

        verify(player).seekTo(5_000L + StuckPlaybackDetector.RECOVERY_SEEK_OFFSET_MS)
    }

    @Test
    fun `checkPositionStuck detects stuck position while playing`() {
        whenever(player.playbackState).thenReturn(Player.STATE_READY)
        whenever(player.isPlaying).thenReturn(true)
        whenever(player.currentPosition).thenReturn(30_000L)

        val detector = createDetector()

        // First check: record position
        nowMs = 10_000L
        detector.checkPlaybackStateForTest()

        // Second check: same position after threshold
        nowMs = 10_000L + StuckPlaybackDetector.POSITION_STUCK_THRESHOLD_MS + 1
        detector.checkPlaybackStateForTest()

        verify(player).seekTo(30_000L + StuckPlaybackDetector.RECOVERY_SEEK_OFFSET_MS)
    }

    @Test
    fun `recovery attempted only once then unrecoverable`() {
        whenever(player.playbackState).thenReturn(Player.STATE_BUFFERING)
        whenever(player.isPlaying).thenReturn(false)
        whenever(player.currentPosition).thenReturn(0L)

        val detector = createDetector()

        // First: starts buffering
        nowMs = 10_000L
        detector.checkPlaybackStateForTest()

        // Second: stuck, recovery attempted
        nowMs = 10_000L + StuckPlaybackDetector.STUCK_THRESHOLD_MS + 1
        detector.checkPlaybackStateForTest()

        // Third: still stuck, already recovered -> unrecoverable
        nowMs = 10_000L + StuckPlaybackDetector.STUCK_THRESHOLD_MS * 2 + 2
        detector.checkPlaybackStateForTest()

        assertEquals(1, unrecoverableEvents.size)
    }

    @Test
    fun `reset clears recovery state`() {
        whenever(player.playbackState).thenReturn(Player.STATE_BUFFERING)
        whenever(player.isPlaying).thenReturn(false)
        whenever(player.currentPosition).thenReturn(0L)

        val detector = createDetector()

        nowMs = 10_000L
        detector.checkPlaybackStateForTest()

        nowMs = 10_000L + StuckPlaybackDetector.STUCK_THRESHOLD_MS + 1
        detector.checkPlaybackStateForTest()

        detector.reset()

        // After reset, buffering should start fresh
        nowMs = 10_000L + StuckPlaybackDetector.STUCK_THRESHOLD_MS * 2 + 2
        detector.checkPlaybackStateForTest()

        // Recovery was reset, so seekTo should happen again (not unrecoverable)
        assertEquals(0, unrecoverableEvents.size)
    }

    @Test
    fun `stopWatching cancels monitoring`() {
        whenever(player.playbackState).thenReturn(Player.STATE_BUFFERING)
        whenever(player.isPlaying).thenReturn(false)
        whenever(player.currentPosition).thenReturn(0L)

        val detector = createDetector()
        detector.stopWatching()

        assertEquals(0, unrecoverableEvents.size)
    }
}
