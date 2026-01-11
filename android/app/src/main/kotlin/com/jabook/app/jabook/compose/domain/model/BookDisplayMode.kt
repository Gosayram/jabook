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

package com.jabook.app.jabook.compose.domain.model

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import com.jabook.app.jabook.compose.core.util.AdaptiveUtils

/**
 * Display modes for books in the library.
 *
 * Replaces and extends the existing LibraryViewMode, providing
 * clearer semantics and additional display modes.
 *
 * This enum is used to unify display methods for books
 * across all app screens (Library, Search, Favorites).
 */
public enum class BookDisplayMode {
    /**
     * Compact grid - 3 columns on phone, 6 on tablet.
     * Maximum display density for viewing large collections.
     */
    GRID_COMPACT,

    /**
     * Comfortable grid - 2 columns on phone, 4 on tablet.
     * Balanced mode between compactness and viewing comfort.
     */
    GRID_COMFORTABLE,

    /**
     * Compact list - horizontal cards with small covers (48dp).
     * Suitable for quick browsing and searching by titles.
     */
    LIST_COMPACT,

    /**
     * Default list - horizontal cards with medium covers (80dp).
     * More detailed display with better cover visibility.
     */
    LIST_DEFAULT,

    ;

    /**
     * Checks if the mode is a grid variant.
     */
    public fun isGrid(): Boolean = this == GRID_COMPACT || this == GRID_COMFORTABLE

    /**
     * Checks if the mode is a list variant.
     */
    public fun isList(): Boolean = !isGrid()

    /**
     * Returns GridCells configuration for this mode.
     *
     * @param isTablet True if device is a tablet (width >= 600dp)
     * @return GridCells configuration or null for list modes
     * @deprecated Use getGridCells(windowSizeClass) instead for better adaptive behavior
     */
    @Deprecated(
        message = "Use getGridCells(windowSizeClass) instead",
        replaceWith = ReplaceWith("getGridCells(WindowSizeClass)"),
    )
    public fun getGridCells(isTablet: Boolean): GridCells? =
        when (this) {
            GRID_COMPACT -> GridCells.Fixed(if (isTablet) 6 else 3)
            GRID_COMFORTABLE -> GridCells.Fixed(if (isTablet) 4 else 2)
            LIST_COMPACT, LIST_DEFAULT -> null // Not applicable for lists
        }

    /**
     * Returns GridCells configuration for this mode based on WindowSizeClass.
     *
     * Uses Material 3 adaptive guidelines for better responsiveness.
     *
     * @param windowSizeClass Window size class for adaptive layout
     * @return GridCells configuration or null for list modes
     */
    public fun getGridCells(windowSizeClass: WindowSizeClass): GridCells? =
        when (this) {
            GRID_COMPACT -> GridCells.Fixed(AdaptiveUtils.getCompactGridColumns(windowSizeClass))
            GRID_COMFORTABLE -> GridCells.Fixed(AdaptiveUtils.getComfortableGridColumns(windowSizeClass))
            LIST_COMPACT, LIST_DEFAULT -> null // Not applicable for lists
        }

    /**
     * Returns cover size for list modes in dp.
     *
     * @return Cover size in dp or null for grid modes
     */
    public fun getListCoverSize(): Int? =
        when (this) {
            LIST_COMPACT -> 48
            LIST_DEFAULT -> 80
            GRID_COMPACT, GRID_COMFORTABLE -> null // Not applicable for grids
        }
}
