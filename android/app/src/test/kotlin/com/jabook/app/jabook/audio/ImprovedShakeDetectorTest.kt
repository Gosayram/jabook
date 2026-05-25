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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ImprovedShakeDetectorTest {
    private lateinit var shakes: MutableList<Long>

    @Before
    fun setUp() {
        shakes = mutableListOf()
    }

    private fun createDetector(
        threshold: Float = ImprovedShakeDetector.DEFAULT_THRESHOLD,
        minShakeCount: Int = ImprovedShakeDetector.DEFAULT_MIN_SHAKE_COUNT,
        windowMs: Long = ImprovedShakeDetector.DEFAULT_WINDOW_MS,
        debounceMs: Long = ImprovedShakeDetector.DEFAULT_DEBOUNCE_MS,
    ): ImprovedShakeDetector =
        ImprovedShakeDetector(
            threshold = threshold,
            minShakeCount = minShakeCount,
            windowMs = windowMs,
            debounceMs = debounceMs,
            clockMs = { timeMs },
            onShakeDetected = { shakes.add(timeMs) },
        )

    private var timeMs: Long = 0L

    private fun gForceAboveThreshold(threshold: Float = ImprovedShakeDetector.DEFAULT_THRESHOLD): FloatArray {
        val g = threshold + 0.5f
        return floatArrayOf(0f, SENSOR_MANAGER_GRAVITY_EARTH, g * SENSOR_MANAGER_GRAVITY_EARTH)
    }

    private fun gForceBelowThreshold(threshold: Float = ImprovedShakeDetector.DEFAULT_THRESHOLD): FloatArray {
        val g = threshold - 1f
        return floatArrayOf(0f, SENSOR_MANAGER_GRAVITY_EARTH, g.coerceAtLeast(0f) * SENSOR_MANAGER_GRAVITY_EARTH)
    }

    // --- Single shake does not trigger ---

    @Test
    fun `single shake does not trigger`() {
        val detector = createDetector(minShakeCount = 2)
        val (ax, ay, az) = gForceAboveThreshold()
        detector.processAccelerometer(ax, ay, az)
        assertTrue(shakes.isEmpty())
    }

    // --- Two shakes within window triggers ---

    @Test
    fun `two shakes within window triggers`() {
        val detector = createDetector(minShakeCount = 2, windowMs = 1000L)
        val (ax, ay, az) = gForceAboveThreshold()

        timeMs = 100L
        detector.processAccelerometer(ax, ay, az)

        timeMs = 200L
        val result = detector.processAccelerometer(ax, ay, az)

        assertEquals(1, shakes.size)
        assertTrue(result)
    }

    // --- Shakes outside window do not trigger ---

    @Test
    fun `shakes outside window do not trigger`() {
        val detector = createDetector(minShakeCount = 2, windowMs = 100L)
        val (ax, ay, az) = gForceAboveThreshold()

        timeMs = 0L
        detector.processAccelerometer(ax, ay, az)

        timeMs = 200L
        val result = detector.processAccelerometer(ax, ay, az)

        assertFalse(result)
        assertTrue(shakes.isEmpty())
    }

    // --- Below threshold does not register ---

    @Test
    fun `below threshold does not register shake`() {
        val detector = createDetector(minShakeCount = 1)
        val (ax, ay, az) = gForceBelowThreshold()

        val result = detector.processAccelerometer(ax, ay, az)

        assertFalse(result)
        assertTrue(shakes.isEmpty())
    }

    // --- Debounce prevents rapid re-trigger ---

    @Test
    fun `debounce prevents rapid re-trigger`() {
        val detector = createDetector(minShakeCount = 2, windowMs = 10_000L, debounceMs = 5_000L)
        val (ax, ay, az) = gForceAboveThreshold()

        timeMs = 100L
        detector.processAccelerometer(ax, ay, az)
        timeMs = 200L
        val first = detector.processAccelerometer(ax, ay, az)
        assertTrue(first)

        timeMs = 300L
        detector.processAccelerometer(ax, ay, az)
        timeMs = 400L
        val second = detector.processAccelerometer(ax, ay, az)
        assertFalse(second)

        assertEquals(1, shakes.size)
    }

    // --- Debounce allows trigger after cooldown ---

    @Test
    fun `debounce allows trigger after cooldown`() {
        val detector = createDetector(minShakeCount = 2, windowMs = 10_000L, debounceMs = 1000L)
        val (ax, ay, az) = gForceAboveThreshold()

        timeMs = 100L
        detector.processAccelerometer(ax, ay, az)
        timeMs = 200L
        val first = detector.processAccelerometer(ax, ay, az)
        assertTrue(first)

        timeMs = 1_500L
        detector.processAccelerometer(ax, ay, az)
        timeMs = 1_600L
        val second = detector.processAccelerometer(ax, ay, az)
        assertTrue(second)

        assertEquals(2, shakes.size)
    }

    // --- Reset clears state ---

    @Test
    fun `reset clears accumulated shakes`() {
        val detector = createDetector(minShakeCount = 2)
        val (ax, ay, az) = gForceAboveThreshold()

        timeMs = 100L
        detector.processAccelerometer(ax, ay, az)
        detector.reset()

        timeMs = 200L
        val result = detector.processAccelerometer(ax, ay, az)
        assertFalse(result)
    }

    // --- minShakeCount = 1 triggers on first shake ---

    @Test
    fun `minShakeCount 1 triggers on single shake`() {
        val detector = createDetector(minShakeCount = 1)
        val (ax, ay, az) = gForceAboveThreshold()

        timeMs = 100L
        val result = detector.processAccelerometer(ax, ay, az)

        assertTrue(result)
        assertEquals(1, shakes.size)
    }

    companion object {
        private const val SENSOR_MANAGER_GRAVITY_EARTH = 9.80665f
    }
}
