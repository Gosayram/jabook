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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.jabook.app.jabook.compose.core.util.AdaptiveUtils
import com.jabook.app.jabook.compose.core.util.rememberCoverPreloader
import com.jabook.app.jabook.compose.core.util.rememberCoverPreloaderForGrid
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
 * Uses WindowSizeClass for adaptive layouts following Material 3 guidelines.
 *
 * @param books List of books to display
 * @param displayMode Current display mode (Grid or List variant)
 * @param actionsProvider Provider for all book actions
 * @param windowSizeClass Window size class for adaptive layout (optional, uses LocalConfiguration if not provided)
 * @param modifier Modifier for the container
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
public fun UnifiedBooksView(
    books: List<Book>,
    displayMode: BookDisplayMode,
    actionsProvider: BookActionsProvider,
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass? = null,
    isSelectionMode: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onToggleSelection: ((String) -> Unit)? = null,
) {
    // Log books for debugging
    androidx.compose.runtime.LaunchedEffect(books.size) {
        android.util.Log.d(
            "UnifiedBooksView",
            "📚 Rendering ${books.size} books in $displayMode mode",
        )
        if (books.isNotEmpty()) {
            val invalidBooks = books.filter { it.title.isBlank() || it.author.isBlank() || it.id.isBlank() }
            if (invalidBooks.isNotEmpty()) {
                android.util.Log.w(
                    "UnifiedBooksView",
                    "⚠️ Found ${invalidBooks.size} books with empty/invalid data out of ${books.size} total",
                )
                invalidBooks.take(3).forEachIndexed { index, book ->
                    android.util.Log.w(
                        "UnifiedBooksView",
                        "  Invalid[$index]: id='${book.id.take(20)}', " +
                            "title='${book.title.take(30)}', " +
                            "author='${book.author.take(20)}', " +
                            "coverUrl=${if (book.coverUrl.isNullOrBlank()) "null/empty" else "present"}",
                    )
                }
            }
            // Log sample of valid books
            val validBooks = books.filter { it.title.isNotBlank() && it.author.isNotBlank() && it.id.isNotBlank() }
            if (validBooks.isNotEmpty()) {
                val sample = validBooks.take(2)
                sample.forEachIndexed { index, book ->
                    android.util.Log.d(
                        "UnifiedBooksView",
                        "  Valid[$index]: id='${book.id.take(20)}', " +
                            "title='${book.title.take(40)}', " +
                            "author='${book.author.take(30)}'",
                    )
                }
            }
        } else {
            android.util.Log.w("UnifiedBooksView", "⚠️ Empty books list provided")
        }
    }

    // Get WindowSizeClass from parameter or calculate from LocalContext
    val context = LocalContext.current
    val effectiveWindowSizeClass =
        windowSizeClass
            ?: calculateWindowSizeClass(
                context as? android.app.Activity
                    ?: (context as? androidx.appcompat.view.ContextThemeWrapper)?.baseContext as? android.app.Activity
                    ?: throw IllegalStateException("Cannot get Activity from context"),
            )

    when {
        displayMode.isGrid() ->
            BooksGridLayout(
                books = books,
                displayMode = displayMode,
                actionsProvider = actionsProvider,
                windowSizeClass = effectiveWindowSizeClass,
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
                windowSizeClass = effectiveWindowSizeClass,
                isSelectionMode = isSelectionMode,
                selectedIds = selectedIds,
                onToggleSelection = onToggleSelection,
                modifier = modifier,
            )
    }
}

/**
 * Grid layout for books with adaptive columns and spacing.
 */
@Composable
private fun BooksGridLayout(
    books: List<Book>,
    displayMode: BookDisplayMode,
    actionsProvider: BookActionsProvider,
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onToggleSelection: ((String) -> Unit)? = null,
) {
    val gridCells = displayMode.getGridCells(windowSizeClass) ?: return
    val contentPadding = AdaptiveUtils.getContentPadding(windowSizeClass)
    val itemSpacing = AdaptiveUtils.getItemSpacing(windowSizeClass)
    val context = LocalContext.current

    // Create grid state for preloading
    val gridState = rememberLazyGridState()

    // Preload covers for visible and upcoming books
    rememberCoverPreloaderForGrid(
        books = books,
        gridState = gridState,
        context = context,
        preloadAhead = 10, // Preload 10 items ahead for grid (more items visible)
    )

    LazyVerticalGrid(
        state = gridState,
        columns = gridCells,
        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
        contentPadding = PaddingValues(contentPadding),
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
 * List layout for books with adaptive padding and spacing.
 */
@Composable
private fun BooksListLayout(
    books: List<Book>,
    displayMode: BookDisplayMode,
    actionsProvider: BookActionsProvider,
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onToggleSelection: ((String) -> Unit)? = null,
) {
    val contentPadding = AdaptiveUtils.getContentPadding(windowSizeClass)
    val itemSpacing = AdaptiveUtils.getItemSpacing(windowSizeClass)
    val context = LocalContext.current

    // Create list state for preloading
    val listState = rememberLazyListState()

    // Preload covers for visible and upcoming books
    rememberCoverPreloader(
        books = books,
        listState = listState,
        context = context,
        preloadAhead = 5, // Preload 5 items ahead for list
    )

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = contentPadding, vertical = contentPadding * 0.75f),
        verticalArrangement = Arrangement.spacedBy(itemSpacing * 0.75f),
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

// Removed isTabletDevice() - now using WindowSizeClass for better adaptive behavior
