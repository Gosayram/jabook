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

package com.jabook.app.jabook.compose.designsystem.component

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.util.CoverUtils
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.BookActionsProvider
import com.jabook.app.jabook.compose.domain.model.BookDisplayMode

/**
 * Unified book card component that adapts its layout based on display mode.
 *
 * This component consolidates all book display logic into a single composable,
 * supporting both grid and list layouts with consistent functionality.
 *
 * Features:
 * - Progress indicator (if book.progress > 0 and showProgress is enabled)
 * - Favorite button (if showFavoriteButton is enabled)
 * - Long press support
 * - Adaptive layout based on display mode
 *
 * @param book The book to display
 * @param displayMode The display mode (Grid or List variant)
 * @param actionsProvider Provider for all book actions
 * @param modifier Modifier for the card
 */
@Composable
fun UnifiedBookCard(
    book: Book,
    displayMode: BookDisplayMode,
    actionsProvider: BookActionsProvider,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: (() -> Unit)? = null,
) {
    when {
        displayMode.isGrid() ->
            GridBookCard(
                book = book,
                actionsProvider = actionsProvider,
                imageModifier = imageModifier,
                isSelectionMode = isSelectionMode,
                isSelected = isSelected,
                onToggleSelection = onToggleSelection,
                modifier = modifier,
            )
        displayMode.isList() ->
            ListBookCard(
                book = book,
                displayMode = displayMode,
                actionsProvider = actionsProvider,
                imageModifier = imageModifier,
                isSelectionMode = isSelectionMode,
                isSelected = isSelected,
                onToggleSelection = onToggleSelection,
                modifier = modifier,
            )
    }
}

/**
 * Grid variant of book card (vertical layout).
 */
@Composable
private fun GridBookCard(
    book: Book,
    actionsProvider: BookActionsProvider,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: (() -> Unit)? = null,
) {
    val isFavorite = actionsProvider.isFavorite(book.id)

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { actionsProvider.onBookClick(book.id) },
                    onLongClick = { actionsProvider.onBookLongPress(book.id) },
                ),
    ) {
        Column {
            // Cover image with favorite button overlay
            Box {
                // Selection checkbox overlay in top-left
                if (isSelectionMode && onToggleSelection != null) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelection() },
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp),
                    )
                }

                val context = LocalContext.current
                AsyncImage(
                    model = CoverUtils.getCoverModel(book, context),
                    contentDescription = book.title,
                    modifier =
                        imageModifier.then(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.7f),
                        ),
                    contentScale = ContentScale.Crop,
                )

                // Favorite button in top-right corner
                if (actionsProvider.showFavoriteButton) {
                    IconButton(
                        onClick = { actionsProvider.onToggleFavorite(book.id, !isFavorite) },
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(
                            imageVector =
                                if (isFavorite) {
                                    Icons.Filled.Favorite
                                } else {
                                    Icons.Outlined.FavoriteBorder
                                },
                            contentDescription =
                                if (isFavorite) {
                                    stringResource(R.string.removeFromFavorites)
                                } else {
                                    stringResource(R.string.addToFavorites)
                                },
                            tint =
                                if (isFavorite) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                        )
                    }
                }
            }

            // Progress indicator
            if (actionsProvider.showProgress && book.progress > 0f) {
                LinearProgressIndicator(
                    progress = { book.progress },
                    modifier = Modifier.fillMaxWidth(),
                )

                // Download progress indicator overlay
                if (actionsProvider.showDownloadStatus && book.isDownloading) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            progress = { book.downloadProgress },
                            modifier = Modifier.size(56.dp),
                            strokeWidth = 4.dp,
                        )
                        Text(
                            text = "${(book.downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
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
 * List variant of book card (horizontal layout).
 */
@Composable
private fun ListBookCard(
    book: Book,
    displayMode: BookDisplayMode,
    actionsProvider: BookActionsProvider,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: (() -> Unit)? = null,
) {
    val isFavorite = actionsProvider.isFavorite(book.id)
    val coverSize = displayMode.getListCoverSize() ?: 48 // Default to 48dp

    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { actionsProvider.onBookClick(book.id) },
                        onLongClick = { actionsProvider.onBookLongPress(book.id) },
                    ).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Selection checkbox at the start
            if (isSelectionMode && onToggleSelection != null) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Cover image
            Box {
                val context = LocalContext.current
                AsyncImage(
                    model = CoverUtils.getCoverModel(book, context),
                    contentDescription = book.title,
                    modifier = Modifier.size(coverSize.dp),
                    contentScale = ContentScale.Crop,
                )

                // Download progress indicator for list mode
                if (actionsProvider.showDownloadStatus && book.isDownloading) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                                .size(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            progress = { book.downloadProgress },
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp,
                        )
                        Text(
                            text = "${(book.downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title, author and progress
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (displayMode == BookDisplayMode.LIST_COMPACT) 1 else 2,
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

                // Progress indicator in the text column
                if (actionsProvider.showProgress && book.progress > 0f) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { book.progress },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
            }

            // Favorite toggle button
            if (actionsProvider.showFavoriteButton) {
                IconButton(
                    onClick = { actionsProvider.onToggleFavorite(book.id, !isFavorite) },
                ) {
                    Icon(
                        imageVector =
                            if (isFavorite) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Outlined.FavoriteBorder
                            },
                        contentDescription =
                            if (isFavorite) {
                                stringResource(R.string.removeFromFavorites)
                            } else {
                                stringResource(R.string.addToFavorites)
                            },
                        tint =
                            if (isFavorite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        }
    }
}
