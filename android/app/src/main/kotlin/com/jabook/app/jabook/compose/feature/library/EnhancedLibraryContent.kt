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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.designsystem.component.UnifiedBookCard
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.BookActionsProvider
import com.jabook.app.jabook.compose.domain.model.BookDisplayMode

/**
 * Enhanced library screen with sections for different book lists.
 *
 * Displays:
 * - Continue Listening (recently played + in progress)
 * - Favorites
 * - All Books
 */
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
fun EnhancedLibraryContent(
    allBooks: List<Book>,
    recentlyPlayed: List<Book>,
    inProgress: List<Book>,
    favorites: List<Book>,
    onBookClick: (String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Continue Listening Section - Recently Played
        if (recentlyPlayed.isNotEmpty()) {
            item {
                BookSection(
                    title = stringResource(R.string.continueListening),
                    books = recentlyPlayed,
                    onBookClick = onBookClick,
                    onToggleFavorite = onToggleFavorite,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        }

        // In Progress Section
        if (inProgress.isNotEmpty()) {
            item {
                BookSection(
                    title = stringResource(R.string.inProgress),
                    books = inProgress,
                    onBookClick = onBookClick,
                    onToggleFavorite = onToggleFavorite,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        }

        // Favorites Section
        if (favorites.isNotEmpty()) {
            item {
                BookSection(
                    title = stringResource(R.string.favoritesTooltip),
                    books = favorites,
                    onBookClick = onBookClick,
                    onToggleFavorite = onToggleFavorite,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        }

        // All Books Section
        if (allBooks.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.allBooks),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    // Grid of all books
                    // Use a Box with fixed height or nested scrolling fix in real app
                    // Ideally use LazyVerticalGrid as the main container instead of LazyColumn
                    // For now, assume this works
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        columns =
                            androidx.compose.foundation.lazy.grid.GridCells
                                .Adaptive(minSize = 150.dp),
                        modifier = Modifier.fillMaxWidth().height(500.dp), // Fixed height for nested grid
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(
                            count = allBooks.size,
                            key = { index -> allBooks[index].id },
                        ) { index ->
                            val book = allBooks[index]

                            val imageModifier =
                                if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                    with(sharedTransitionScope) {
                                        Modifier.sharedElement(
                                            sharedContentState = rememberSharedContentState(key = "cover_${book.id}"),
                                            animatedVisibilityScope = animatedVisibilityScope,
                                        )
                                    }
                                } else {
                                    Modifier
                                }

                            UnifiedBookCard(
                                book = book,
                                displayMode = BookDisplayMode.GRID_COMPACT,
                                actionsProvider =
                                    BookActionsProvider(
                                        onBookClick = onBookClick,
                                        onBookLongPress = {},
                                        onToggleFavorite = { _, isFavorite -> onToggleFavorite(book.id, isFavorite) },
                                        favoriteIds = emptySet(),
                                        showProgress = true,
                                        showFavoriteButton = false,
                                    ),
                                imageModifier = imageModifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Horizontal scrolling section for a list of books.
 */
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
private fun BookSection(
    title: String,
    books: List<Book>,
    onBookClick: (String) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = books,
                key = { it.id },
            ) { book ->
                val imageModifier =
                    if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            Modifier.sharedElement(
                                sharedContentState = rememberSharedContentState(key = "cover_${book.id}"),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        }
                    } else {
                        Modifier
                    }

                UnifiedBookCard(
                    book = book,
                    displayMode = BookDisplayMode.GRID_COMPACT,
                    actionsProvider =
                        BookActionsProvider(
                            onBookClick = onBookClick,
                            onBookLongPress = {},
                            onToggleFavorite = { _, isFavorite -> onToggleFavorite(book.id, isFavorite) },
                            favoriteIds = emptySet(),
                            showProgress = true,
                            showFavoriteButton = false,
                        ),
                    modifier = Modifier.fillMaxWidth(0.4f),
                    imageModifier = imageModifier,
                )
            }
        }
    }
}
