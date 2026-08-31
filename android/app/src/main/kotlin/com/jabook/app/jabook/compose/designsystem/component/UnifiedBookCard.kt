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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.logger.LoggerFactoryImpl
import com.jabook.app.jabook.compose.core.util.AdaptiveUtils
import com.jabook.app.jabook.compose.core.util.CoverUtils
import com.jabook.app.jabook.compose.data.model.DownloadStatus
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
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
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
    windowSizeClass: WindowSizeClass? = null,
) {
    // Log if book has invalid/empty data
    LaunchedEffect(book.id) {
        val hasEmptyTitle = book.title.isBlank()
        val hasEmptyAuthor = book.author.isBlank()
        val hasEmptyId = book.id.isBlank()
        if (hasEmptyTitle || hasEmptyAuthor || hasEmptyId) {
            unifiedBookCardLogger.w {
                "Book card with invalid data: " +
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
                windowSizeClass = windowSizeClass,
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
                windowSizeClass = windowSizeClass,
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
    windowSizeClass: WindowSizeClass? = null,
) {
    val haptic = LocalHapticFeedback.current
    // Ponytail: non-null fallback avoids scattering null checks across AdaptiveUtils calls
    val effectiveWSC =
        windowSizeClass ?: WindowSizeClass.calculateFromSize(
            DpSize(360.dp, 800.dp),
        )
    val isFavorite = actionsProvider.isFavorite(book.id)

    // Glassmorphic Card Style
    // ponytail: surface at 0.6f glassmorphic; fallback to surfaceContainer token when spec requires opaque (cards/page.md 12dp)
    val glassColors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        )
    val glassBorder =
        BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
        )

    Card(
        shape = CardDefaults.shape, // M3 medium = 12dp per cards/page.md
        colors = glassColors,
        border = glassBorder,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(CardDefaults.shape)
                .combinedClickable(
                    onClick = { actionsProvider.onBookClick(book.id) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        actionsProvider.onBookLongPress(book.id)
                    },
                ).semantics(mergeDescendants = true) {},
    ) {
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
            val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
            val errorColor = MaterialTheme.colorScheme.error
            val imageRequest =
                remember(book.id, book.coverUrl, book.localPath, context, placeholderColor, errorColor) {
                    CoverUtils
                        .createCoverImageRequest(
                            book = book,
                            context = context,
                            placeholderColor = placeholderColor,
                            errorColor = errorColor,
                            fallbackColor = placeholderColor,
                            cornerRadius = 12f, // 12dp per M3 cards spec (was 8dp)
                        ).build()
                }

            AsyncImage(
                model = imageRequest,
                contentDescription = book.title,
                modifier =
                    imageModifier.then(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.72f),
                    ),
                contentScale = ContentScale.Crop,
            )

            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .border(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            shape =
                                RoundedCornerShape(12.dp),
                        ),
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
                        modifier = Modifier.size(AdaptiveUtils.getIconSize(effectiveWSC)),
                    )
                }
            }

            // Shift badges below the selection checkbox to avoid overlap in selection mode
            val badgeTopPadding = if (isSelectionMode && onToggleSelection != null) 52.dp else 8.dp

            if (actionsProvider.showDownloadStatus && book.isDownloading) {
                DownloadProgressBadge(
                    progress = book.downloadProgress,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 8.dp, top = badgeTopPadding),
                )
            } else if (actionsProvider.showDownloadStatus) {
                DownloadStatusBadge(
                    status = book.downloadStatus,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 8.dp, top = badgeTopPadding),
                )
            } else if (book.isCompleted) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 8.dp, top = badgeTopPadding)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                RoundedCornerShape(4.dp),
                            ).padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }

            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    listOf(
                                        Color.Transparent,
                                        Color.Black
                                            .copy(alpha = 0.78f),
                                    ),
                            ),
                        ).padding(AdaptiveUtils.getCardPadding(effectiveWSC)),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = book.title,
                        style =
                            AdaptiveUtils.getAdaptiveTextStyle(
                                MaterialTheme.typography.titleSmall,
                                effectiveWSC,
                            ),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (book.author.isNotBlank()) {
                        Text(
                            text = book.author,
                            style =
                                AdaptiveUtils.getAdaptiveTextStyle(
                                    MaterialTheme.typography.bodySmall,
                                    effectiveWSC,
                                ),
                            color =
                                Color.White
                                    .copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!book.narrator.isNullOrBlank()) {
                        Text(
                            text = book.narrator,
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                Color.White
                                    .copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (actionsProvider.showProgress && book.progress > 0f) {
                ThinProgressBar(
                    progress = book.progress,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                    trackColor =
                        Color.White
                            .copy(alpha = 0.2f),
                    progressColor = MaterialTheme.colorScheme.primary,
                    height = 2.dp,
                )
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
    windowSizeClass: WindowSizeClass? = null,
) {
    val haptic = LocalHapticFeedback.current
    // Ponytail: non-null fallback avoids scattering null checks across AdaptiveUtils calls
    val effectiveWSC =
        windowSizeClass ?: WindowSizeClass.calculateFromSize(
            DpSize(360.dp, 800.dp),
        )
    val isFavorite = actionsProvider.isFavorite(book.id)
    // Use adaptive cover size based on WindowSizeClass
    val coverSize =
        when (displayMode) {
            BookDisplayMode.LIST_COMPACT -> AdaptiveUtils.getCompactListCoverSize(effectiveWSC)
            BookDisplayMode.LIST_DEFAULT -> AdaptiveUtils.getListCoverSize(effectiveWSC)
            else -> displayMode.getListCoverSize()?.dp ?: 48.dp
        }

    // Glassmorphic Card Style
    // ponytail: surface at 0.6f glassmorphic; fallback to surfaceContainer token when spec requires opaque (cards/page.md 12dp)
    val glassColors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        )
    val glassBorder =
        BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
        )

    Card(
        shape = CardDefaults.shape, // M3 medium = 12dp per cards/page.md
        colors = glassColors,
        border = glassBorder,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(CardDefaults.shape)
                    .combinedClickable(
                        onClick = { actionsProvider.onBookClick(book.id) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            actionsProvider.onBookLongPress(book.id)
                        },
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
                val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
                val errorColor = MaterialTheme.colorScheme.error
                val imageRequest =
                    remember(book.id, book.coverUrl, book.localPath, context, placeholderColor, errorColor) {
                        CoverUtils
                            .createCoverImageRequest(
                                book = book,
                                context = context,
                                placeholderColor = placeholderColor,
                                errorColor = errorColor,
                                fallbackColor = placeholderColor,
                                cornerRadius = 12f, // 12dp per M3 cards spec (was 8dp)
                            ).build()
                    }

                AsyncImage(
                    model = imageRequest,
                    contentDescription = book.title,
                    modifier = imageModifier.then(Modifier.size(coverSize)),
                    contentScale = ContentScale.Crop,
                )

                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .border(
                                width = 0.5.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                shape =
                                    RoundedCornerShape(12.dp),
                            ),
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
                } else if (actionsProvider.showDownloadStatus) {
                    DownloadStatusBadge(
                        status = book.downloadStatus,
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
                if (!book.narrator.isNullOrBlank()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Headphones,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        Text(
                            text = book.narrator,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (book.isCompleted) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.status_completed),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else if (book.progress > 0f && actionsProvider.showProgress) {
                    val remaining = book.remainingDuration
                    if (remaining.inWholeMinutes > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                            Text(
                                text = stringResource(R.string.minutes_remaining, remaining.inWholeMinutes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                } else {
                    // New/not-started books: show total duration
                    val totalMin = book.totalDuration.inWholeMinutes
                    if (totalMin > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                            Text(
                                text = stringResource(R.string.total_duration_minutes, totalMin),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }

                // Progress indicator in the text column
                if (actionsProvider.showProgress && book.progress > 0f) {
                    Spacer(modifier = Modifier.height(4.dp))
                    ThinProgressBar(
                        progress = book.progress,
                        modifier = Modifier.fillMaxWidth(),
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        progressColor = MaterialTheme.colorScheme.primary,
                        height = 4.dp,
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
private fun DownloadStatusBadge(
    status: DownloadStatus,
    modifier: Modifier = Modifier,
) {
    val (labelRes, containerColor, contentColor) =
        when (status) {
            DownloadStatus.DOWNLOADED ->
                Triple(
                    R.string.downloadedLabel,
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f),
                    MaterialTheme.colorScheme.onTertiaryContainer,
                )
            DownloadStatus.FAILED ->
                Triple(
                    R.string.downloadFailedLabel,
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                    MaterialTheme.colorScheme.onErrorContainer,
                )
            DownloadStatus.NOT_DOWNLOADED ->
                Triple(
                    R.string.streamingLabel,
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
            DownloadStatus.DOWNLOADING ->
                return
        }

    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = contentColor,
        modifier =
            modifier
                .background(
                    color = containerColor,
                    shape =
                        RoundedCornerShape(999.dp),
                ).padding(horizontal = 8.dp, vertical = 4.dp),
    )
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
                            RoundedCornerShape(999.dp),
                    ).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
