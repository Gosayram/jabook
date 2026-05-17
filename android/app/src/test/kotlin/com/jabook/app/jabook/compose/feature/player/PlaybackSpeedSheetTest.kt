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

import com.jabook.app.jabook.compose.core.constants.PlaybackSpeedConstants
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [PlaybackSpeedSheet] utility functions.
 *
 * Covers:
 * - addRecentSpeed: maintains max 3 unique recent speeds
 * - dialSpeedForDrag: maps horizontal drag to speed range
 * - dialTickStep: calculates tick positions for haptics
 * - dialSweepAngle: calculates dial visual sweep angle
 */
class PlaybackSpeedSheetTest {
    // --- addRecentSpeed ---

    @Test
    fun `addRecentSpeed adds new speed to empty list`() {
        val recentSpeeds = mutableListOf<Float>()
        addRecentSpeed(recentSpeeds, 1.5f)
        assertEquals(listOf(1.5f), recentSpeeds)
    }

    @Test
    fun `addRecentSpeed adds to front of list`() {
        val recentSpeeds = mutableListOf(1.0f, 1.25f, 1.5f)
        addRecentSpeed(recentSpeeds, 2.0f)
        assertEquals(listOf(2.0f, 1.0f, 1.25f), recentSpeeds)
        assertEquals(3, recentSpeeds.size)
    }

    @Test
    fun `addRecentSpeed removes duplicates and moves to front`() {
        val recentSpeeds = mutableListOf(1.0f, 1.5f, 2.0f)
        addRecentSpeed(recentSpeeds, 1.5f)
        assertEquals(listOf(1.5f, 1.0f, 2.0f), recentSpeeds)
    }

    @Test
    fun `addRecentSpeed trims to max 3 speeds`() {
        val recentSpeeds = mutableListOf(1.0f, 1.25f, 1.5f)
        addRecentSpeed(recentSpeeds, 1.75f)
        assertEquals(listOf(1.75f, 1.0f, 1.25f), recentSpeeds)
        assertEquals(3, recentSpeeds.size)
    }

    @Test
    fun `addRecentSpeed handles floating point precision`() {
        val recentSpeeds = mutableListOf(1.5001f, 1.25f)
        addRecentSpeed(recentSpeeds, 1.5f) // Should be treated as same as 1.5001
        assertEquals(2, recentSpeeds.size) // Removed old, added new
    }

    // --- dialSpeedForDrag ---

    @Test
    fun `dialSpeedForDrag returns current speed when dragDeltaX is zero`() {
        val result = dialSpeedForDrag(1.5f, 0f, 1000f)
        assertEquals(1.5f, result, 0.01f)
    }

    @Test
    fun `dialSpeedForDrag increases speed for positive drag`() {
        val result = dialSpeedForDrag(1.5f, 500f, 1000f)
        // delta = 500/1000 * (2.0 - 0.5) = 0.75, result = 1.5 + 0.75 = 2.25
        // But coerced to max 2.0
        assertEquals(2.0f, result, 0.01f)
    }

    @Test
    fun `dialSpeedForDrag decreases speed for negative drag`() {
        val result = dialSpeedForDrag(1.5f, -250f, 1000f)
        // delta = -250/1000 * 1.5 = -0.375, result = 1.5 - 0.375 = 1.125
        assertEquals(1.125f, result, 0.01f)
    }

    @Test
    fun `dialSpeedForDrag coerces to min speed`() {
        val result = dialSpeedForDrag(0.6f, -1000f, 1000f)
        assertEquals(PlaybackSpeedConstants.MIN_SPEED, result, 0.01f)
    }

    @Test
    fun `dialSpeedForDrag coerces to max speed`() {
        val result = dialSpeedForDrag(1.9f, 10000f, 1000f)
        assertEquals(PlaybackSpeedConstants.MAX_SPEED, result, 0.01f)
    }

    @Test
    fun `dialSpeedForDrag handles invalid dialWidthPx`() {
        assertEquals(1.5f, dialSpeedForDrag(1.5f, 100f, 0f), 0.01f)
        assertEquals(1.5f, dialSpeedForDrag(1.5f, 100f, -100f), 0.01f)
        assertEquals(1.5f, dialSpeedForDrag(1.5f, 100f, Float.NaN), 0.01f)
    }

    // --- dialTickStep ---

    @Test
    fun `dialTickStep calculates correct tick for 0p5x`() {
        val result = dialTickStep(0.5f)
        assertEquals(10, result) // 0.5 / 0.05 = 10
    }

    @Test
    fun `dialTickStep calculates correct tick for 1p0x`() {
        val result = dialTickStep(1.0f)
        assertEquals(20, result) // 1.0 / 0.05 = 20
    }

    @Test
    fun `dialTickStep calculates correct tick for 2p0x`() {
        val result = dialTickStep(2.0f)
        assertEquals(40, result) // 2.0 / 0.05 = 40
    }

    // --- dialSweepAngle ---

    @Test
    fun `dialSweepAngle returns zero for min speed`() {
        val result = dialSweepAngle(PlaybackSpeedConstants.MIN_SPEED)
        assertEquals(0f, result, 0.01f)
    }

    @Test
    fun `dialSweepAngle returns max sweep for max speed`() {
        val result = dialSweepAngle(PlaybackSpeedConstants.MAX_SPEED)
        assertEquals(PlaybackSpeedConstants.DIAL_TOTAL_SWEEP, result, 0.01f)
    }

    @Test
    fun `dialSweepAngle calculates linear sweep`() {
        val result = dialSweepAngle(1.25f)
        // midpoint: (1.25 - 0.5) / (2.0 - 0.5) = 0.5, sweep = 270 * 0.5 = 135
        assertEquals(135f, result, 0.01f)
    }
}
