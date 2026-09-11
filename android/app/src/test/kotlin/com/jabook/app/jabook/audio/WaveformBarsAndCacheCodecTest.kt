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

import com.jabook.app.jabook.compose.feature.player.downsampleToBars
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformBarsAndCacheCodecTest {
    @Test
    fun `downsampleToBars max-pools into bar count`() {
        val peaks = FloatArray(100) { index -> if (index == 55) 1f else 0.1f }
        val bars = downsampleToBars(peaks, bars = 10)

        assertEquals(10, bars.size)
        assertEquals(1f, bars[5], 0.0001f)
        assertEquals(0.1f, bars[0], 0.0001f)
    }

    @Test
    fun `downsampleToBars empty input keeps squiggle fallback`() {
        assertTrue(downsampleToBars(FloatArray(0), bars = 72).isEmpty())
    }

    @Test
    fun `cache codec round-trips peaks`() {
        val peaks = floatArrayOf(0f, 0.25f, 0.5f, 1f)
        val decoded = decodePeaks(encodePeaks(peaks))

        assertEquals(peaks.size, decoded?.size)
        assertEquals(0f, decoded?.get(0) ?: -1f, 0.01f)
        assertEquals(0.25f, decoded?.get(1) ?: -1f, 0.01f)
        assertEquals(0.5f, decoded?.get(2) ?: -1f, 0.01f)
        assertEquals(1f, decoded?.get(3) ?: -1f, 0.01f)
    }

    @Test
    fun `cache codec rejects corruption`() {
        val encoded = encodePeaks(floatArrayOf(0.5f))
        assertNull(decodePeaks(ByteArray(0)))
        assertNull(decodePeaks(encoded.copyOf(encoded.size - 1)))
        val badVersion = encoded.copyOf().also { it[0] = 99 }
        assertNull(decodePeaks(badVersion))
    }
}
