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

package com.jabook.app.jabook.compose.core.util

import android.content.Context
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.compose.core.theme.SpacingTokens

/**
 * Utility object for adaptive UI values based on WindowSizeClass.
 *
 * Provides adaptive padding, spacing, and sizes that adjust based on screen size
 * following Material Design 3 guidelines.
 */
public object AdaptiveUtils {
    /**
     * Gets effective window size class, applying device-specific overrides.
     *
     * @param windowSizeClass Original WindowSizeClass from calculateWindowSizeClass
     * @param context Android context for device detection
     * @return WindowSizeClass with device-specific overrides applied
     */
    public fun getEffectiveWindowSizeClass(
        windowSizeClass: WindowSizeClass?,
        @Suppress("UNUSED_PARAMETER") context: Context,
    ): WindowSizeClass? = windowSizeClass

    /**
     * Resolves window size class with device-specific overrides and compact fallback.
     */
    public fun resolveWindowSizeClass(
        windowSizeClass: WindowSizeClass,
        context: Context,
    ): WindowSizeClass = getEffectiveWindowSizeClass(windowSizeClass, context) ?: windowSizeClass

    /**
     * Resolves nullable window size class with device-specific overrides.
     */
    public fun resolveWindowSizeClassOrNull(
        windowSizeClass: WindowSizeClass?,
        context: Context,
    ): WindowSizeClass? = windowSizeClass?.let { resolveWindowSizeClass(it, context) }

    /**
     * Returns adaptive padding with compact fallback when size class is unavailable.
     */
    public fun getContentPaddingOrDefault(windowSizeClass: WindowSizeClass?): Dp = windowSizeClass?.let { getContentPadding(it) } ?: 16.dp

    /**
     * Returns adaptive spacing with compact fallback when size class is unavailable.
     */
    public fun getItemSpacingOrDefault(windowSizeClass: WindowSizeClass?): Dp = windowSizeClass?.let { getItemSpacing(it) } ?: 12.dp

    /**
     * Returns adaptive small spacing with compact fallback when size class is unavailable.
     */
    public fun getSmallSpacingOrDefault(windowSizeClass: WindowSizeClass?): Dp = windowSizeClass?.let { getSmallSpacing(it) } ?: 4.dp

    /**
     * Returns adaptive card padding with compact fallback when size class is unavailable.
     */
    public fun getCardPaddingOrDefault(windowSizeClass: WindowSizeClass?): Dp = windowSizeClass?.let { getCardPadding(it) } ?: 12.dp

    /**
     * Returns adaptive padding based on window width.
     *
     * - Compact: 16dp (phones)
     * - Medium: 24dp (tablets)
     * - Expanded: 32dp (foldables/desktops)
     */
    public fun getContentPadding(windowSizeClass: WindowSizeClass): Dp =
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> SpacingTokens.ContentPaddingCompact
            WindowWidthSizeClass.Medium -> SpacingTokens.ContentPaddingMedium
            WindowWidthSizeClass.Expanded -> SpacingTokens.ContentPaddingExpanded
            else -> SpacingTokens.ContentPaddingCompact
        }

    /**
     * Returns adaptive horizontal padding for content.
     *
     * - Compact: 16dp
     * - Medium: 24dp
     * - Expanded: 32dp
     */
    public fun getHorizontalPadding(windowSizeClass: WindowSizeClass): Dp =
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> SpacingTokens.ContentPaddingCompact
            WindowWidthSizeClass.Medium -> SpacingTokens.ContentPaddingMedium
            WindowWidthSizeClass.Expanded -> SpacingTokens.ContentPaddingExpanded
            else -> SpacingTokens.ContentPaddingCompact
        }

    /**
     * Returns adaptive vertical padding for content.
     *
     * - Compact: 16dp
     * - Medium: 24dp
     * - Expanded: 32dp
     */
    public fun getVerticalPadding(windowSizeClass: WindowSizeClass): Dp =
        when (windowSizeClass.heightSizeClass) {
            WindowHeightSizeClass.Compact -> SpacingTokens.ContentPaddingCompact
            WindowHeightSizeClass.Medium -> SpacingTokens.ContentPaddingMedium
            WindowHeightSizeClass.Expanded -> SpacingTokens.ContentPaddingExpanded
            else -> SpacingTokens.ContentPaddingCompact
        }

    /**
     * Returns adaptive spacing between items.
     *
     * - Compact: 12dp
     * - Medium: 16dp
     * - Expanded: 20dp
     */
    public fun getItemSpacing(windowSizeClass: WindowSizeClass): Dp =
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
    public fun getSmallSpacing(windowSizeClass: WindowSizeClass): Dp =
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
    public fun getCardPadding(windowSizeClass: WindowSizeClass): Dp =
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 12.dp
            WindowWidthSizeClass.Medium -> 16.dp
            WindowWidthSizeClass.Expanded -> 20.dp
            else -> 12.dp
        }

    /**
     * Checks if the window is considered a tablet (Medium or Expanded width).
     */
    public fun isTablet(windowSizeClass: WindowSizeClass): Boolean = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

    /**
     * Checks if the window is considered large (Expanded width).
     */
    public fun isLargeScreen(windowSizeClass: WindowSizeClass): Boolean = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded

    /**
     * Returns adaptive max content width for centered layouts.
     *
     * - Compact: No limit (fillMaxWidth)
     * - Medium: 840dp
     * - Expanded: 1200dp
     */
    public fun getMaxContentWidth(windowSizeClass: WindowSizeClass): Dp? =
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
    public fun getIconSize(windowSizeClass: WindowSizeClass): Dp =
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
    public fun getSmallIconSize(windowSizeClass: WindowSizeClass): Dp =
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
    public fun getButtonHeight(windowSizeClass: WindowSizeClass): Dp =
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
    public fun getCardElevation(windowSizeClass: WindowSizeClass): Dp =
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
    public fun getTextScaleFactor(windowSizeClass: WindowSizeClass): Float =
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
    public fun getAdaptiveTextStyle(
        baseStyle: TextStyle,
        windowSizeClass: WindowSizeClass,
    ): TextStyle {
        val scaleFactor = getTextScaleFactor(windowSizeClass)
        val scaledLineHeight = baseStyle.lineHeight * scaleFactor
        return baseStyle
            .copy(
                fontSize = baseStyle.fontSize * scaleFactor,
                lineHeight = scaledLineHeight,
            )
    }

    /**
     * Returns adaptive cover image size for list items.
     *
     * - Compact: 48dp
     * - Medium: 64dp
     * - Expanded: 80dp
     */
    public fun getListCoverSize(windowSizeClass: WindowSizeClass): Dp =
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
    public fun getCompactListCoverSize(windowSizeClass: WindowSizeClass): Dp =
        when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Compact -> 40.dp
            WindowWidthSizeClass.Medium -> 56.dp
            WindowWidthSizeClass.Expanded -> 72.dp
            else -> 40.dp
        }

    // --- 5-breakpoint pane widths (foundations/layout) ---

    /** Canonical 5 breakpoints overlayed on WSC: widthDp maps to compact/medium/expanded/large/xl. */
    public fun getBreakpoint(screenWidthDp: Int): Int =
        when {
            screenWidthDp < 600 -> 0 // compact
            screenWidthDp < 840 -> 1 // medium
            screenWidthDp < 1200 -> 2 // expanded (360 pane)
            screenWidthDp < 1600 -> 3 // large (412 pane)
            else -> 4 // xl (412 centered)
        }

    /** Supporting pane width per spec: 360 expanded, 412 large/xl, 360 otherwise. */
    public fun getSupportingPaneWidth(screenWidthDp: Int): Dp =
        when (getBreakpoint(screenWidthDp)) {
            3, 4 -> 412.dp
            else -> 360.dp
        }

    public fun getSupportingPaneWidth(windowSizeClass: WindowSizeClass?): Dp {
        // ponytail: WSC alone can't distinguish large/xl, fallback to 360; caller with widthDp gets 412.
        if (windowSizeClass == null) return 360.dp
        return when (windowSizeClass.widthSizeClass) {
            WindowWidthSizeClass.Expanded -> 360.dp // large/xl upgrade via screenWidthDp overload
            else -> 360.dp
        }
    }

    /**
     * Override PaneScaffoldDirective to enforce 8dp scaffold spacing + canonical pane width.
     * Use via `directive = remember(widthDp, baseDirective){ canonicalDirective(baseDirective, widthDp) }`
     */
    public fun canonicalDirective(
        base: androidx.compose.material3.adaptive.layout.PaneScaffoldDirective,
        screenWidthDp: Int,
    ): androidx.compose.material3.adaptive.layout.PaneScaffoldDirective {
        val paneWidth = getSupportingPaneWidth(screenWidthDp)
        return base.copy(defaultPanePreferredWidth = paneWidth, horizontalPartitionSpacerSize = 8.dp)
    }

    // ponytail: Ruler API (Compose UI 1.7+) available but skipped — grid spacing already 8dp-aligned via SpacingTokens;
    // cross-layout Ruler alignment adds measurability cost for no current misalignment. Wire when a shared header/grid
    // ruler is visibly off.
}
