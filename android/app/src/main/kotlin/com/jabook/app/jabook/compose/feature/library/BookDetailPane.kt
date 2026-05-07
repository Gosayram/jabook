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

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.util.AdaptiveUtils
import com.jabook.app.jabook.compose.core.util.CoverUtils
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.Chapter

/**
 * Book detail pane component for displaying book information in list-detail layout.
 *
 * This component is shown in the detail pane of ListDetailPaneScaffold on medium/expanded screens.
 * It displays the book cover, metadata, action buttons, and a preview of chapters.
 *
 * @param book The book to display details for
 * @param onPlayClick Callback when the play button is clicked
 * @param onClose Callback when the close button is clicked
 * @param onToggleFavorite Callback when favorite button is toggled
 * @param modifier Modifier for the root composable
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
public fun BookDetailPane(
    book: Book?,
    chapters: List<Chapter>,
    onPlayClick: () -> Unit,
    onClose: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    val context = LocalContext.current
    val windowSizeClass =
        calculateWindowSizeClass(
            context as? android.app.Activity
                ?: (context as? androidx.appcompat.view.ContextThemeWrapper)?.baseContext as? android.app.Activity
                ?: throw IllegalStateException("Cannot get Activity from context"),
        )
    val maxContentWidth = AdaptiveUtils.getMaxContentWidth(windowSizeClass)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.bookDetails)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        if (book == null) {
            // Empty state when no book is selected
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.selectBookToViewDetails),
                    style =
                        AdaptiveUtils.getAdaptiveTextStyle(
                            MaterialTheme.typography.bodyLarge,
                            windowSizeClass,
                        ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Book details content with adaptive max width and centering
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                val listState = rememberLazyListState()
                val parallaxOffset by remember {
                    derivedStateOf {
                        val isCoverVisible = listState.firstVisibleItemIndex == 0
                        val scrollOffset = listState.firstVisibleItemScrollOffset.toFloat()
                        if (isCoverVisible) {
                            scrollOffset * 0.35f
                        } else {
                            96f
                        }
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (maxContentWidth != null) {
                                    Modifier.widthIn(max = maxContentWidth)
                                } else {
                                    Modifier
                                },
                            ),
                    contentPadding = PaddingValues(AdaptiveUtils.getContentPadding(windowSizeClass)),
                    verticalArrangement = Arrangement.spacedBy(AdaptiveUtils.getItemSpacing(windowSizeClass)),
                ) {
                    // Book cover
                    item {
                        val context = LocalContext.current
                        val imageRequest =
                            CoverUtils
                                .createCoverImageRequest(
                                    book = book,
                                    context = context,
                                    placeholderColor = MaterialTheme.colorScheme.surfaceVariant,
                                    errorColor = MaterialTheme.colorScheme.error,
                                    fallbackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    cornerRadius = 16f, // 16dp rounded corners for detail view
                                ).build()

                        AsyncImage(
                            model = imageRequest,
                            contentDescription = book.title,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .graphicsLayer {
                                        translationY = parallaxOffset
                                    }.clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors =
                                                listOf(
                                                    Color.Transparent,
                                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                                                ),
                                        ),
                                    ),
                        )
                    }

                    // Book title and author with adaptive text sizes
                    item {
                        Column {
                            Text(
                                text = book.title,
                                style =
                                    AdaptiveUtils.getAdaptiveTextStyle(
                                        MaterialTheme.typography.headlineSmall,
                                        windowSizeClass,
                                    ),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(AdaptiveUtils.getItemSpacing(windowSizeClass) * 0.5f))
                            Text(
                                text = book.author,
                                style =
                                    AdaptiveUtils.getAdaptiveTextStyle(
                                        MaterialTheme.typography.bodyMedium,
                                        windowSizeClass,
                                    ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Action buttons
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalButton(
                                onClick = onPlayClick,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .heightIn(min = AdaptiveUtils.getButtonHeight(windowSizeClass)),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(AdaptiveUtils.getIconSize(windowSizeClass)),
                                )
                                Spacer(modifier = Modifier.width(AdaptiveUtils.getItemSpacing(windowSizeClass) * 0.5f))
                                Text(
                                    text = stringResource(R.string.play),
                                    style =
                                        AdaptiveUtils.getAdaptiveTextStyle(
                                            MaterialTheme.typography.labelLarge,
                                            windowSizeClass,
                                        ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            FilledTonalButton(
                                onClick = onToggleFavorite,
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .heightIn(min = AdaptiveUtils.getButtonHeight(windowSizeClass)),
                            ) {
                                Icon(
                                    imageVector =
                                        if (book.isFavorite) {
                                            Icons.Default.Favorite
                                        } else {
                                            Icons.Outlined.FavoriteBorder
                                        },
                                    contentDescription = null,
                                    modifier = Modifier.size(AdaptiveUtils.getIconSize(windowSizeClass)),
                                )
                                Spacer(modifier = Modifier.width(AdaptiveUtils.getItemSpacing(windowSizeClass) * 0.5f))
                                Text(
                                    text =
                                        if (book.isFavorite) {
                                            stringResource(R.string.removeFromFavorites)
                                        } else {
                                            stringResource(R.string.addToFavorites)
                                        },
                                    style =
                                        AdaptiveUtils.getAdaptiveTextStyle(
                                            MaterialTheme.typography.labelLarge,
                                            windowSizeClass,
                                        ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    // Book metadata
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetadataRow(
                                label = stringResource(R.string.totalDuration),
                                value = formatDuration(book.totalDuration.inWholeMilliseconds),
                            )
                            MetadataRow(
                                label = stringResource(R.string.currentPosition),
                                value = formatDuration(book.currentPosition.inWholeMilliseconds),
                            )
                            MetadataRow(
                                label = stringResource(R.string.progress),
                                value = "${(book.progress * 100).toInt()}%",
                            )
                            if (book.isDownloaded) {
                                MetadataRow(
                                    label = stringResource(R.string.downloadStatus),
                                    value = stringResource(R.string.downloaded),
                                )
                            }
                        }
                    }

                    // Chapters progress preview
                    if (chapters.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.chapters),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        items(
                            count = chapters.size,
                            key = { index -> chapters[index].id },
                        ) { index ->
                            val chapter = chapters[index]
                            ChapterProgressRow(chapter = chapter)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChapterProgressRow(chapter: Chapter) {
    val remaining = chapter.remainingDuration.inWholeMinutes.coerceAtLeast(0)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${chapter.displayNumber}. ${chapter.title}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "${(chapter.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { chapter.progress },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = pluralStringResource(R.plurals.durationMinutesFull, remaining.toInt(), remaining.toInt()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Displays a single metadata row with label and value.
 */
@Composable
private fun MetadataRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * Formats a duration in milliseconds to elapsed-time format.
 */
private fun formatDuration(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1000L
    return DateUtils.formatElapsedTime(totalSeconds)
}
