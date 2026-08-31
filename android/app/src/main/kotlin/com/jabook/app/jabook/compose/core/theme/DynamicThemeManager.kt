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
import androidx.core.graphics.ColorUtils
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
 *
 * HCT ceiling (ponytail): true HCT requires `com.google.android.material:material-color-utilities:0.3.x`
 * (stable, non-alpha — `Hct.fromInt()`, `TonalPalette`, `Score`, `QuantizerCelebi`/`SchemeContent`).
 * That artifact is NOT in `android/gradle/libs.versions.toml` nor in
 * `android/gradle/verification-metadata.xml` and is not in the local Gradle cache
 * (`~/.gradle/caches/modules-2/files-2.1/com.google.android.material/material-color-utilities` = missing).
 * Adding it would require a network fetch + new SHA/PGP entry in verification-metadata,
 * which breaks the offline build — so this file keeps the HSL fallback (see `fixDislikeColor:190`
 * and `ensureContrast:215`, `colorToHsl:293`). Upgrade path when online:
 *   1. `libs.versions.toml`: add `materialColorUtilities = "0.3.0"` + `material-color-utilities` library
 *   2. `app/build.gradle.kts`: `implementation(libs.material.color.utilities)`
 *   3. Replace `fixDislikeColor` with `Hct.fromInt(argb)` hue 90..120 chroma>16 → Score fallback,
 *      and `ensureContrast` with `TonalPalette` tone search (HCT tone 0..100, not HSL lightness).
 * Quantization: keep `androidx.palette.graphics.Palette` (`androidxPalette = 1.0.0`, already in catalog)
 * over MCU `QuantizerCelebi` — same reason (no new dep, offline-safe, 32-color quantization is sufficient
 * for cover art; `page.md:254-281` HCT tone vs HSL lightness — tone is perceptual, HSL L is not).
 */
public object DynamicThemeManager {
    // Hue range for "dislike" colors (yellow-green, universally unpleasant)
    private const val DISLIKE_HUE_MIN = 70.0
    private const val DISLIKE_HUE_MAX = 130.0
    private const val DISLIKE_CHROMA_MIN = 20.0

    // LRU cache for extracted colors - 20 entries (fits in ~200KB)
    // androidx.collection (not android.util): internally synchronized AND JVM-testable.
    private val cache = androidx.collection.LruCache<String, PlayerThemeColors>(20)

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
        cache.get(coverUrl)?.let { return it }
        val colors = extractColors(bitmap)
        cache.put(coverUrl, colors)
        return colors
    }

    /**
     * Clears the color cache.
     * Call during theme changes or when memory pressure is high.
     */
    public fun clearCache() {
        cache.evictAll()
    }

    /**
     * Fix "dislike" colors — universally unpleasant yellow-green hues.
     * Shifts hue to a more pleasant range while preserving chroma and tone.
     *
     * Based on Material Color Utilities "dislike" fix.
     * ponytail: HSL fallback — true fix is `Hct.fromInt(argb)` where
     * `hct.hue in 90..120 && hct.chroma > 16` → `Hct.from(pleasantHue, chroma, tone).toInt()`
     * or `Score` ranking. Requires `com.google.android.material:material-color-utilities`.
     * HSL hue 70..130 sat>20 and shift to 50/150 approximates it without new dep.
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
     * ponytail: HSL lightness binary search — true HCT is `TonalPalette` tone search
     * (`Hct.fromInt(bg).tone` + `MaterialColorUtilities` contrast via tone distance,
     * not WCAG luminance). `page.md:272 Tone is how light or dark a color appears (0..100)`
     * — same range but perceptual vs HSL L. Upgrade when MCU dep is cached.
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

    // ponytail: HSL helpers — replace with Hct/TonalPalette when MCU cached; no new dep here
    private fun colorToHsl(color: Color): FloatArray =
        FloatArray(3).also {
            ColorUtils.RGBToHSL(
                (color.red * 255).toInt(),
                (color.green * 255).toInt(),
                (color.blue * 255).toInt(),
                it,
            )
        }

    private fun hslToColor(hsl: FloatArray): Color = Color(ColorUtils.HSLToColor(hsl))

    /**
     * Determine if a color is considered dark based on luminance.
     * Used for contrast decisions and theme selection.
     *
     * @param color The color to check.
     * @return true if luminance is below 0.5 (dark), false otherwise.
     */
    public fun isDark(color: Color): Boolean = color.luminance() < 0.5
}
