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

package com.jabook.app.jabook.compose.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.designsystem.component.EmptyState
import com.jabook.app.jabook.compose.domain.model.DownloadFilter
import com.jabook.app.jabook.compose.domain.model.DownloadInfo
import com.jabook.app.jabook.compose.domain.model.DownloadPriority
import com.jabook.app.jabook.compose.domain.model.DownloadState

/**
 * Downloads screen showing active downloads.
 *
 * @param onNavigateBack Callback when back button is clicked
 * @param modifier Modifier for the screen
 * @param viewModel ViewModel for the screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadViewModel = hiltViewModel(),
) {
    val filteredDownloads by viewModel.filteredDownloads.collectAsStateWithLifecycle()
    val currentFilter by viewModel.currentFilter.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads)) },
                windowInsets =
                    androidx.compose.foundation.layout
                        .WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            // Filter tabs
            PrimaryScrollableTabRow(
                selectedTabIndex = currentFilter.ordinal,
            ) {
                DownloadFilter.entries.forEach { filter ->
                    Tab(
                        selected = filter == currentFilter,
                        onClick = { viewModel.setFilter(filter) },
                        text = { Text(filter.name) },
                    )
                }
            }

            // Downloads list
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    filteredDownloads.isEmpty() -> {
                        EmptyState(message = "No downloads in ${currentFilter.name.lowercase()}")
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding =
                                androidx.compose.foundation.layout
                                    .PaddingValues(16.dp),
                        ) {
                            items(
                                items = filteredDownloads,
                                key = { it.bookId },
                            ) { downloadInfo ->
                                DownloadCard(
                                    downloadInfo = downloadInfo,
                                    onCancel = { viewModel.cancelDownload(downloadInfo.bookId) },
                                    onUpdatePriority = { priority ->
                                        viewModel.updatePriority(downloadInfo.bookId, priority)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card showing download progress.
 *
 * @param downloadInfo Download information
 * @param onCancel Callback when cancel is clicked
 * @param modifier Modifier for the card
 */
@Composable
private fun DownloadCard(
    downloadInfo: DownloadInfo,
    onCancel: () -> Unit,
    onUpdatePriority: (DownloadPriority) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPriorityMenu by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header with title, priority, and actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Priority indicator
                    Icon(
                        imageVector =
                            when (downloadInfo.priority) {
                                DownloadPriority.URGENT -> Icons.Default.PriorityHigh
                                DownloadPriority.HIGH -> Icons.Default.ArrowUpward
                                DownloadPriority.NORMAL -> Icons.Default.Remove
                                DownloadPriority.LOW -> Icons.Default.ArrowDownward
                            },
                        contentDescription = stringResource(R.string.priorityDownloadinfopriorityname),
                        tint =
                            when (downloadInfo.priority) {
                                DownloadPriority.URGENT -> MaterialTheme.colorScheme.error
                                DownloadPriority.HIGH -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = downloadInfo.bookTitle,
                            style = MaterialTheme.typography.titleMedium,
                        )

                        // Queue position if queued
                        if (downloadInfo.isQueued) {
                            Text(
                                text = "Position in queue: ${downloadInfo.queuePosition}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Row {
                    // Priority menu
                    Box {
                        IconButton(onClick = { showPriorityMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.priorityMenu),
                            )
                        }

                        DropdownMenu(
                            expanded = showPriorityMenu,
                            onDismissRequest = { showPriorityMenu = false },
                        ) {
                            DownloadPriority.entries.forEach { priority ->
                                DropdownMenuItem(
                                    text = { Text(priority.name) },
                                    onClick = {
                                        onUpdatePriority(priority)
                                        showPriorityMenu = false
                                    },
                                )
                            }
                        }
                    }

                    // Cancel button
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = stringResource(R.string.cancelDownload),
                        )
                    }
                }
            }

            // Progress indicator
            when (val state = downloadInfo.state) {
                is DownloadState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Progress percentage and speed in separate row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = state.formattedSpeed,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    // Downloaded / Total bytes
                    if (state.totalBytes != null && state.totalBytes!! > 0) {
                        Text(
                            text = "${formatBytes(state.downloadedBytes)} / ${formatBytes(state.totalBytes!!)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = formatBytes(state.downloadedBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is DownloadState.Paused -> {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text(
                        text = "Paused at ${(state.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is DownloadState.Failed -> {
                    Text(
                        text = "Failed: ${state.error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                is DownloadState.Completed -> {
                    Text(
                        text = stringResource(R.string.completed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                DownloadState.Idle -> {
                    Text(
                        text = "Waiting to start...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Format bytes to human-readable string.
 */
private fun formatBytes(bytes: Long): String =
    when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
