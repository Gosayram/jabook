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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.compose.core.logger.LoggerFactoryImpl
import com.jabook.app.jabook.compose.core.util.AdaptiveUtils
import com.jabook.app.jabook.compose.core.util.rememberCoverPreloader
import com.jabook.app.jabook.compose.core.util.rememberCoverPreloaderForGrid
import com.jabook.app.jabook.compose.designsystem.component.UnifiedBookCard
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.BookActionsProvider
import com.jabook.app.jabook.compose.domain.model.BookDisplayMode
import kotlinx.coroutines.launch

/**
 * Logger for UnifiedBooksView Composable functions.
 */
private val unifiedBooksViewLogger by lazy { LoggerFactoryImpl().get("UnifiedBooksView") }

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
        unifiedBooksViewLogger.d {
            "📚 Rendering ${books.size} books in $displayMode mode"
        }
        if (books.isNotEmpty()) {
            val invalidBooks = books.filter { it.title.isBlank() || it.author.isBlank() || it.id.isBlank() }
            if (invalidBooks.isNotEmpty()) {
                unifiedBooksViewLogger.w {
                    "⚠️ Found ${invalidBooks.size} books with empty/invalid data out of ${books.size} total"
                }
                invalidBooks.take(3).forEachIndexed { index, book ->
                    unifiedBooksViewLogger.w {
                        "  Invalid[$index]: id='${book.id.take(20)}', " +
                            "title='${book.title.take(30)}', " +
                            "author='${book.author.take(20)}', " +
                            "coverUrl=${if (book.coverUrl.isNullOrBlank()) "null/empty" else "present"}"
                    }
                }
            }
            // Log sample of valid books
            val validBooks = books.filter { it.title.isNotBlank() && it.author.isNotBlank() && it.id.isNotBlank() }
            if (validBooks.isNotEmpty()) {
                val sample = validBooks.take(2)
                sample.forEachIndexed { index, book ->
                    unifiedBooksViewLogger.d {
                        "  Valid[$index]: id='${book.id.take(20)}', " +
                            "title='${book.title.take(40)}', " +
                            "author='${book.author.take(30)}'"
                    }
                }
            }
        } else {
            unifiedBooksViewLogger.w { "⚠️ Empty books list provided" }
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
    val configuration = LocalConfiguration.current
    val isVeryNarrow = configuration.screenWidthDp < 360
    val gridCells =
        remember(displayMode, windowSizeClass, configuration.screenWidthDp) {
            if (isVeryNarrow) {
                GridCells.Fixed(1)
            } else {
                when {
                    configuration.screenWidthDp >= 840 -> GridCells.Fixed(4)
                    configuration.screenWidthDp >= 600 -> GridCells.Fixed(3)
                    else -> GridCells.Fixed(2)
                }
            }
        }
    val contentPadding = remember(windowSizeClass) { AdaptiveUtils.getContentPadding(windowSizeClass) }
    val itemSpacing = remember(windowSizeClass) { AdaptiveUtils.getItemSpacing(windowSizeClass) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Create grid state for preloading
    val gridState = rememberLazyGridState()

    // derivedStateOf: recalculates only when firstVisibleItemIndex changes, not on every recompose
    val showScrollToTop by remember { derivedStateOf { gridState.firstVisibleItemIndex > 3 } }

    // Preload covers for visible and upcoming books
    rememberCoverPreloaderForGrid(
        books = books,
        gridState = gridState,
        context = context,
        preloadAhead = 10, // Preload 10 items ahead for grid (more items visible)
    )

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = gridCells,
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            contentPadding = PaddingValues(contentPadding),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                items = books,
                key = { it.id },
                contentType = { "book_grid_${displayMode.name}" },
            ) { book ->
                SwipeableBookCard(
                    book = book,
                    displayMode = displayMode,
                    actionsProvider = actionsProvider,
                    isSelectionMode = isSelectionMode,
                    isSelected = selectedIds.contains(book.id),
                    onToggleSelection = { onToggleSelection?.invoke(book.id) },
                )
            }
        }

        AnimatedVisibility(
            visible = showScrollToTop,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            FloatingActionButton(
                onClick = { scope.launch { gridState.animateScrollToItem(0) } },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Scroll to top",
                )
            }
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
    val contentPadding = remember(windowSizeClass) { AdaptiveUtils.getContentPadding(windowSizeClass) }
    val itemSpacing = remember(windowSizeClass) { AdaptiveUtils.getItemSpacing(windowSizeClass) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Create list state for preloading
    val listState = rememberLazyListState()

    // derivedStateOf: recalculates only when firstVisibleItemIndex changes, not on every recompose
    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }

    // Preload covers for visible and upcoming books
    rememberCoverPreloader(
        books = books,
        listState = listState,
        context = context,
        preloadAhead = 5, // Preload 5 items ahead for list
    )

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = contentPadding, vertical = contentPadding * 0.75f),
            verticalArrangement = Arrangement.spacedBy(itemSpacing * 0.75f),
        ) {
            items(
                items = books,
                key = { it.id },
                contentType = { "book_list_${displayMode.name}" },
            ) { book ->
                SwipeableBookCard(
                    book = book,
                    displayMode = displayMode,
                    actionsProvider = actionsProvider,
                    isSelectionMode = isSelectionMode,
                    isSelected = selectedIds.contains(book.id),
                    onToggleSelection = { onToggleSelection?.invoke(book.id) },
                )
            }
        }

        AnimatedVisibility(
            visible = showScrollToTop,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            FloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Scroll to top",
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableBookCard(
    book: Book,
    displayMode: BookDisplayMode,
    actionsProvider: BookActionsProvider,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: (() -> Unit)?,
) {
    if (isSelectionMode || actionsProvider.onDeleteBook == null) {
        UnifiedBookCard(
            book = book,
            displayMode = displayMode,
            actionsProvider = actionsProvider,
            isSelectionMode = isSelectionMode,
            isSelected = isSelected,
            onToggleSelection = onToggleSelection,
        )
        return
    }

    val onDeleteBook = requireNotNull(actionsProvider.onDeleteBook)
    val dismissState = rememberSwipeToDismissBoxState()

    LaunchedEffect(dismissState.currentValue, book.id) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> {
                actionsProvider.onToggleFavorite(book.id, !actionsProvider.isFavorite(book.id))
                dismissState.reset()
            }

            SwipeToDismissBoxValue.EndToStart -> {
                onDeleteBook(book.id)
                dismissState.reset()
            }

            SwipeToDismissBoxValue.Settled -> Unit
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val isStartToEnd = direction == SwipeToDismissBoxValue.StartToEnd
            val isEndToStart = direction == SwipeToDismissBoxValue.EndToStart
            val backgroundColor =
                when {
                    isStartToEnd -> MaterialTheme.colorScheme.primaryContainer
                    isEndToStart -> MaterialTheme.colorScheme.errorContainer
                    else -> Color.Transparent
                }
            val contentColor =
                when {
                    isStartToEnd -> MaterialTheme.colorScheme.onPrimaryContainer
                    isEndToStart -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .background(backgroundColor),
            ) {
                if (isStartToEnd) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = contentColor,
                        modifier =
                            Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 20.dp),
                    )
                } else if (isEndToStart) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = contentColor,
                        modifier =
                            Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 20.dp),
                    )
                }
            }
        },
    ) {
        UnifiedBookCard(
            book = book,
            displayMode = displayMode,
            actionsProvider = actionsProvider,
            isSelectionMode = isSelectionMode,
            isSelected = isSelected,
            onToggleSelection = onToggleSelection,
        )
    }
}

// Removed isTabletDevice() - now using WindowSizeClass for better adaptive behavior
