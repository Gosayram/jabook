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

package com.jabook.app.jabook.compose.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.compose.designsystem.component.UnifiedBookCard
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.BookActionsProvider
import com.jabook.app.jabook.compose.domain.model.BookDisplayMode

/**
 * Unified books view that displays books in either grid or list layout.
 *
 * This component automatically selects the appropriate layout container
 * (LazyVerticalGrid or LazyColumn) based on the display mode and delegates
 * individual book rendering to UnifiedBookCard.
 *
 * @param books List of books to display
 * @param displayMode Current display mode (Grid or List variant)
 * @param actionsProvider Provider for all book actions
 * @param modifier Modifier for the container
 */
@Composable
fun UnifiedBooksView(
    books: List<Book>,
    displayMode: BookDisplayMode,
    actionsProvider: BookActionsProvider,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onToggleSelection: ((String) -> Unit)? = null,
) {
    when {
        displayMode.isGrid() ->
            BooksGridLayout(
                books = books,
                displayMode = displayMode,
                actionsProvider = actionsProvider,
                isSelectionMode = isSelectionMode,
                selectedIds = selectedIds,
                onToggleSelection = onToggleSelection,
                modifier = modifier,
            )
        displayMode.isList() ->
            BooksListLayout(
                books = books,
                displayMode = displayMode,
                actionsProvider = actionsProvider,
                isSelectionMode = isSelectionMode,
                selectedIds = selectedIds,
                onToggleSelection = onToggleSelection,
                modifier = modifier,
            )
    }
}

/**
 * Grid layout for books.
 */
@Composable
private fun BooksGridLayout(
    books: List<Book>,
    displayMode: BookDisplayMode,
    actionsProvider: BookActionsProvider,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onToggleSelection: ((String) -> Unit)? = null,
) {
    val isTablet = isTabletDevice()
    val gridCells = displayMode.getGridCells(isTablet) ?: return

    LazyVerticalGrid(
        columns = gridCells,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(books, key = { it.id }) { book ->
            UnifiedBookCard(
                book = book,
                displayMode = displayMode,
                actionsProvider = actionsProvider,
                isSelectionMode = isSelectionMode,
                isSelected = selectedIds.contains(book.id),
                onToggleSelection = { onToggleSelection?.invoke(book.id) },
            )
        }
    }
}

/**
 * List layout for books.
 */
@Composable
private fun BooksListLayout(
    books: List<Book>,
    displayMode: BookDisplayMode,
    actionsProvider: BookActionsProvider,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onToggleSelection: ((String) -> Unit)? = null,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(books, key = { it.id }) { book ->
            UnifiedBookCard(
                book = book,
                displayMode = displayMode,
                actionsProvider = actionsProvider,
                isSelectionMode = isSelectionMode,
                isSelected = selectedIds.contains(book.id),
                onToggleSelection = { onToggleSelection?.invoke(book.id) },
            )
        }
    }
}

/**
 * Helper to detect tablet devices.
 * Considers devices with width >= 600dp as tablets.
 */
@Composable
private fun isTabletDevice(): Boolean {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    return screenWidthDp >= 600
}
