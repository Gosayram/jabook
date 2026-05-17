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

class PlaybackSpeedDialPolicyTest {
    @Test
    fun dialSpeedForDrag_mapsDragDeltaToSpeedAndClampsRange() {
        val same = dialSpeedForDrag(currentSpeed = 1.0f, dragDeltaX = 0f, dialWidthPx = 200f)
        val faster = dialSpeedForDrag(currentSpeed = 1.0f, dragDeltaX = 100f, dialWidthPx = 200f)
        val maxed = dialSpeedForDrag(currentSpeed = 2.9f, dragDeltaX = 500f, dialWidthPx = 200f)
        val mined = dialSpeedForDrag(currentSpeed = 0.7f, dragDeltaX = -500f, dialWidthPx = 200f)

        assertEquals(1.0f, same, 0.0001f)
        // speedSpan = MAX-MIN = 1.5f; delta = (100/200)*1.5 = 0.75; result = 1.0+0.75 = 1.75f
        assertEquals(1.75f, faster, 0.0001f)
        assertEquals(PlaybackSpeedConstants.MAX_SPEED, maxed, 0.0001f)
        assertEquals(PlaybackSpeedConstants.MIN_SPEED, mined, 0.0001f)
    }

    @Test
    fun dialSpeedForDrag_withInvalidWidthReturnsClampedCurrent() {
        val zeroWidth = dialSpeedForDrag(currentSpeed = 4.0f, dragDeltaX = 50f, dialWidthPx = 0f)
        val nanWidth = dialSpeedForDrag(currentSpeed = -1.0f, dragDeltaX = 50f, dialWidthPx = Float.NaN)

        assertEquals(PlaybackSpeedConstants.MAX_SPEED, zeroWidth, 0.0001f)
        assertEquals(PlaybackSpeedConstants.MIN_SPEED, nanWidth, 0.0001f)
    }

    @Test
    fun dialSweepAngle_mapsSpeedToExpectedArcRange() {
        assertEquals(0f, dialSweepAngle(PlaybackSpeedConstants.MIN_SPEED), 0.0001f)
        assertEquals(270f, dialSweepAngle(PlaybackSpeedConstants.MAX_SPEED), 0.0001f)
        // speedSpan = 1.5f; (1.75-0.5)/1.5 * 270 = 225f
        assertEquals(225f, dialSweepAngle(1.75f), 0.0001f)
    }

    @Test
    fun dialTickStep_usesQuarterStepBucket() {
        assertEquals(10, dialTickStep(0.50f))
        assertEquals(25, dialTickStep(1.25f))
        assertEquals(40, dialTickStep(2.0f))
    }
}
