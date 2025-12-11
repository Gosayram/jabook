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

package com.jabook.app.jabook.compose.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jabook.app.jabook.compose.designsystem.component.BookCard
import com.jabook.app.jabook.compose.designsystem.component.EmptyState

/**
 * Search screen for finding audiobooks.
 *
 * Features:
 * - Real-time search with debouncing
 * - Search results in grid layout
 * - Clear search button
 * - Empty state when no results
 *
 * @param onNavigateBack Callback to navigate back
 * @param onBookClick Callback when book is clicked
 * @param viewModel ViewModel provided by Hilt
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onBookClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = searchQuery,
                        onValueChange = viewModel::onSearchQueryChanged,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search books...") },
                        singleLine = true,
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        trailingIcon =
                            if (searchQuery.isNotEmpty()) {
                                {
                                    IconButton(onClick = viewModel::clearSearch) {
                                        Icon(
                                            imageVector = Icons.Filled.Clear,
                                            contentDescription = "Clear search",
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            when {
                searchQuery.isEmpty() -> {
                    EmptyState(
                        message = "Enter a search query to find books",
                    )
                }

                searchResults.isEmpty() -> {
                    EmptyState(
                        message = "No books found for \"$searchQuery\"",
                    )
                }

                else -> {
                    SearchResults(
                        results = searchResults,
                        onBookClick = onBookClick,
                    )
                }
            }
        }
    }
}

/**
 * Search results grid.
 */
@Composable
private fun SearchResults(
    results: List<com.jabook.app.jabook.compose.domain.model.Book>,
    onBookClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement =
            androidx.compose.foundation.layout.Arrangement
                .spacedBy(12.dp),
        verticalArrangement =
            androidx.compose.foundation.layout.Arrangement
                .spacedBy(16.dp),
    ) {
        items(
            items = results,
            key = { it.id },
        ) { book ->
            BookCard(
                title = book.title,
                author = book.author,
                coverUrl = book.coverUrl,
                onClick = { onBookClick(book.id) },
            )
        }
    }
}
