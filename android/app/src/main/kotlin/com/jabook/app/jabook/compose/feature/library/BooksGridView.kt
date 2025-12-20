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

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.data.model.LibraryViewMode
import com.jabook.app.jabook.compose.domain.model.Book

/**
 * Grid view for displaying books with adaptive column count.
 *
 * **DEPRECATED:** Use [UnifiedBooksView] with [BookDisplayMode.GRID_COMPACT] or
 * [BookDisplayMode.GRID_COMFORTABLE] instead.
 *
 * @param books List of books to display
 * @param viewMode Current grid view mode (GRID_COMPACT or GRID_COMFORTABLE)
 * @param onBookClick Callback when book is clicked
 * @param onToggleFavorite Callback to toggle favorite status
 * @param onBookLongPress Callback when book is long-pressed
 * @param modifier Modifier
 *
 * @see com.jabook.app.jabook.compose.feature.library.UnifiedBooksView
 * @see com.jabook.app.jabook.compose.domain.model.BookDisplayMode
 */
@Deprecated(
    message = "Use UnifiedBooksView with BookDisplayMode.GRID_COMPACT or GRID_COMFORTABLE instead",
    replaceWith =
        ReplaceWith(
            "UnifiedBooksView(books, displayMode, actionsProvider)",
            "com.jabook.app.jabook.compose.feature.library.UnifiedBooksView",
            "com.jabook.app.jabook.compose.domain.model.BookDisplayMode",
        ),
)
@Composable
fun BooksGridView(
    books: List<Book>,
    viewMode: LibraryViewMode,
    onBookClick: (String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onBookLongPress: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTablet = isTabletDevice()

    val columns =
        when (viewMode) {
            LibraryViewMode.GRID_COMPACT ->
                GridCells.Fixed(if (isTablet) 6 else 3)
            LibraryViewMode.GRID_COMFORTABLE ->
                GridCells.Fixed(if (isTablet) 4 else 2)
            else -> GridCells.Fixed(2) // Fallback
        }

    LazyVerticalGrid(
        columns = columns,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = modifier,
    ) {
        items(books, key = { it.id }) { book ->
            BookGridItem(
                book = book,
                onBookClick = onBookClick,
                onToggleFavorite = onToggleFavorite,
                onBookLongPress = onBookLongPress,
            )
        }
    }
}

/**
 * Single book grid item with cover and title.
 */
@Composable
private fun BookGridItem(
    book: Book,
    onBookClick: (String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onBookLongPress: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = { onBookClick(book.id) },
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onBookClick(book.id) },
                    onLongClick = { onBookLongPress(book.id) },
                ),
    ) {
        Column {
            // Cover image with favorite button overlay
            Box {
                AsyncImage(
                    model = book.coverUrl,
                    contentDescription = book.title,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.7f),
                    contentScale = ContentScale.Crop,
                )

                // Favorite button in top-right corner
                IconButton(
                    onClick = { onToggleFavorite(book.id, !book.isFavorite) },
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(
                        imageVector =
                            if (book.isFavorite) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Outlined.FavoriteBorder
                            },
                        contentDescription =
                            if (book.isFavorite) {
                                stringResource(R.string.removeFromFavorites)
                            } else {
                                stringResource(R.string.addToFavorites)
                            },
                        tint =
                            if (book.isFavorite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                }
            }

            // Progress indicator
            if (book.progress > 0f) {
                LinearProgressIndicator(
                    progress = { book.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Title and author
            Column(
                modifier = Modifier.padding(8.dp),
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.author.isNotBlank()) {
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
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
