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

package com.jabook.app.jabook.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// Beta Light Color Scheme (Cyber-Premium Tech)
private val BetaLightColorScheme =
    lightColorScheme(
        primary = beta_light_primary,
        onPrimary = beta_light_onPrimary,
        primaryContainer = beta_light_primaryContainer,
        onPrimaryContainer = beta_light_onPrimaryContainer,
        secondary = beta_light_secondary,
        onSecondary = beta_light_onSecondary,
        secondaryContainer = beta_light_secondaryContainer,
        onSecondaryContainer = beta_light_onSecondaryContainer,
        tertiary = beta_light_tertiary,
        onTertiary = beta_light_onTertiary,
        // ponytail: derived tonal 90 for container, 10 for onContainer — no new hex palette
        tertiaryContainer = Color(0xFFB0F0FF),
        onTertiaryContainer = Color(0xFF002020),
        error = beta_light_error,
        onError = beta_light_onError,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = beta_light_background,
        onBackground = beta_light_onBackground,
        surface = beta_light_surface,
        onSurface = beta_light_onSurface,
        surfaceVariant = beta_light_surfaceVariant,
        onSurfaceVariant = beta_light_onSurfaceVariant,
        outline = beta_light_outline,
        outlineVariant = beta_light_outline.copy(alpha = 0.5f),
        scrim = Color.Black.copy(alpha = 0.32f),
        inverseSurface = Color(0xFF2F3033),
        inverseOnSurface = Color(0xFFF1F0F4),
        inversePrimary = Color(0xFFB8F5A2),
        surfaceDim = Color(0xFFDBDBDB),
        surfaceBright = Color(0xFFFDFDFD),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF8F8F8),
        surfaceContainer = Color(0xFFF0F0F0),
        surfaceContainerHigh = Color(0xFFEAEAEA),
        surfaceContainerHighest = Color(0xFFE5E5E5),
        primaryFixed = Color(0xFFB8F5A2),
        primaryFixedDim = Color(0xFF8FE080),
        onPrimaryFixed = Color(0xFF002200),
        onPrimaryFixedVariant = Color(0xFF005300),
        secondaryFixed = Color(0xFFCAE6FF),
        secondaryFixedDim = Color(0xFF96CCFF),
        onSecondaryFixed = Color(0xFF001E30),
        onSecondaryFixedVariant = Color(0xFF004A6E),
        tertiaryFixed = Color(0xFFB0F0FF),
        tertiaryFixedDim = Color(0xFF70E8FF),
        onTertiaryFixed = Color(0xFF002020),
        onTertiaryFixedVariant = Color(0xFF004F4F),
    )

// Beta Dark Color Scheme (Cyber-Premium Tech)
private val BetaDarkColorScheme =
    darkColorScheme(
        primary = beta_dark_primary,
        onPrimary = beta_dark_onPrimary,
        primaryContainer = beta_dark_primaryContainer,
        onPrimaryContainer = beta_dark_onPrimaryContainer,
        secondary = beta_dark_secondary,
        onSecondary = beta_dark_onSecondary,
        secondaryContainer = beta_dark_secondaryContainer,
        onSecondaryContainer = beta_dark_onSecondaryContainer,
        tertiary = beta_dark_tertiary,
        onTertiary = beta_dark_onTertiary,
        // ponytail: tonal 30 container for dark, 90 for onContainer
        tertiaryContainer = Color(0xFF004F4F),
        onTertiaryContainer = Color(0xFFB0F0FF),
        error = beta_dark_error,
        onError = beta_dark_onError,
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = beta_dark_background,
        onBackground = beta_dark_onBackground,
        surface = beta_dark_surface,
        onSurface = beta_dark_onSurface,
        surfaceVariant = beta_dark_surfaceVariant,
        onSurfaceVariant = beta_dark_onSurfaceVariant,
        outline = beta_dark_outline,
        outlineVariant = beta_dark_outline.copy(alpha = 0.5f),
        scrim = Color.Black.copy(alpha = 0.32f),
        inverseSurface = Color(0xFFE2E2E6),
        inverseOnSurface = Color(0xFF2F3033),
        inversePrimary = Color(0xFF006600),
        surfaceDim = Color(0xFF0F0F0F),
        surfaceBright = Color(0xFF3A3A3A),
        surfaceContainerLowest = Color(0xFF0A0A0A),
        surfaceContainerLow = Color(0xFF1A1A1A),
        surfaceContainer = Color(0xFF1E1E1E),
        surfaceContainerHigh = Color(0xFF262626),
        surfaceContainerHighest = Color(0xFF2C2C2C),
        primaryFixed = Color(0xFFB8F5A2),
        primaryFixedDim = Color(0xFF8FE080),
        onPrimaryFixed = Color(0xFF002200),
        onPrimaryFixedVariant = Color(0xFF005300),
        secondaryFixed = Color(0xFFCAE6FF),
        secondaryFixedDim = Color(0xFF96CCFF),
        onSecondaryFixed = Color(0xFF001E30),
        onSecondaryFixedVariant = Color(0xFF004A6E),
        tertiaryFixed = Color(0xFFB0F0FF),
        tertiaryFixedDim = Color(0xFF70E8FF),
        onTertiaryFixed = Color(0xFF002020),
        onTertiaryFixedVariant = Color(0xFF004F4F),
    )

// Prod Light Color Scheme (Royal Premium)
private val ProdLightColorScheme =
    lightColorScheme(
        primary = prod_light_primary,
        onPrimary = prod_light_onPrimary,
        primaryContainer = prod_light_primaryContainer,
        onPrimaryContainer = prod_light_onPrimaryContainer,
        secondary = prod_light_secondary,
        onSecondary = prod_light_onSecondary,
        secondaryContainer = prod_light_secondaryContainer,
        onSecondaryContainer = prod_light_onSecondaryContainer,
        tertiary = prod_light_tertiary,
        onTertiary = prod_light_onTertiary,
        // ponytail: derived tonal 90 for container, 10 for onContainer
        tertiaryContainer = Color(0xFFFFDEA6),
        onTertiaryContainer = Color(0xFF271900),
        error = prod_light_error,
        onError = prod_light_onError,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = prod_light_background,
        onBackground = prod_light_onBackground,
        surface = prod_light_surface,
        onSurface = prod_light_onSurface,
        surfaceVariant = prod_light_surfaceVariant,
        onSurfaceVariant = prod_light_onSurfaceVariant,
        outline = prod_light_outline,
        outlineVariant = prod_light_outline.copy(alpha = 0.5f),
        scrim = Color.Black.copy(alpha = 0.32f),
        inverseSurface = Color(0xFF2F3033),
        inverseOnSurface = Color(0xFFF1F0F4),
        inversePrimary = Color(0xFFFFDEA6),
        surfaceDim = Color(0xFFDBDBDB),
        surfaceBright = Color(0xFFFDFDFD),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF8F8F8),
        surfaceContainer = Color(0xFFF0F0F0),
        surfaceContainerHigh = Color(0xFFEAEAEA),
        surfaceContainerHighest = Color(0xFFE5E5E5),
        primaryFixed = Color(0xFFFFDEA6),
        primaryFixedDim = Color(0xFFFFC95C),
        onPrimaryFixed = Color(0xFF271900),
        onPrimaryFixedVariant = Color(0xFF5C4200),
        secondaryFixed = Color(0xFFE9DDFF),
        secondaryFixedDim = Color(0xFFCFBCFF),
        onSecondaryFixed = Color(0xFF22005D),
        onSecondaryFixedVariant = Color(0xFF4F378A),
        tertiaryFixed = Color(0xFFFFDEA6),
        tertiaryFixedDim = Color(0xFFFFC95C),
        onTertiaryFixed = Color(0xFF271900),
        onTertiaryFixedVariant = Color(0xFF5C4200),
    )

// Prod Dark Color Scheme (Royal Premium)
private val ProdDarkColorScheme =
    darkColorScheme(
        primary = prod_dark_primary,
        onPrimary = prod_dark_onPrimary,
        primaryContainer = prod_dark_primaryContainer,
        onPrimaryContainer = prod_dark_onPrimaryContainer,
        secondary = prod_dark_secondary,
        onSecondary = prod_dark_onSecondary,
        secondaryContainer = prod_dark_secondaryContainer,
        onSecondaryContainer = prod_dark_onSecondaryContainer,
        tertiary = prod_dark_tertiary,
        onTertiary = prod_dark_onTertiary,
        // ponytail: tonal 30 container for dark, 90 for onContainer
        tertiaryContainer = Color(0xFF5C4200),
        onTertiaryContainer = Color(0xFFFFDEA6),
        error = prod_dark_error,
        onError = prod_dark_onError,
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = prod_dark_background,
        onBackground = prod_dark_onBackground,
        surface = prod_dark_surface,
        onSurface = prod_dark_onSurface,
        surfaceVariant = prod_dark_surfaceVariant,
        onSurfaceVariant = prod_dark_onSurfaceVariant,
        outline = prod_dark_outline,
        outlineVariant = prod_dark_outline.copy(alpha = 0.5f),
        scrim = Color.Black.copy(alpha = 0.32f),
        inverseSurface = Color(0xFFE6E1E6),
        inverseOnSurface = Color(0xFF2F3033),
        inversePrimary = Color(0xFF7B5800),
        surfaceDim = Color(0xFF0F0F0F),
        surfaceBright = Color(0xFF3A3A3A),
        surfaceContainerLowest = Color(0xFF0A0A0A),
        surfaceContainerLow = Color(0xFF1A1A1A),
        surfaceContainer = Color(0xFF1E1E1E),
        surfaceContainerHigh = Color(0xFF262626),
        surfaceContainerHighest = Color(0xFF2C2C2C),
        primaryFixed = Color(0xFFFFDEA6),
        primaryFixedDim = Color(0xFFFFC95C),
        onPrimaryFixed = Color(0xFF271900),
        onPrimaryFixedVariant = Color(0xFF5C4200),
        secondaryFixed = Color(0xFFE9DDFF),
        secondaryFixedDim = Color(0xFFCFBCFF),
        onSecondaryFixed = Color(0xFF22005D),
        onSecondaryFixedVariant = Color(0xFF4F378A),
        tertiaryFixed = Color(0xFFFFDEA6),
        tertiaryFixedDim = Color(0xFFFFC95C),
        onTertiaryFixed = Color(0xFF271900),
        onTertiaryFixedVariant = Color(0xFF5C4200),
    )

/**
 * Jabook application theme with flavor-specific branding.
 *
 * Beta flavor: Cyber-Premium Tech (Deep Navy + Neon Green)
 * Prod flavor: Royal Premium (Deep Purple + Luxury Gold)
 * Dev/Stage flavors: Use beta theme
 *
 * @param darkTheme Whether to use dark theme. Defaults to system setting.
 * @param isBetaFlavor Whether this is beta/dev/stage flavor (true) or prod (false). Defaults to true.
 * @param selectedFont The selected font preference (DEFAULT, SYSTEM, or Google Font)
 * @param content The composable content to be themed.
 */
// AMOLED Dark Color Scheme (True Black)
// Optimized for OLED screens: pure black background saves battery
private val AmoledDarkColorScheme =
    ProdDarkColorScheme.copy(
        background = androidx.compose.ui.graphics.Color.Black,
        surface = androidx.compose.ui.graphics.Color.Black,
        // Keep variant surfaces slightly above pure black to preserve visual separation.
        surfaceVariant =
            androidx.compose.ui.graphics
                .Color(0xFF121212),
        // Surface containers for layered UI elements (cards, sheets, dialogs)
        // Graduated from pure black to maintain visual hierarchy
        surfaceContainerLowest = androidx.compose.ui.graphics.Color.Black,
        surfaceContainerLow =
            androidx.compose.ui.graphics
                .Color(0xFF0A0A0A),
        surfaceContainer =
            androidx.compose.ui.graphics
                .Color(0xFF121212),
        surfaceContainerHigh =
            androidx.compose.ui.graphics
                .Color(0xFF1A1A1A),
        surfaceContainerHighest =
            androidx.compose.ui.graphics
                .Color(0xFF222222),
    )

/**
 * Jabook application theme with flavor-specific branding.
 *
 * Beta flavor: Cyber-Premium Tech (Deep Navy + Neon Green)
 * Prod flavor: Royal Premium (Deep Purple + Luxury Gold)
 * Dev/Stage flavors: Use beta theme
 *
 * @param darkTheme Whether to use dark theme. Defaults to system setting.
 * @param amoledMode Whether to use pure black background (AMOLED mode). Only applies if darkTheme is true.
 * @param isBetaFlavor Whether this is beta/dev/stage flavor (true) or prod (false). Defaults to true.
 * @param selectedFont The selected font preference (DEFAULT, SYSTEM, or Google Font)
 * @param content The composable content to be themed.
 */
@Composable
public fun JabookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoledMode: Boolean = false,
    // Dynamic color is available on Android 12+
    // Disabled by default to enforce Premium Branding identity
    dynamicColor: Boolean = false,
    isBetaFlavor: Boolean = true,
    selectedFont: com.jabook.app.jabook.compose.data.model.AppFont = com.jabook.app.jabook.compose.data.model.AppFont.DEFAULT,
    content: @Composable () -> Unit,
) {
    // AMOLED Mode takes priority over dynamic colors to ensure pure black background
    // Dynamic colors would override the black background with wallpaper-based colors
    val colorScheme =
        when {
            // AMOLED Mode (always dark, overrides dynamic colors and flavor themes)
            darkTheme && amoledMode -> AmoledDarkColorScheme
            // Dynamic color is available on Android 12+ (only when not in AMOLED mode)
            dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
                val context = LocalView.current.context
                if (darkTheme) {
                    androidx.compose.material3.dynamicDarkColorScheme(
                        context,
                    )
                } else {
                    androidx.compose.material3.dynamicLightColorScheme(context)
                }
            }
            isBetaFlavor && darkTheme -> BetaDarkColorScheme
            isBetaFlavor && !darkTheme -> BetaLightColorScheme
            !isBetaFlavor && darkTheme -> ProdDarkColorScheme
            else -> ProdLightColorScheme
        }

    // Create typography based on font preference
    // Use FontUtils to get FontFamily (supports both bundled and Google Fonts)
    val fontFamily =
        com.jabook.app.jabook.compose.core.util.FontUtils
            .getFontFamily(selectedFont)
    val typography = createTypography(fontFamily)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge is enabled in Activity, so we just need to ensure
            // the system bars contrast matches the theme.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    // ponytail: squircle feel via stdlib RoundedCornerShape — 28dp+20dp expressive; RoundedPolygon skipped (alpha dep)
    // ponytail: M3 Shapes(8) is internal in 1.4.0; extra tokens defined as stdlib shapes outside Shapes
    val shapes =
        Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp),
        )

    // ponytail: missing M3 shape tokens — stdlib only, no RoundedPolygon (alpha dep)
    @Suppress("UNUSED_VARIABLE")
    val shapeNone = RoundedCornerShape(0.dp)

    @Suppress("UNUSED_VARIABLE")
    val largeIncreased = RoundedCornerShape(20.dp)

    @Suppress("UNUSED_VARIABLE")
    val extraLargeIncreased = RoundedCornerShape(32.dp)

    @Suppress("UNUSED_VARIABLE")
    val extraExtraLarge = RoundedCornerShape(48.dp)

    @Suppress("UNUSED_VARIABLE")
    val shapeFull = CircleShape

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = shapes,
        content = content,
    )
}
