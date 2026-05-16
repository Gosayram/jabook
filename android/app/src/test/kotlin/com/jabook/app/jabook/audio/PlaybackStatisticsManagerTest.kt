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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for PlaybackStatisticsManager.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackStatisticsManagerTest {

    @Test
    fun testPlaybackStatistics_tracksListeningTime() {
        val context = FakeContext()
        val manager = PlaybackStatisticsManager(context)
        manager.addListeningTime(1000)
        assertThat(manager.totalListeningTimeMs).isEqualTo(1000)
    }

    @Test
    fun testPlaybackStatistics_tracksSkips() {
        val context = FakeContext()
        val manager = PlaybackStatisticsManager(context)
        manager.incrementSkips()
        assertThat(manager.skips).isEqualTo(1)
    }

    @Test
    fun testPlaybackStatistics_tracksCompleted() {
        val context = FakeContext()
        val manager = PlaybackStatisticsManager(context)
        manager.incrementTracksCompleted()
        assertThat(manager.tracksCompleted).isEqualTo(1)
    }

    @Test
    fun testPlaybackStatistics_formatTime() {
        val context = FakeContext()
        val manager = PlaybackStatisticsManager(context)
        manager.addListeningTime(3661000)
        assertThat(manager.getStatsMap()["total_listening_time_formatted"]).isEqualTo("1:01:01")
    }
}