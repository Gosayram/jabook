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
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for BookDisplayMode.
 *
 * Tests the display mode enum including:
 * - Mode type checking (isGrid, isList)
 * - Grid cells configuration
 * - List cover sizes
 */
class BookDisplayModeTest {
    @Test
    fun `GRID_COMPACT is identified as grid mode`() {
        assertTrue(BookDisplayMode.GRID_COMPACT.isGrid())
        assertFalse(BookDisplayMode.GRID_COMPACT.isList())
    }

    @Test
    fun `GRID_COMFORTABLE is identified as grid mode`() {
        assertTrue(BookDisplayMode.GRID_COMFORTABLE.isGrid())
        assertFalse(BookDisplayMode.GRID_COMFORTABLE.isList())
    }

    @Test
    fun `LIST_COMPACT is identified as list mode`() {
        assertFalse(BookDisplayMode.LIST_COMPACT.isGrid())
        assertTrue(BookDisplayMode.LIST_COMPACT.isList())
    }

    @Test
    fun `LIST_DEFAULT is identified as list mode`() {
        assertFalse(BookDisplayMode.LIST_DEFAULT.isGrid())
        assertTrue(BookDisplayMode.LIST_DEFAULT.isList())
    }

    @Test
    fun `GRID_COMPACT returns compact grid cells for compact window`() {
        val gridCells = BookDisplayMode.GRID_COMPACT.getGridCells(compactWindow())
        assertEquals(GridCells.Fixed(3), gridCells)
    }

    @Test
    fun `GRID_COMPACT returns adaptive grid cells for medium window`() {
        val gridCells = BookDisplayMode.GRID_COMPACT.getGridCells(mediumWindow())
        assertEquals(GridCells.Fixed(5), gridCells)
    }

    @Test
    fun `GRID_COMPACT returns adaptive grid cells for expanded window`() {
        val gridCells = BookDisplayMode.GRID_COMPACT.getGridCells(expandedWindow())
        assertEquals(GridCells.Fixed(7), gridCells)
    }

    @Test
    fun `GRID_COMFORTABLE returns compact grid cells for compact window`() {
        val gridCells = BookDisplayMode.GRID_COMFORTABLE.getGridCells(compactWindow())
        assertEquals(GridCells.Fixed(2), gridCells)
    }

    @Test
    fun `GRID_COMFORTABLE returns adaptive grid cells for medium window`() {
        val gridCells = BookDisplayMode.GRID_COMFORTABLE.getGridCells(mediumWindow())
        assertEquals(GridCells.Fixed(4), gridCells)
    }

    @Test
    fun `GRID_COMFORTABLE returns adaptive grid cells for expanded window`() {
        val gridCells = BookDisplayMode.GRID_COMFORTABLE.getGridCells(expandedWindow())
        assertEquals(GridCells.Fixed(6), gridCells)
    }

    @Test
    fun `LIST_COMPACT returns null for grid cells`() {
        assertNull(BookDisplayMode.LIST_COMPACT.getGridCells(compactWindow()))
        assertNull(BookDisplayMode.LIST_COMPACT.getGridCells(mediumWindow()))
    }

    @Test
    fun `LIST_DEFAULT returns null for grid cells`() {
        assertNull(BookDisplayMode.LIST_DEFAULT.getGridCells(compactWindow()))
        assertNull(BookDisplayMode.LIST_DEFAULT.getGridCells(expandedWindow()))
    }

    @Test
    fun `LIST_COMPACT returns 48dp cover size`() {
        assertEquals(48, BookDisplayMode.LIST_COMPACT.getListCoverSize())
    }

    @Test
    fun `LIST_DEFAULT returns 80dp cover size`() {
        assertEquals(80, BookDisplayMode.LIST_DEFAULT.getListCoverSize())
    }

    @Test
    fun `GRID_COMPACT returns null for list cover size`() {
        assertNull(BookDisplayMode.GRID_COMPACT.getListCoverSize())
    }

    @Test
    fun `GRID_COMFORTABLE returns null for list cover size`() {
        assertNull(BookDisplayMode.GRID_COMFORTABLE.getListCoverSize())
    }

    @Test
    fun `enum has exactly 4 values`() {
        val allModes = BookDisplayMode.entries
        assertEquals(4, allModes.size)
    }

    @Test
    fun `all enum values can be accessed`() {
        val modes =
            listOf(
                BookDisplayMode.GRID_COMPACT,
                BookDisplayMode.GRID_COMFORTABLE,
                BookDisplayMode.LIST_COMPACT,
                BookDisplayMode.LIST_DEFAULT,
            )

        assertEquals(BookDisplayMode.entries.toSet(), modes.toSet())
    }

    @Test
    fun `all grid modes return true for isGrid`() {
        val gridModes = listOf(BookDisplayMode.GRID_COMPACT, BookDisplayMode.GRID_COMFORTABLE)
        gridModes.forEach { mode ->
            assertTrue("$mode should be grid", mode.isGrid())
        }
    }

    @Test
    fun `all list modes return true for isList`() {
        val listModes = listOf(BookDisplayMode.LIST_COMPACT, BookDisplayMode.LIST_DEFAULT)
        listModes.forEach { mode ->
            assertTrue("$mode should be list", mode.isList())
        }
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    private fun compactWindow(): WindowSizeClass = WindowSizeClass.calculateFromSize(DpSize(360.dp, 800.dp))

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    private fun mediumWindow(): WindowSizeClass = WindowSizeClass.calculateFromSize(DpSize(700.dp, 900.dp))

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    private fun expandedWindow(): WindowSizeClass = WindowSizeClass.calculateFromSize(DpSize(1000.dp, 900.dp))
}
