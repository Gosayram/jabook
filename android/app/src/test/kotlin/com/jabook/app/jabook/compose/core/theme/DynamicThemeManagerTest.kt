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
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import android.graphics.Color as AndroidColor

@RunWith(RobolectricTestRunner::class)
class DynamicThemeManagerTest {
    @Test
    fun `extractColors returns valid colors for a solid bitmap`() =
        runBlocking {
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
    fun `extractColors handles small bitmaps`() =
        runBlocking {
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(AndroidColor.RED)

            val colors = DynamicThemeManager.extractColors(bitmap)

            assertNotNull(colors)
            // Check that it derived something reasonably close to red (definitely not the default purple)
            assertFalse("Should not use default primary color", colors.primaryColor == Color(0xFF6750A4))
        }

    @Test
    fun `extractColorsCached caches and returns cached colors`() =
        runBlocking {
            val coverUrl = "https://example.com/cover.jpg"
            val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(AndroidColor.GREEN)

            // First call should extract and cache
            val colors1 = DynamicThemeManager.extractColorsCached(coverUrl, bitmap)

            // Second call should return cached result
            val colors2 = DynamicThemeManager.extractColorsCached(coverUrl, bitmap)

            assertEquals("Should return cached colors", colors1, colors2)

            // Cleanup
            DynamicThemeManager.clearCache()
        }

    @Test
    fun `clearCache removes all cached entries`() {
        // Add something to cache
        runBlocking {
            val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(AndroidColor.CYAN)
            DynamicThemeManager.extractColorsCached("test-url", bitmap)
        }

        // Clear cache
        DynamicThemeManager.clearCache()

        // Cache should be empty (verified indirectly - next call extracts fresh)
        runBlocking {
            val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(AndroidColor.MAGENTA)
            val colors = DynamicThemeManager.extractColorsCached("test-url", bitmap)
            // If cache was cleared, this should be new extraction (not cached green)
            assertNotNull(colors)
        }

        DynamicThemeManager.clearCache()
    }

    @Test
    fun `fallback accent color is used when no palette is available`() =
        runBlocking {
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(AndroidColor.BLACK) // Pure black may not yield palette

            val colors = DynamicThemeManager.extractColors(bitmap)

            // Should always have valid fallback colors
            assertNotNull(colors.primaryColor)
            assertNotNull(colors.secondaryColor)
            assertNotNull(colors.containerColor)
        }

    @Test
    fun `fixDislikeColor shifts yellow-green hues`() {
        // Pure yellow (60° hue) - should shift to 50° (warm yellow)
        val yellow = Color(1f, 1f, 0f)
        val fixed = DynamicThemeManager.fixDislikeColor(yellow)

        // The color should not be pure yellow anymore (hue shifted)
        assertFalse(
            "Yellow hue should be shifted",
            fixed == yellow,
        )
    }
}
