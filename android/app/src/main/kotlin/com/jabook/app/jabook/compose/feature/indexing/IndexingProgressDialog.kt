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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.R
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
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = {
            // Don't allow dismiss during indexing
            if (progress is IndexingProgress.Completed || progress is IndexingProgress.Error) {
                onDismiss()
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
                modifier = modifier.fillMaxWidth(),
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
                            progress = { progress.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Status text
                        val forumProgressText =
                            stringResource(
                                R.string.indexingStatusForumProgress,
                                progress.currentForumIndex + 1,
                                progress.totalForums,
                            )
                        val pageText = stringResource(R.string.indexingStatusPage, progress.currentPage + 1)
                        val topicsIndexedText =
                            pluralStringResource(
                                R.plurals.indexTopicsIndexed,
                                progress.topicsIndexed,
                                progress.topicsIndexed,
                            )
                        Text(
                            text =
                                stringResource(R.string.indexingStatusForum, progress.currentForum) + "\n" +
                                    forumProgressText + "\n" +
                                    pageText + "\n" +
                                    topicsIndexedText,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                        )

                        // Progress percentage
                        Text(
                            text = "${(progress.progress * 100).toInt()}%",
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
