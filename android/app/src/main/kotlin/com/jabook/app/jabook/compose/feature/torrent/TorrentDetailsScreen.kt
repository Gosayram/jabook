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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.navigation.NavigationClickGuard
import com.jabook.app.jabook.compose.core.util.AdaptiveUtils
import com.jabook.app.jabook.compose.core.util.LocalWindowSizeClass
import com.jabook.app.jabook.compose.core.util.UiFormatters
import com.jabook.app.jabook.compose.data.torrent.TorrentFile
import kotlinx.coroutines.flow.collect
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
public fun TorrentDetailsScreen(
    onNavigateBack: () -> Unit,
    onPlayBook: (String) -> Unit,
    viewModel: TorrentDetailsViewModel = hiltViewModel(),
) {
    // Get window size class for adaptive sizing
    val context = LocalContext.current
    val wsc = LocalWindowSizeClass.current
    val windowSizeClass = wsc?.let { AdaptiveUtils.resolveWindowSizeClassOrNull(it, context) } ?: wsc
    val contentPadding = AdaptiveUtils.getContentPaddingOrDefault(windowSizeClass)
    val itemSpacing = AdaptiveUtils.getItemSpacingOrDefault(windowSizeClass)

    val navigationClickGuard = remember { NavigationClickGuard() }
    val safeNavigateBack = dropUnlessResumed { navigationClickGuard.run(onNavigateBack) }

    val download by viewModel.download.collectAsStateWithLifecycle()

    val currentOnPlayBook by rememberUpdatedState(onPlayBook)

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { bookId: String ->
            currentOnPlayBook(bookId)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        // TopAppBar applies statusBars insets itself; zeroed to avoid double inset under NavigationSuiteScaffold.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = download?.name ?: stringResource(R.string.torrentDetailsTitle),
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = safeNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        val state = download

        var showFileSelection by rememberSaveable { mutableStateOf(false) }

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
            val isBuffering by viewModel.isBuffering.collectAsStateWithLifecycle()
            val monitoredHash by viewModel.monitoredHash.collectAsStateWithLifecycle()

            // Only show the buffering dialog for THIS screen's torrent — the monitor
            // is a singleton and may be streaming a different hash in background.
            if (isBuffering && monitoredHash == viewModel.hash) {
                AlertDialog(
                    onDismissRequest = { /* Disable dismiss */ },
                    title = { Text(stringResource(R.string.torrentBufferingTitle)) },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CircularProgressIndicator()
                            Text(
                                stringResource(R.string.torrentBufferingDescription),
                                modifier = Modifier.padding(top = 8.dp),
                            )
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
                            Text(
                                stringResource(R.string.torrentStateLabel, state.state),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                stringResource(R.string.torrentProgressLabel, (state.progress * 100).toInt()),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                stringResource(R.string.torrentSizeLabel, UiFormatters.formatFileSize(state.totalSize)),
                                style = MaterialTheme.typography.bodyMedium,
                            )

                            if (state.eta > 0) {
                                Text(
                                    stringResource(R.string.torrentEtaLabel, UiFormatters.formatDuration(state.eta * 1000L)),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.files),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                        TextButton(onClick = { showFileSelection = true }) {
                            Text(stringResource(R.string.manageFiles))
                        }
                    }
                }

                items(
                    items = state.files,
                    key = { file -> file.index },
                    contentType = { "file_item" },
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
    val fileName = remember(file.path) { File(file.path).name }
    val isAudio =
        remember(file.path) {
            val ext = File(file.path).extension.lowercase()
            ext in setOf("mp3", "m4a", "m4b", "aac", "flac", "ogg", "wav")
        }

    ListItem(
        headlineContent = { Text(fileName) },
        supportingContent = {
            Column {
                Text(UiFormatters.formatFileSize(file.size))
                LinearProgressIndicator(
                    progress = { file.progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        },
        trailingContent = {
            if (isAudio) {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play))
                }
            }
        },
    )
}
