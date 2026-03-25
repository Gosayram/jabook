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

package com.jabook.app.jabook.compose.feature.library

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.compose.data.model.LibraryViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class BooksGridViewColumnCountTest {
    @Test
    fun `uses compact defaults when window size class is unavailable`() {
        assertEquals(3, resolveGridColumnCount(LibraryViewMode.GRID_COMPACT, null))
        assertEquals(2, resolveGridColumnCount(LibraryViewMode.GRID_COMFORTABLE, null))
        assertEquals(2, resolveGridColumnCount(LibraryViewMode.LIST_COMPACT, null))
    }

    @Test
    fun `compact window uses phone column counts`() {
        val compactWindow = window(360, 800)

        assertEquals(3, resolveGridColumnCount(LibraryViewMode.GRID_COMPACT, compactWindow))
        assertEquals(2, resolveGridColumnCount(LibraryViewMode.GRID_COMFORTABLE, compactWindow))
    }

    @Test
    fun `medium window uses adaptive medium column counts`() {
        val mediumWindow = window(700, 900)

        assertEquals(5, resolveGridColumnCount(LibraryViewMode.GRID_COMPACT, mediumWindow))
        assertEquals(4, resolveGridColumnCount(LibraryViewMode.GRID_COMFORTABLE, mediumWindow))
    }

    @Test
    fun `expanded window uses adaptive expanded column counts`() {
        val expandedWindow = window(1000, 900)

        assertEquals(7, resolveGridColumnCount(LibraryViewMode.GRID_COMPACT, expandedWindow))
        assertEquals(6, resolveGridColumnCount(LibraryViewMode.GRID_COMFORTABLE, expandedWindow))
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    private fun window(
        widthDp: Int,
        heightDp: Int,
    ): WindowSizeClass = WindowSizeClass.calculateFromSize(DpSize(widthDp.dp, heightDp.dp))
}
