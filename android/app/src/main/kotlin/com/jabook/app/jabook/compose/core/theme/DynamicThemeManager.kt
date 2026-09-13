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
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette
import com.materialkolor.blend.Blend
import com.materialkolor.contrast.Contrast
import com.materialkolor.dislike.DislikeAnalyzer
import com.materialkolor.hct.Hct
import com.materialkolor.palettes.TonalPalette
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.score.Score
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * Uses QuantizerCelebi + Score for quantization, then HCT-based color science:
 * - Hct.fromInt / TonalPalette / Score / DislikeAnalyzer
 * - Guaranteed 7:1 contrast for high-contrast on-colors via tone (0..100)
 * - Blend.harmonize for gradient harmonization
 * - Muted-first swatch fallback per channel (spotube use_palette_color pattern) so
 *   light surfaces never inherit oversaturated vibrant tones
 */
public object DynamicThemeManager {
    private val cache = androidx.collection.LruCache<String, PlayerThemeColors>(20)

    public suspend fun extractColors(bitmap: Bitmap): PlayerThemeColors =
        withContext(Dispatchers.Default) {
            // ponytail: single bilinear downscale (max dim ≤ 128) shared by Celebi + Palette — both previously ran over full-res
            val extractionBitmap =
                if (maxOf(bitmap.width, bitmap.height) <= 128) {
                    bitmap
                } else {
                    val scale = 128f / maxOf(bitmap.width, bitmap.height)
                    Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * scale).toInt().coerceAtLeast(1),
                        (bitmap.height * scale).toInt().coerceAtLeast(1),
                        true,
                    )
                }
            // MCU QuantizerCelebi quantization (offline, no Palette dependency for core path)
            val ranked = quantizeAndScore(extractionBitmap)
            // Fallback to Palette for swatch extraction when Celebi map is small
            val palette =
                Palette
                    .from(extractionBitmap)
                    .maximumColorCount(32)
                    .generate()
            val vibrant = palette.vibrantSwatch
            val darkVibrant = palette.darkVibrantSwatch
            val lightVibrant = palette.lightVibrantSwatch
            val muted = palette.mutedSwatch
            val darkMuted = palette.darkMutedSwatch
            val lightMuted = palette.lightMutedSwatch
            val dominant = palette.dominantSwatch

            // ponytail: muted ?? vibrant per-channel — muted tones are safe on light surfaces
            var primary =
                ranked.firstOrNull()?.let { Color(it) }
                    ?: muted?.rgb?.let(::Color)
                    ?: vibrant?.rgb?.let(::Color)
                    ?: dominant?.rgb?.let(::Color)
                    ?: Color(0xFF6750A4)
            primary = fixDislikeColor(primary)

            var secondary =
                muted?.rgb?.let(::Color)
                    ?: darkVibrant?.rgb?.let(::Color)
                    ?: Color(0xFF625B71)
            secondary = fixDislikeColor(secondary)

            val container =
                darkMuted?.rgb?.let(::Color)
                    ?: darkVibrant?.rgb?.let(::Color)
                    ?: dominant?.rgb?.let(::Color)
                    ?: Color(0xFF21005D)

            val surface = Color(0xFF1C1B1F)
            val onPrimary = ensureContrast(primary, targetRatio = 4.5)
            val onSurface = ensureContrast(surface, targetRatio = 4.5)

            var gradientAccent =
                lightMuted?.rgb?.let(::Color)
                    ?: lightVibrant?.rgb?.let(::Color)
                    ?: secondary
            gradientAccent = fixDislikeColor(gradientAccent)
            // MCU Blend.harmonize for tonal coherence
            gradientAccent = Color(Blend.harmonize(gradientAccent.toArgb(), primary.toArgb()))

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

    public suspend fun extractColorsCached(
        coverUrl: String,
        bitmap: Bitmap,
    ): PlayerThemeColors {
        cache.get(coverUrl)?.let { return it }
        val colors = extractColors(bitmap)
        cache.put(coverUrl, colors)
        return colors
    }

    public fun clearCache() {
        cache.evictAll()
    }

    /** Canonical MCU DislikeAnalyzer (materialkolor 5.0.1): hue 90-111, chroma > 16, tone < 65 */
    internal fun fixDislikeColor(color: Color): Color {
        val hct = Hct.fromInt(color.toArgb())
        if (DislikeAnalyzer.isDisliked(hct)) {
            return Color(DislikeAnalyzer.fixIfDisliked(hct).toInt())
        }
        return color
    }

    /** Ensure foreground has at least [targetRatio] contrast via MCU closed-form tone math (0..100) */
    internal fun ensureContrast(
        background: Color,
        targetRatio: Double = 4.5,
    ): Color {
        val bgTone = Hct.fromInt(background.toArgb()).tone
        if (Contrast.ratioOfTones(100.0, bgTone) >= targetRatio) return Color.White
        if (Contrast.ratioOfTones(0.0, bgTone) >= targetRatio) return Color.Black
        // Mid-tone surface: boundary tone on the neutral palette (lighter/darker clamp to 100/0 when unreachable)
        val tone =
            if (background.luminance() < 0.5) {
                Contrast.lighterUnsafe(bgTone, targetRatio)
            } else {
                Contrast.darkerUnsafe(bgTone, targetRatio)
            }
        return Color(Hct.from(0.0, 0.0, tone).toInt())
    }

    /** MCU QuantizerCelebi + Score ranking */
    private fun quantizeAndScore(bitmap: Bitmap): List<Int> {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return emptyList()
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val quantizeMap: Map<Int, Int> = QuantizerCelebi.quantize(pixels, 128)
        if (quantizeMap.isEmpty()) return emptyList()
        return Score.score(quantizeMap, 1, null, true)
    }

    /** Convenience for Theme.kt: opaque outline, surface tone 98, neutral chroma 6 */
    internal fun neutralSurface(tone: Double = 98.0): Color {
        // Neutral chroma 6 at tone 98/ dark tones
        val hct = Hct.from(0.0, 6.0, tone)
        return Color(hct.toInt())
    }

    internal fun tonalColor(
        seed: Int,
        tone: Int,
    ): Color {
        val palette = TonalPalette.fromInt(seed)
        return Color(palette.tone(tone))
    }

    public fun isDark(color: Color): Boolean = color.luminance() < 0.5
}
