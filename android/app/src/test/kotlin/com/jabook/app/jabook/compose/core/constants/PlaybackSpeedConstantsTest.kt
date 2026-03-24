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

package com.jabook.app.jabook.compose.core.constants

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for PlaybackSpeedConstants.
 *
 * Tests speed list generation and formatting functionality.
 */
class PlaybackSpeedConstantsTest {
    @Test
    fun `MIN_SPEED should be 0,5`() {
        assertEquals(0.5f, PlaybackSpeedConstants.MIN_SPEED)
    }

    @Test
    fun `MAX_SPEED should be 2,0`() {
        assertEquals(2.0f, PlaybackSpeedConstants.MAX_SPEED)
    }

    @Test
    fun `SPEED_STEP should be 0,05`() {
        assertEquals(0.05f, PlaybackSpeedConstants.SPEED_STEP)
    }

    @Test
    fun `generateSpeedsList should return list starting with MIN_SPEED`() {
        val speeds = PlaybackSpeedConstants.generateSpeedsList()
        assertEquals(0.5f, speeds.first(), 0.001f)
    }

    @Test
    fun `generateSpeedsList should return list ending with MAX_SPEED`() {
        val speeds = PlaybackSpeedConstants.generateSpeedsList()
        assertEquals(2.0f, speeds.last(), 0.001f)
    }

    @Test
    fun `generateSpeedsList should contain 1,0x speed`() {
        val speeds = PlaybackSpeedConstants.generateSpeedsList()
        assertTrue(speeds.any { kotlin.math.abs(it - 1.0f) < 0.001f })
    }

    @Test
    fun `generateSpeedsList should have correct number of items`() {
        val speeds = PlaybackSpeedConstants.generateSpeedsList()
        // From 0.5 to 2.0 with step 0.05 = (2.0 - 0.5) / 0.05 + 1 = 31 items
        assertEquals(31, speeds.size)
    }

    @Test
    fun `SLIDER_STEPS should be correct`() {
        // (MAX - MIN) / STEP - 1 = (2.0 - 0.5) / 0.05 - 1 = 30 - 1 = 29
        assertEquals(29, PlaybackSpeedConstants.SLIDER_STEPS)
    }

    @Test
    fun `formatSpeed should format 1,0 correctly`() {
        val formatted = PlaybackSpeedConstants.formatSpeed(1.0f)
        // Accept both 1.00x and 1,00x (locale-dependent)
        assertTrue(formatted.matches(Regex("1[.,]00x")))
    }

    @Test
    fun `formatSpeed should format 1,5 correctly`() {
        val formatted = PlaybackSpeedConstants.formatSpeed(1.5f)
        assertTrue(formatted.matches(Regex("1[.,]50x")))
    }

    @Test
    fun `formatSpeed should format 0,75 correctly`() {
        val formatted = PlaybackSpeedConstants.formatSpeed(0.75f)
        assertTrue(formatted.matches(Regex("0[.,]75x")))
    }

    @Test
    fun `formatSpeed should format 2,0 correctly`() {
        val formatted = PlaybackSpeedConstants.formatSpeed(2.0f)
        assertTrue(formatted.matches(Regex("2[.,]00x")))
    }

    @Test
    fun `generateSpeedsList should have consistent step intervals`() {
        val speeds = PlaybackSpeedConstants.generateSpeedsList()
        for (i in 1 until speeds.size) {
            val step = speeds[i] - speeds[i - 1]
            assertEquals(0.05f, step, 0.001f)
        }
    }
}
