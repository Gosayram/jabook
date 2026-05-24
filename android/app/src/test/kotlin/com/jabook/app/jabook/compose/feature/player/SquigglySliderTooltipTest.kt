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

/** Unit tests for clampSliderTooltipOffset function. */
class SquigglySliderTooltipTest {
    @Test
    fun `clampSliderTooltipOffset clamps to zero at fraction zero`() {
        val result = clampSliderTooltipOffset(xOffsetDp = 0.dp, sliderWidthDp = 100.dp, tooltipWidthDp = 56.dp)
        assertEquals(0f, result.value, 0.01f)
    }

    @Test
    fun `clampSliderTooltipOffset clamps to zero when tooltip larger than slider`() {
        val result = clampSliderTooltipOffset(xOffsetDp = 10.dp, sliderWidthDp = 50.dp, tooltipWidthDp = 60.dp)
        assertEquals(0f, result.value, 0.01f)
    }

    @Test
    fun `clampSliderTooltipOffset clamps to max at fraction one`() {
        val result = clampSliderTooltipOffset(xOffsetDp = 100.dp, sliderWidthDp = 100.dp, tooltipWidthDp = 56.dp)
        assertEquals(44f, result.value, 0.01f)
    }

    @Test
    fun `clampSliderTooltipOffset center position`() {
        val result = clampSliderTooltipOffset(xOffsetDp = 50.dp, sliderWidthDp = 100.dp, tooltipWidthDp = 56.dp)
        assertEquals(22f, result.value, 0.01f)
    }

    @Test
    fun `clampSliderTooltipOffset with small tooltip`() {
        val result = clampSliderTooltipOffset(xOffsetDp = 50.dp, sliderWidthDp = 100.dp, tooltipWidthDp = 30.dp)
        assertEquals(35f, result.value, 0.01f)
    }

    @Test
    fun `clampSliderTooltipOffset near end boundary`() {
        val result = clampSliderTooltipOffset(xOffsetDp = 95.dp, sliderWidthDp = 100.dp, tooltipWidthDp = 56.dp)
        assertEquals(44f, result.value, 0.01f)
    }

    @Test
    fun `clampSliderTooltipOffset with default tooltip width`() {
        val result = clampSliderTooltipOffset(xOffsetDp = 50.dp, sliderWidthDp = 100.dp)
        assertEquals(22f, result.value, 0.01f)
    }
}
