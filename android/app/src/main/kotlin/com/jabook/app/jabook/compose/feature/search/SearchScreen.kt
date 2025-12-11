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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jabook.app.jabook.compose.data.remote.model.SearchResult
import com.jabook.app.jabook.compose.designsystem.component.BookCard
import com.jabook.app.jabook.compose.designsystem.component.EmptyState

/**
 * Search screen for finding audiobooks.
 *
 * Features:
 * - Real-time local search with debouncing
 * - Online Rutracker search
 * - Search results in grid layout
 * - Clear search button
 * - Loading and error states
 *
 * @param onNavigateBack Callback to navigate back
 * @param onBookClick Callback when book is clicked (local Book)
 * @param onOnlineBookClick Callback when online search result is clicked
 * @param viewModel ViewModel provided by Hilt
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onNavigateBack: () -> Unit,
    onBookClick: (String) -> Unit,
    onOnlineBookClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val localResults by viewModel.localResults.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
        ) {
            // Online search button
            if (searchQuery.isNotEmpty()) {
                Button(
                    onClick = viewModel::searchOnline,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text("Search Online on Rutracker")
                }

                Spacer(Modifier.height(16.dp))
            }

            // Content based on UI state
            when (val state = uiState) {
                is SearchUiState.Idle -> {
                    // Show local results only
                    LocalSearchResults(
                        query = searchQuery,
                        results = localResults,
                        onBookClick = onBookClick,
                    )
                }

                is SearchUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is SearchUiState.Success -> {
                    OnlineSearchResults(
                        results = state.onlineResults,
                        onBookClick = onOnlineBookClick,
                    )
                }

                is SearchUiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "Error: ${state.message}",
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = viewModel::searchOnline) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Local search results.
 */
@Composable
private fun LocalSearchResults(
    query: String,
    results: List<com.jabook.app.jabook.compose.domain.model.Book>,
    onBookClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        query.isEmpty() -> {
            EmptyState(
                message = "Enter a search query to find books",
            )
        }

        results.isEmpty() -> {
            EmptyState(
                message = "No local books found for \"$query\"",
            )
        }

        else -> {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
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
    }
}

/**
 * Online search results.
 */
@Composable
private fun OnlineSearchResults(
    results: List<SearchResult>,
    onBookClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (results.isEmpty()) {
        EmptyState(
            message = "No online results found",
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(
                items = results,
                key = { it.topicId },
            ) { result ->
                OnlineBookCard(
                    result = result,
                    onClick = { onBookClick(result) },
                )
            }
        }
    }
}

/**
 * Card for online search result.
 */
@Composable
private fun OnlineBookCard(
    result: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Using BookCard with online data
    BookCard(
        title = result.title,
        author = result.author,
        coverUrl = null, // No cover from search results
        onClick = onClick,
        modifier = modifier,
    )
}
