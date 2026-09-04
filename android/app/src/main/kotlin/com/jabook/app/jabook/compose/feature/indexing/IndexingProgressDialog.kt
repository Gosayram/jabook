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

package com.jabook.app.jabook.compose.feature.indexing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.util.UiFormatters
import com.jabook.app.jabook.compose.data.indexing.ForumState
import com.jabook.app.jabook.compose.data.indexing.ForumStatus
import com.jabook.app.jabook.compose.data.indexing.IndexingProgress

/**
 * Dialog showing indexing progress.
 *
 * @param progress Current indexing progress
 * @param onDismiss Callback when dialog is dismissed
 * @param onHide Callback when user wants to hide dialog and continue in background
 * @param indexSize Current index size from database (used for accurate count display)
 * @param modifier Modifier for the dialog
 */
@Composable
public fun IndexingProgressDialog(
    progress: IndexingProgress,
    onDismiss: () -> Unit,
    onHide: (() -> Unit)? = null,
    indexSize: Int = 0,
    forumStatuses: List<ForumStatus> = emptyList(),
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = {
            when (progress) {
                is IndexingProgress.Completed, is IndexingProgress.Error -> onDismiss()
                // Outside tap during indexing hides the dialog; indexing continues in background
                is IndexingProgress.Idle, is IndexingProgress.InProgress -> onHide?.invoke()
            }
        },
        title = {
            Text(
                when (progress) {
                    is IndexingProgress.Idle -> stringResource(R.string.indexingDialogTitle)
                    is IndexingProgress.InProgress -> stringResource(R.string.indexingDialogTitle)
                    is IndexingProgress.Completed -> stringResource(R.string.indexingCompletedTitle)
                    is IndexingProgress.Error -> stringResource(R.string.indexingErrorTitle)
                },
            )
        },
        text = {
            Column(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (progress) {
                    is IndexingProgress.Idle -> {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.indexingPreparing),
                            textAlign = TextAlign.Center,
                        )
                    }

                    is IndexingProgress.InProgress -> {
                        // Progress bar
                        LinearProgressIndicator(
                            progress = { progress.detail.percentComplete },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Status text
                        val forumProgressText =
                            stringResource(
                                R.string.indexingStatusForumProgress,
                                progress.detail.totalForumsCompleted + 1,
                                progress.detail.totalForums,
                            )
                        val topicsIndexedText =
                            pluralStringResource(
                                R.plurals.indexTopicsIndexed,
                                progress.detail.topicsFound,
                                progress.detail.topicsFound,
                            )
                        Text(
                            text =
                                forumProgressText + "\n" +
                                    topicsIndexedText,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        // Per-forum status list
                        if (forumStatuses.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyColumn(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(forumStatuses, key = { it.forumId }, contentType = { "forum_status" }) { status ->
                                    ForumStatusRow(status)
                                }
                            }
                        }

                        // Progress percentage
                        Text(
                            text = UiFormatters.formatPercent(progress.detail.percentComplete),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    is IndexingProgress.Completed -> {
                        // Use indexSize from database as single source of truth
                        val displayCount = if (indexSize > 0) indexSize else progress.totalTopics
                        val topicsIndexedText =
                            pluralStringResource(
                                R.plurals.indexTopicsIndexed,
                                displayCount,
                                displayCount,
                            )
                        val durationSecondsText =
                            stringResource(
                                R.string.indexingDurationSeconds,
                                progress.durationMs / 1000,
                            )
                        Text(
                            text =
                                stringResource(R.string.indexingCompletedHeadline) + "\n" +
                                    topicsIndexedText + "\n" +
                                    durationSecondsText,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    is IndexingProgress.Error -> {
                        Text(
                            text = progress.message,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error,
                        )
                        if (progress.forumId != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.indexingForumLabel, progress.forumId),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (progress is IndexingProgress.Completed || progress is IndexingProgress.Error) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        },
        dismissButton = {
            // Show "Скрыть" button during indexing to continue in background
            if (progress is IndexingProgress.InProgress || progress is IndexingProgress.Idle) {
                onHide?.let { hide ->
                    TextButton(onClick = hide) {
                        Text(stringResource(R.string.hideAction))
                    }
                }
            }
        },
    )
}

@Composable
private fun ForumStatusRow(status: ForumStatus) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (status.state) {
            ForumState.INDEXED -> {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.indexingStatusIndexed),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            ForumState.IN_PROGRESS -> {
                val inProgressLabel = stringResource(R.string.indexingStatusInProgress)
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .size(16.dp)
                            .semantics { contentDescription = inProgressLabel },
                    strokeWidth = 2.dp,
                )
            }
            ForumState.FAILED -> {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = stringResource(R.string.indexingStatusFailed),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            ForumState.PENDING -> {
                Icon(
                    imageVector = Icons.Filled.Pending,
                    contentDescription = stringResource(R.string.indexingStatusPending),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = status.forumName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (status.state == ForumState.INDEXED && status.topicsCount > 0) {
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.indexTopicsCount,
                            status.topicsCount,
                            status.topicsCount,
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (status.state == ForumState.FAILED && status.errorMessage != null) {
                Text(
                    text = status.errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
