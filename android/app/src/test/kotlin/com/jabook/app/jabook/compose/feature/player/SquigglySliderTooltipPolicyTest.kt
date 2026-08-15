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

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

public class SquigglySliderTooltipPolicyTest {
    @Test
    public fun `clampSliderTooltipOffset keeps tooltip within left and right bounds`() {
        assertEquals(
            0.dp,
            clampSliderTooltipOffset(
                xOffsetDp = 4.dp,
                sliderWidthDp = 200.dp,
            ),
        )
        assertEquals(
            68.dp,
            clampSliderTooltipOffset(
                xOffsetDp = 96.dp,
                sliderWidthDp = 200.dp,
            ),
        )
        assertEquals(
            144.dp,
            clampSliderTooltipOffset(
                xOffsetDp = 300.dp,
                sliderWidthDp = 200.dp,
            ),
        )
    }

    @Test
    public fun `clampSliderTooltipOffset handles narrow slider widths`() {
        assertEquals(
            0.dp,
            clampSliderTooltipOffset(
                xOffsetDp = 50.dp,
                sliderWidthDp = 40.dp,
                tooltipWidthDp = 56.dp,
            ),
        )
    }

    @Test
    public fun `clampSliderTooltipOffset returns zero for 0 percent position`() {
        val sliderWidth = 200.dp
        val tooltipWidth = 56.dp
        val thumbRadius = 12.dp
        val zeroPercentOffset = thumbRadius
        assertEquals(
            0.dp,
            clampSliderTooltipOffset(
                xOffsetDp = zeroPercentOffset,
                sliderWidthDp = sliderWidth,
                tooltipWidthDp = tooltipWidth,
            ),
        )
    }

    @Test
    public fun `clampSliderTooltipOffset returns max offset for 100 percent position`() {
        val sliderWidth = 200.dp
        val tooltipWidth = 56.dp
        val thumbRadius = 12.dp
        val hundredPercentOffset = sliderWidth - thumbRadius
        val expectedMax = (sliderWidth - tooltipWidth).coerceAtLeast(0.dp)
        assertEquals(
            expectedMax,
            clampSliderTooltipOffset(
                xOffsetDp = hundredPercentOffset,
                sliderWidthDp = sliderWidth,
                tooltipWidthDp = tooltipWidth,
            ),
        )
    }

    @Test
    public fun `clampSliderTooltipOffset handles very wide tooltips`() {
        val sliderWidth = 100.dp
        val tooltipWidth = 80.dp
        assertEquals(
            10.dp,
            clampSliderTooltipOffset(
                xOffsetDp = 50.dp,
                sliderWidthDp = sliderWidth,
                tooltipWidthDp = tooltipWidth,
            ),
        )
    }

    @Test
    public fun `clampSliderTooltipOffset handles very narrow sliders`() {
        val sliderWidth = 40.dp
        val tooltipWidth = 56.dp
        assertEquals(
            0.dp,
            clampSliderTooltipOffset(
                xOffsetDp = 20.dp,
                sliderWidthDp = sliderWidth,
            ),
        )
    }

    @Test
    public fun `clampSliderTooltipOffset handles negative offset by clamping to zero`() {
        assertEquals(
            0.dp,
            clampSliderTooltipOffset(
                xOffsetDp = (-10).dp,
                sliderWidthDp = 200.dp,
            ),
        )
    }

    @Test
    public fun `clampSliderTooltipOffset clamps to max when offset exceeds bounds`() {
        val sliderWidth = 200.dp
        val tooltipWidth = 56.dp
        val maxOffset = (sliderWidth - tooltipWidth).coerceAtLeast(0.dp)
        assertEquals(
            maxOffset,
            clampSliderTooltipOffset(
                xOffsetDp = 500.dp,
                sliderWidthDp = sliderWidth,
                tooltipWidthDp = tooltipWidth,
            ),
        )
    }
}
