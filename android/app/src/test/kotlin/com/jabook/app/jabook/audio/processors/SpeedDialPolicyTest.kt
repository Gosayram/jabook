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

/**
 * Unit tests for [SpeedDialPolicy].
 *
 * Verifies discrete speed step behavior: snapping, stepping up/down,
 * index mapping, formatting, and boundary conditions.
 */
class SpeedDialPolicyTest {
    // ── Constants ──────────────────────────────────────────────────

    @Test
    fun `MIN_SPEED should be 0,5`() {
        assertEquals(0.5f, SpeedDialPolicy.MIN_SPEED)
    }

    @Test
    fun `MAX_SPEED should be 3,5`() {
        assertEquals(3.5f, SpeedDialPolicy.MAX_SPEED)
    }

    @Test
    fun `STEP should be 0,05`() {
        assertEquals(0.05f, SpeedDialPolicy.STEP)
    }

    @Test
    fun `TOTAL_STEPS should be 61`() {
        // (3.5 - 0.5) / 0.05 + 1 = 61
        assertEquals(61, SpeedDialPolicy.TOTAL_STEPS)
    }

    @Test
    fun `DEFAULT_SPEED should be 1,0`() {
        assertEquals(1.0f, SpeedDialPolicy.DEFAULT_SPEED)
    }

    // ── snapToStep ─────────────────────────────────────────────────

    @Test
    fun `snapToStep returns exact step value unchanged`() {
        assertEquals(1.0f, SpeedDialPolicy.snapToStep(1.0f), EPSILON)
        assertEquals(0.5f, SpeedDialPolicy.snapToStep(0.5f), EPSILON)
        assertEquals(3.5f, SpeedDialPolicy.snapToStep(3.5f), EPSILON)
        assertEquals(1.5f, SpeedDialPolicy.snapToStep(1.5f), EPSILON)
    }

    @Test
    fun `snapToStep rounds to nearest step`() {
        assertEquals(1.0f, SpeedDialPolicy.snapToStep(1.02f), EPSILON)
        assertEquals(1.05f, SpeedDialPolicy.snapToStep(1.03f), EPSILON)
        assertEquals(0.5f, SpeedDialPolicy.snapToStep(0.51f), EPSILON)
        assertEquals(2.0f, SpeedDialPolicy.snapToStep(1.99f), EPSILON)
    }

    @Test
    fun `snapToStep clamps values below MIN_SPEED`() {
        assertEquals(0.5f, SpeedDialPolicy.snapToStep(0.1f), EPSILON)
        assertEquals(0.5f, SpeedDialPolicy.snapToStep(0.0f), EPSILON)
        assertEquals(0.5f, SpeedDialPolicy.snapToStep(-1.0f), EPSILON)
    }

    @Test
    fun `snapToStep clamps values above MAX_SPEED`() {
        assertEquals(3.5f, SpeedDialPolicy.snapToStep(4.0f), EPSILON)
        assertEquals(3.5f, SpeedDialPolicy.snapToStep(100.0f), EPSILON)
    }

    // ── stepUp ─────────────────────────────────────────────────────

    @Test
    fun `stepUp increments by STEP`() {
        assertEquals(1.05f, SpeedDialPolicy.stepUp(1.0f), EPSILON)
        assertEquals(0.55f, SpeedDialPolicy.stepUp(0.5f), EPSILON)
        assertEquals(2.0f, SpeedDialPolicy.stepUp(1.95f), EPSILON)
    }

    @Test
    fun `stepUp stays at MAX_SPEED when already at max`() {
        assertEquals(3.5f, SpeedDialPolicy.stepUp(3.5f), EPSILON)
    }

    @Test
    fun `stepUp from near-max stays at MAX_SPEED`() {
        assertEquals(3.5f, SpeedDialPolicy.stepUp(3.45f), EPSILON)
    }

    // ── stepDown ───────────────────────────────────────────────────

    @Test
    fun `stepDown decrements by STEP`() {
        assertEquals(0.95f, SpeedDialPolicy.stepDown(1.0f), EPSILON)
        assertEquals(3.45f, SpeedDialPolicy.stepDown(3.5f), EPSILON)
        assertEquals(1.0f, SpeedDialPolicy.stepDown(1.05f), EPSILON)
    }

    @Test
    fun `stepDown stays at MIN_SPEED when already at min`() {
        assertEquals(0.5f, SpeedDialPolicy.stepDown(0.5f), EPSILON)
    }

    @Test
    fun `stepDown from near-min stays at MIN_SPEED`() {
        assertEquals(0.5f, SpeedDialPolicy.stepDown(0.55f), EPSILON)
    }

    // ── stepIndex / speedForIndex ──────────────────────────────────

    @Test
    fun `stepIndex of MIN_SPEED is 0`() {
        assertEquals(0, SpeedDialPolicy.stepIndex(0.5f))
    }

    @Test
    fun `stepIndex of MAX_SPEED is TOTAL_STEPS minus 1`() {
        assertEquals(SpeedDialPolicy.TOTAL_STEPS - 1, SpeedDialPolicy.stepIndex(3.5f))
    }

    @Test
    fun `stepIndex of DEFAULT_SPEED is 10`() {
        // (1.0 - 0.5) / 0.05 = 10
        assertEquals(10, SpeedDialPolicy.stepIndex(1.0f))
    }

    @Test
    fun `speedForIndex is inverse of stepIndex`() {
        for (i in 0 until SpeedDialPolicy.TOTAL_STEPS) {
            val speed = SpeedDialPolicy.speedForIndex(i)
            assertEquals(i, SpeedDialPolicy.stepIndex(speed))
        }
    }

    @Test
    fun `speedForIndex clamps out-of-range indices`() {
        assertEquals(0.5f, SpeedDialPolicy.speedForIndex(-1), EPSILON)
        assertEquals(3.5f, SpeedDialPolicy.speedForIndex(100), EPSILON)
    }

    // ── allSteps ───────────────────────────────────────────────────

    @Test
    fun `allSteps has TOTAL_STEPS elements`() {
        assertEquals(SpeedDialPolicy.TOTAL_STEPS, SpeedDialPolicy.allSteps().size)
    }

    @Test
    fun `allSteps starts with MIN_SPEED`() {
        assertEquals(0.5f, SpeedDialPolicy.allSteps().first(), EPSILON)
    }

    @Test
    fun `allSteps ends with MAX_SPEED`() {
        assertEquals(3.5f, SpeedDialPolicy.allSteps().last(), EPSILON)
    }

    @Test
    fun `allSteps has consistent step intervals`() {
        val steps = SpeedDialPolicy.allSteps()
        for (i in 1 until steps.size) {
            val delta = steps[i] - steps[i - 1]
            assertEquals(0.05f, delta, EPSILON)
        }
    }

    // ── formatSpeed ────────────────────────────────────────────────

    @Test
    fun `formatSpeed formats integer speeds`() {
        assertEquals("1x", SpeedDialPolicy.formatSpeed(1.0f))
        assertEquals("2x", SpeedDialPolicy.formatSpeed(2.0f))
    }

    @Test
    fun `formatSpeed formats half speeds`() {
        assertEquals("0.5x", SpeedDialPolicy.formatSpeed(0.5f))
        assertEquals("1.5x", SpeedDialPolicy.formatSpeed(1.5f))
    }

    @Test
    fun `formatSpeed formats fractional speeds`() {
        assertEquals("0.75x", SpeedDialPolicy.formatSpeed(0.75f))
        assertEquals("1.25x", SpeedDialPolicy.formatSpeed(1.25f))
    }

    @Test
    fun `formatSpeed snaps non-step values`() {
        // 1.37 should snap to 1.35
        val formatted = SpeedDialPolicy.formatSpeed(1.37f)
        assertTrue(
            "Expected format to end with 'x' but got: $formatted",
            formatted.endsWith("x"),
        )
    }

    // ── Boundary roundtrip ─────────────────────────────────────────

    @Test
    fun `stepUp then stepDown returns original value`() {
        var speed = 1.0f
        speed = SpeedDialPolicy.stepUp(speed)
        speed = SpeedDialPolicy.stepDown(speed)
        assertEquals(1.0f, speed, EPSILON)
    }

    @Test
    fun `stepDown then stepUp returns original value`() {
        var speed = 2.0f
        speed = SpeedDialPolicy.stepDown(speed)
        speed = SpeedDialPolicy.stepUp(speed)
        assertEquals(2.0f, speed, EPSILON)
    }

    private companion object {
        const val EPSILON = 0.001f
    }
}
