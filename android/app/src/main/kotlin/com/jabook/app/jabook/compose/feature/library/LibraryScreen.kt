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
import androidx.compose.material.icons.filled.Search
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
import com.jabook.app.jabook.compose.designsystem.component.EmptyState
import com.jabook.app.jabook.compose.designsystem.component.ErrorScreen
import com.jabook.app.jabook.compose.designsystem.component.LoadingScreen
import com.jabook.app.jabook.compose.l10n.LocalStrings

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
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val inProgress by viewModel.inProgress.collectAsStateWithLifecycle()
    val favorites by viewModel.favoriteBooks.collectAsStateWithLifecycle()
    val strings = LocalStrings.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.screenLibrary) },
                actions = {
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                        )
                    }
                },
            )
        },
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
