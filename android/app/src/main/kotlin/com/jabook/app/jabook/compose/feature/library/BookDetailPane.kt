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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.util.CoverUtils
import com.jabook.app.jabook.compose.domain.model.Book
import kotlin.time.Duration.Companion.milliseconds

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailPane(
    book: Book?,
    onPlayClick: () -> Unit,
    onClose: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
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
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Book details content
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Book cover
                item {
                    val context = LocalContext.current
                    val imageRequest =
                        CoverUtils.createCoverImageRequest(
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
                                .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }

                // Book title and author
                item {
                    Column {
                        Text(
                            text = book.title,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = book.author,
                            style = MaterialTheme.typography.bodyMedium,
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
                                    .heightIn(min = 48.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.play),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        FilledTonalButton(
                            onClick = onToggleFavorite,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                        ) {
                            Icon(
                                imageVector =
                                    if (book.isFavorite) {
                                        Icons.Default.Favorite
                                    } else {
                                        Icons.Default.FavoriteBorder
                                    },
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(if (book.isFavorite) R.string.unfavorite else R.string.favorite),
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
            }
        }
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
 * Formats a duration in milliseconds to a human-readable string.
 * Examples: "1h 23m", "45m", "12s"
 */
private fun formatDuration(millis: Long): String {
    val duration = millis.milliseconds
    val hours = duration.inWholeHours
    val minutes = (duration.inWholeMinutes % 60)
    val seconds = (duration.inWholeSeconds % 60)

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}
