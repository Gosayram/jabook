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

package com.jabook.app.jabook.audio.processors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LufsRmsEstimatorTest {
    @Test
    fun `estimateLufs returns null for insufficient samples`() {
        val smallSamples = FloatArray(100) { 0.3f }
        assertNull(LufsRmsEstimator.estimateLufs(smallSamples))
    }

    @Test
    fun `estimateLufs returns null for silence`() {
        val silentSamples = FloatArray(2048) { 0.0f }
        assertNull(LufsRmsEstimator.estimateLufs(silentSamples))
    }

    @Test
    fun `estimateLufs returns reasonable LUFS for speech-level audio`() {
        // RMS ≈ 0.3, which is typical for speech
        val samples = FloatArray(2048) { 0.3f }
        val lufs = LufsRmsEstimator.estimateLufs(samples)
        assertNotNull(lufs)
        // 20*log10(0.3) ≈ -10.46, minus offset 0.691 ≈ -11.15
        assertEquals(-11.15, lufs!!, 1.0)
    }

    @Test
    fun `estimateLufs returns lower LUFS for quiet audio`() {
        val quiet = FloatArray(2048) { 0.05f }
        val lufsQuiet = LufsRmsEstimator.estimateLufs(quiet)
        assertNotNull(lufsQuiet)

        val loud = FloatArray(2048) { 0.5f }
        val lufsLoud = LufsRmsEstimator.estimateLufs(loud)
        assertNotNull(lufsLoud)

        assert(lufsQuiet!! < lufsLoud!!)
    }

    @Test
    fun `estimateLufsFromPcm16 returns null for zero channels`() {
        assertNull(LufsRmsEstimator.estimateLufsFromPcm16(ShortArray(2048), 0))
    }

    @Test
    fun `estimateLufsFromPcm16 returns null for insufficient data`() {
        assertNull(LufsRmsEstimator.estimateLufsFromPcm16(ShortArray(10), 1))
    }

    @Test
    fun `estimateLufsFromPcm16 handles stereo`() {
        val pcm = ShortArray(4096) { (it % 1000).toShort() }
        val lufs = LufsRmsEstimator.estimateLufsFromPcm16(pcm, 2)
        assertNotNull(lufs)
    }

    @Test
    fun `integrateEstimates returns null for empty list`() {
        assertNull(LufsRmsEstimator.integrateEstimates(emptyList()))
    }

    @Test
    fun `integrateEstimates returns null for list of NaN values`() {
        assertNull(LufsRmsEstimator.integrateEstimates(listOf(Double.NaN, Double.NaN)))
    }

    @Test
    fun `integrateEstimates returns single estimate unchanged`() {
        val lufs = LufsRmsEstimator.integrateEstimates(listOf(-18.0))
        assertEquals(-18.0, lufs!!, 0.5)
    }

    @Test
    fun `integrateEstimates averages multiple estimates`() {
        val estimates = listOf(-16.0, -18.0, -20.0)
        val integrated = LufsRmsEstimator.integrateEstimates(estimates)
        assertNotNull(integrated)
        // Should be somewhere around -18 (energy-weighted)
        assert(integrated!! in -21.0..-15.0)
    }
}
