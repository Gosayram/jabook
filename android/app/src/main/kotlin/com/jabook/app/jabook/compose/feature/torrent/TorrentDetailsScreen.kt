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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.jabook.app.jabook.compose.core.util.AdaptiveUtils
import com.jabook.app.jabook.compose.data.torrent.TorrentFile
import kotlinx.coroutines.flow.collect
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
public fun TorrentDetailsScreen(
    onNavigateBack: () -> Unit,
    onPlayBook: (String) -> Unit,
    viewModel: TorrentDetailsViewModel = hiltViewModel(),
) {
    // Get window size class for adaptive sizing
    val context = LocalContext.current
    val activity =
        context as? android.app.Activity
            ?: (context as? androidx.appcompat.view.ContextThemeWrapper)?.baseContext as? android.app.Activity
            ?: null
    val rawWindowSizeClass = activity?.let { calculateWindowSizeClass(it) }
    val windowSizeClass = AdaptiveUtils.resolveWindowSizeClassOrNull(rawWindowSizeClass, context)
    val contentPadding = AdaptiveUtils.getContentPaddingOrDefault(windowSizeClass)
    val itemSpacing = AdaptiveUtils.getItemSpacingOrDefault(windowSizeClass)

    val download by viewModel.download.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { bookId: String ->
            onPlayBook(bookId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = download?.name ?: "Torrent Details",
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val state = download

        var showFileSelection by remember { androidx.compose.runtime.mutableStateOf(false) }

        if (showFileSelection && state != null) {
            FileSelectionDialog(
                files = state.files,
                onConfirm = { selectedIndices ->
                    viewModel.updateFileSelection(selectedIndices)
                    showFileSelection = false
                },
                onDismiss = { showFileSelection = false },
            )
        }

        if (state == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val isBuffering by viewModel.isBuffering.collectAsState()

            if (isBuffering) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { /* Disable dismiss */ },
                    title = { Text("Buffering...") },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator()
                            Text("Please wait while we buffer enough data for smooth playback.", modifier = Modifier.padding(top = 8.dp))
                        }
                    },
                    confirmButton = {},
                )
            }

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                contentPadding = PaddingValues(contentPadding),
                verticalArrangement = Arrangement.spacedBy(itemSpacing),
            ) {
                item {
                    // Header Info
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(contentPadding)) {
                            Text("State: ${state.state}", style = MaterialTheme.typography.bodyMedium)
                            Text("Progress: ${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                            Text("Size: ${formatSize(state.totalSize)}", style = MaterialTheme.typography.bodyMedium)

                            if (state.eta > 0) {
                                Text("ETA: ${formatEta(state.eta)}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                item {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Files",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                        androidx.compose.material3.TextButton(onClick = { showFileSelection = true }) {
                            Text("Manage Files")
                        }
                    }
                }

                items(
                    items = state.files,
                    key = { file -> file.index },
                ) { file ->
                    FileItem(
                        file = file,
                        onPlay = { viewModel.playFile(file) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FileItem(
    file: TorrentFile,
    onPlay: () -> Unit,
) {
    val isAudio =
        remember(file.path) {
            val ext = File(file.path).extension.lowercase()
            ext in setOf("mp3", "m4a", "m4b", "aac", "flac", "ogg", "wav")
        }

    ListItem(
        headlineContent = { Text(File(file.path).name) },
        supportingContent = {
            Column {
                Text(formatSize(file.size))
                LinearProgressIndicator(
                    progress = { file.progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        },
        trailingContent = {
            if (isAudio) {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                }
            }
        },
    )
}

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> "%.2f GB".format(Locale.US, gb)
        mb >= 1 -> "%.2f MB".format(Locale.US, mb)
        kb >= 1 -> "%.2f KB".format(Locale.US, kb)
        else -> "$bytes B"
    }
}

private fun formatEta(seconds: Long): String {
    if (seconds < 0) return "∞"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(Locale.US, hours, minutes, secs)
    } else {
        "%02d:%02d".format(Locale.US, minutes, secs)
    }
}
