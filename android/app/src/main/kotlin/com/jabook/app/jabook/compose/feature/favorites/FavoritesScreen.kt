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

package com.jabook.app.jabook.compose.feature.favorites

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jabook.app.jabook.compose.data.local.entity.FavoriteEntity
import com.jabook.app.jabook.compose.ui.favorites.FavoritesViewModel
import androidx.compose.ui.res.stringResource
import com.jabook.app.jabook.R

/**
 * Favorites screen displaying user's favorite audiobooks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToTopic: (String) -> Unit,
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableSetOf<String>() }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

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
                    Text(
                        if (isSelectionMode) {
                            "${selectedIds.size} selected"
                        } else {
                            "Favorites"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSelectionMode) {
                                isSelectionMode = false
                                selectedIds.clear()
                            } else {
                                onNavigateBack()
                            }
                        },
                    ) {
                        Icon(
                            if (isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isSelectionMode) "Cancel" else "Back",
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
                                    Icon(Icons.Default.Delete, "Delete selected")
                                }
                            }
                        } else {
                            // Normal mode: show menu
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, "More")
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
                                            "Clear All",
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
                "No favorite audiobooks",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Add audiobooks to favorites from search results",
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
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(favorites, key = { it.topicId }) { favorite ->
            FavoriteListItem(
                favorite = favorite,
                isFavorite = favoriteIds.contains(favorite.topicId),
                isSelectionMode = isSelectionMode,
                isSelected = selectedIds.contains(favorite.topicId),
                onToggleSelection = { onToggleSelection(favorite.topicId) },
                onClick = { onItemClick(favorite.topicId) },
                onLongClick = { onItemLongClick(favorite.topicId) },
                onToggleFavorite = { onToggleFavorite(favorite.topicId) },
            )
        }
    }
}

/**
 * Individual favorite list item.
 */
@Composable
private fun FavoriteListItem(
    favorite: FavoriteEntity,
    isFavorite: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(favorite.title) },
        supportingContent = {
            if (favorite.author.isNotEmpty()) {
                Text(favorite.author)
            }
        },
        leadingContent = {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelection() },
                )
            }
        },
        trailingContent = {
            if (!isSelectionMode) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        modifier =
            Modifier
                .clickable(onClick = onClick)
                .fillMaxWidth(),
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
        title = { Text(stringResource(R.string.clearAllFavorites1)) },
        text = {
            Text(stringResource(R.string.thisWillRemoveAllFavoriteAudiobooksThisActionCanno))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Clear All", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel1))
            }
        },
    )
}
