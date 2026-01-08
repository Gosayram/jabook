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
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Theme colors extracted from artwork.
 *
 * @property primaryColor Dominant vibrant color, suitable for primary actions.
 * @property onPrimaryColor Content color on primary background.
 * @property secondaryColor Secondary vibrant color or muted variant.
 * @property surfaceColor Muted surface color, usually dark for player backgrounds.
 * @property onSurfaceColor Content color on surface.
 * @property containerColor Dominant color for container or gradient start.
 */
data class PlayerThemeColors(
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
 */
object DynamicThemeManager {
    /**
     * Extracts a color palette from the given bitmap asynchronously.
     *
     * @param bitmap The source bitmap (album art).
     * @return Extracted PlayerThemeColors.
     */
    suspend fun extractColors(bitmap: Bitmap): PlayerThemeColors =
        withContext(Dispatchers.Default) {
            val palette =
                Palette
                    .from(bitmap)
                    .generate()

            // Extract vibrant and muted swatches
            val vibrant = palette.vibrantSwatch
            val darkVibrant = palette.darkVibrantSwatch
            val lightVibrant = palette.lightVibrantSwatch
            val muted = palette.mutedSwatch
            val darkMuted = palette.darkMutedSwatch
            val dominant = palette.dominantSwatch

            // Determines colors with fallbacks
            // Primary: Vibrant -> Light Vibrant -> Dominant -> Default
            val primary =
                vibrant?.rgb?.let(::Color)
                    ?: lightVibrant?.rgb?.let(::Color)
                    ?: dominant?.rgb?.let(::Color)
                    ?: Color(0xFF6750A4)

            // Secondary: Dark Vibrant -> Muted -> Default
            val secondary =
                darkVibrant?.rgb?.let(::Color)
                    ?: muted?.rgb?.let(::Color)
                    ?: Color(0xFF625B71)

            // Container (Background Gradient Start): Dark Muted -> Dark Vibrant -> Dominant
            val container =
                darkMuted?.rgb?.let(::Color)
                    ?: darkVibrant?.rgb?.let(::Color)
                    ?: dominant?.rgb?.let(::Color)
                    ?: Color(0xFF21005D)

            // Surface: Dark Muted (darkened) -> Black
            val surface = Color(0xFF1C1B1F) // Keep standard dark surface for consistency

            // Calculate On-Colors (simplified, ideally use luminance check)
            val onPrimary = if (isDark(primary)) Color.White else Color.Black
            val onSurface = Color(0xFFE6E1E5)

            // Extract rich colors for Mesh Gradient (3-4 colors)
            // 1. Deep/Dark base (Container)
            // 2. Main Vibrant accent (Primary)
            // 3. Secondary/Different accent (Secondary or Light Vibrant)
            val gradient1 = container
            val gradient2 = primary
            val gradient3 =
                lightVibrant?.rgb?.let(::Color)
                    ?: secondary

            PlayerThemeColors(
                primaryColor = primary,
                onPrimaryColor = onPrimary,
                secondaryColor = secondary,
                surfaceColor = surface,
                onSurfaceColor = onSurface,
                containerColor = container,
                gradientColors = listOf(gradient1, gradient2, gradient3),
            )
        }

    /**
     * Helper to determine if a color is dark.
     */
    internal fun isDark(color: Color): Boolean {
        // Calculate luminance: 0.299*R + 0.587*G + 0.114*B
        // Compose Color uses sRGB color space
        val luminance = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
        return luminance < 0.5
    }
}
