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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.domain.model.Book
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dialog showing detailed properties of an audiobook.
 *
 * Displays:
 * - Title and Author
 * - Date added
 * - Directory path
 * - Total duration
 * - File size (if available)
 * - Number of chapters
 */
@Composable
fun BookPropertiesDialog(
    book: Book,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Header with close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.bookProperties),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                }

                // Book title and author
                PropertyRow(
                    label = stringResource(R.string.title),
                    value = book.title,
                )

                PropertyRow(
                    label = stringResource(R.string.author),
                    value = book.author,
                )

                // Date added
                PropertyRow(
                    label = stringResource(R.string.dateAdded),
                    value = formatDate(book.addedDate),
                )

                // Directory
                book.localPath?.let { path ->
                    PropertyRow(
                        label = stringResource(R.string.directory),
                        value = File(path).parent ?: path,
                        isPath = true,
                    )
                }

                // Duration
                PropertyRow(
                    label = stringResource(R.string.duration),
                    value = formatDuration(book.totalDuration.inWholeSeconds),
                )

                // File size (if available)
                book.localPath?.let { path ->
                    // Simplified size calculation without accessing non-existent chapters prop
                    val size = calculateDirectorySize(path)

                    if (size > 0) {
                        PropertyRow(
                            label = stringResource(R.string.size),
                            value = formatFileSize(size),
                        )
                    }
                }

                // Progress
                if (book.progress > 0f) {
                    PropertyRow(
                        label = stringResource(R.string.progress),
                        value = "${(book.progress * 100).toInt()}%",
                    )
                }
            }
        }
    }
}

/**
 * Single property row with label and value.
 */
@Composable
private fun PropertyRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isPath: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = value,
            style =
                if (isPath) {
                    MaterialTheme.typography.bodySmall
                } else {
                    MaterialTheme.typography.bodyMedium
                },
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Format timestamp to readable date.
 */
private fun formatDate(timestamp: Long): String {
    val date = Date(timestamp)
    val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return formatter.format(date)
}

/**
 * Format duration in seconds to HH:MM:SS or MM:SS.
 */
private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%d:%02d", minutes, secs)
    }
}

/**
 * Calculate total size of directory.
 * Returns size in bytes, or 0 if calculation fails.
 */
private fun calculateDirectorySize(path: String): Long =
    try {
        val file = File(path)
        if (file.isDirectory) {
            file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else {
            file.length()
        }
    } catch (e: Exception) {
        0L
    }

/**
 * Format file size to human-readable string.
 */
private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0

    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.2f MB", mb)
        kb >= 1.0 -> String.format("%.2f KB", kb)
        else -> "$bytes B"
    }
}
