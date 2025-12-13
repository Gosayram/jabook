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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.lyricist.LocalStrings
import com.jabook.app.jabook.compose.designsystem.component.EmptyState
import com.jabook.app.jabook.compose.designsystem.component.ErrorScreen
import com.jabook.app.jabook.compose.designsystem.component.LoadingScreen

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
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val inProgress by viewModel.inProgress.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteBooks.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val snackbarHostState = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Permission launcher for scanning
    val permissionLauncher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                viewModel.startLibraryScan()
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar("Storage permission required to scan books")
                }
            }
        }

    // Observe scan state changes
    androidx.compose.runtime.LaunchedEffect(scanState) {
        when (val state = scanState) {
            is ScanState.Completed -> {
                snackbarHostState.showSnackbar("Found ${state.booksFound} books")
            }
            is ScanState.Failed -> {
                snackbarHostState.showSnackbar("Scan failed: ${state.error}")
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    // Search button
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                        )
                    }
                    // Downloads button
                    IconButton(onClick = onNavigateToDownloads) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Downloads",
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
                        contentDescription = "Scan Library",
                    )
                }
            }
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (uiState) {
                is LibraryUiState.Loading -> {
                    LoadingScreen(message = "Loading library...")
                }

                is LibraryUiState.Success -> {
                    EnhancedLibraryContent(
                        allBooks = (uiState as LibraryUiState.Success).books,
                        recentlyPlayed = recentlyPlayed,
                        inProgress = inProgress,
                        favorites = favorites,
                        onBookClick = onBookClick,
                        onToggleFavorite = viewModel::toggleFavorite,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is LibraryUiState.Empty -> {
                    EmptyState(
                        message = "No books in your library yet.\nAdd books to get started!",
                    )
                }

                is LibraryUiState.Error -> {
                    ErrorScreen(
                        message = (uiState as LibraryUiState.Error).message,
                    )
                }
            }
        }
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
                LoadingScreen(message = "Loading library...")
            }

            is LibraryUiState.Success -> {
                BooksList(
                    books = uiState.books,
                    onBookClick = onBookClick,
                )
            }

            is LibraryUiState.Empty -> {
                EmptyState(
                    message = "No books in your library yet.\nAdd books to get started!",
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
