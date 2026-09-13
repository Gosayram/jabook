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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.jabook.app.jabook.compose.core.theme.JabookExpressiveMotionScheme
import com.jabook.app.jabook.compose.core.theme.JabookReducedMotionScheme
import com.jabook.app.jabook.compose.core.theme.LocalJabookMotionScheme
import com.jabook.app.jabook.compose.core.theme.getAccentSwatch
import com.jabook.app.jabook.compose.core.theme.getDefaultAccentIndex
import com.jabook.app.jabook.compose.core.util.rememberReduceMotion
import com.materialkolor.contrast.Contrast
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeContent

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
        // MCU: outlineVariant opaque (no 0.5f), surface tone 98 neutral chroma 6
        outlineVariant = Color(Hct.from(Hct.fromInt(beta_light_outline.toArgb()).hue, 6.0, 80.0).toInt()),
        scrim = Color.Black.copy(alpha = 0.32f),
        inverseSurface = Color(0xFF2F3033),
        inverseOnSurface = Color(0xFFF1F0F4),
        inversePrimary = Color(0xFFB8F5A2),
        surfaceDim = Color(Hct.from(0.0, 6.0, 87.0).toInt()),
        surfaceBright = Color(Hct.from(0.0, 6.0, 98.0).toInt()),
        surfaceContainerLowest = Color(Hct.from(0.0, 6.0, 100.0).toInt()),
        surfaceContainerLow = Color(Hct.from(0.0, 6.0, 98.0).toInt()),
        surfaceContainer = Color(Hct.from(0.0, 6.0, 94.0).toInt()),
        surfaceContainerHigh = Color(Hct.from(0.0, 6.0, 92.0).toInt()),
        surfaceContainerHighest = Color(Hct.from(0.0, 6.0, 90.0).toInt()),
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
        outlineVariant = Color(Hct.from(Hct.fromInt(beta_dark_outline.toArgb()).hue, 6.0, 60.0).toInt()),
        scrim = Color.Black.copy(alpha = 0.32f),
        inverseSurface = Color(0xFFE2E2E6),
        inverseOnSurface = Color(0xFF2F3033),
        inversePrimary = Color(0xFF006600),
        surfaceDim = Color(Hct.from(0.0, 6.0, 6.0).toInt()),
        surfaceBright = Color(Hct.from(0.0, 6.0, 24.0).toInt()),
        surfaceContainerLowest = Color(Hct.from(0.0, 6.0, 4.0).toInt()),
        surfaceContainerLow = Color(Hct.from(0.0, 6.0, 10.0).toInt()),
        surfaceContainer = Color(Hct.from(0.0, 6.0, 12.0).toInt()),
        surfaceContainerHigh = Color(Hct.from(0.0, 6.0, 17.0).toInt()),
        surfaceContainerHighest = Color(Hct.from(0.0, 6.0, 22.0).toInt()),
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
        outlineVariant = Color(Hct.from(Hct.fromInt(prod_light_outline.toArgb()).hue, 6.0, 80.0).toInt()),
        scrim = Color.Black.copy(alpha = 0.32f),
        inverseSurface = Color(0xFF2F3033),
        inverseOnSurface = Color(0xFFF1F0F4),
        inversePrimary = Color(0xFFFFDEA6),
        surfaceDim = Color(Hct.from(0.0, 6.0, 87.0).toInt()),
        surfaceBright = Color(Hct.from(0.0, 6.0, 98.0).toInt()),
        surfaceContainerLowest = Color(Hct.from(0.0, 6.0, 100.0).toInt()),
        surfaceContainerLow = Color(Hct.from(0.0, 6.0, 98.0).toInt()),
        surfaceContainer = Color(Hct.from(0.0, 6.0, 94.0).toInt()),
        surfaceContainerHigh = Color(Hct.from(0.0, 6.0, 92.0).toInt()),
        surfaceContainerHighest = Color(Hct.from(0.0, 6.0, 90.0).toInt()),
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
        outlineVariant = Color(Hct.from(Hct.fromInt(prod_dark_outline.toArgb()).hue, 6.0, 60.0).toInt()),
        scrim = Color.Black.copy(alpha = 0.32f),
        inverseSurface = Color(0xFFE6E1E6),
        inverseOnSurface = Color(0xFF2F3033),
        inversePrimary = Color(0xFF7B5800),
        surfaceDim = Color(Hct.from(0.0, 6.0, 6.0).toInt()),
        surfaceBright = Color(Hct.from(0.0, 6.0, 24.0).toInt()),
        surfaceContainerLowest = Color(Hct.from(0.0, 6.0, 4.0).toInt()),
        surfaceContainerLow = Color(Hct.from(0.0, 6.0, 10.0).toInt()),
        surfaceContainer = Color(Hct.from(0.0, 6.0, 12.0).toInt()),
        surfaceContainerHigh = Color(Hct.from(0.0, 6.0, 17.0).toInt()),
        surfaceContainerHighest = Color(Hct.from(0.0, 6.0, 22.0).toInt()),
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

// AMOLED ramp (True Black): pure black background saves battery on OLED screens.
// Keeps variant surfaces slightly above black to preserve visual separation.
private fun ColorScheme.withAmoledSurfaces(): ColorScheme =
    copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceVariant = Color(0xFF121212),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0A0A0A),
        surfaceContainer = Color(0xFF121212),
        surfaceContainerHigh = Color(0xFF1A1A1A),
        surfaceContainerHighest = Color(0xFF222222),
    )

// ponytail: M3 shape tokens 10/10 — 5 core in MaterialTheme.shapes (L=16 fixed, was 20), 5 extended as top-level ShapeTokens (None 0, L-inc 20, XL-inc 32, XXL 48, Full CircleShape) — stdlib only, no new dep
public val shapeNone: RoundedCornerShape = RoundedCornerShape(0.dp)
public val largeIncreased: RoundedCornerShape = RoundedCornerShape(20.dp)
public val extraLargeIncreased: RoundedCornerShape = RoundedCornerShape(32.dp)
public val extraExtraLarge: RoundedCornerShape = RoundedCornerShape(48.dp)
public val shapeFull: RoundedCornerShape = CircleShape

// MCU HCT tonal: 7:1 contrast via TonalPalette tone, surface tone 98 neutral chroma 6, outlineVariant opaque
public enum class ContrastLevel { Standard, Medium, High }

// MCU: HCT tone adjustment (0..100 perceptual) — replaces HSL lightness
private fun adjustTone(
    color: Color,
    delta: Double,
): Color {
    val hct = Hct.fromInt(color.toArgb())
    val newTone = (hct.tone + delta).coerceIn(0.0, 100.0)
    return Color(Hct.from(hct.hue, hct.chroma, newTone).toInt())
}

private fun adjustLightness(
    color: Color,
    delta: Float,
): Color = adjustTone(color, delta.toDouble() * 100)

// MCU closed-form 7:1 contrast: white/black fast-path, else boundary tone via Contrast.lighterUnsafe/darkerUnsafe
private fun ensureContrastTone(
    background: Color,
    targetRatio: Double = 7.0,
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

private fun ColorScheme.withMediumContrast(isDark: Boolean): ColorScheme =
    if (isDark) {
        copy(
            outline = adjustTone(outline, 15.0),
            outlineVariant = adjustTone(outlineVariant, 12.0),
            surfaceContainer = adjustTone(surfaceContainer, -4.0),
            surfaceContainerHigh = adjustTone(surfaceContainerHigh, -6.0),
            surfaceContainerHighest = adjustTone(surfaceContainerHighest, -8.0),
            surfaceDim = adjustTone(surfaceDim, -2.0),
            scrim = Color.Black.copy(alpha = 0.45f),
            onSurface = ensureContrastTone(surface, 7.0),
        )
    } else {
        copy(
            outline = adjustTone(outline, -14.0),
            outlineVariant = adjustTone(outlineVariant, -10.0),
            surfaceContainer = Color(Hct.from(0.0, 6.0, 98.0).toInt()),
            surfaceContainerLow = Color(Hct.from(0.0, 6.0, 98.0).toInt()),
            surfaceContainerHigh = adjustTone(surfaceContainerHigh, 4.0),
            surfaceContainerHighest = adjustTone(surfaceContainerHighest, 2.0),
            surfaceDim = adjustTone(surfaceDim, 2.0),
            onSurface = ensureContrastTone(surface, 7.0),
        )
    }

private fun ColorScheme.withHighContrast(isDark: Boolean): ColorScheme =
    if (isDark) {
        copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceVariant = Color(Hct.from(0.0, 6.0, 12.0).toInt()),
            surfaceDim = Color.Black,
            surfaceBright = adjustTone(surfaceBright, 12.0),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(Hct.from(0.0, 6.0, 10.0).toInt()),
            surfaceContainer = Color(Hct.from(0.0, 6.0, 12.0).toInt()),
            surfaceContainerHigh = Color(Hct.from(0.0, 6.0, 17.0).toInt()),
            surfaceContainerHighest = Color(Hct.from(0.0, 6.0, 22.0).toInt()),
            onBackground = Color.White,
            onSurface = Color.White,
            outline = Color.White,
            outlineVariant = Color(Hct.from(0.0, 6.0, 80.0).toInt()),
            scrim = Color.Black.copy(alpha = 0.60f),
            inverseSurface = Color.White,
            inverseOnSurface = Color.Black,
        )
    } else {
        copy(
            background = Color.White,
            surface = Color(Hct.from(0.0, 6.0, 98.0).toInt()),
            surfaceVariant = Color(Hct.from(0.0, 6.0, 90.0).toInt()),
            surfaceDim = Color(Hct.from(0.0, 6.0, 87.0).toInt()),
            surfaceBright = Color.White,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color.White,
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color(Hct.from(0.0, 6.0, 92.0).toInt()),
            surfaceContainerHighest = Color(Hct.from(0.0, 6.0, 90.0).toInt()),
            onBackground = Color.Black,
            onSurface = Color.Black,
            onSurfaceVariant = Color.Black,
            outline = Color.Black,
            outlineVariant = Color(Hct.from(0.0, 6.0, 30.0).toInt()),
            scrim = Color.Black.copy(alpha = 0.60f),
            inverseSurface = Color(0xFF121212),
            inverseOnSurface = Color.White,
        )
    }

/** Returns [this] with [level] contrast applied. Standard = no-op. */
public fun ColorScheme.withContrast(
    level: ContrastLevel,
    isDark: Boolean,
): ColorScheme =
    when (level) {
        ContrastLevel.Standard -> this
        ContrastLevel.Medium -> withMediumContrast(isDark)
        ContrastLevel.High -> withHighContrast(isDark)
    }

/**
 * Builds a complete Material 3 [ColorScheme] from a curated accent swatch seed
 * (spotube pattern: named swatch → per-accent scheme via MCU [SchemeContent]).
 *
 * Returns `null` for the default swatch index ([getDefaultAccentIndex]) or an
 * out-of-range index so flavor branding applies unchanged.
 *
 * Contrast: the scheme is generated natively at the MCU contrast level
 * ([ContrastLevel.Medium] → 0.5, [ContrastLevel.High] → 1.0) so the official
 * ContrastCurve tunes all roles consistently; the shared hand-tuned
 * [ColorScheme.withContrast] pass is NOT applied on top of swatch schemes
 * (flavor/dynamic schemes still use it).
 */
public fun accentSwatchColorScheme(
    swatchIndex: Int,
    isDark: Boolean,
    contrastLevel: ContrastLevel = ContrastLevel.Standard,
): ColorScheme? {
    if (swatchIndex == getDefaultAccentIndex()) return null
    val swatch = getAccentSwatch(swatchIndex) ?: return null
    val mcuContrastLevel =
        when (contrastLevel) {
            ContrastLevel.Standard -> 0.0
            ContrastLevel.Medium -> 0.5
            ContrastLevel.High -> 1.0
        }
    val scheme = SchemeContent(Hct.fromInt(swatch.primary.toArgb()), isDark, mcuContrastLevel)
    return (if (isDark) darkColorScheme() else lightColorScheme()).copy(
        primary = Color(scheme.primary),
        onPrimary = Color(scheme.onPrimary),
        primaryContainer = Color(scheme.primaryContainer),
        onPrimaryContainer = Color(scheme.onPrimaryContainer),
        inversePrimary = Color(scheme.inversePrimary),
        secondary = Color(scheme.secondary),
        onSecondary = Color(scheme.onSecondary),
        secondaryContainer = Color(scheme.secondaryContainer),
        onSecondaryContainer = Color(scheme.onSecondaryContainer),
        tertiary = Color(scheme.tertiary),
        onTertiary = Color(scheme.onTertiary),
        tertiaryContainer = Color(scheme.tertiaryContainer),
        onTertiaryContainer = Color(scheme.onTertiaryContainer),
        background = Color(scheme.background),
        onBackground = Color(scheme.onBackground),
        surface = Color(scheme.surface),
        onSurface = Color(scheme.onSurface),
        surfaceVariant = Color(scheme.surfaceVariant),
        onSurfaceVariant = Color(scheme.onSurfaceVariant),
        surfaceTint = Color(scheme.surfaceTint),
        inverseSurface = Color(scheme.inverseSurface),
        inverseOnSurface = Color(scheme.inverseOnSurface),
        error = Color(scheme.error),
        onError = Color(scheme.onError),
        errorContainer = Color(scheme.errorContainer),
        onErrorContainer = Color(scheme.onErrorContainer),
        outline = Color(scheme.outline),
        outlineVariant = Color(scheme.outlineVariant),
        scrim = Color(scheme.scrim),
        surfaceDim = Color(scheme.surfaceDim),
        surfaceBright = Color(scheme.surfaceBright),
        surfaceContainerLowest = Color(scheme.surfaceContainerLowest),
        surfaceContainerLow = Color(scheme.surfaceContainerLow),
        surfaceContainer = Color(scheme.surfaceContainer),
        surfaceContainerHigh = Color(scheme.surfaceContainerHigh),
        surfaceContainerHighest = Color(scheme.surfaceContainerHighest),
        primaryFixed = Color(scheme.primaryFixed),
        primaryFixedDim = Color(scheme.primaryFixedDim),
        onPrimaryFixed = Color(scheme.onPrimaryFixed),
        onPrimaryFixedVariant = Color(scheme.onPrimaryFixedVariant),
        secondaryFixed = Color(scheme.secondaryFixed),
        secondaryFixedDim = Color(scheme.secondaryFixedDim),
        onSecondaryFixed = Color(scheme.onSecondaryFixed),
        onSecondaryFixedVariant = Color(scheme.onSecondaryFixedVariant),
        tertiaryFixed = Color(scheme.tertiaryFixed),
        tertiaryFixedDim = Color(scheme.tertiaryFixedDim),
        onTertiaryFixed = Color(scheme.onTertiaryFixed),
        onTertiaryFixedVariant = Color(scheme.onTertiaryFixedVariant),
    )
}

// Required API per task: simple function that copies light scheme with higher contrast via adjustLightness.
// Delegates to generic withMediumContrast/withHighContrast for reuse.
public fun createMediumContrastColorScheme(
    base: ColorScheme,
    isDark: Boolean = false,
): ColorScheme = base.withMediumContrast(isDark)

public fun createHighContrastColorScheme(
    base: ColorScheme,
    isDark: Boolean = false,
): ColorScheme = base.withHighContrast(isDark)

// Convenience: 4 scheme aliases (lightMedium/high, darkMedium/high) for direct use without recomposing.
public val BetaLightMediumContrastScheme: ColorScheme get() = BetaLightColorScheme.withMediumContrast(false)
public val BetaLightHighContrastScheme: ColorScheme get() = BetaLightColorScheme.withHighContrast(false)
public val BetaDarkMediumContrastScheme: ColorScheme get() = BetaDarkColorScheme.withMediumContrast(true)
public val BetaDarkHighContrastScheme: ColorScheme get() = BetaDarkColorScheme.withHighContrast(true)
public val ProdLightMediumContrastScheme: ColorScheme get() = ProdLightColorScheme.withMediumContrast(false)
public val ProdLightHighContrastScheme: ColorScheme get() = ProdLightColorScheme.withHighContrast(false)
public val ProdDarkMediumContrastScheme: ColorScheme get() = ProdDarkColorScheme.withMediumContrast(true)
public val ProdDarkHighContrastScheme: ColorScheme get() = ProdDarkColorScheme.withHighContrast(true)

// ponytail: system high-contrast gate — mirrors Settings→Accessibility and Android's highTextContrastEnabled (via reflection for API 36 compat)
@Composable
public fun rememberContrastLevel(highContrastEnabled: Boolean = false): ContrastLevel {
    if (highContrastEnabled) return ContrastLevel.High
    val context = LocalView.current.context
    val isSystemHighContrast =
        try {
            val am =
                context.getSystemService(
                    android.content.Context.ACCESSIBILITY_SERVICE,
                ) as android.view.accessibility.AccessibilityManager
            // ponytail: reflect to avoid compile error on SDK 36 where isHighTextContrastEnabled may be removed
            val m = am.javaClass.getMethod("isHighTextContrastEnabled")
            (m.invoke(am) as? Boolean) == true
        } catch (_: Exception) {
            false
        }
    return if (isSystemHighContrast) ContrastLevel.High else ContrastLevel.Standard
}

/**
 * Jabook application theme with flavor-specific branding.
 *
 * Beta flavor: Cyber-Premium Tech (Deep Navy + Neon Green)
 * Prod flavor: Royal Premium (Deep Purple + Luxury Gold)
 * Dev/Stage flavors: Use beta theme
 *
 * Color scheme precedence (highest wins):
 * 1. AMOLED ramp (dark + [amoledMode]) — pure black surfaces on top of any base
 * 2. Dynamic color (Android 12+, [dynamicColor]) — wallpaper colors win over swatch
 * 3. Accent swatch ([accentSwatchIndex] >= 1, non-default) — [SchemeContent] scheme
 *    from the swatch seed
 * 4. Flavor branding (Beta/Prod light/dark)
 *
 * Swatch bases carry the native MCU ContrastCurve for [contrastLevel]; other bases
 * go through the [ContrastLevel] pass ([ColorScheme.withContrast]).
 *
 * @param darkTheme Whether to use dark theme. Defaults to system setting.
 * @param amoledMode Whether to use pure black background (AMOLED mode). Only applies if darkTheme is true.
 * @param contrastLevel M3 contrast level (Standard/Medium/High) — gated by Settings→Accessibility or system highTextContrastEnabled.
 * @param accentSwatchIndex Persisted accent swatch index (proto `accent_swatch_index`); 0 = default (flavor branding).
 * @param isBetaFlavor Whether this is beta/dev/stage flavor (true) or prod (false). Defaults to true.
 * @param selectedFont The selected font preference (DEFAULT, SYSTEM, or Google Font)
 * @param content The composable content to be themed.
 */
@Composable
public fun JabookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoledMode: Boolean = false,
    contrastLevel: ContrastLevel = ContrastLevel.Standard,
    // Dynamic color is available on Android 12+
    // Disabled by default to enforce Premium Branding identity
    dynamicColor: Boolean = false,
    accentSwatchIndex: Int = getDefaultAccentIndex(),
    isBetaFlavor: Boolean = true,
    selectedFont: com.jabook.app.jabook.compose.data.model.AppFont = com.jabook.app.jabook.compose.data.model.AppFont.DEFAULT,
    content: @Composable () -> Unit,
) {
    // ponytail: precedence AMOLED > dynamicColor (S+) > accent swatch (> default index) > flavor branding
    val usingSwatchScheme = accentSwatchIndex != getDefaultAccentIndex()
    val swatchScheme =
        if (usingSwatchScheme) {
            accentSwatchColorScheme(accentSwatchIndex, darkTheme, contrastLevel)
        } else {
            null
        }
    val baseScheme =
        when {
            // AMOLED Mode (always dark, overrides dynamic colors and flavor themes)
            darkTheme && amoledMode -> (swatchScheme ?: ProdDarkColorScheme).withAmoledSurfaces()
            // Dynamic color is available on Android 12+ (only when not in AMOLED mode);
            // wins over the accent swatch
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
            swatchScheme != null -> swatchScheme
            isBetaFlavor && darkTheme -> BetaDarkColorScheme
            isBetaFlavor && !darkTheme -> BetaLightColorScheme
            !isBetaFlavor && darkTheme -> ProdDarkColorScheme
            else -> ProdLightColorScheme
        }
    // ponytail: flavor/dynamic get the M3 hand-tuned medium/high pass; swatch schemes carry the
    // native MCU ContrastCurve from [accentSwatchColorScheme] — no second pass on top
    val colorScheme =
        if (contrastLevel == ContrastLevel.Standard || swatchScheme != null) {
            baseScheme
        } else {
            baseScheme.withContrast(contrastLevel, darkTheme)
        }

    // Create typography based on font preference
    // Use FontUtils to get FontFamily (supports both bundled and Google Fonts)
    // ponytail: app-wide Typography stays base (Brand display/headline + Plain body/label via same Inter);
    // EmphasizedTypography is local-only for badges/selected states — no global toggle to keep contrast hierarchy
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

    // ponytail: M3 Shapes 10/10 — 5 core via MaterialTheme.shapes (L=16 fixed, was 20 mis-mapped; XL 28 correct; XS 4/S 8/M 12 kept) + 5 extended top-level ShapeTokens; stdlib only
    val shapes =
        Shapes(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(28.dp),
        )

    val motionScheme =
        if (rememberReduceMotion()) JabookReducedMotionScheme else JabookExpressiveMotionScheme
    CompositionLocalProvider(LocalJabookMotionScheme provides motionScheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = content,
        )
    }
}
