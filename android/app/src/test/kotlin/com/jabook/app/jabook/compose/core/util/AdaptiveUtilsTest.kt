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

package com.jabook.app.jabook.compose.core.util

import android.content.Context
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdaptiveUtilsTest {
    @Test
    fun `resolveWindowSizeClass returns null when input is null`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertNull(AdaptiveUtils.resolveWindowSizeClassOrNull(windowSizeClass = null, context = context))
    }

    @Test
    fun `resolveWindowSizeClass keeps compact width class for compact windows`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val compactWindow = window(widthDp = 360, heightDp = 800)

        val resolved = AdaptiveUtils.resolveWindowSizeClass(compactWindow, context)

        assertEquals(WindowWidthSizeClass.Compact, resolved.widthSizeClass)
    }

    @Test
    fun `nullable adaptive values use compact defaults when window size class is missing`() {
        assertEquals(16.dp, AdaptiveUtils.getContentPaddingOrDefault(windowSizeClass = null))
        assertEquals(12.dp, AdaptiveUtils.getItemSpacingOrDefault(windowSizeClass = null))
        assertEquals(4.dp, AdaptiveUtils.getSmallSpacingOrDefault(windowSizeClass = null))
        assertEquals(12.dp, AdaptiveUtils.getCardPaddingOrDefault(windowSizeClass = null))
    }

    @Test
    fun `nullable adaptive values use window size class when present`() {
        val mediumWindow = window(widthDp = 700, heightDp = 900)

        assertEquals(24.dp, AdaptiveUtils.getContentPaddingOrDefault(mediumWindow))
        assertEquals(16.dp, AdaptiveUtils.getItemSpacingOrDefault(mediumWindow))
        assertEquals(6.dp, AdaptiveUtils.getSmallSpacingOrDefault(mediumWindow))
        assertEquals(16.dp, AdaptiveUtils.getCardPaddingOrDefault(mediumWindow))
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    private fun window(
        widthDp: Int,
        heightDp: Int,
    ): WindowSizeClass = WindowSizeClass.calculateFromSize(DpSize(widthDp.dp, heightDp.dp))
}
