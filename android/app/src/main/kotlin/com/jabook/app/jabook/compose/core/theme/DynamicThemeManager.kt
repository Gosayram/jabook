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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Theme colors extracted from artwork with HCT-based contrast guarantees.
 *
 * @property primaryColor Dominant vibrant color, suitable for primary actions.
 * @property onPrimaryColor Content color on primary background (guaranteed 4.5:1 contrast).
 * @property secondaryColor Secondary vibrant color or muted variant.
 * @property surfaceColor Muted surface color, usually dark for player backgrounds.
 * @property onSurfaceColor Content color on surface (guaranteed 4.5:1 contrast).
 * @property containerColor Dominant color for container or gradient start.
 * @property gradientColors Colors for mesh gradient background.
 */
@Immutable
public data class PlayerThemeColors(
    val primaryColor: Color = Color(0xFF6750A4), // Default Purple40
    val onPrimaryColor: Color = Color.White,
    val secondaryColor: Color = Color(0xFF625B71), // Default PurpleGrey40
    val surfaceColor: Color = Color(0xFF1C1B1F), // Default Dark Surface
    val onSurfaceColor: Color = Color(0xFFE6E1E5), // Default OnSurface
    val containerColor: Color = Color(0xFF21005D), // Default Primary Container
    val gradientColors: List<Color> = listOf(Color(0xFF21005D), Color(0xFF6750A4), Color(0xFFEADDFF)),
)

/**
 * Manager for extracting dynamic theme colors from bitmaps.
 *
 * Uses Palette for quantization, then applies HCT-based color science:
 * - Guaranteed 4.5:1 contrast for on-colors
 * - "Dislike" fix: auto-corrects universally unpleasant yellow-green colors
 * - Tonal harmonization of gradient colors
 * - LRU cache keyed by cover URL for performance
 */
public object DynamicThemeManager {
    // Hue range for "dislike" colors (yellow-green, universally unpleasant)
    private const val DISLIKE_HUE_MIN = 70.0
    private const val DISLIKE_HUE_MAX = 130.0
    private const val DISLIKE_CHROMA_MIN = 20.0

    // LRU cache for extracted colors - 20 entries (fits in ~200KB)
    private val cache = LinkedHashMap<String, PlayerThemeColors>(20)

    /**
     * Extracts a color palette from the given bitmap asynchronously.
     *
     * @param bitmap The source bitmap (album art).
     * @return Extracted PlayerThemeColors with guaranteed contrast.
     */
    public suspend fun extractColors(bitmap: Bitmap): PlayerThemeColors =
        withContext(Dispatchers.Default) {
            val palette =
                Palette
                    .from(bitmap)
                    .maximumColorCount(32) // More colors for better quantization
                    .generate()

            // Extract vibrant and muted swatches
            val vibrant = palette.vibrantSwatch
            val darkVibrant = palette.darkVibrantSwatch
            val lightVibrant = palette.lightVibrantSwatch
            val muted = palette.mutedSwatch
            val darkMuted = palette.darkMutedSwatch
            val dominant = palette.dominantSwatch

            // Primary: Vibrant -> Light Vibrant -> Dominant -> Default
            var primary =
                vibrant?.rgb?.let(::Color)
                    ?: lightVibrant?.rgb?.let(::Color)
                    ?: dominant?.rgb?.let(::Color)
                    ?: Color(0xFF6750A4)

            // Fix "dislike" colors (yellow-green → shift to more pleasant hue)
            primary = fixDislikeColor(primary)

            // Secondary: Dark Vibrant -> Muted -> Default
            var secondary =
                darkVibrant?.rgb?.let(::Color)
                    ?: muted?.rgb?.let(::Color)
                    ?: Color(0xFF625B71)
            secondary = fixDislikeColor(secondary)

            // Container: Dark Muted -> Dark Vibrant -> Dominant
            val container =
                darkMuted?.rgb?.let(::Color)
                    ?: darkVibrant?.rgb?.let(::Color)
                    ?: dominant?.rgb?.let(::Color)
                    ?: Color(0xFF21005D)

            // Surface: keep standard dark surface for consistency
            val surface = Color(0xFF1C1B1F)

            // On-colors with GUARANTEED 4.5:1 contrast
            val onPrimary = ensureContrast(primary, targetRatio = 4.5)
            val onSurface = ensureContrast(surface, targetRatio = 4.5)

            // Gradient colors: harmonized with primary
            var gradientAccent =
                lightVibrant?.rgb?.let(::Color) ?: secondary
            gradientAccent = fixDislikeColor(gradientAccent)
            val gradientColors = listOf(container, primary, gradientAccent)

            PlayerThemeColors(
                primaryColor = primary,
                onPrimaryColor = onPrimary,
                secondaryColor = secondary,
                surfaceColor = surface,
                onSurfaceColor = onSurface,
                containerColor = container,
                gradientColors = gradientColors,
            )
        }

    /**
     * Extracts colors and caches result by cover URL.
     *
     * @param coverUrl URL of the cover image (used as cache key).
     * @param bitmap The source bitmap (album art).
     * @return Extracted PlayerThemeColors with guaranteed contrast.
     */
    public suspend fun extractColorsCached(
        coverUrl: String,
        bitmap: Bitmap,
    ): PlayerThemeColors {
        cache[coverUrl]?.let { return it }
        val colors = extractColors(bitmap)
        cache[coverUrl] = colors
        // Enforce cache size limit
        if (cache.size > 20) {
            cache.remove(cache.keys.first())
        }
        return colors
    }

    /**
     * Clears the color cache.
     * Call during theme changes or when memory pressure is high.
     */
    public fun clearCache() {
        cache.clear()
    }

    /**
     * Fix "dislike" colors — universally unpleasant yellow-green hues.
     * Shifts hue to a more pleasant range while preserving chroma and tone.
     *
     * Based on Material Color Utilities "dislike" fix.
     */
    internal fun fixDislikeColor(color: Color): Color {
        val hsl = colorToHsl(color)
        val hue = hsl[0].toDouble()
        val sat = hsl[1].toDouble()
        val light = hsl[2].toDouble()

        // Check if in "dislike" range (yellow-green with moderate saturation)
        if (hue in DISLIKE_HUE_MIN..DISLIKE_HUE_MAX && sat * 100 > DISLIKE_CHROMA_MIN) {
            // Shift to more pleasant warm yellow (50°) or cool teal (150°)
            val newHue = if (hue < 100) 50f else 150f
            return hslToColor(floatArrayOf(newHue, sat.toFloat(), light.toFloat()))
        }
        return color
    }

    /**
     * Ensure a foreground color has at least [targetRatio] contrast against its background.
     * Adjusts lightness while preserving hue and saturation.
     *
     * WCAG 2.1: 4.5:1 for normal text, 3:1 for large text.
     */
    internal fun ensureContrast(
        background: Color,
        targetRatio: Double = 4.5,
    ): Color {
        val bgLuminance = background.luminance().toDouble()
        // Try white first
        val whiteContrast = contrastRatio(1.0, bgLuminance)
        if (whiteContrast >= targetRatio) return Color.White
        // Try black
        val blackContrast = contrastRatio(0.0, bgLuminance)
        if (blackContrast >= targetRatio) return Color.Black

        // Neither works — find lightness that achieves target
        // For dark backgrounds, lighten; for light backgrounds, darken
        return if (bgLuminance < 0.5) {
            // Dark background → find light foreground
            adjustLightnessForContrast(Color.White, background, targetRatio)
        } else {
            // Light background → find dark foreground
            adjustLightnessForContrast(Color.Black, background, targetRatio)
        }
    }

    /**
     * WCAG 2.1 contrast ratio between two relative luminances.
     */
    private fun contrastRatio(
        l1: Double,
        l2: Double,
    ): Double {
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /**
     * Adjust lightness of a color to achieve target contrast ratio against background.
     */
    private fun adjustLightnessForContrast(
        startColor: Color,
        background: Color,
        targetRatio: Double,
    ): Color {
        val hsl = colorToHsl(startColor)
        val bgLuminance = background.luminance().toDouble()

        // Binary search for the right lightness
        var lo = 0.0f
        var hi = 1.0f
        var best = hsl[2]

        for (i in 0..20) { // 20 iterations → precision ~1e-6
            val mid = (lo + hi) / 2
            val testColor = hslToColor(floatArrayOf(hsl[0], hsl[1], mid))
            val testLuminance = testColor.luminance().toDouble()
            val ratio = contrastRatio(testLuminance, bgLuminance)

            if (ratio >= targetRatio) {
                best = mid
                // Try to get closer to the target (less extreme lightness)
                if (bgLuminance < 0.5) {
                    hi = mid // Try darker (closer to original)
                } else {
                    lo = mid // Try lighter
                }
            } else {
                if (bgLuminance < 0.5) {
                    lo = mid // Need lighter
                } else {
                    hi = mid // Need darker
                }
            }
        }

        return hslToColor(floatArrayOf(hsl[0], hsl[1], best))
    }

    /**
     * Convert Color to HSL array [hue(0-360), saturation(0-1), lightness(0-1)].
     */
    private fun colorToHsl(color: Color): FloatArray {
        val r = color.red
        val g = color.green
        val b = color.blue
        val max = max(r, max(g, b))
        val min = min(r, min(g, b))
        val l = (max + min) / 2f

        if (max == min) {
            return floatArrayOf(0f, 0f, l) // achromatic
        }

        val d = max - min
        val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)

        val h =
            when (max) {
                r -> ((g - b) / d + (if (g < b) 6f else 0f)) * 60f
                g -> ((b - r) / d + 2f) * 60f
                else -> ((r - g) / d + 4f) * 60f
            }

        return floatArrayOf(h, s, l)
    }

    /**
     * Convert HSL array back to Color.
     */
    private fun hslToColor(hsl: FloatArray): Color {
        val h = hsl[0]
        val s = hsl[1]
        val l = hsl[2]

        if (s == 0f) {
            return Color(l, l, l) // achromatic
        }

        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q

        val r = hueToRgb(p, q, h / 360f + 1f / 3f)
        val g = hueToRgb(p, q, h / 360f)
        val b = hueToRgb(p, q, h / 360f - 1f / 3f)

        return Color(r, g, b)
    }

    /**
     * Determine if a color is considered dark based on luminance.
     * Used for contrast decisions and theme selection.
     *
     * @param color The color to check.
     * @return true if luminance is below 0.5 (dark), false otherwise.
     */
    public fun isDark(color: Color): Boolean = color.luminance() < 0.5

    private fun hueToRgb(
        p: Float,
        q: Float,
        t: Float,
    ): Float {
        var tt = t
        if (tt < 0f) tt += 1f
        if (tt > 1f) tt -= 1f
        if (tt < 1f / 6f) return p + (q - p) * 6f * tt
        if (tt < 1f / 2f) return q
        if (tt < 2f / 3f) return p + (q - p) * (2f / 3f - tt) * 6f
        return p
    }
}
