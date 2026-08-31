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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.jabook.app.jabook.R

/**
 * Create Material 3 Typography with the specified font family.
 *
 * @param fontFamily The font family to use. Pass null to use system default fonts.
 */
public fun createTypography(fontFamily: FontFamily = FontFamily.Default): Typography =
    Typography(
        // Display styles (Premium: SemiBold for impact)
        displayLarge =
            TextStyle(
                fontFamily = fontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 57.sp,
                lineHeight = 64.sp,
                letterSpacing = (-0.25).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = fontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 45.sp,
                lineHeight = 52.sp,
                letterSpacing = 0.sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 36.sp,
                lineHeight = 44.sp,
                letterSpacing = 0.sp,
            ),
        // Headline styles (Premium: Medium/SemiBold for better contrast)
        headlineLarge =
            TextStyle(
                fontFamily = fontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                letterSpacing = 0.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                letterSpacing = 0.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                letterSpacing = 0.sp,
            ),
        // Title styles
        titleLarge =
            TextStyle(
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        // Body styles
        bodyLarge =
            TextStyle(
                fontFamily = fontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = fontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.25.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = fontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.4.sp,
            ),
        // Label styles
        labelLarge =
            TextStyle(
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = fontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
    )

/**
 * Emphasized Material 3 Typography — same sizes/lineHeights as [createTypography]
 * but with weights bumped one step for emphasis.
 *
 * Mapping (M3 emphasized): displayLarge SemiBold→Bold (600→700), displayMedium
 * SemiBold→Bold, displaySmall Medium→SemiBold, headlineLarge SemiBold→Bold,
 * headlineMedium/small Medium→SemiBold, title* Medium→SemiBold, labelSmall/
 * labelMedium Medium→SemiBold. Body stays Normal (readability).
 *
 * Where to use: badges, selected list items, Extended FAB label (when added).
 * Apply locally via `Text(style = EmphasizedTypography.labelSmall)` or
 * `MaterialTheme.typography` copy — do not replace app-wide [Typography].
 *
 * ponytail: static Inter 400/500/600/700 suffices; variable-font axes skipped.
 */
public fun createEmphasizedTypography(fontFamily: FontFamily = FontFamily.Default): Typography {
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

/**
 * Brand vs Plain (M3 Expressive): display/headline = Brand (Inter SemiBold/Bold),
 * body/label/title = Plain (Inter Regular/Medium). Both currently Inter — same
 * family, different weights satisfy the token split. No separate font file needed;
 * when brand requires distinct family, swap display/headline fontFamily only.
 * ponytail: single InterFontFamily covers brand+plain; split files only if brand diverges.
 */

/**
 * Custom font family using Inter fonts from res/font/.
 * This is the default app font family.
 */
public val InterFontFamily: FontFamily =
    FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.inter_medium, FontWeight.Medium, FontStyle.Normal),
        Font(R.font.inter_semibold, FontWeight.SemiBold, FontStyle.Normal),
        Font(R.font.inter_bold, FontWeight.Bold, FontStyle.Normal),
    )

// Default typography using app's custom Inter font family
public val Typography: Typography = createTypography(InterFontFamily)

// Default emphasized typography — use locally for badges / selected / FAB
public val EmphasizedTypography: Typography = createEmphasizedTypography(InterFontFamily)
