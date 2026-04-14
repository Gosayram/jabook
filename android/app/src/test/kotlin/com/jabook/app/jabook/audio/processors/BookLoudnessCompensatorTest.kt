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
import org.junit.Before
import org.junit.Test

/** Unit tests for [BookLoudnessCompensator]. */
class BookLoudnessCompensatorTest {
    private lateinit var compensator: BookLoudnessCompensator

    @Before
    fun setUp() {
        compensator = BookLoudnessCompensator()
    }

    // --- computeBookGain ---

    @Test
    fun computeBookGain_nullLufs_returnsNoGain() {
        assertEquals(BookLoudnessCompensator.NO_GAIN, compensator.computeBookGain(null))
    }

    @Test
    fun computeBookGain_nanLufs_returnsNoGain() {
        assertEquals(BookLoudnessCompensator.NO_GAIN, compensator.computeBookGain(Double.NaN))
    }

    @Test
    fun computeBookGain_positiveLufs_returnsNoGain() {
        assertEquals(BookLoudnessCompensator.NO_GAIN, compensator.computeBookGain(5.0))
    }

    @Test
    fun computeBookGain_zeroLufs_returnsNoGain() {
        assertEquals(BookLoudnessCompensator.NO_GAIN, compensator.computeBookGain(0.0))
    }

    @Test
    fun computeBookGain_quietBook_getsBoost() {
        val gain = compensator.computeBookGain(-20.0)
        assertTrue("Quiet book should get gain boost, got $gain", gain > 1.0f)
    }

    @Test
    fun computeBookGain_loudBook_getsAttenuation() {
        val gain = compensator.computeBookGain(-12.0)
        assertTrue("Loud book should get attenuation, got $gain", gain < 1.0f)
    }

    @Test
    fun computeBookGain_bookAtTarget_returnsNoGain() {
        // Default target is -16 LUFS
        val gain = compensator.computeBookGain(-16.0)
        assertEquals(1.0f, gain, 0.01f)
    }

    @Test
    fun computeBookGain_extremeValues_withinGainRange() {
        val extremeQuiet = compensator.computeBookGain(-40.0)
        val extremeLoud = compensator.computeBookGain(-5.0)
        assertTrue(extremeQuiet in BookLoudnessCompensator.GAIN_RANGE)
        assertTrue(extremeLoud in BookLoudnessCompensator.GAIN_RANGE)
    }

    @Test
    fun computeBookGain_validNegative_matchesPolicyCompensationGain() {
        val policy = LufsLoudnessCompensationPolicy()
        val lufs = -19.0

        val expected = policy.compensationGain(lufs)
        val actual = compensator.computeBookGain(lufs)

        assertEquals(expected, actual, 0.0001f)
    }

    // --- computeTransitionGain ---

    @Test
    fun computeTransitionGain_previousNull_returnsNoGain() {
        assertEquals(BookLoudnessCompensator.NO_GAIN, compensator.computeTransitionGain(null, -20.0))
    }

    @Test
    fun computeTransitionGain_newNull_returnsNoGain() {
        assertEquals(BookLoudnessCompensator.NO_GAIN, compensator.computeTransitionGain(-20.0, null))
    }

    @Test
    fun computeTransitionGain_bothNull_returnsNoGain() {
        assertEquals(BookLoudnessCompensator.NO_GAIN, compensator.computeTransitionGain(null, null))
    }

    @Test
    fun computeTransitionGain_previousNan_returnsNoGain() {
        assertEquals(BookLoudnessCompensator.NO_GAIN, compensator.computeTransitionGain(Double.NaN, -20.0))
    }

    @Test
    fun computeTransitionGain_newNan_returnsNoGain() {
        assertEquals(BookLoudnessCompensator.NO_GAIN, compensator.computeTransitionGain(-20.0, Double.NaN))
    }

    @Test
    fun computeTransitionGain_previousPositive_returnsNoGain() {
        assertEquals(BookLoudnessCompensator.NO_GAIN, compensator.computeTransitionGain(5.0, -20.0))
    }

    @Test
    fun computeTransitionGain_newPositive_returnsNoGain() {
        assertEquals(BookLoudnessCompensator.NO_GAIN, compensator.computeTransitionGain(-20.0, 5.0))
    }

    @Test
    fun computeTransitionGain_sameLufs_returnsNoGain() {
        assertEquals(1.0f, compensator.computeTransitionGain(-18.0, -18.0), 0.01f)
    }

    @Test
    fun computeTransitionGain_toQuieterBook_getsBoost() {
        val gain = compensator.computeTransitionGain(-16.0, -20.0)
        assertTrue("Switching to quieter book should boost, got $gain", gain > 1.0f)
    }

    @Test
    fun computeTransitionGain_toLouderBook_getsAttenuation() {
        val gain = compensator.computeTransitionGain(-20.0, -16.0)
        assertTrue("Switching to louder book should attenuate, got $gain", gain < 1.0f)
    }

    @Test
    fun computeTransitionGain_extremeTransition_withinGainRange() {
        val gain = compensator.computeTransitionGain(-10.0, -30.0)
        assertTrue(gain in BookLoudnessCompensator.GAIN_RANGE)
    }

    @Test
    fun computeTransitionGain_validNegativeValues_matchesPolicyTransitionGain() {
        val policy = LufsLoudnessCompensationPolicy()
        val prev = -22.0
        val next = -17.0

        val expected = policy.transitionGain(prev, next)
        val actual = compensator.computeTransitionGain(prev, next)

        assertEquals(expected, actual, 0.0001f)
    }

    @Test
    fun computeTransitionGain_roundtrip_cancelsOut() {
        val forward = compensator.computeTransitionGain(-20.0, -14.0)
        val backward = compensator.computeTransitionGain(-14.0, -20.0)
        assertEquals(1.0f, forward * backward, 0.1f)
    }
}
