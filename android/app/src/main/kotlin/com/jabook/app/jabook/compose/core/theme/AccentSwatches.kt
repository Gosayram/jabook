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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Curated accent swatches for fallback when no cover is available.
 * Each swatch provides a complete tonal palette for Material 3 theme generation.
 */
public data class AccentSwatch(
    public val name: String,
    public val primary: Color,
    public val secondary: Color,
    public val tertiary: Color,
) {
    public fun toPlayerThemeColors(): PlayerThemeColors =
        PlayerThemeColors(
            primaryColor = primary,
            onPrimaryColor = if (primary.luminance() < 0.5) Color.White else Color.Black,
            secondaryColor = secondary,
            surfaceColor = Color(0xFF1C1B1F),
            onSurfaceColor = Color(0xFFE6E1E5),
            containerColor = primary.copy(alpha = 0.8f),
            gradientColors = listOf(primary.copy(alpha = 0.8f), primary, tertiary),
        )
}

/**
 * Curated accent swatches: violet, indigo, blue, turquoise, green, gold, amber, terracotta.
 */
internal val CURATED_ACCENT_SWATCHES: List<AccentSwatch> =
    listOf(
        AccentSwatch(
            name = "Violet",
            primary = Color(0xFF6750A4),
            secondary = Color(0xFF625B71),
            tertiary = Color(0xFF7D5260),
        ),
        AccentSwatch(
            name = "Indigo",
            primary = Color(0xFF4F46E5),
            secondary = Color(0xFF6366F1),
            tertiary = Color(0xFF7C3AED),
        ),
        AccentSwatch(
            name = "Blue",
            primary = Color(0xFF2563EB),
            secondary = Color(0xFF1D4ED8),
            tertiary = Color(0xFF0EA5E9),
        ),
        AccentSwatch(
            name = "Turquoise",
            primary = Color(0xFF0D9488),
            secondary = Color(0xFF0F766E),
            tertiary = Color(0xFF14B8A6),
        ),
        AccentSwatch(
            name = "Green",
            primary = Color(0xFF16A34A),
            secondary = Color(0xFF15803D),
            tertiary = Color(0xFF22C55E),
        ),
        AccentSwatch(
            name = "Gold",
            primary = Color(0xFFCA8A04),
            secondary = Color(0xFFA16207),
            tertiary = Color(0xFFEAB308),
        ),
        AccentSwatch(
            name = "Amber",
            primary = Color(0xFFF59E0B),
            secondary = Color(0xFFD97706),
            tertiary = Color(0xFFFBBF24),
        ),
        AccentSwatch(
            name = "Terracotta",
            primary = Color(0xFFEA580C),
            secondary = Color(0xFFC2410C),
            tertiary = Color(0xFFF97316),
        ),
    )

/**
 * Gets the default accent swatch index (Violet).
 */
public fun getDefaultAccentIndex(): Int = 0

/**
 * Gets the accent swatch at the given index.
 */
public fun getAccentSwatch(index: Int): AccentSwatch? = CURATED_ACCENT_SWATCHES.getOrNull(index)

/**
 * Gets all curated accent swatch names.
 */
public fun getAccentSwatchNames(): List<String> = CURATED_ACCENT_SWATCHES.map { it.name }
