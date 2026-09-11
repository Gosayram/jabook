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

package com.jabook.app.jabook.compose.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownsampleToBarsTest {
    @Test
    fun `empty peaks yield empty bars so slider keeps squiggle`() {
        assertTrue(downsampleToBars(FloatArray(0), SEEKBAR_WAVEFORM_BARS).isEmpty())
    }

    @Test
    fun `non-empty peaks yield exactly the requested bar count`() {
        val bars = downsampleToBars(FloatArray(3_600) { 0.5f }, SEEKBAR_WAVEFORM_BARS)
        assertEquals(SEEKBAR_WAVEFORM_BARS, bars.size)
    }

    @Test
    fun `bar value is max of its bucket`() {
        val peaks = FloatArray(10) { 0.1f }
        peaks[7] = 0.9f
        val bars = downsampleToBars(peaks, 5)
        // 10 peaks / 5 bars → bucket 3 covers peaks[6..7] → picks the 0.9 spike
        assertEquals(0.9f, bars[3], 0.0001f)
        assertEquals(0.1f, bars[0], 0.0001f)
    }

    @Test
    fun `more peaks than bars still covers every bucket with at least one sample`() {
        val peaks = FloatArray(2) { 1f }
        val bars = downsampleToBars(peaks, 72)
        assertEquals(72, bars.size)
        assertTrue(bars.all { it > 0f })
    }
}
