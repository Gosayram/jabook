
package com.jabook.app.features.downloads.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.rememberDismissState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jabook.app.R
import com.jabook.app.core.domain.model.DownloadProgress
import com.jabook.app.core.domain.model.TorrentStatus
import com.jabook.app.features.downloads.DownloadsTab
import com.jabook.app.features.downloads.DownloadsViewModel
import com.jabook.app.features.downloads.presentation.components.DownloadItemCard
import com.jabook.app.shared.ui.AppThemeMode
import com.jabook.app.shared.ui.ThemeViewModel
import com.jabook.app.shared.ui.components.EmptyStateType
import com.jabook.app.shared.ui.components.JaBookEmptyState
import com.jabook.app.shared.ui.components.ThemeToggleButton
import com.jabook.app.shared.ui.components.getDynamicVerticalPadding

data class DownloadsEmptyState(
    val type: EmptyStateType,
    val title: String,
    val subtitle: String,
)

data class DownloadsActions(
    val onPause: (String) -> Unit,
    val onResume: (String) -> Unit,
    val onCancel: (String) -> Unit,
    val onRetry: (DownloadProgress) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel,
    themeMode: AppThemeMode,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle error messages
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissError()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_title)) },
                actions = {
                    if (uiState.selectedTab == DownloadsTab.Completed && uiState.completedDownloads.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearCompleted() }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = stringResource(R.string.clear_completed))
                        }
                    } else if (uiState.selectedTab == DownloadsTab.Failed && uiState.failedDownloads.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearFailed() }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = stringResource(R.string.clear_failed))
                        }
                    }
                    IconButton(onClick = { viewModel.refreshDownloads() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                    ThemeToggleButton(themeMode = themeMode, onToggle = { themeViewModel.toggleTheme() })
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = getDynamicVerticalPadding())) {
            // Tab Row
            DownloadsTabRow(
                selectedTab = uiState.selectedTab,
                onTabSelected = viewModel::selectTab,
                activeCount = uiState.activeDownloads.size,
                completedCount = uiState.completedDownloads.size,
                failedCount = uiState.failedDownloads.size,
                modifier = Modifier.fillMaxWidth(),
            )

            androidx.compose.foundation.layout
                .Spacer(modifier = Modifier.height(16.dp))

            // Content based on selected tab
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                when (uiState.selectedTab) {
                    DownloadsTab.Active -> {
                        DownloadsList(
                            downloads = uiState.activeDownloads,
                            isLoading = uiState.isLoading,
                            emptyState =
                                DownloadsEmptyState(
                                    EmptyStateType.EmptyDownloads,
                                    stringResource(R.string.no_active_downloads),
                                    stringResource(R.string.start_downloading_discovery),
                                ),
                            actions =
                                DownloadsActions({
                                    viewModel.pauseDownload(it)
                                }, { viewModel.resumeDownload(it) }, { viewModel.cancelDownload(it) }, { viewModel.retryDownload(it) }),
                            modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                        )
                    }
                    DownloadsTab.Completed -> {
                        DownloadsList(
                            downloads = uiState.completedDownloads,
                            isLoading = uiState.isLoading,
                            emptyState =
                                DownloadsEmptyState(
                                    EmptyStateType.EmptyDownloads,
                                    stringResource(R.string.no_completed_downloads),
                                    stringResource(R.string.completed_downloads_appear_here),
                                ),
                            actions =
                                DownloadsActions({
                                    viewModel.pauseDownload(it)
                                }, { viewModel.resumeDownload(it) }, { viewModel.cancelDownload(it) }, { viewModel.retryDownload(it) }),
                            modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                        )
                    }
                    DownloadsTab.Failed -> {
                        DownloadsList(
                            downloads = uiState.failedDownloads,
                            isLoading = uiState.isLoading,
                            emptyState =
                                DownloadsEmptyState(
                                    EmptyStateType.GeneralError,
                                    stringResource(R.string.no_failed_downloads),
                                    stringResource(R.string.great_no_download_errors),
                                ),
                            actions =
                                DownloadsActions({
                                    viewModel.pauseDownload(it)
                                }, { viewModel.resumeDownload(it) }, { viewModel.cancelDownload(it) }, { viewModel.retryDownload(it) }),
                            modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadsTabRow(
    selectedTab: DownloadsTab,
    onTabSelected: (DownloadsTab) -> Unit,
    activeCount: Int,
    completedCount: Int,
    failedCount: Int,
    modifier: Modifier = Modifier,
) {
    val tabs =
        listOf(
            DownloadsTab.Active to stringResource(R.string.active_downloads, activeCount),
            DownloadsTab.Completed to stringResource(R.string.completed_downloads, completedCount),
            DownloadsTab.Failed to stringResource(R.string.failed_downloads, failedCount),
        )

    PrimaryTabRow(selectedTabIndex = selectedTab.ordinal, modifier = modifier) {
        tabs.forEachIndexed { index, (tab, title) ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                text = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
            )
        }
    }
}

@Composable
private fun DownloadsList(
    downloads: List<DownloadProgress>,
    isLoading: Boolean,
    emptyState: DownloadsEmptyState,
    actions: DownloadsActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        if (isLoading && downloads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (downloads.isEmpty()) {
            JaBookEmptyState(
                state = emptyState.type,
                title = emptyState.title,
                subtitle = emptyState.subtitle,
                modifier = Modifier.fillMaxSize().padding(32.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(downloads, key = { it.torrentId }) { download ->
                    DownloadListItem(download, actions)
                }
            }
        }
    }
}

@Composable
private fun DownloadListItem(
    download: DownloadProgress,
    actions: DownloadsActions,
) {
    val dismissState =
        rememberDismissState(
            confirmStateChange = { value ->
                when (value) {
                    DismissValue.DismissedToEnd -> {
                        if (download.status == TorrentStatus.DOWNLOADING) {
                            actions.onPause(download.torrentId)
                        } else if (download.status == TorrentStatus.PAUSED) {
                            actions.onResume(download.torrentId)
                        }
                        false
                    }
                    DismissValue.DismissedToStart -> {
                        actions.onCancel(download.torrentId)
                        false
                    }
                    else -> false
                }
            },
        )
    SwipeToDismiss(
        state = dismissState,
        background = {
            val direction = dismissState.dismissDirection ?: return@SwipeToDismiss
            val color =
                if (direction == DismissDirection.StartToEnd) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            val icon =
                if (direction == DismissDirection.StartToEnd) {
                    if (download.status == TorrentStatus.PAUSED) {
                        Icons.Default.PlayArrow
                    } else {
                        Icons.Default.Pause
                    }
                } else {
                    Icons.Default.Clear
                }
            val contentTint =
                if (direction == DismissDirection.StartToEnd) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onError
                }
            Box(
                modifier = Modifier.fillMaxSize().background(color, RoundedCornerShape(8.dp)).padding(24.dp),
                contentAlignment =
                    if (direction == DismissDirection.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = contentTint)
            }
        },
        directions = setOf(DismissDirection.StartToEnd, DismissDirection.EndToStart),
    ) {
        DownloadItemCard(
            download = download,
            onPause = { actions.onPause(download.torrentId) },
            onResume = { actions.onResume(download.torrentId) },
            onCancel = { actions.onCancel(download.torrentId) },
            onRetry = { actions.onRetry(download) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
