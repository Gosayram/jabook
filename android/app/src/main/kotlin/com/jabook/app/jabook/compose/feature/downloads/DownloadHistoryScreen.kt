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

package com.jabook.app.jabook.compose.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.util.AdaptiveUtils
import com.jabook.app.jabook.compose.core.util.UiFormatters
import com.jabook.app.jabook.compose.domain.model.DownloadHistoryItem
import com.jabook.app.jabook.compose.domain.model.HistorySortOrder
import com.jabook.app.jabook.download.DownloadProgressInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Download History Screen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
public fun DownloadHistoryScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadHistoryViewModel = hiltViewModel(),
) {
    // Get window size class for adaptive sizing
    val context = LocalContext.current
    val activity =
        context as? android.app.Activity
            ?: (context as? androidx.appcompat.view.ContextThemeWrapper)?.baseContext as? android.app.Activity
    val rawWindowSizeClass = activity?.let { calculateWindowSizeClass(it) }
    val windowSizeClass = AdaptiveUtils.resolveWindowSizeClassOrNull(rawWindowSizeClass, context)
    val contentPadding = AdaptiveUtils.getContentPaddingOrDefault(windowSizeClass)
    val itemSpacing = AdaptiveUtils.getItemSpacingOrDefault(windowSizeClass)

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle(initialValue = emptyList())

    var showSortMenu by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val tabs = listOf(stringResource(R.string.downloadsActiveTab), stringResource(R.string.downloadsHistoryTab))

    Scaffold(
        topBar = {
            if (!searchActive) {
                TopAppBar(
                    title = { Text(stringResource(R.string.downloadHistory)) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    },
                    actions = {
                        // Search button
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, stringResource(R.string.search))
                        }

                        // Sort button
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.sort))
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.newestFirst)) },
                                onClick = {
                                    viewModel.updateSortOrder(HistorySortOrder.DATE_DESC)
                                    showSortMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.oldestFirst)) },
                                onClick = {
                                    viewModel.updateSortOrder(HistorySortOrder.DATE_ASC)
                                    showSortMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.titleAz)) },
                                onClick = {
                                    viewModel.updateSortOrder(HistorySortOrder.TITLE_ASC)
                                    showSortMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.titleZa)) },
                                onClick = {
                                    viewModel.updateSortOrder(HistorySortOrder.TITLE_DESC)
                                    showSortMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.largestFirst)) },
                                onClick = {
                                    viewModel.updateSortOrder(HistorySortOrder.SIZE_DESC)
                                    showSortMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.smallestFirst)) },
                                onClick = {
                                    viewModel.updateSortOrder(HistorySortOrder.SIZE_ASC)
                                    showSortMenu = false
                                },
                            )
                        }

                        // Options menu
                        IconButton(onClick = { showOptionsMenu = true }) {
                            Icon(Icons.Default.MoreVert, stringResource(R.string.options))
                        }

                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.clearAllHistory)) },
                                onClick = {
                                    viewModel.clearHistory()
                                    showOptionsMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.ClearAll, null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.deleteOld30Days)) },
                                onClick = {
                                    viewModel.deleteOldEntries()
                                    showOptionsMenu = false
                                },
                            )
                        }
                    },
                )
            }
        },
        modifier = modifier,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            PrimaryScrollableTabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) },
                    )
                }
            }

            // Search bar when active
            if (searchActive) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = contentPadding, vertical = itemSpacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = { Text(stringResource(R.string.searchBooks)) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            IconButton(onClick = {
                                viewModel.updateSearchQuery("")
                                searchActive = false
                            }) {
                                Icon(Icons.Default.Close, stringResource(R.string.close))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // History list or empty state
            if (selectedTabIndex == 1 && history.isEmpty()) {
                EmptyHistoryState()
            } else if (selectedTabIndex == 0 && activeDownloads.isEmpty()) {
                // Empty state for Active tab (no active downloads)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.noActiveDownloads),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        androidx.compose.foundation.layout
                            .PaddingValues(contentPadding),
                    verticalArrangement = Arrangement.spacedBy(itemSpacing),
                ) {
                    if (selectedTabIndex == 0) {
                        // Active downloads
                        items(activeDownloads, key = { it.workId.toString() }) { item ->
                            ActiveDownloadCard(item = item, contentPadding = contentPadding)
                        }
                    } else {
                        // History
                        items(history, key = { it.id }) { entry ->
                            HistoryCard(entry = entry, contentPadding = contentPadding)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.noDownloadHistory),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.completedDownloadsWillAppearHere),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryCard(
    entry: DownloadHistoryItem,
    contentPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = contentPadding),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Title
            Text(
                text = entry.bookTitle,
                style = MaterialTheme.typography.titleMedium,
            )

            // Status and date
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Status badge
                val (statusIcon, statusColor) =
                    when (entry.status) {
                        "completed" -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
                        "failed" -> Icons.Default.Error to MaterialTheme.colorScheme.error
                        "cancelled" -> Icons.Default.Cancel to MaterialTheme.colorScheme.onSurfaceVariant
                        else -> Icons.Default.Book to MaterialTheme.colorScheme.onSurface
                    }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = entry.status.replaceFirstChar { it.titlecase(Locale.getDefault()) },
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                    )
                }

                // Date
                Text(
                    text = formatDate(entry.completedAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Size (if available)
            entry.totalBytes?.let { bytes ->
                Text(
                    text = UiFormatters.formatFileSize(bytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Error message (if failed)
            entry.errorMessage?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Format timestamp to readable date.
 */
private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

/**
 * Card for active download item.
 */
@Composable
private fun ActiveDownloadCard(
    item: DownloadProgressInfo,
    contentPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = contentPadding),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Title
            Text(
                text = item.bookTitle,
                style = MaterialTheme.typography.titleMedium,
            )

            // Progress indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // State badge
                val stateText =
                    when (item.state) {
                        androidx.work.WorkInfo.State.RUNNING -> stringResource(R.string.downloading_state)
                        androidx.work.WorkInfo.State.ENQUEUED -> stringResource(R.string.queued_state)
                        androidx.work.WorkInfo.State.SUCCEEDED -> stringResource(R.string.completed_state)
                        androidx.work.WorkInfo.State.FAILED -> stringResource(R.string.error)
                        androidx.work.WorkInfo.State.CANCELLED -> stringResource(R.string.cancelled_state)
                        else -> item.state.name
                    }

                Text(
                    text = stateText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )

                // Progress
                Text(
                    text = "${item.progress}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Peers count (if available)
            if (item.numPeers > 0) {
                Text(
                    text = "${item.numPeers} peers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
