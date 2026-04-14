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

package com.jabook.app.jabook.compose.designsystem.component

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.logger.LoggerFactoryImpl
import com.jabook.app.jabook.compose.core.util.AdaptiveUtils
import com.jabook.app.jabook.compose.core.util.CoverUtils
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.BookActionsProvider
import com.jabook.app.jabook.compose.domain.model.BookDisplayMode

/**
 * Logger for UnifiedBookCard Composable functions.
 */
private val unifiedBookCardLogger by lazy { LoggerFactoryImpl().get("UnifiedBookCard") }

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
public fun UnifiedBookCard(
    book: Book,
    displayMode: BookDisplayMode,
    actionsProvider: BookActionsProvider,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: (() -> Unit)? = null,
) {
    // Log if book has invalid/empty data
    androidx.compose.runtime.LaunchedEffect(book.id) {
        val hasEmptyTitle = book.title.isBlank()
        val hasEmptyAuthor = book.author.isBlank()
        val hasEmptyId = book.id.isBlank()
        if (hasEmptyTitle || hasEmptyAuthor || hasEmptyId) {
            unifiedBookCardLogger.w {
                "⚠️ Book card with invalid data: " +
                    "id='${book.id.take(20)}', " +
                    "title=${if (hasEmptyTitle) "EMPTY" else "'${book.title.take(30)}'"}, " +
                    "author=${if (hasEmptyAuthor) "EMPTY" else "'${book.author.take(20)}'"}, " +
                    "coverUrl=${if (book.coverUrl.isNullOrBlank()) "null/empty" else "present"}"
            }
        }
    }

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
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
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
    val context = LocalContext.current
    val windowSizeClass =
        calculateWindowSizeClass(
            context as? android.app.Activity
                ?: (context as? androidx.appcompat.view.ContextThemeWrapper)?.baseContext as? android.app.Activity
                ?: throw IllegalStateException("Cannot get Activity from context"),
        )
    val isFavorite = actionsProvider.isFavorite(book.id)

    // Glassmorphic Card Style
    val glassColors =
        androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        )
    val glassBorder =
        androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
        )

    Card(
        colors = glassColors,
        border = glassBorder,
        modifier =
            modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { actionsProvider.onBookClick(book.id) },
                    onLongClick = { actionsProvider.onBookLongPress(book.id) },
                ).semantics(mergeDescendants = true) {},
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
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                .padding(4.dp),
                    )
                }

                val context = LocalContext.current
                val imageRequest =
                    CoverUtils
                        .createCoverImageRequest(
                            book = book,
                            context = context,
                            placeholderColor = MaterialTheme.colorScheme.surfaceVariant,
                            errorColor = MaterialTheme.colorScheme.error,
                            fallbackColor = MaterialTheme.colorScheme.surfaceVariant,
                            cornerRadius = 8f, // 8dp rounded corners
                        ).build()

                AsyncImage(
                    model = imageRequest,
                    contentDescription = book.title,
                    modifier =
                        imageModifier.then(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.7f),
                        ),
                    contentScale = ContentScale.Crop,
                )

                // Favorite button in top-right corner with adaptive icon size
                if (actionsProvider.showFavoriteButton) {
                    IconButton(
                        onClick = { actionsProvider.onToggleFavorite(book.id, !isFavorite) },
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .sizeIn(minWidth = 48.dp, minHeight = 48.dp),
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
                            modifier = Modifier.size(AdaptiveUtils.getIconSize(windowSizeClass)),
                        )
                    }
                }

                if (actionsProvider.showDownloadStatus && book.isDownloading) {
                    DownloadProgressBadge(
                        progress = book.downloadProgress,
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp),
                    )
                }
            }

            // Progress indicator
            if (actionsProvider.showProgress && book.progress > 0f) {
                LinearProgressIndicator(
                    progress = { book.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Title and author with adaptive text sizes and improved spacing
            Column(
                modifier = Modifier.padding(AdaptiveUtils.getCardPadding(windowSizeClass)),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = book.title,
                    style =
                        AdaptiveUtils.getAdaptiveTextStyle(
                            MaterialTheme.typography.titleSmall,
                            windowSizeClass,
                        ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = MaterialTheme.typography.titleSmall.lineHeight,
                )
                if (book.author.isNotBlank()) {
                    Text(
                        text = book.author,
                        style =
                            AdaptiveUtils.getAdaptiveTextStyle(
                                MaterialTheme.typography.bodyMedium,
                                windowSizeClass,
                            ),
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
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
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
    val context = LocalContext.current
    val windowSizeClass =
        calculateWindowSizeClass(
            context as? android.app.Activity
                ?: (context as? androidx.appcompat.view.ContextThemeWrapper)?.baseContext as? android.app.Activity
                ?: throw IllegalStateException("Cannot get Activity from context"),
        )
    val isFavorite = actionsProvider.isFavorite(book.id)
    // Use adaptive cover size based on WindowSizeClass
    val coverSize =
        when (displayMode) {
            BookDisplayMode.LIST_COMPACT -> AdaptiveUtils.getCompactListCoverSize(windowSizeClass)
            BookDisplayMode.LIST_DEFAULT -> AdaptiveUtils.getListCoverSize(windowSizeClass)
            else -> displayMode.getListCoverSize()?.dp ?: 48.dp
        }

    // Glassmorphic Card Style
    val glassColors =
        androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        )
    val glassBorder =
        androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
        )

    Card(
        colors = glassColors,
        border = glassBorder,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { actionsProvider.onBookClick(book.id) },
                        onLongClick = { actionsProvider.onBookLongPress(book.id) },
                    ).semantics(mergeDescendants = true) {}
                    .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Selection checkbox at the start
            if (isSelectionMode && onToggleSelection != null) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Cover image
            Box {
                val context = LocalContext.current
                val imageRequest =
                    CoverUtils
                        .createCoverImageRequest(
                            book = book,
                            context = context,
                            placeholderColor = MaterialTheme.colorScheme.surfaceVariant,
                            errorColor = MaterialTheme.colorScheme.error,
                            fallbackColor = MaterialTheme.colorScheme.surfaceVariant,
                            cornerRadius = 8f, // 8dp rounded corners
                        ).build()

                AsyncImage(
                    model = imageRequest,
                    contentDescription = book.title,
                    modifier = Modifier.size(coverSize),
                    contentScale = ContentScale.Crop,
                )

                // Download progress indicator for list mode
                if (actionsProvider.showDownloadStatus && book.isDownloading) {
                    DownloadProgressBadge(
                        progress = book.downloadProgress,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title, author and progress with improved spacing
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = if (displayMode == BookDisplayMode.LIST_COMPACT) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = MaterialTheme.typography.titleMedium.lineHeight,
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
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
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

@Composable
private fun DownloadProgressBadge(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val clampedPercent = (progress.coerceIn(0f, 1f) * 100).toInt()
    Box(
        modifier =
            modifier
                .semantics(mergeDescendants = true) {}
                .padding(2.dp),
    ) {
        Text(
            text = stringResource(R.string.downloadProgressBadge, clampedPercent),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier =
                Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        shape =
                            androidx.compose.foundation.shape
                                .RoundedCornerShape(999.dp),
                    ).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
