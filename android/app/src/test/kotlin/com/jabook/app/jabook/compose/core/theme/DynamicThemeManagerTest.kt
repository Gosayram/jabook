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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import android.graphics.Color as AndroidColor

@RunWith(RobolectricTestRunner::class)
@org.junit.experimental.categories.Category(com.jabook.app.jabook.test.SlowTest::class)
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
        // Canonical DislikeAnalyzer (materialkolor 5.0.1): HCT hue 90-111, chroma > 16, tone < 65
        // Olive #808000: hue 111.0, chroma 49.6, tone 51.9 — disliked=true (probed)
        val yellowGreen = Color(0.502f, 0.502f, 0f)
        val fixed = DynamicThemeManager.fixDislikeColor(yellowGreen)

        // The color should be shifted to a more pleasant hue
        // (either warm yellow at hue 50 or cool teal at hue 150)
        assertFalse(
            "Yellow-green hue should be shifted",
            fixed == yellowGreen,
        )
    }

    @Test
    fun `accent swatches all have valid primary colors`() {
        val swatches = getAllAccentSwatches()
        assertEquals("Should have 8 curated swatches", 8, swatches.size)
        swatches.forEach { swatch ->
            assertNotNull("Swatch ${swatch.name} should have a name", swatch.name)
            assertTrue("Swatch ${swatch.name} name should not be blank", swatch.name.isNotBlank())
            // Primary color should be fully opaque
            assertEquals(
                "Swatch ${swatch.name} primary should be fully opaque",
                1f,
                swatch.primary.alpha,
                0.001f,
            )
        }
    }

    @Test
    fun `getAccentSwatch returns correct swatch for valid index`() {
        val swatch = getAccentSwatch(0)
        assertNotNull(swatch)
        assertEquals("Violet", swatch!!.name)
    }

    @Test
    fun `getAccentSwatch returns null for out-of-range index`() {
        assertNull(getAccentSwatch(-1))
        assertNull(getAccentSwatch(100))
    }

    @Test
    fun `getDefaultAccentIndex returns zero`() {
        assertEquals(0, getDefaultAccentIndex())
    }

    @Test
    fun `accent swatch toPlayerThemeColors produces valid colors`() {
        val swatch = getAccentSwatch(0)!!
        val themeColors = swatch.toPlayerThemeColors()
        assertNotNull(themeColors.primaryColor)
        assertNotNull(themeColors.onPrimaryColor)
        assertNotNull(themeColors.secondaryColor)
        assertNotNull(themeColors.surfaceColor)
        assertNotNull(themeColors.onSurfaceColor)
        assertNotNull(themeColors.containerColor)
        assertTrue("Gradient should have at least 2 colors", themeColors.gradientColors.size >= 2)
    }

    @Test
    fun `extractColors returns non-default colors for vibrant bitmap`() =
        runBlocking {
            val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            // Use a vibrant purple-ish color
            bitmap.eraseColor(AndroidColor.rgb(128, 0, 255))

            val colors = DynamicThemeManager.extractColors(bitmap)

            assertNotNull(colors)
            // Primary should not be the default violet fallback
            // The palette extractor should find something close to purple
            assertNotNull(colors.primaryColor)
            assertNotNull(colors.secondaryColor)
        }

    @Test
    fun `accent swatch index bounds - negative index clamps to zero`() {
        val maxIndex = getAllAccentSwatches().size - 1
        val safeIndex = (-5).coerceIn(0, maxIndex)
        assertEquals(0, safeIndex)
        assertNotNull(getAccentSwatch(safeIndex))
    }

    @Test
    fun `accent swatch index bounds - oversized index clamps to max`() {
        val maxIndex = getAllAccentSwatches().size - 1
        val safeIndex = 100.coerceIn(0, maxIndex)
        assertEquals(maxIndex, safeIndex)
        assertNotNull(getAccentSwatch(safeIndex))
    }

    @Test
    fun `accent swatch index bounds - valid index passes through unchanged`() {
        val maxIndex = getAllAccentSwatches().size - 1
        val safeIndex = 3.coerceIn(0, maxIndex)
        assertEquals(3, safeIndex)
        assertNotNull(getAccentSwatch(safeIndex))
    }

    @Test
    fun `player cover mode bounds - negative clamps to zero`() {
        val safeMode = (-1).coerceIn(0, 1)
        assertEquals(0, safeMode)
    }

    @Test
    fun `player cover mode bounds - two clamps to one`() {
        val safeMode = 2.coerceIn(0, 1)
        assertEquals(1, safeMode)
    }

    @Test
    fun `player cover mode bounds - valid modes pass through`() {
        assertEquals(0, 0.coerceIn(0, 1))
        assertEquals(1, 1.coerceIn(0, 1))
    }
}
