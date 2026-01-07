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

package com.jabook.app.jabook.compose.core.theme

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DynamicThemeManagerTest {

    @Test
    fun `extractColors returns valid colors for a solid bitmap`() = runTest {
        // Create a solid blue bitmap
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(AndroidColor.BLUE)

        val colors = DynamicThemeManager.extractColors(bitmap)

        assertNotNull(colors)
        // Palette should at least pick dominant color
        assertNotNull(colors.primaryColor)
        assertNotNull(colors.onPrimaryColor)
        assertNotNull(colors.containerColor)
    }

    @Test
    fun `isDark correctly identifies dark and light colors`() {
        assertTrue(DynamicThemeManager.isDark(Color.Black))
        assertTrue(DynamicThemeManager.isDark(Color(0xFF21005D))) // Dark Purple
        
        assertFalse(DynamicThemeManager.isDark(Color.White))
        assertFalse(DynamicThemeManager.isDark(Color.Yellow))
        assertFalse(DynamicThemeManager.isDark(Color(0xFFE6E1E5))) // Light Gray
    }

    @Test
    fun `extractColors handles small bitmaps`() = runTest {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(AndroidColor.RED)

        val colors = DynamicThemeManager.extractColors(bitmap)

        assertNotNull(colors)
        // Check that it derived something reasonably close to red (definitely not the default purple)
        assertFalse("Should not use default primary color", colors.primaryColor == Color(0xFF6750A4))
    }
}
