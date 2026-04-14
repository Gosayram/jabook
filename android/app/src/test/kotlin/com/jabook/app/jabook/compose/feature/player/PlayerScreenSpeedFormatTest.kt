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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerScreenSpeedFormatTest {
    @Test
    fun `formatPlaybackSpeedLabel formats integer speed without decimals`() {
        assertEquals("2x", formatPlaybackSpeedLabel(2.0f))
    }

    @Test
    fun `formatPlaybackSpeedLabel keeps up to two decimals for fractional speed`() {
        val label = formatPlaybackSpeedLabel(1.25f)
        assertTrue(label == "1.25x" || label == "1,25x")
    }

    @Test
    fun `formatPlaybackSpeedLabel trims trailing zeros`() {
        val label = formatPlaybackSpeedLabel(1.50f)
        assertTrue(label == "1.5x" || label == "1,5x")
    }
}
