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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.domain.model.Book

/**
 * Grouped view for audiobooks library.
 *
 * Groups books alphabetically with sticky headers for each letter.
 * Uses LazyColumn with sticky header support for efficient scrolling.
 *
 * @param books List of books to display
 * @param onBookClick Callback when book is clicked
 * @param onToggleFavorite Callback to toggle favorite status
 * @param modifier Modifier
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BooksGroupedView(
    books: List<Book>,
    onBookClick: (String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Group books by first letter
    val groupedBooks =
        books
            .groupBy { book ->
                book.title.firstOrNull()?.uppercaseChar() ?: '#'
            }.toSortedMap()

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groupedBooks.forEach { (letter, booksInGroup) ->
            // Sticky header for letter
            stickyHeader(key = letter) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = letter.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }

            // Books in this letter group
            items(booksInGroup, key = { it.id }) { book ->
                BookListItem(
                    book = book,
                    onBookClick = onBookClick,
                    onToggleFavorite = onToggleFavorite,
                )
            }
        }
    }
}

/**
 * Single book list item for grouped view.
 */
@Composable
private fun BookListItem(
    book: Book,
    onBookClick: (String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = { onBookClick(book.id) },
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Cover thumbnail
            AsyncImage(
                model = book.coverUrl,
                contentDescription = book.title,
                modifier =
                    Modifier
                        .size(60.dp)
                        .aspectRatio(0.7f),
                contentScale = ContentScale.Crop,
            )

            // Book info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (book.author.isNotBlank()) {
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Favorite button
            IconButton(
                onClick = { onToggleFavorite(book.id, !book.isFavorite) },
                modifier = Modifier.align(Alignment.CenterVertically),
            ) {
                Icon(
                    imageVector =
                        if (book.isFavorite) {
                            Icons.Default.Favorite
                        } else {
                            Icons.Default.FavoriteBorder
                        },
                    contentDescription =
                        if (book.isFavorite) {
                            stringResource(
                                R.string.removeFromFavorites,
                            )
                        } else {
                            stringResource(R.string.addToFavorites)
                        },
                    tint =
                        if (book.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}
