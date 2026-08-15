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
import androidx.media3.exoplayer.analytics.AnalyticsListener
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AudioUnderrunMonitorTest {
    @Test
    fun `register and unregister manage analytics listener`() {
        val player = mock<ExoPlayer>()
        val monitor = AudioUnderrunMonitor(player = player)

        monitor.register()
        verify(player).addAnalyticsListener(monitor)

        monitor.unregister()
        verify(player).removeAnalyticsListener(monitor)
    }

    @Test
    fun `playback state buffering and ready track rebuffer duration`() {
        val player = mock<ExoPlayer>()
        val nowValues = mutableListOf(1_000L, 2_200L)
        val monitor =
            AudioUnderrunMonitor(
                player = player,
                nowMsProvider = { nowValues.removeFirst() },
            )
        val eventTime = mock<AnalyticsListener.EventTime>()

        monitor.onPlaybackStateChanged(eventTime, Player.STATE_BUFFERING)
        monitor.onPlaybackStateChanged(eventTime, Player.STATE_READY)

        val metrics = monitor.metricsSnapshotForTests()
        assertEquals(0, metrics.totalUnderruns)
        assertEquals(1, metrics.totalRebuffers)
        assertEquals(0, metrics.totalStalls)
        assertEquals(1_200L, metrics.totalRebufferDurationMs)
    }

    @Test
    fun `long rebuffer counts as stall`() {
        val player = mock<ExoPlayer>()
        val nowValues = mutableListOf(5_000L, 8_000L, 8_000L)
        val monitor =
            AudioUnderrunMonitor(
                player = player,
                nowMsProvider = { nowValues.removeFirst() },
            )
        val eventTime = mock<AnalyticsListener.EventTime>()

        monitor.onPlaybackStateChanged(eventTime, Player.STATE_BUFFERING)
        monitor.onPlaybackStateChanged(eventTime, Player.STATE_READY)

        val metrics = monitor.metricsSnapshotForTests()
        assertEquals(1, metrics.totalRebuffers)
        assertEquals(1, metrics.totalStalls)
        assertEquals(3_000L, metrics.totalRebufferDurationMs)
    }

    @Test
    fun `audio underrun increments total underruns`() {
        val player = mock<ExoPlayer>()
        val monitor =
            AudioUnderrunMonitor(
                player = player,
                nowMsProvider = { 1_000L },
            )
        val eventTime = mock<AnalyticsListener.EventTime>()

        monitor.onAudioUnderrun(
            eventTime = eventTime,
            bufferSize = 4096,
            bufferSizeMs = 150L,
            elapsedSinceLastUnderrunMs = 150L,
        )

        val metrics = monitor.metricsSnapshotForTests()
        assertEquals(1, metrics.totalUnderruns)
        assertEquals(0, metrics.totalRebuffers)
        assertEquals(0, metrics.totalStalls)
    }
}
