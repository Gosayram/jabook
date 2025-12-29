// Copyright 2025 Jabook Contributors
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

package com.jabook.app.jabook.compose.core.util

import android.content.Context
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * Utility object for adaptive UI values based on WindowSizeClass.
 *
 * Provides adaptive padding, spacing, and sizes that adjust based on screen size
 * following Material Design 3 guidelines.
 */
object AdaptiveUtils {
    /**
     * Threshold for smallest screen width in dp to distinguish phones from tablets.
     * Devices with smallestScreenWidthDp >= 600dp are typically tablets.
     */
    private const val TABLET_SMALLEST_WIDTH_DP = 600

    /**
     * Maximum aspect ratio for phones in portrait mode.
     * Modern phones typically have aspect ratios around 2:1 or less.
     * Devices with very tall aspect ratios (> 2.5:1) are likely phones, not tablets.
     */
    private const val MAX_PHONE_ASPECT_RATIO = 2.5f

    /**
     * Minimum density for high-DPI phones that might be misclassified.
     * Very high density (>= 420dpi) combined with narrow width suggests a phone.
     */
    private const val HIGH_DENSITY_THRESHOLD = 420f

    /**
     * Checks if the device should be forced to Compact size class based on
     * physical screen characteristics rather than just WindowSizeClass calculation.
     *
     * This method detects phones that have high-resolution displays which
     * WindowSizeClass incorrectly classifies as tablets (Medium/Expanded).
     *
     * Detection logic:
     * 1. If smallestScreenWidthDp < 600dp, it's definitely a phone
     * 2. If smallestScreenWidthDp >= 600dp but has very tall aspect ratio (> 2.5:1),
     *    it's likely a phone (modern phones are tall and narrow)
     * 3. If smallestScreenWidthDp >= 600dp but has very high density (>= 420dpi)
     *    and narrow width, it's likely a phone
     *
     * @param context Android context to get screen configuration
     * @return true if device should be treated as Compact (phone)
     */
    fun shouldForceCompact(context: Context): Boolean {
        val configuration = context.resources.configuration
        val displayMetrics = context.resources.displayMetrics

        // Get smallest screen width in dp (always in portrait orientation)
        val smallestScreenWidthDp = configuration.smallestScreenWidthDp

        // If smallestScreenWidthDp < 600dp, it's definitely a phone
        if (smallestScreenWidthDp < TABLET_SMALLEST_WIDTH_DP) {
            return true
        }

        // For devices with smallestScreenWidthDp >= 600dp, check additional characteristics
        val screenWidthDp = configuration.screenWidthDp
        val screenHeightDp = configuration.screenHeightDp

        // Calculate aspect ratio (always use portrait dimensions)
        val widthDp = min(screenWidthDp, screenHeightDp)
        val heightDp = max(screenWidthDp, screenHeightDp)
        val aspectRatio = heightDp.toFloat() / widthDp.toFloat()

        // Get density
        val density = displayMetrics.densityDpi / 160f // Convert to density scale

        // Check if it's a tall, narrow phone with high density
        // Modern phones have tall aspect ratios (often > 2:1) and high density
        val isTallNarrowPhone = aspectRatio > MAX_PHONE_ASPECT_RATIO
        val isHighDensityPhone = density >= HIGH_DENSITY_THRESHOLD && widthDp < 450

        // If device has very tall aspect ratio or high density with narrow width,
        // it's likely a phone, not a tablet
        return isTallNarrowPhone || isHighDensityPhone
    }

    /**
     * Gets effective window size class, applying device-specific overrides.
     *
     * @param windowSizeClass Original WindowSizeClass from calculateWindowSizeClass
     * @param context Android context for device detection
     * @return WindowSizeClass with device-specific overrides applied
     */
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    fun getEffectiveWindowSizeClass(
        windowSizeClass: WindowSizeClass?,
        context: Context,
    ): WindowSizeClass? {
        if (windowSizeClass == null) return null
        if (shouldForceCompact(context) && windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact) {
            // Force to Compact by creating a new WindowSizeClass with Compact width
            // Use a size that will result in Compact classification (360dp x 800dp)
            return WindowSizeClass.calculateFromSize(
                DpSize(360.dp, 800.dp), // Force compact size
            )
        }
        return windowSizeClass
    }

    /**
     * Returns adaptive padding based on window width.
     *
     * - Compact: 16dp (phones)
     * - Medium: 24dp (tablets)
     * - Expanded: 32dp (foldables/desktops)
     */
    fun getContentPadding(windowSizeClass: WindowSizeClass): Dp =
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 16.dp
            WindowWidthSizeClass.Medium -> 24.dp
            WindowWidthSizeClass.Expanded -> 32.dp
            else -> 16.dp
        }

    /**
     * Returns adaptive horizontal padding for content.
     *
     * - Compact: 16dp
     * - Medium: 24dp
     * - Expanded: 32dp
     */
    fun getHorizontalPadding(windowSizeClass: WindowSizeClass): Dp =
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 16.dp
            WindowWidthSizeClass.Medium -> 24.dp
            WindowWidthSizeClass.Expanded -> 32.dp
            else -> 16.dp
        }

    /**
     * Returns adaptive vertical padding for content.
     *
     * - Compact: 16dp
     * - Medium: 24dp
     * - Expanded: 32dp
     */
    fun getVerticalPadding(windowSizeClass: WindowSizeClass): Dp =
        when (windowSizeClass.heightSizeClass) {
            WindowHeightSizeClass.Compact -> 16.dp
            WindowHeightSizeClass.Medium -> 24.dp
            WindowHeightSizeClass.Expanded -> 32.dp
            else -> 16.dp
        }

    /**
     * Returns adaptive spacing between items.
     *
     * - Compact: 12dp
     * - Medium: 16dp
     * - Expanded: 20dp
     */
    fun getItemSpacing(windowSizeClass: WindowSizeClass): Dp =
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 12.dp
            WindowWidthSizeClass.Medium -> 16.dp
            WindowWidthSizeClass.Expanded -> 20.dp
            else -> 12.dp
        }

    /**
     * Returns adaptive small spacing (for tight layouts).
     *
     * - Compact: 4dp
     * - Medium: 6dp
     * - Expanded: 8dp
     */
    fun getSmallSpacing(windowSizeClass: WindowSizeClass): Dp =
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 4.dp
            WindowWidthSizeClass.Medium -> 6.dp
            WindowWidthSizeClass.Expanded -> 8.dp
            else -> 4.dp
        }

    /**
     * Returns adaptive card padding.
     *
     * - Compact: 12dp
     * - Medium: 16dp
     * - Expanded: 20dp
     */
    fun getCardPadding(windowSizeClass: WindowSizeClass): Dp =
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 12.dp
            WindowWidthSizeClass.Medium -> 16.dp
            WindowWidthSizeClass.Expanded -> 20.dp
            else -> 12.dp
        }

    /**
     * Checks if the window is considered a tablet (Medium or Expanded width).
     */
    fun isTablet(windowSizeClass: WindowSizeClass): Boolean = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    /**
     * Checks if the window is considered large (Expanded width).
     */
    fun isLargeScreen(windowSizeClass: WindowSizeClass): Boolean = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded

    /**
     * Returns adaptive grid column count for compact grid mode.
     *
     * - Compact: 3 columns
     * - Medium: 5 columns
     * - Expanded: 7 columns
     */
    fun getCompactGridColumns(windowSizeClass: WindowSizeClass): Int =
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 3
            WindowWidthSizeClass.Medium -> 5
            WindowWidthSizeClass.Expanded -> 7
            else -> 3
        }

    /**
     * Returns adaptive grid column count for comfortable grid mode.
     *
     * - Compact: 2 columns
     * - Medium: 4 columns
     * - Expanded: 6 columns
     */
    fun getComfortableGridColumns(windowSizeClass: WindowSizeClass): Int =
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 2
            WindowWidthSizeClass.Medium -> 4
            WindowWidthSizeClass.Expanded -> 6
            else -> 2
        }

    /**
     * Returns adaptive max content width for centered layouts.
     *
     * - Compact: No limit (fillMaxWidth)
     * - Medium: 840dp
     * - Expanded: 1200dp
     */
    fun getMaxContentWidth(windowSizeClass: WindowSizeClass): Dp? =
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> null // No limit
            WindowWidthSizeClass.Medium -> 840.dp
            WindowWidthSizeClass.Expanded -> 1200.dp
            else -> null
        }

    /**
     * Returns adaptive icon size based on window width.
     *
     * - Compact: 24dp (standard)
     * - Medium: 28dp
     * - Expanded: 32dp
     */
    fun getIconSize(windowSizeClass: WindowSizeClass): Dp =
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 24.dp
            WindowWidthSizeClass.Medium -> 28.dp
            WindowWidthSizeClass.Expanded -> 32.dp
            else -> 24.dp
        }

    /**
     * Returns adaptive small icon size (for compact UI elements).
     *
     * - Compact: 20dp
     * - Medium: 24dp
     * - Expanded: 28dp
     */
    fun getSmallIconSize(windowSizeClass: WindowSizeClass): Dp =
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 20.dp
            WindowWidthSizeClass.Medium -> 24.dp
            WindowWidthSizeClass.Expanded -> 28.dp
            else -> 20.dp
        }

    /**
     * Returns adaptive button height.
     *
     * - Compact: 40dp
     * - Medium: 48dp
     * - Expanded: 56dp
     */
    fun getButtonHeight(windowSizeClass: WindowSizeClass): Dp =
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 40.dp
            WindowWidthSizeClass.Medium -> 48.dp
            WindowWidthSizeClass.Expanded -> 56.dp
            else -> 40.dp
        }

    /**
     * Returns adaptive card elevation.
     *
     * - Compact: 1dp
     * - Medium: 2dp
     * - Expanded: 3dp
     */
    fun getCardElevation(windowSizeClass: WindowSizeClass): Dp =
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 1.dp
            WindowWidthSizeClass.Medium -> 2.dp
            WindowWidthSizeClass.Expanded -> 3.dp
            else -> 1.dp
        }

    /**
     * Returns adaptive text scale factor for font sizes.
     *
     * - Compact: 1.0 (base size)
     * - Medium: 1.1 (10% larger)
     * - Expanded: 1.2 (20% larger)
     */
    fun getTextScaleFactor(windowSizeClass: WindowSizeClass): Float =
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 1.0f
            WindowWidthSizeClass.Medium -> 1.1f
            WindowWidthSizeClass.Expanded -> 1.2f
            else -> 1.0f
        }

    /**
     * Returns adaptive text style with scaled font size.
     *
     * @param baseStyle Base text style from MaterialTheme
     * @param windowSizeClass Window size class for scaling
     * @return Text style with scaled font size
     */
    fun getAdaptiveTextStyle(
        baseStyle: TextStyle,
        windowSizeClass: WindowSizeClass,
    ): TextStyle {
        val scaleFactor = getTextScaleFactor(windowSizeClass)
        val scaledLineHeight = baseStyle.lineHeight?.let { it * scaleFactor }
        return baseStyle
            .copy(
                fontSize = baseStyle.fontSize * scaleFactor,
            ).let { style ->
                if (scaledLineHeight != null) {
                    style.copy(lineHeight = scaledLineHeight)
                } else {
                    style
                }
            }
    }

    /**
     * Returns adaptive cover image size for list items.
     *
     * - Compact: 48dp
     * - Medium: 64dp
     * - Expanded: 80dp
     */
    fun getListCoverSize(windowSizeClass: WindowSizeClass): Dp =
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 48.dp
            WindowWidthSizeClass.Medium -> 64.dp
            WindowWidthSizeClass.Expanded -> 80.dp
            else -> 48.dp
        }

    /**
     * Returns adaptive cover image size for compact list items.
     *
     * - Compact: 40dp
     * - Medium: 56dp
     * - Expanded: 72dp
     */
    fun getCompactListCoverSize(windowSizeClass: WindowSizeClass): Dp =
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 40.dp
            WindowWidthSizeClass.Medium -> 56.dp
            WindowWidthSizeClass.Expanded -> 72.dp
            else -> 40.dp
        }
}
