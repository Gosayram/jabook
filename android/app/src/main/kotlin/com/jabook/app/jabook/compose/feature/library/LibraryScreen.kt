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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import com.jabook.app.jabook.compose.data.model.LibraryViewMode
import com.jabook.app.jabook.compose.designsystem.component.EmptyState
import com.jabook.app.jabook.compose.designsystem.component.ErrorScreen
import com.jabook.app.jabook.compose.designsystem.component.LoadingScreen
import com.jabook.app.jabook.compose.domain.model.BookActionsProvider
import com.jabook.app.jabook.compose.domain.model.BookDisplayMode
import kotlinx.coroutines.launch

/**
 * Library screen - displays the user's audiobook collection.
 *
 * This is the main entry point for the library feature.
 * It handles the different UI states and delegates to specific composables.
 *
 * @param onBookClick Callback when a book is clicked
 * @param viewModel ViewModel provided by Hilt
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBookClick: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val inProgress by viewModel.inProgress.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteBooks.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val snackbarHostState = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val storagePermissionText = stringResource(R.string.storagePermissionRequired)
    val foundBooksMessageTemplate = stringResource(R.string.foundBooksMessage)
    val scanFailedMessageTemplate = stringResource(R.string.scanFailedMessage)

    // Permission launcher for scanning
    val permissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract =
                androidx.activity.result.contract.ActivityResultContracts
                    .RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                viewModel.startLibraryScan()
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar(storagePermissionText)
                }
            }
        }

    // Observe scan state changes
    androidx.compose.runtime.LaunchedEffect(scanState) {
        when (val state = scanState) {
            is ScanState.Completed -> {
                snackbarHostState.showSnackbar(foundBooksMessageTemplate.format(state.booksFound))
            }
            is ScanState.Failed -> {
                snackbarHostState.showSnackbar(scanFailedMessageTemplate.format(state.error))
            }
            else -> {}
        }
    }

    // Get context for permission check in pull-to-refresh
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { }, // Empty title for more space
                    windowInsets =
                        androidx.compose.foundation.layout
                            .WindowInsets(0, 0, 0, 0),
                    actions = {
                        val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()

                        // Sort menu
                        SortOrderMenu(
                            currentSortOrder = sortOrder,
                            onSortOrderChanged = viewModel::onSortOrderChanged,
                        )

                        // View mode toggle
                        ViewModeToggle(
                            currentMode = viewMode,
                            onModeChanged = viewModel::onViewModeChanged,
                        )

                        // Search button
                        IconButton(onClick = onNavigateToSearch) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = stringResource(R.string.search),
                            )
                        }
                        // Downloads button
                        IconButton(onClick = onNavigateToDownloads) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = stringResource(R.string.downloads),
                            )
                        }
                    },
                )
            },
            floatingActionButton = {
                androidx.compose.material3.FloatingActionButton(
                    onClick = {
                        val permission =
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                android.Manifest.permission.READ_MEDIA_AUDIO
                            } else {
                                android.Manifest.permission.READ_EXTERNAL_STORAGE
                            }
                        permissionLauncher.launch(permission)
                    },
                ) {
                    if (scanState is ScanState.Scanning) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.padding(8.dp),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.scanLibrary),
                        )
                    }
                }
            },
            contentWindowInsets =
                androidx.compose.foundation.layout
                    .WindowInsets(0, 0, 0, 0),
            modifier = Modifier.fillMaxSize(),
        ) { padding ->
            androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                isRefreshing = scanState is ScanState.Scanning,
                onRefresh = {
                    val permission =
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            android.Manifest.permission.READ_MEDIA_AUDIO
                        } else {
                            android.Manifest.permission.READ_EXTERNAL_STORAGE
                        }
                    // Check permission and start scan using pre-obtained context
                    val hasPermission =
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            permission,
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                    if (hasPermission) {
                        viewModel.startLibraryScan()
                    } else {
                        permissionLauncher.launch(permission)
                    }
                },
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                when (uiState) {
                    is LibraryUiState.Loading -> {
                        LoadingScreen(message = stringResource(R.string.loadingLibrary))
                    }

                    is LibraryUiState.Success -> {
                        val books = (uiState as LibraryUiState.Success).books
                        val actionsProvider =
                            viewModel.createBookActionsProvider(
                                onBookClick = onBookClick,
                            )

                        // Use unified view for all display modes
                        UnifiedBooksView(
                            books = books,
                            displayMode = viewMode.toBookDisplayMode(),
                            actionsProvider = actionsProvider,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    is LibraryUiState.Empty -> {
                        EmptyState(
                            message = stringResource(R.string.noBooksInLibrary),
                        )
                    }

                    is LibraryUiState.Error -> {
                        ErrorScreen(
                            message = (uiState as LibraryUiState.Error).message,
                        )
                    }
                }
            }

            // Book properties dialog
            val selectedBook by viewModel.selectedBookForProperties.collectAsStateWithLifecycle()
            selectedBook?.let { book ->
                BookPropertiesDialog(
                    book = book,
                    onDismiss = viewModel::hideBookProperties,
                )
            }
        }

        androidx.compose.material3.SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .padding(top = 16.dp)
                    .statusBarsPadding(),
        )
    }
}

/**
 * Content composable that handles different UI states.
 * @deprecated Use EnhancedLibraryContent directly from LibraryScreen
 */
@Composable
private fun LibraryContent(
    uiState: LibraryUiState,
    onBookClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when (uiState) {
            is LibraryUiState.Loading -> {
                LoadingScreen(message = stringResource(R.string.loadingLibrary))
            }

            is LibraryUiState.Success -> {
                // Using simple provider since this deprecated composable
                // doesn't have access to favorites or other state
                val simpleProvider =
                    BookActionsProvider(
                        onBookClick = onBookClick,
                        onBookLongPress = {},
                        onToggleFavorite = { _, _ -> },
                    )

                UnifiedBooksView(
                    books = uiState.books,
                    displayMode = BookDisplayMode.GRID_COMPACT,
                    actionsProvider = simpleProvider,
                )
            }

            is LibraryUiState.Empty -> {
                EmptyState(
                    message = stringResource(R.string.noBooksInLibrary),
                )
            }

            is LibraryUiState.Error -> {
                ErrorScreen(
                    message = uiState.message,
                )
            }
        }
    }
}

/**
 * View mode toggle buttons for switching between list and grid views.
 */
@Composable
private fun ViewModeToggle(
    currentMode: LibraryViewMode,
    onModeChanged: (LibraryViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Row(modifier = modifier) {
        // List view
        IconButton(
            onClick = { onModeChanged(LibraryViewMode.LIST_COMPACT) },
        ) {
            Icon(
                imageVector =
                    if (currentMode == LibraryViewMode.LIST_COMPACT) {
                        Icons.AutoMirrored.Filled.List
                    } else {
                        Icons.AutoMirrored.Outlined.List
                    },
                contentDescription = stringResource(R.string.viewModeList),
                tint =
                    if (currentMode == LibraryViewMode.LIST_COMPACT) {
                        androidx.compose.material3.MaterialTheme.colorScheme.primary
                    } else {
                        androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }

        // Grid view - toggle between compact and comfortable
        IconButton(
            onClick = {
                onModeChanged(
                    if (currentMode.isGrid()) {
                        // Cycle between GRID_COMPACT and GRID_COMFORTABLE
                        if (currentMode == LibraryViewMode.GRID_COMPACT) {
                            LibraryViewMode.GRID_COMFORTABLE
                        } else {
                            LibraryViewMode.GRID_COMPACT
                        }
                    } else {
                        LibraryViewMode.GRID_COMPACT
                    },
                )
            },
        ) {
            Icon(
                imageVector =
                    if (currentMode.isGrid()) {
                        Icons.Filled.GridView
                    } else {
                        Icons.Outlined.GridView
                    },
                contentDescription = stringResource(R.string.viewModeGrid),
                tint =
                    if (currentMode.isGrid()) {
                        androidx.compose.material3.MaterialTheme.colorScheme.primary
                    } else {
                        androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

/**
 * Helper extension to check if view mode is a grid variant.
 */
private fun LibraryViewMode.isGrid() = this == LibraryViewMode.GRID_COMPACT || this == LibraryViewMode.GRID_COMFORTABLE

/**
 * Sort order menu.
 */
@Composable
private fun SortOrderMenu(
    currentSortOrder: com.jabook.app.jabook.compose.data.model.BookSortOrder,
    onSortOrderChanged: (com.jabook.app.jabook.compose.data.model.BookSortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = stringResource(R.string.sort_by),
            )
        }

        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            com.jabook.app.jabook.compose.data.model.BookSortOrder.entries.forEach { order ->
                androidx.compose.material3.DropdownMenuItem(
                    text = {
                        Text(
                            text =
                                when (order) {
                                    com.jabook.app.jabook.compose.data.model.BookSortOrder.BY_ACTIVITY ->
                                        stringResource(
                                            R.string.sort_by_activity,
                                        )
                                    com.jabook.app.jabook.compose.data.model.BookSortOrder.TITLE_ASC ->
                                        stringResource(
                                            R.string.sort_title_asc,
                                        )
                                    com.jabook.app.jabook.compose.data.model.BookSortOrder.TITLE_DESC ->
                                        stringResource(
                                            R.string.sort_title_desc,
                                        )
                                    com.jabook.app.jabook.compose.data.model.BookSortOrder.AUTHOR_ASC ->
                                        stringResource(
                                            R.string.sort_author_asc,
                                        )
                                    com.jabook.app.jabook.compose.data.model.BookSortOrder.AUTHOR_DESC ->
                                        stringResource(
                                            R.string.sort_author_desc,
                                        )
                                    com.jabook.app.jabook.compose.data.model.BookSortOrder.RECENTLY_ADDED ->
                                        stringResource(
                                            R.string.sort_recently_added,
                                        )
                                    com.jabook.app.jabook.compose.data.model.BookSortOrder.OLDEST_FIRST ->
                                        stringResource(
                                            R.string.sort_oldest_first,
                                        )
                                },
                        )
                    },
                    onClick = {
                        onSortOrderChanged(order)
                        expanded = false
                    },
                    leadingIcon = {
                        if (order == currentSortOrder) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * Converts LibraryViewMode to BookDisplayMode.
 * Temporary helper during migration period.
 */
private fun LibraryViewMode.toBookDisplayMode(): com.jabook.app.jabook.compose.domain.model.BookDisplayMode =
    when (this) {
        LibraryViewMode.LIST_COMPACT -> com.jabook.app.jabook.compose.domain.model.BookDisplayMode.LIST_COMPACT
        LibraryViewMode.GRID_COMPACT -> com.jabook.app.jabook.compose.domain.model.BookDisplayMode.GRID_COMPACT
        LibraryViewMode.GRID_COMFORTABLE -> com.jabook.app.jabook.compose.domain.model.BookDisplayMode.GRID_COMFORTABLE
    }
