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

package com.jabook.app.jabook.compose.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberSupportingPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.logger.LoggerFactoryImpl
import com.jabook.app.jabook.compose.core.navigation.NavigationClickGuard
import com.jabook.app.jabook.compose.data.local.entity.SearchHistoryEntity
import com.jabook.app.jabook.compose.designsystem.component.EmptyState
import com.jabook.app.jabook.compose.domain.model.RutrackerSearchResult
import com.jabook.app.jabook.compose.domain.model.SearchFilters
import com.jabook.app.jabook.compose.domain.model.SearchSortOrder
import kotlinx.coroutines.launch

/**
 * Logger for SearchScreen Composable functions.
 */
private val searchScreenLogger by lazy { LoggerFactoryImpl().get("SearchScreen") }

/**
 * Search screen for finding audiobooks.
 *
 * Features:
 * - Real-time local search with debouncing
 * - Online Rutracker search
 * - Search results in grid layout
 * - Clear search button
 * - Loading and error states
 * - Search history
 *
 * @param onNavigateBack Callback to navigate back
 * @param onBookClick Callback when book is clicked (local Book)
 * @param onOnlineBookClick Callback when online search result is clicked
 * @param viewModel ViewModel provided by Hilt
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Suppress("DEPRECATION") // hiltViewModel is from correct package but marked deprecated in some versions
@Composable
public fun SearchScreen(
    onNavigateBack: () -> Unit,
    onBookClick: (String) -> Unit,
    onOnlineBookClick: (RutrackerSearchResult) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val localResults by viewModel.localResults.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()

    val navigationClickGuard = remember { NavigationClickGuard() }
    val safeNavigateBack = dropUnlessResumed { navigationClickGuard.run(onNavigateBack) }

    var showSortMenu by remember { mutableStateOf(false) }

    // Navigator for SupportingPaneScaffold
    val scaffoldNavigator = rememberSupportingPaneScaffoldNavigator()
    val scope = rememberCoroutineScope()

    // Check index status for online search
    val indexingViewModel: com.jabook.app.jabook.compose.feature.indexing.IndexingViewModel = hiltViewModel()
    val context = androidx.compose.ui.platform.LocalContext.current
    val isIndexing by indexingViewModel.isIndexing.collectAsStateWithLifecycle()
    var indexSize by remember { mutableStateOf(0) }
    var showIndexingMessage by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        indexSize = indexingViewModel.getIndexSize()
        if (indexSize == 0) {
            showIndexingMessage = true
        }
    }

    // Update index size when indexing completes
    androidx.compose.runtime.LaunchedEffect(isIndexing) {
        if (!isIndexing) {
            indexSize = indexingViewModel.getIndexSize()
        }
    }

    // Removed filter sheet - using adaptive pane instead

    // Premium Background Gradient
    val backgroundGradient =
        androidx.compose.ui.graphics.Brush.verticalGradient(
            colors =
                listOf(
                    MaterialTheme.colorScheme.background,
                    MaterialTheme.colorScheme.surface,
                ),
        )

    // SupportingPaneScaffold for adaptive filter display
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(backgroundGradient),
    ) {
        SupportingPaneScaffold(
            directive = scaffoldNavigator.scaffoldDirective,
            value = scaffoldNavigator.scaffoldValue,
            mainPane = {
                AnimatedPane {
                    Scaffold(
                        containerColor = Color.Transparent, // Transparent to show gradient
                        topBar = {
                            TopAppBar(
                                title = {
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
                                        trailingIcon = {
                                            if (searchQuery.isNotBlank()) {
                                                IconButton(onClick = viewModel::clearSearch) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Clear,
                                                        contentDescription = stringResource(R.string.clearSearch),
                                                    )
                                                }
                                            }
                                        },
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = safeNavigateBack) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = stringResource(R.string.back),
                                        )
                                    }
                                },
                                actions = {
                                    // Sort Button
                                    Box {
                                        IconButton(onClick = { showSortMenu = true }) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Sort,
                                                contentDescription = stringResource(R.string.sort),
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showSortMenu,
                                            onDismissRequest = { showSortMenu = false },
                                        ) {
                                            SearchSortOrder.entries.forEach { order ->
                                                DropdownMenuItem(
                                                    text = { Text(order.name.replace("_", " ")) },
                                                    onClick = {
                                                        viewModel.updateSortOrder(order)
                                                        showSortMenu = false
                                                    },
                                                    leadingIcon =
                                                        if (order == sortOrder) {
                                                            { Icon(Icons.Filled.Check, contentDescription = null) }
                                                        } else {
                                                            null
                                                        },
                                                )
                                            }
                                        }
                                    }

                                    // Filter Button - toggles supporting pane
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                if (scaffoldNavigator.canNavigateBack()) {
                                                    scaffoldNavigator.navigateBack()
                                                } else {
                                                    scaffoldNavigator.navigateTo(SupportingPaneScaffoldRole.Supporting)
                                                }
                                            }
                                        },
                                    ) {
                                        Icon(
                                            Icons.Filled.FilterList,
                                            contentDescription = stringResource(R.string.filters),
                                        )
                                    }
                                },
                                colors =
                                    androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                                        containerColor = Color.Transparent,
                                        scrolledContainerColor = Color.Transparent,
                                    ),
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) { padding ->
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                                    .padding(16.dp),
                        ) {
                            // Show indexing message if index is empty
                            if (showIndexingMessage && indexSize == 0) {
                                androidx.compose.material3.Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors =
                                        androidx.compose.material3.CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        ),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.indexNotCreatedTitle),
                                            style = MaterialTheme.typography.titleMedium,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(R.string.indexNotCreatedDescriptionLong),
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = {
                                                indexingViewModel.startIndexing(context)
                                            },
                                        ) {
                                            Text(stringResource(R.string.startIndexing))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                            }

                            // Online search button - only show if index exists
                            if (searchQuery.isNotEmpty()) {
                                if (indexSize > 0) {
                                    Button(
                                        onClick = viewModel::searchOnline,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Icon(Icons.Filled.Search, contentDescription = null)
                                        Spacer(Modifier.padding(4.dp))
                                        Text(stringResource(R.string.searchOnlineRutracker))
                                    }
                                    Spacer(Modifier.height(16.dp))
                                } else {
                                    // Show message that indexing is needed for online search
                                    val isIndexingNow = indexingViewModel.isIndexing.collectAsStateWithLifecycle().value
                                    androidx.compose.material3.Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors =
                                            androidx.compose.material3.CardDefaults.cardColors(
                                                containerColor =
                                                    if (isIndexingNow) {
                                                        MaterialTheme.colorScheme.primaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.secondaryContainer
                                                    },
                                            ),
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Text(
                                                text =
                                                    if (isIndexingNow) {
                                                        stringResource(R.string.indexingInProgressTitle)
                                                    } else {
                                                        stringResource(R.string.indexNotCreatedTitle)
                                                    },
                                                style = MaterialTheme.typography.titleSmall,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text =
                                                    if (isIndexingNow) {
                                                        stringResource(R.string.indexingInProgressDescription)
                                                    } else {
                                                        stringResource(R.string.indexRequiredForOnlineSearchDescription)
                                                    },
                                                style = MaterialTheme.typography.bodySmall,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                }
                            }

                            // Content based on UI state
                            when (val state = uiState) {
                                is SearchUiState.Idle -> {
                                    // Show local results only
                                    LocalSearchResults(
                                        query = searchQuery,
                                        results = localResults,
                                        searchHistory = searchHistory,
                                        onBookClick = onBookClick,
                                        onHistoryItemClick = { query ->
                                            viewModel.onSearchQueryChanged(query)
                                            // Optionally trigger online search automatically or just set query
                                        },
                                        onHistoryItemDelete = { id -> viewModel.deleteSearchHistoryItem(id) },
                                        onClearHistory = viewModel::clearSearchHistory,
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
                                        favoriteIds = favoriteIds,
                                        onBookClick = onOnlineBookClick,
                                        onToggleFavorite = { result -> viewModel.toggleFavorite(result) },
                                    )
                                }

                                is SearchUiState.Error -> {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.errorWithMessage, state.message),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Button(onClick = viewModel::searchOnline) {
                                            Text(stringResource(R.string.retry))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            supportingPane = {
                AnimatedPane {
                    // Show filters pane
                    SearchFiltersPane(
                        filters = filters,
                        onApplyFilters = { newFilters ->
                            viewModel.updateFilters(newFilters)
                            // On compact screens, navigate back after applying
                            scope.launch {
                                if (scaffoldNavigator.canNavigateBack()) {
                                    scaffoldNavigator.navigateBack()
                                }
                            }
                        },
                        onReset = {
                            viewModel.updateFilters(SearchFilters())
                        },
                    )
                }
            },
            modifier = Modifier, // The original modifier is now applied to the Box
        )
    }
}

/**
 * Local search results.
 */
@Composable
private fun LocalSearchResults(
    query: String,
    results: List<com.jabook.app.jabook.compose.domain.model.Book>,
    searchHistory: List<SearchHistoryEntity>,
    onBookClick: (String) -> Unit,
    onHistoryItemClick: (String) -> Unit,
    onHistoryItemDelete: (Int) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        query.isEmpty() -> {
            if (searchHistory.isNotEmpty()) {
                SearchHistoryList(
                    history = searchHistory,
                    onItemClick = onHistoryItemClick,
                    onItemDelete = onHistoryItemDelete,
                    onClearHistory = onClearHistory,
                    modifier = modifier,
                )
            } else {
                EmptyState(
                    message = stringResource(R.string.enterSearchTerm),
                )
            }
        }

        results.isEmpty() -> {
            EmptyState(
                message = stringResource(R.string.noLocalBooksFound, query),
            )
        }

        else -> {
            // Use UnifiedBooksView for local results
            com.jabook.app.jabook.compose.feature.library.UnifiedBooksView(
                books = results,
                displayMode = com.jabook.app.jabook.compose.domain.model.BookDisplayMode.GRID_COMPACT,
                actionsProvider =
                    com.jabook.app.jabook.compose.domain.model.BookActionsProvider(
                        onBookClick = onBookClick,
                        onBookLongPress = {},
                        onToggleFavorite = { _, _ -> },
                        favoriteIds = emptySet(),
                        showProgress = false,
                        showFavoriteButton = false,
                    ),
                modifier = modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SearchHistoryList(
    history: List<SearchHistoryEntity>,
    onItemClick: (String) -> Unit,
    onItemDelete: (Int) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.recentSearches),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            androidx.compose.material3.TextButton(onClick = onClearHistory) {
                Text(stringResource(R.string.clearAll))
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(
                items = history,
                key = { it.id },
            ) { item ->
                ListItem(
                    headlineContent = { Text(item.query) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { onItemDelete(item.id) }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(item.query) },
                )
            }
        }
    }
}

/**
 * Online search results using unified components.
 */
@Composable
private fun OnlineSearchResults(
    results: List<RutrackerSearchResult>,
    favoriteIds: Set<String>,
    onBookClick: (RutrackerSearchResult) -> Unit,
    onToggleFavorite: (RutrackerSearchResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Log results for debugging
    androidx.compose.runtime.LaunchedEffect(results.size) {
        searchScreenLogger.d {
            "📊 OnlineSearchResults: ${results.size} results, ${favoriteIds.size} favorites"
        }
        if (results.isNotEmpty()) {
            val sample = results.take(3)
            sample.forEachIndexed { index, result ->
                searchScreenLogger.d {
                    "  Result[$index]: id='${result.topicId}', " +
                        "title='${result.title.take(40)}', " +
                        "author='${result.author.take(30)}', " +
                        "coverUrl=${if (result.coverUrl.isNullOrBlank()) "null/empty" else "present"}, " +
                        "valid=${result.isValid()}"
                }
            }
            // Check for invalid results
            val invalidResults = results.filter { !it.isValid() }
            if (invalidResults.isNotEmpty()) {
                searchScreenLogger.w {
                    "⚠️ Found ${invalidResults.size} invalid results out of ${results.size}"
                }
                invalidResults.take(3).forEachIndexed { index, result ->
                    searchScreenLogger.w {
                        "  Invalid[$index]: id='${result.topicId}', " +
                            "title='${result.title.take(30)}', " +
                            "author='${result.author.take(20)}'"
                    }
                }
            }
        }
    }

    if (results.isEmpty()) {
        EmptyState(
            message = stringResource(R.string.noResults),
        )
    } else {
        // Convert SearchResults to Books for unified display
        val booksFromResults =
            results.mapIndexed { index, result ->
                val book =
                    com.jabook.app.jabook.compose.domain.model.Book(
                        id = result.topicId,
                        title = result.title,
                        author = result.uploader ?: result.author,
                        coverUrl = result.coverUrl,
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
                        isFavorite = favoriteIds.contains(result.topicId),
                        sourceUrl = result.torrentUrl,
                    )
                // Log if book has empty/invalid data
                if (book.title.isBlank() || book.author.isBlank()) {
                    searchScreenLogger.w {
                        "⚠️ Book[$index] has empty data: id='${book.id}', " +
                            "title='${book.title}', author='${book.author}'"
                    }
                }
                book
            }

        searchScreenLogger.d {
            "✅ Converted ${results.size} results to ${booksFromResults.size} books"
        }

        com.jabook.app.jabook.compose.feature.library.UnifiedBooksView(
            books = booksFromResults,
            displayMode = com.jabook.app.jabook.compose.domain.model.BookDisplayMode.GRID_COMPACT,
            actionsProvider =
                com.jabook.app.jabook.compose.domain.model.BookActionsProvider(
                    onBookClick = { bookId ->
                        // Find original SearchResult by topicId
                        results.find { it.topicId == bookId }?.let(onBookClick)
                    },
                    onBookLongPress = {},
                    onToggleFavorite = { bookId, _ ->
                        // Find original SearchResult by topicId
                        results.find { it.topicId == bookId }?.let(onToggleFavorite)
                    },
                    favoriteIds = favoriteIds,
                    showProgress = false,
                    showFavoriteButton = true,
                ),
            modifier = modifier.fillMaxSize(),
        )
    }
}
