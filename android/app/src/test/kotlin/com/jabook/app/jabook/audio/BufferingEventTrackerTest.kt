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
import org.robolectric.RobolectricTestRunner
import org.mockito.kotlin.whenever

@RunWith(RobolectricTestRunner::class)
class BufferingEventTrackerTest {
    private lateinit var player: Player
    private lateinit var longRebuffers: MutableList<Pair<Long, Long>>
    private var nowMs: Long = 10_000L

    @Before
    fun setUp() {
        player = mock()
        longRebuffers = mutableListOf()
    }

    private fun createTracker(): BufferingEventTracker =
        BufferingEventTracker(
            player = player,
            onLongRebuffer = { durationMs, positionMs -> longRebuffers.add(durationMs to positionMs) },
            nowMsProvider = { nowMs },
        )

    // --- Short rebuffer ---

    @Test
    fun `short rebuffer tracked but no long-rebuffer alert`() {
        whenever(player.currentPosition).thenReturn(30_000L)
        val tracker = createTracker()

        nowMs = 10_000L
        tracker.onPlaybackStateChanged(Player.STATE_BUFFERING)

        nowMs = 12_000L
        tracker.onPlaybackStateChanged(Player.STATE_READY)

        val stats = tracker.statsForTest()
        assertEquals(1, stats.totalRebuffers)
        assertEquals(2_000L, stats.totalRebufferDurationMs)
        assertEquals(0, longRebuffers.size)
    }

    // --- Long rebuffer triggers alert ---

    @Test
    fun `long rebuffer triggers alert`() {
        whenever(player.currentPosition).thenReturn(30_000L)
        val tracker = createTracker()

        nowMs = 10_000L
        tracker.onPlaybackStateChanged(Player.STATE_BUFFERING)

        nowMs = 15_000L
        tracker.onPlaybackStateChanged(Player.STATE_READY)

        val stats = tracker.statsForTest()
        assertEquals(1, stats.totalRebuffers)
        assertEquals(5_000L, stats.longestRebufferMs)
        assertEquals(1, longRebuffers.size)
        assertEquals(5_000L, longRebuffers[0].first)
        assertEquals(30_000L, longRebuffers[0].second)
    }

    // --- Multiple rebuffers ---

    @Test
    fun `multiple rebuffers accumulate stats`() {
        whenever(player.currentPosition).thenReturn(0L)
        val tracker = createTracker()

        nowMs = 10_000L
        tracker.onPlaybackStateChanged(Player.STATE_BUFFERING)
        nowMs = 11_000L
        tracker.onPlaybackStateChanged(Player.STATE_READY)

        nowMs = 15_000L
        tracker.onPlaybackStateChanged(Player.STATE_BUFFERING)
        nowMs = 19_000L
        tracker.onPlaybackStateChanged(Player.STATE_READY)

        val stats = tracker.statsForTest()
        assertEquals(2, stats.totalRebuffers)
        assertEquals(5_000L, stats.totalRebufferDurationMs)
        assertEquals(4_000L, stats.longestRebufferMs)
    }

    // --- State READY without prior BUFFERING ---

    @Test
    fun `READY without prior BUFFERING does nothing`() {
        val tracker = createTracker()
        tracker.onPlaybackStateChanged(Player.STATE_READY)

        val stats = tracker.statsForTest()
        assertEquals(0, stats.totalRebuffers)
    }

    // --- Register/unregister ---

    @Test
    fun `register adds listener to player`() {
        val tracker = createTracker()
        tracker.register()

        org.mockito.kotlin
            .verify(player)
            .addListener(tracker)
    }

    @Test
    fun `unregister removes listener from player`() {
        val tracker = createTracker()
        tracker.unregister()

        org.mockito.kotlin
            .verify(player)
            .removeListener(tracker)
    }
}
