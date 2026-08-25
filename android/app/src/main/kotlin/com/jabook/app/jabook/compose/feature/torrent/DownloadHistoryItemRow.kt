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

package com.jabook.app.jabook.compose.feature.torrent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.domain.model.DownloadHistoryItem

/** Status constants for download history. */
internal object DownloadHistoryStatus {
    const val COMPLETED = "completed"
    const val FAILED = "failed"
    const val CANCELLED = "cancelled"
}

/**
 * A compact row representing a past download with status badge.
 *
 * - Completed rows show a play-circle action to open the book.
 * - Failed rows show error text and a retry affordance when [onRetry] is provided.
 * - Cancelled rows are visually subdued.
 */
@Composable
public fun DownloadHistoryItemRow(
    item: DownloadHistoryItem,
    onOpenBook: (String) -> Unit,
    onRetry: ((DownloadHistoryItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isCancelled = item.status == DownloadHistoryStatus.CANCELLED
    val containerColor =
        if (isCancelled) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }

    Card(
        onClick = { onOpenBook(item.bookId) },
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.bookTitle,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color =
                        if (isCancelled) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                )

                Spacer(Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HistoryStatusBadge(status = item.status)
                    if (!item.errorMessage.isNullOrBlank() && item.status == DownloadHistoryStatus.FAILED) {
                        Text(
                            text = item.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // Trailing action per status
            when (item.status) {
                DownloadHistoryStatus.COMPLETED -> {
                    IconButton(onClick = { onOpenBook(item.bookId) }) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.historyStatusCompleted),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                DownloadHistoryStatus.FAILED -> {
                    if (onRetry != null) {
                        IconButton(onClick = { onRetry(item) }) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.historyRetry),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                DownloadHistoryStatus.CANCELLED -> {
                    // Cancelled rows have no primary action; icon is decorative
                    Icon(
                        imageVector = Icons.Filled.Cancel,
                        contentDescription = stringResource(R.string.historyStatusCancelled),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryStatusBadge(status: String) {
    val (text, color, icon) =
        when (status) {
            DownloadHistoryStatus.COMPLETED ->
                Triple(
                    stringResource(R.string.historyStatusCompleted),
                    MaterialTheme.colorScheme.primary,
                    Icons.Filled.CheckCircle,
                )

            DownloadHistoryStatus.FAILED ->
                Triple(
                    stringResource(R.string.historyStatusFailed),
                    MaterialTheme.colorScheme.error,
                    Icons.Filled.Error,
                )

            else ->
                Triple(
                    stringResource(R.string.historyStatusCancelled),
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    Icons.Filled.Cancel,
                )
        }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.height(12.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}
