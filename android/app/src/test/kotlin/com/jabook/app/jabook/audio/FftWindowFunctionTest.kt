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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class FftWindowFunctionTest {
    // --- Hann window ---

    @Test
    fun `hann window zeros first and last samples`() {
        val samples = FloatArray(64) { 1f }
        FftWindowFunction.applyHann(samples)

        assertEquals(0f, samples[0], 0.001f)
        assertEquals(0f, samples[samples.size - 1], 0.001f)
    }

    @Test
    fun `hann window peaks at center`() {
        val samples = FloatArray(64) { 1f }
        FftWindowFunction.applyHann(samples)

        val center = samples[samples.size / 2]
        assertTrue("Center should be close to 1.0", center > 0.9f)
    }

    @Test
    fun `hann window reduces total energy`() {
        val samples = FloatArray(256) { 1f }
        val beforeEnergy = samples.sum()
        FftWindowFunction.applyHann(samples)
        val afterEnergy = samples.sum().let { if (it < 0) -it else it }

        assertTrue("Windowed energy should be less than original", afterEnergy < beforeEnergy)
    }

    // --- Log bands ---

    @Test
    fun `toLogBands returns correct number of bands`() {
        val fft = FloatArray(512) { (it * 0.1f) }
        val bands = FftWindowFunction.toLogBands(fft, 32)

        assertEquals(32, bands.size)
    }

    @Test
    fun `toLogBands with single peak returns non-zero band`() {
        val fft = FloatArray(512) { 0f }
        fft[100] = 1.0f

        val bands = FftWindowFunction.toLogBands(fft, 16)
        val nonZeroBands = bands.count { it > 0f }

        assertTrue("At least one band should be non-zero", nonZeroBands >= 1)
    }

    @Test
    fun `toLogBands with all zeros returns all zeros`() {
        val fft = FloatArray(512) { 0f }
        val bands = FftWindowFunction.toLogBands(fft, 8)

        assertTrue("All bands should be zero", bands.all { abs(it) < 0.001f })
    }

    @Test
    fun `toLogBands with single element fft returns bands`() {
        val fft = FloatArray(1) { 1f }
        val bands = FftWindowFunction.toLogBands(fft, 4)

        assertEquals(4, bands.size)
    }

    // --- Edge cases: n < 2 ---

    @Test
    fun `hann window with empty array returns empty`() {
        val samples = FloatArray(0)
        val result = FftWindowFunction.applyHann(samples)
        assertEquals(0, result.size)
    }

    @Test
    fun `hann window with single sample returns unchanged`() {
        val samples = floatArrayOf(0.8f)
        val result = FftWindowFunction.applyHann(samples)
        assertEquals(0.8f, result[0], 0.001f)
    }

    @Test
    fun `hann window with two samples zeros edges`() {
        val samples = floatArrayOf(1f, 1f)
        FftWindowFunction.applyHann(samples)
        assertEquals(0f, samples[0], 0.001f)
        assertEquals(0f, samples[1], 0.001f)
    }

    // --- Known coefficients for n=8 ---

    @Test
    fun `hann window n=8 first and last are zero`() {
        val samples = FloatArray(8) { 1f }
        FftWindowFunction.applyHann(samples)
        assertEquals(0f, samples[0], 0.001f)
        assertEquals(0f, samples[7], 0.001f)
    }

    @Test
    fun `hann window n=8 middle is close to one`() {
        val samples = FloatArray(8) { 1f }
        FftWindowFunction.applyHann(samples)
        assertTrue("Middle should be > 0.9", samples[3] > 0.9f || samples[4] > 0.9f)
    }
}
