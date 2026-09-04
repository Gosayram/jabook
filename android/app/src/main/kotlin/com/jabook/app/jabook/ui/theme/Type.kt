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

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.jabook.app.jabook.R

/**
 * M3 language script height categories — scale lineHeight to avoid clipping.
 * Factors: Small 1.0, Medium 1.07, Large 1.30, ExtraLarge 2.0 (material3/styles/typography).
 */
public enum class LanguageHeight(
    public val factor: Float,
) {
    Small(1.0f),
    Medium(1.07f),
    Large(1.30f),
    ExtraLarge(2.0f),
}

/** Returns factor for [height] category. */
public fun languageHeightFactor(height: LanguageHeight): Float = height.factor

private fun scaledLineHeight(
    base: TextUnit,
    height: LanguageHeight,
): TextUnit = if (height == LanguageHeight.Small) base else (base.value * height.factor).sp

// ponytail: helper — wdth/GRAD/opsz per style; if axis missing in font (e.g. Inter lacks GRAD/opsz) Android ignores setting gracefully.
private fun brandSettings(
    width: Float = 100f,
    grade: Int? = null,
    opsz: TextUnit? = null,
): FontVariation.Settings {
    val list = mutableListOf<FontVariation.Setting>()
    // wdth axis — if inter_variable.ttf lacks it, ignored gracefully (no crash)
    list.add(FontVariation.width(width))
    if (grade != null) list.add(FontVariation.grade(grade))
    if (opsz != null) list.add(FontVariation.opticalSizing(opsz))
    // ponytail: XOPQ/YOPQ etc via raw tag when needed: FontVariation.Setting("XOPQ", 50f)
    return FontVariation.Settings(*list.toTypedArray())
}

/**
 * Brand vs Plain (M3 Expressive): display/headline = Brand (Inter Variable),
 * body/label/title = Plain (Inter static). Unify to single family by passing same
 * family for both — split kept for editorial wdth tuning.
 */

/**
 * Static plain family — Inter regular/medium/semibold/bold.
 */
public val InterFontFamily: FontFamily =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.inter_medium, FontWeight.Medium, FontStyle.Normal),
        Font(R.font.inter_semibold, FontWeight.SemiBold, FontStyle.Normal),
        Font(R.font.inter_bold, FontWeight.Bold, FontStyle.Normal),
    )

/**
 * Variable brand family — single inter_variable.ttf covering all weights.
 * wdth 100 default (plain width); opsz tuned per-size via settings where needed.
 * ponytail: 856k variable file replaces 4 statics for brand; plain keeps statics for body/label.
 * If wdth absent in file, width setting is ignored — fallback graceful (verify via FontVariation allowed).
 */
public val InterVariableFontFamily: FontFamily =
    FontFamily(
        Font(R.font.inter_variable, FontWeight.Normal, FontStyle.Normal, variationSettings = brandSettings(100f, opsz = 16.sp)),
        Font(R.font.inter_variable, FontWeight.Medium, FontStyle.Normal, variationSettings = brandSettings(100f, opsz = 16.sp)),
        Font(R.font.inter_variable, FontWeight.SemiBold, FontStyle.Normal, variationSettings = brandSettings(100f, opsz = 32.sp)),
        Font(R.font.inter_variable, FontWeight.Bold, FontStyle.Normal, variationSettings = brandSettings(100f, opsz = 57.sp)),
    )

/**
 * Emphasized variable brand — wdth 110 for expressive display/headline, grade + opsz for headlineLarge.
 * Mirrors static weight-bump but via axis so widths don't reflow (GRAD adds weight without width change).
 * ponytail: grade/opsz ignored if Inter lacks those axes — fallback graceful.
 */
public val InterVariableEmphasizedFamily: FontFamily =
    FontFamily(
        Font(R.font.inter_variable, FontWeight.Normal, FontStyle.Normal, variationSettings = brandSettings(110f, opsz = 16.sp)),
        Font(R.font.inter_variable, FontWeight.Medium, FontStyle.Normal, variationSettings = brandSettings(110f, opsz = 16.sp)),
        Font(
            R.font.inter_variable,
            FontWeight.SemiBold,
            FontStyle.Normal,
            variationSettings = brandSettings(110f, grade = 12, opsz = 32.sp),
        ),
        Font(R.font.inter_variable, FontWeight.Bold, FontStyle.Normal, variationSettings = brandSettings(110f, grade = 18, opsz = 57.sp)),
    )

/**
 * Create Material 3 Typography with brand/plain split and variable axes.
 *
 * @param brandFontFamily FontFamily for display/headline (default InterVariable — wdth/opsz/GRAD via variable file)
 * @param plainFontFamily FontFamily for body/label/title (default Inter static for readability)
 * @param languageHeight Scales lineHeight by 1.0/1.07/1.30/2.0 per script category
 */
public fun createTypography(
    brandFontFamily: FontFamily = InterVariableFontFamily,
    plainFontFamily: FontFamily = InterFontFamily,
    languageHeight: LanguageHeight = LanguageHeight.Small,
): Typography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = brandFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 57.sp,
                lineHeight = scaledLineHeight(64.sp, languageHeight),
                letterSpacing = (-0.25).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = brandFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 45.sp,
                lineHeight = scaledLineHeight(52.sp, languageHeight),
                letterSpacing = 0.sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = brandFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 36.sp,
                lineHeight = scaledLineHeight(44.sp, languageHeight),
                letterSpacing = 0.sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = brandFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                lineHeight = scaledLineHeight(40.sp, languageHeight),
                letterSpacing = 0.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = brandFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 28.sp,
                lineHeight = scaledLineHeight(36.sp, languageHeight),
                letterSpacing = 0.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = brandFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 24.sp,
                lineHeight = scaledLineHeight(32.sp, languageHeight),
                letterSpacing = 0.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = plainFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp,
                lineHeight = scaledLineHeight(28.sp, languageHeight),
                letterSpacing = 0.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = plainFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = scaledLineHeight(24.sp, languageHeight),
                letterSpacing = 0.15.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = plainFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = scaledLineHeight(20.sp, languageHeight),
                letterSpacing = 0.1.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = plainFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = scaledLineHeight(24.sp, languageHeight),
                letterSpacing = 0.5.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = plainFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = scaledLineHeight(20.sp, languageHeight),
                letterSpacing = 0.25.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = plainFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = scaledLineHeight(16.sp, languageHeight),
                letterSpacing = 0.4.sp,
            ),
        labelLarge =
            TextStyle(
                fontFamily = plainFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = scaledLineHeight(20.sp, languageHeight),
                letterSpacing = 0.1.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = plainFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = scaledLineHeight(16.sp, languageHeight),
                letterSpacing = 0.5.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = plainFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = scaledLineHeight(16.sp, languageHeight),
                letterSpacing = 0.5.sp,
            ),
    )

/**
 * Legacy single-family overload — uses InterVariable for brand, keeps compat for callers passing one family.
 * If caller passes InterFontFamily explicitly, brand will still be variable; to force unified plain, pass same family as brand.
 */
public fun createTypography(fontFamily: FontFamily = InterFontFamily): Typography =
    if (fontFamily == InterFontFamily) {
        createTypography(brandFontFamily = InterVariableFontFamily, plainFontFamily = InterFontFamily)
    } else {
        // unify — caller provided family used for both brand+plain (e.g. FontFamily.Default)
        createTypography(brandFontFamily = fontFamily, plainFontFamily = fontFamily)
    }

/**
 * Emphasized Material 3 Typography — same sizes/lineHeights but with weight bump + wdth 110 + GRAD/opsz.
 * Display/headline switch to InterVariableEmphasizedFamily (wdth 110, headlineLarge GRAD tuned); body unchanged.
 */
public fun createEmphasizedTypography(
    brandFontFamily: FontFamily = InterVariableEmphasizedFamily,
    plainFontFamily: FontFamily = InterFontFamily,
    languageHeight: LanguageHeight = LanguageHeight.Small,
): Typography {
    val base =
        createTypography(brandFontFamily = InterVariableFontFamily, plainFontFamily = plainFontFamily, languageHeight = languageHeight)

    // ponytail: demonstrate wdth axis via both typed and raw forms — both compile; raw fallback if FontVariation.width unavailable
    @Suppress("UNUSED_VARIABLE")
    val emphasizedHeadlineWidthDemo = FontVariation.Settings(FontVariation.width(110f))

    @Suppress("UNUSED_VARIABLE")
    val emphasizedHeadlineWidthRawDemo = FontVariation.Settings(FontVariation.Setting("wdth", 110f))
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = brandFontFamily, fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontFamily = brandFontFamily, fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontFamily = brandFontFamily, fontWeight = FontWeight.SemiBold),
        headlineLarge = base.headlineLarge.copy(fontFamily = brandFontFamily, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = brandFontFamily, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = brandFontFamily, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Legacy single-family emphasized overload — compat. */
public fun createEmphasizedTypography(fontFamily: FontFamily = FontFamily.Default): Typography {
    // ponytail: demo variable axis — wdth 110 for expressive headlineLarge (requires variable file)
    @Suppress("UNUSED_VARIABLE")
    val emphasizedHeadlineWidthDemo = FontVariation.Settings(FontVariation.width(110f))

    @Suppress("UNUSED_VARIABLE")
    val emphasizedHeadlineWidthRawDemo = FontVariation.Settings(FontVariation.Setting("wdth", 110f))
    if (fontFamily == FontFamily.Default) {
        return createEmphasizedTypography(brandFontFamily = InterVariableEmphasizedFamily, plainFontFamily = InterFontFamily)
    }
    val base = createTypography(fontFamily)
    return base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.SemiBold),
    )
}

// Default typography — brand InterVariable display/headline + plain Inter body/label
public val Typography: Typography = createTypography(InterVariableFontFamily, InterFontFamily)

// Default emphasized — wdth 110 + GRAD/opsz via variable brand
public val EmphasizedTypography: Typography = createEmphasizedTypography(InterVariableEmphasizedFamily, InterFontFamily)
