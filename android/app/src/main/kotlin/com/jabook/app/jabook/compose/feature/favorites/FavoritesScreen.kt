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

package com.jabook.app.jabook.compose.feature.favorites

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.navigation.NavigationClickGuard
import com.jabook.app.jabook.compose.data.local.entity.FavoriteEntity
import com.jabook.app.jabook.compose.ui.favorites.FavoritesViewModel

/**
 * Favorites screen displaying user's favorite audiobooks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun FavoritesScreen(
    viewModel: FavoritesViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToTopic: (String) -> Unit,
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()

    val navigationClickGuard = remember { NavigationClickGuard() }
    val safeNavigateBack = dropUnlessResumed { navigationClickGuard.run(onNavigateBack) }

    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableSetOf<String>() }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Show error messages
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text("${selectedIds.size} selected")
                    } else {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.searchPlaceholder)) },
                            singleLine = true,
                            colors =
                                TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = stringResource(R.string.clearSearch),
                                        )
                                    }
                                }
                            },
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSelectionMode) {
                                isSelectionMode = false
                                selectedIds.clear()
                            } else {
                                safeNavigateBack()
                            }
                        },
                    ) {
                        Icon(
                            if (isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription =
                                if (isSelectionMode) {
                                    stringResource(
                                        R.string.cancel,
                                    )
                                } else {
                                    stringResource(R.string.back)
                                },
                        )
                    }
                },
                actions = {
                    if (favorites.isNotEmpty()) {
                        if (isSelectionMode) {
                            // Selection mode: show delete button
                            if (selectedIds.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        viewModel.removeMultipleFavorites(selectedIds.toList())
                                        selectedIds.clear()
                                        isSelectionMode = false
                                    },
                                ) {
                                    Icon(Icons.Default.Delete, stringResource(R.string.deleteSelected))
                                }
                            }
                        } else {
                            // Sort menu
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = stringResource(R.string.sort_by),
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                            ) {
                                com.jabook.app.jabook.compose.data.model.BookSortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text =
                                                    when (order) {
                                                        com.jabook.app.jabook.compose.data.model.BookSortOrder.BY_ACTIVITY ->
                                                            stringResource(R.string.sort_by_activity)
                                                        com.jabook.app.jabook.compose.data.model.BookSortOrder.TITLE_ASC ->
                                                            stringResource(R.string.sort_title_asc)
                                                        com.jabook.app.jabook.compose.data.model.BookSortOrder.TITLE_DESC ->
                                                            stringResource(R.string.sort_title_desc)
                                                        com.jabook.app.jabook.compose.data.model.BookSortOrder.AUTHOR_ASC ->
                                                            stringResource(R.string.sort_author_asc)
                                                        com.jabook.app.jabook.compose.data.model.BookSortOrder.AUTHOR_DESC ->
                                                            stringResource(R.string.sort_author_desc)
                                                        com.jabook.app.jabook.compose.data.model.BookSortOrder.RECENTLY_ADDED ->
                                                            stringResource(R.string.sort_recently_added)
                                                        com.jabook.app.jabook.compose.data.model.BookSortOrder.OLDEST_FIRST ->
                                                            stringResource(R.string.sort_oldest_first)
                                                    },
                                            )
                                        },
                                        onClick = {
                                            viewModel.onSortOrderChanged(order)
                                            showSortMenu = false
                                        },
                                        leadingIcon = {
                                            if (order == sortOrder) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                )
                                            }
                                        },
                                    )
                                }
                            }

                            // Normal mode: show menu
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, stringResource(R.string.more))
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.select)) },
                                    leadingIcon = { Icon(Icons.Default.Checklist, null) },
                                    onClick = {
                                        isSelectionMode = true
                                        showMenu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(R.string.clearAll),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.DeleteSweep,
                                            null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        showClearAllDialog = true
                                        showMenu = false
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (favorites.isEmpty()) {
            FavoritesEmptyState(modifier = Modifier.padding(padding))
        } else {
            FavoritesList(
                favorites = favorites,
                favoriteIds = favoriteIds,
                isSelectionMode = isSelectionMode,
                selectedIds = selectedIds,
                onToggleSelection = { id ->
                    if (selectedIds.contains(id)) {
                        selectedIds.remove(id)
                    } else {
                        selectedIds.add(id)
                    }
                },
                onItemClick = { id ->
                    if (isSelectionMode) {
                        if (selectedIds.contains(id)) {
                            selectedIds.remove(id)
                        } else {
                            selectedIds.add(id)
                        }
                    } else {
                        onNavigateToTopic(id)
                    }
                },
                onItemLongClick = { id ->
                    if (!isSelectionMode) {
                        isSelectionMode = true
                        selectedIds.add(id)
                    }
                },
                onToggleFavorite = { id ->
                    viewModel.removeFromFavorites(id)
                },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showClearAllDialog) {
        ClearAllFavoritesDialog(
            onConfirm = {
                viewModel.clearAllFavorites()
                showClearAllDialog = false
                isSelectionMode = false
                selectedIds.clear()
            },
            onDismiss = { showClearAllDialog = false },
        )
    }
}

/**
 * Empty state when no favorites exist.
 */
@Composable
private fun FavoritesEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier.padding(bottom = 16.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Text(
                stringResource(R.string.noFavoritesMessage),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.addFavoritesHint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * List of favorite audiobooks.
 */
@Composable
private fun FavoritesList(
    favorites: List<FavoriteEntity>,
    favoriteIds: Set<String>,
    isSelectionMode: Boolean,
    selectedIds: Set<String>,
    onToggleSelection: (String) -> Unit,
    onItemClick: (String) -> Unit,
    onItemLongClick: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Convert FavoriteEntities to Books for unified display
    // Use remember to avoid recomputing on every recomposition
    val booksFromFavorites =
        remember(favorites, favoriteIds) {
            favorites.map { favorite ->
                com.jabook.app.jabook.compose.domain.model.Book(
                    id = favorite.topicId,
                    title = favorite.title,
                    author = favorite.author,
                    coverUrl = favorite.coverUrl?.takeIf { it.isNotEmpty() },
                    description = null,
                    totalDuration = kotlin.time.Duration.ZERO,
                    currentPosition = kotlin.time.Duration.ZERO,
                    progress = 0f,
                    currentChapterIndex = 0,
                    downloadStatus = com.jabook.app.jabook.compose.data.model.DownloadStatus.NOT_DOWNLOADED,
                    downloadProgress = 0f,
                    localPath = null,
                    addedDate = System.currentTimeMillis(),
                    lastPlayedDate = null,
                    isFavorite = favoriteIds.contains(favorite.topicId),
                    sourceUrl = favorite.magnetUrl.takeIf { it.isNotEmpty() },
                )
            }
        }

    com.jabook.app.jabook.compose.feature.library.UnifiedBooksView(
        books = booksFromFavorites,
        displayMode = com.jabook.app.jabook.compose.domain.model.BookDisplayMode.LIST_DEFAULT,
        actionsProvider =
            com.jabook.app.jabook.compose.domain.model.BookActionsProvider(
                onBookClick = onItemClick,
                onBookLongPress = onItemLongClick,
                onToggleFavorite = { id, _ -> onToggleFavorite(id) },
                favoriteIds = favoriteIds,
                showProgress = false,
                showFavoriteButton = !isSelectionMode, // Hide favorite button in selection mode
            ),
        isSelectionMode = isSelectionMode,
        selectedIds = selectedIds,
        onToggleSelection = onToggleSelection,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Dialog to confirm clearing all favorites.
 */
@Composable
private fun ClearAllFavoritesDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clearAllFavoritesTitle)) },
        text = {
            Text(stringResource(R.string.thisWillRemoveAllFavoriteAudiobooksThisActionCanno))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.clearAll), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
