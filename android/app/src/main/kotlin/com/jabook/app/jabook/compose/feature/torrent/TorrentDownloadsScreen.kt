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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.dropUnlessResumed
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.navigation.NavigationClickGuard
import com.jabook.app.jabook.compose.core.util.AdaptiveUtils
import com.jabook.app.jabook.compose.data.torrent.TorrentDownload
import com.jabook.app.jabook.compose.designsystem.component.EmptyState
import com.jabook.app.jabook.compose.designsystem.component.ErrorScreen
import com.jabook.app.jabook.compose.designsystem.component.LoadingScreen

/**
 * Screen for managing torrent downloads
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3WindowSizeClassApi::class,
)
@Composable
public fun TorrentDownloadsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TorrentDownloadsViewModel = hiltViewModel(),
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

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showCompletedOnly by viewModel.showCompletedOnly.collectAsStateWithLifecycle()

    val navigationClickGuard = remember { NavigationClickGuard() }
    val safeNavigateBack = dropUnlessResumed { navigationClickGuard.run(onNavigateBack) }

    var downloadToDelete by remember { mutableStateOf<TorrentDownload?>(null) }

    val pendingMagnetLink by viewModel.pendingMagnetLink.collectAsStateWithLifecycle()
    val pendingDownloadPath by viewModel.pendingDownloadPath.collectAsStateWithLifecycle()

    // Folder picker launcher for Add Dialog
    val folderLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract =
                androidx.activity.result.contract.ActivityResultContracts
                    .OpenDocumentTree(),
        ) { uri ->
            uri?.let {
                val takeFlags =
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
                viewModel.updatePendingPathFromUri(it.toString())
            }
        }

    // Add Torrent Dialog
    if (pendingMagnetLink != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelAddTorrent() },
            title = { Text(stringResource(R.string.add_torrent_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.magnet_link_label),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = pendingMagnetLink!!,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 16.dp),
                    )

                    Text(
                        text = stringResource(R.string.download_location_label),
                        style = MaterialTheme.typography.labelMedium,
                    )

                    androidx.compose.material3.OutlinedTextField(
                        value = pendingDownloadPath,
                        onValueChange = {}, // Read-only
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { folderLauncher.launch(null) }) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = stringResource(R.string.change_folder),
                                )
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.confirmAddTorrent() },
                ) {
                    Text(stringResource(R.string.add_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelAddTorrent() }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (downloadToDelete != null) {
        var deleteFiles by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { downloadToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.delete_download)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            R.string.deleteDownloadConfirmMessage,
                            downloadToDelete?.name.orEmpty(),
                        ),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Checkbox(
                            checked = deleteFiles,
                            onCheckedChange = { deleteFiles = it },
                        )
                        Text(stringResource(R.string.deleteDownloadWithFiles))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDownload(downloadToDelete!!.hash, deleteFiles)
                        downloadToDelete = null
                    },
                ) {
                    Text(stringResource(R.string.delete_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { downloadToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.torrent_downloads)) },
                navigationIcon = {
                    IconButton(onClick = safeNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    Box {
                        var menuExpanded by remember { mutableStateOf(false) }

                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more_options),
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            // Pause All
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.torrent_pause)) },
                                onClick = {
                                    viewModel.pauseAll()
                                    menuExpanded = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Pause, null)
                                },
                            )

                            // Resume All
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.torrent_resume)) },
                                onClick = {
                                    viewModel.resumeAll()
                                    menuExpanded = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.PlayArrow, null)
                                },
                            )

                            // Delete Completed
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_download)) },
                                onClick = {
                                    viewModel.deleteAllCompleted()
                                    menuExpanded = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, null)
                                },
                            )
                        }
                    }
                },
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            when (val state = uiState) {
                is TorrentDownloadsUiState.Loading -> {
                    LoadingScreen()
                }

                is TorrentDownloadsUiState.Empty -> {
                    EmptyState(
                        message = stringResource(R.string.no_downloads),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                is TorrentDownloadsUiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = {}, // No retry action for now
                    )
                }

                is TorrentDownloadsUiState.Success -> {
                    TorrentDownloadsList(
                        activeDownloads = state.activeDownloads,
                        pausedDownloads = state.pausedDownloads,
                        completedDownloads = state.completedDownloads,
                        errorDownloads = state.errorDownloads,
                        onPauseClick = { hash -> viewModel.pauseDownload(hash) },
                        onResumeClick = { hash -> viewModel.resumeDownload(hash) },
                        onDeleteClick = { download -> downloadToDelete = download },
                        onItemClick = { download -> onNavigateToDetails(download.hash) },
                        showCompletedOnly = showCompletedOnly,
                        contentPadding = contentPadding,
                        itemSpacing = itemSpacing,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TorrentDownloadsList(
    activeDownloads: List<TorrentDownload>,
    pausedDownloads: List<TorrentDownload>,
    completedDownloads: List<TorrentDownload>,
    errorDownloads: List<TorrentDownload>,
    onPauseClick: (String) -> Unit,
    onResumeClick: (String) -> Unit,
    onDeleteClick: (TorrentDownload) -> Unit,
    onItemClick: (TorrentDownload) -> Unit,
    showCompletedOnly: Boolean,
    contentPadding: androidx.compose.ui.unit.Dp,
    itemSpacing: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(contentPadding),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
    ) {
        if (!showCompletedOnly) {
            // Active downloads
            if (activeDownloads.isNotEmpty()) {
                stickyHeader {
                    SectionHeader(title = stringResource(R.string.downloading_state))
                }
                items(activeDownloads, key = { it.hash }) { download ->
                    TorrentDownloadItem(
                        download = download,
                        onPauseClick = { onPauseClick(download.hash) },
                        onResumeClick = { onResumeClick(download.hash) },
                        onDeleteClick = { onDeleteClick(download) },
                        onItemClick = { onItemClick(download) },
                    )
                }
            }

            // Paused downloads
            if (pausedDownloads.isNotEmpty()) {
                stickyHeader {
                    SectionHeader(title = stringResource(R.string.paused_state))
                }
                items(pausedDownloads, key = { it.hash }) { download ->
                    TorrentDownloadItem(
                        download = download,
                        onPauseClick = { onPauseClick(download.hash) },
                        onResumeClick = { onResumeClick(download.hash) },
                        onDeleteClick = { onDeleteClick(download) },
                        onItemClick = { onItemClick(download) },
                    )
                }
            }
        }

        // Completed downloads
        if (completedDownloads.isNotEmpty()) {
            stickyHeader {
                SectionHeader(title = stringResource(R.string.completed_state))
            }
            items(completedDownloads, key = { it.hash }) { download ->
                TorrentDownloadItem(
                    download = download,
                    onPauseClick = { onPauseClick(download.hash) },
                    onResumeClick = { onResumeClick(download.hash) },
                    onDeleteClick = { onDeleteClick(download) },
                    onItemClick = { onItemClick(download) },
                )
            }
        }

        // Error downloads
        if (!showCompletedOnly && errorDownloads.isNotEmpty()) {
            stickyHeader {
                SectionHeader(title = stringResource(R.string.error_state))
            }
            items(errorDownloads, key = { it.hash }) { download ->
                TorrentDownloadItem(
                    download = download,
                    onPauseClick = { onPauseClick(download.hash) },
                    onResumeClick = { onResumeClick(download.hash) },
                    onDeleteClick = { onDeleteClick(download) },
                    onItemClick = { onItemClick(download) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
    )
}
