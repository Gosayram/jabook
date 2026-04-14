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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
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
        error = beta_light_error,
        onError = beta_light_onError,
        background = beta_light_background,
        onBackground = beta_light_onBackground,
        surface = beta_light_surface,
        onSurface = beta_light_onSurface,
        surfaceVariant = beta_light_surfaceVariant,
        onSurfaceVariant = beta_light_onSurfaceVariant,
        outline = beta_light_outline,
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
        error = beta_dark_error,
        onError = beta_dark_onError,
        background = beta_dark_background,
        onBackground = beta_dark_onBackground,
        surface = beta_dark_surface,
        onSurface = beta_dark_onSurface,
        surfaceVariant = beta_dark_surfaceVariant,
        onSurfaceVariant = beta_dark_onSurfaceVariant,
        outline = beta_dark_outline,
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
        error = prod_light_error,
        onError = prod_light_onError,
        background = prod_light_background,
        onBackground = prod_light_onBackground,
        surface = prod_light_surface,
        onSurface = prod_light_onSurface,
        surfaceVariant = prod_light_surfaceVariant,
        onSurfaceVariant = prod_light_onSurfaceVariant,
        outline = prod_light_outline,
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
        error = prod_dark_error,
        onError = prod_dark_onError,
        background = prod_dark_background,
        onBackground = prod_dark_onBackground,
        surface = prod_dark_surface,
        onSurface = prod_dark_onSurface,
        surfaceVariant = prod_dark_surfaceVariant,
        onSurfaceVariant = prod_dark_onSurfaceVariant,
        outline = prod_dark_outline,
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

    // Custom shapes with rounded corners for a modern look
    val shapes =
        Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(28.dp),
        )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = shapes,
        content = content,
    )
}
