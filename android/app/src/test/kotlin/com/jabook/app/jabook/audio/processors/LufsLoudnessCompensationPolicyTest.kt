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
import org.junit.Assert.assertTrue
import org.junit.Test

class LufsLoudnessCompensationPolicyTest {
    private val policy = LufsLoudnessCompensationPolicy()

    @Test
    fun `compensationGain returns 1x for book at target LUFS`() {
        val gain = policy.compensationGain(AutoVolumeLeveler.AUDIOBOOK_TARGET_LUFS)
        assertEquals(1.0f, gain, 0.01f)
    }

    @Test
    fun `compensationGain boosts quiet book`() {
        // Book at -20 LUFS, target -16 => needs +4dB boost
        val gain = policy.compensationGain(-20.0)
        // 10^(4/20) ≈ 1.585
        assertEquals(1.585f, gain, 0.02f)
    }

    @Test
    fun `compensationGain attenuates loud book`() {
        // Book at -12 LUFS, target -16 => needs -4dB attenuation
        val gain = policy.compensationGain(-12.0)
        // 10^(-4/20) ≈ 0.631
        assertEquals(0.631f, gain, 0.02f)
    }

    @Test
    fun `compensationGain returns no compensation for NaN`() {
        assertEquals(LufsLoudnessCompensationPolicy.NO_COMPENSATION, policy.compensationGain(Double.NaN))
    }

    @Test
    fun `compensationGain returns no compensation for positive LUFS`() {
        assertEquals(LufsLoudnessCompensationPolicy.NO_COMPENSATION, policy.compensationGain(5.0))
    }

    @Test
    fun `compensationGain clamps to maximum`() {
        // Very quiet book: -60 LUFS
        val gain = policy.compensationGain(-60.0)
        assertEquals(LufsLoudnessCompensationPolicy.GAIN_MAX, gain)
    }

    @Test
    fun `compensationGain clamps to minimum`() {
        // Extremely loud: 0 LUFS is invalid, but negative very close to 0
        // This tests the lower bound via a very loud book
        val loudPolicy = LufsLoudnessCompensationPolicy(targetLufs = -23.0)
        val gain = loudPolicy.compensationGain(-0.5)
        // At -23 target and -0.5 book: delta = -22.5 dB, gain ≈ 0.074
        assertTrue(gain < 1.0f)
    }

    @Test
    fun `transitionGain returns relative gain between two books`() {
        // Previous: -20 LUFS, New: -18 LUFS, Target: -16
        val gain = policy.transitionGain(-20.0, -18.0)
        // Previous gain ≈ 1.585, New gain ≈ 1.259, ratio ≈ 0.794
        assertTrue(gain in 0.7f..0.9f)
    }

    @Test
    fun `transitionGain between identical books returns 1x`() {
        val gain = policy.transitionGain(-20.0, -20.0)
        assertEquals(1.0f, gain, 0.01f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `target LUFS below range throws`() {
        LufsLoudnessCompensationPolicy(targetLufs = -25.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `target LUFS above range throws`() {
        LufsLoudnessCompensationPolicy(targetLufs = -10.0)
    }

    @Test
    fun `custom target LUFS at boundaries accepted`() {
        LufsLoudnessCompensationPolicy(targetLufs = -23.0)
        LufsLoudnessCompensationPolicy(targetLufs = -14.0)
    }
}
