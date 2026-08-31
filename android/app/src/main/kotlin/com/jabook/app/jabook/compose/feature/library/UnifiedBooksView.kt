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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.theme.SpacingTokens
import com.jabook.app.jabook.compose.core.util.AdaptiveUtils
import com.jabook.app.jabook.compose.core.util.LocalWindowSizeClass
import com.jabook.app.jabook.compose.core.util.rememberCoverPreloader
import com.jabook.app.jabook.compose.core.util.rememberCoverPreloaderForGrid
import com.jabook.app.jabook.compose.designsystem.component.UnifiedBookCard
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.BookActionsProvider
import com.jabook.app.jabook.compose.domain.model.BookDisplayMode
import kotlinx.coroutines.launch

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
    // Get WindowSizeClass from parameter or calculate from LocalContext
    val context = LocalContext.current
    val effectiveWindowSizeClass =
        windowSizeClass
            ?: LocalWindowSizeClass.current?.let {
                AdaptiveUtils.resolveWindowSizeClassOrNull(it, context)
            }

    when {
        displayMode.isGrid() ->
            BooksGridLayout(
                books = books,
                displayMode = displayMode,
                actionsProvider = actionsProvider,
                windowSizeClass =
                    effectiveWindowSizeClass
                        ?: WindowSizeClass.calculateFromSize(
                            DpSize(360.dp, 800.dp),
                        ),
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
                windowSizeClass =
                    effectiveWindowSizeClass
                        ?: WindowSizeClass.calculateFromSize(
                            DpSize(360.dp, 800.dp),
                        ),
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
    val isWide = configuration.screenWidthDp >= 840
    // ponytail: Ruler API (HorizontalRuler/VerticalRuler) available in Compose UI 1.7+ but skipped —
    // grid already 8dp-aligned via SpacingTokens; wire ruler when header/grid ruler misaligns.
    val gridCells =
        remember(displayMode, windowSizeClass, configuration.screenWidthDp) {
            if (isVeryNarrow) {
                GridCells.Fixed(1)
            } else if (isWide) {
                GridCells.Adaptive(minSize = SpacingTokens.GridMinCellExpanded)
            } else {
                GridCells.Adaptive(minSize = SpacingTokens.GridMinCellCompact)
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
                    windowSizeClass = windowSizeClass,
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
                    contentDescription = stringResource(R.string.scrollToTop),
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
        // Use two-column grid for compact list on expanded layouts
        val isExpandedWidth = AdaptiveUtils.isLargeScreen(windowSizeClass)
        if (isExpandedWidth && displayMode == BookDisplayMode.LIST_COMPACT) {
            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                columns =
                    GridCells
                        .Fixed(2),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = contentPadding, vertical = contentPadding * 0.75f),
                verticalArrangement = Arrangement.spacedBy(itemSpacing * 0.75f),
                horizontalArrangement = Arrangement.spacedBy(itemSpacing * 0.75f),
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
                        windowSizeClass = windowSizeClass,
                    )
                }
            }
        } else {
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
                        windowSizeClass = windowSizeClass,
                    )
                }
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
                    contentDescription = stringResource(R.string.scrollToTop),
                )
            }
        }
    }
}

@Composable
private fun SwipeableBookCard(
    book: Book,
    displayMode: BookDisplayMode,
    actionsProvider: BookActionsProvider,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: (() -> Unit)?,
    windowSizeClass: WindowSizeClass? = null,
) {
    UnifiedBookCard(
        book = book,
        displayMode = displayMode,
        actionsProvider = actionsProvider,
        isSelectionMode = isSelectionMode,
        isSelected = isSelected,
        onToggleSelection = onToggleSelection,
        windowSizeClass = windowSizeClass,
    )
}

// Removed isTabletDevice() - now using WindowSizeClass for better adaptive behavior
