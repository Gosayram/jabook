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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jabook.app.jabook.compose.data.indexing.IndexingProgress

/**
 * Dialog showing indexing progress.
 */
@Composable
fun IndexingProgressDialog(
    progress: IndexingProgress,
    onDismiss: () -> Unit,
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
                    is IndexingProgress.Idle -> "Индексация"
                    is IndexingProgress.InProgress -> "Индексация"
                    is IndexingProgress.Completed -> "Индексация завершена"
                    is IndexingProgress.Error -> "Ошибка индексации"
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
                            text = "Подготовка к индексации...",
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
                        Text(
                            text =
                                "Индексируем форум: ${progress.currentForum}\n" +
                                    "Форум ${progress.currentForumIndex + 1} из ${progress.totalForums}\n" +
                                    "Страница ${progress.currentPage + 1}\n" +
                                    "Проиндексировано: ${progress.topicsIndexed} тем",
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
                        Text(
                            text =
                                "Индексация завершена!\n" +
                                    "Проиндексировано: ${progress.totalTopics} тем\n" +
                                    "Время: ${progress.durationMs / 1000} сек",
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
                                text = "Форум: ${progress.forumId}",
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
                    Text("Закрыть")
                }
            }
        },
    )
}
