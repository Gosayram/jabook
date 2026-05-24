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

class PlaybackSpeedSheetPolicyTest {
    @Test
    fun addRecentSpeed_deduplicatesWithinTolerance() {
        val recent = mutableListOf(1.0f, 1.25f, 1.5f)

        addRecentSpeed(recent, 1.249f)

        assertEquals(listOf(1.249f, 1.0f, 1.5f), recent)
    }

    @Test
    fun addRecentSpeed_insertsAtFrontAndCapsAtThree() {
        val recent = mutableListOf(1.0f, 1.25f, 1.5f)

        addRecentSpeed(recent, 2.0f)

        assertEquals(listOf(2.0f, 1.0f, 1.25f), recent)
    }

    @Test
    fun addRecentSpeed_existingValueMovesToFront() {
        val recent = mutableListOf(0.75f, 1.0f, 1.25f)

        addRecentSpeed(recent, 1.0f)

        assertEquals(listOf(1.0f, 0.75f, 1.25f), recent)
    }

    @Test
    fun addRecentSpeed_keepsDistinctValuesOutsideTolerance() {
        val recent = mutableListOf(1.0f, 1.25f, 1.5f)

        addRecentSpeed(recent, 1.261f)

        assertEquals(listOf(1.261f, 1.0f, 1.25f), recent)
    }

    @Test
    fun addRecentSpeed_whenListFullDropsOldestAfterInsert() {
        val recent = mutableListOf(0.8f, 1.0f, 1.2f)

        addRecentSpeed(recent, 1.4f)

        assertEquals(listOf(1.4f, 0.8f, 1.0f), recent)
    }

    // --- dialSpeedForDrag tests ---

    @Test
    fun dialSpeedForDrag_clampsToMinAndMaxSpeed() {
        val resultMin = dialSpeedForDrag(0.5f, -100f, 200f)
        val resultMax = dialSpeedForDrag(3.0f, 100f, 200f)

        assertEquals(PlaybackSpeedConstants.MIN_SPEED, resultMin, 0.01f)
        assertEquals(PlaybackSpeedConstants.MAX_SPEED, resultMax, 0.01f)
    }

    @Test
    fun dialSpeedForDrag_returnsCurrentSpeedWhenInvalidWidth() {
        val result = dialSpeedForDrag(1.5f, 0f, 0f)

        assertEquals(1.5f, result, 0.01f)
    }

    @Test
    fun dialSpeedForDrag_returnsCurrentSpeedWhenNegativeWidth() {
        val result = dialSpeedForDrag(1.5f, 50f, -100f)

        assertEquals(1.5f, result, 0.01f)
    }

    @Test
    fun dialSpeedForDrag_mapsDragDeltaToSpeedChange() {
        val dialWidth = 200f
        val speedSpan = PlaybackSpeedConstants.MAX_SPEED - PlaybackSpeedConstants.MIN_SPEED

        val dragHalf = dialWidth / 2
        val result = dialSpeedForDrag(1.0f, dragHalf, dialWidth)

        assertEquals(1.0f + speedSpan / 2, result, 0.01f)
    }

    @Test
    fun dialSpeedForDrag_fullWidthDragReachesMaxSpeed() {
        val dialWidth = 200f
        val result = dialSpeedForDrag(1.0f, dialWidth, dialWidth)

        assertEquals(PlaybackSpeedConstants.MAX_SPEED, result, 0.01f)
    }

    // --- dialSweepAngle tests ---

    @Test
    fun dialSweepAngle_returnsZeroAtMinSpeed() {
        val result = dialSweepAngle(PlaybackSpeedConstants.MIN_SPEED)

        assertEquals(0f, result, 0.01f)
    }

    @Test
    fun dialSweepAngle_returnsFullSweepAtMaxSpeed() {
        val result = dialSweepAngle(PlaybackSpeedConstants.MAX_SPEED)

        assertEquals(PlaybackSpeedConstants.DIAL_TOTAL_SWEEP, result, 0.01f)
    }

    @Test
    fun dialSweepAngle_returnsHalfSweepAtMidSpeed() {
        val midSpeed = (PlaybackSpeedConstants.MIN_SPEED + PlaybackSpeedConstants.MAX_SPEED) / 2
        val result = dialSweepAngle(midSpeed)

        assertEquals(PlaybackSpeedConstants.DIAL_TOTAL_SWEEP / 2, result, 0.01f)
    }

    // --- formatSpeedDisplay tests ---

    @Test
    fun formatSpeedDisplay_formatsIntegerSpeedWithoutDecimals() {
        assertEquals("1x", formatSpeedDisplay(1.0f))
        assertEquals("2x", formatSpeedDisplay(2.0f))
    }

    @Test
    fun formatSpeedDisplay_formatsDecimalSpeedWithTwoDecimals() {
        assertEquals("1.25x", formatSpeedDisplay(1.25f))
        assertEquals("0.50x", formatSpeedDisplay(0.5f))
    }

    // --- formatSpeedChip tests ---

    @Test
    fun formatSpeedChip_formatsIntegerSpeedWithoutDecimals() {
        assertEquals("1x", formatSpeedChip(1.0f))
        assertEquals("2x", formatSpeedChip(2.0f))
    }

    @Test
    fun formatSpeedChip_formatsOneDecimalWhenHundredthsZero() {
        // 1.50 * 100 = 150.0, 150 % 10 = 0, so formats with %.1fx
        assertEquals("1.5x", formatSpeedChip(1.50f))
    }

    @Test
    fun formatSpeedChip_formatsTwoDecimalsForOtherValues() {
        assertEquals("1.25x", formatSpeedChip(1.25f))
        assertEquals("0.55x", formatSpeedChip(0.55f))
    }
}
