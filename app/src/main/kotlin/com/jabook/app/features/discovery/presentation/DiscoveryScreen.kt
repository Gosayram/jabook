package com.jabook.app.features.discovery.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jabook.app.R
import com.jabook.app.core.domain.model.RuTrackerAudiobook
import com.jabook.app.core.domain.model.RuTrackerCategory
import com.jabook.app.features.discovery.DiscoveryUiState
import com.jabook.app.features.discovery.DiscoveryViewModel
import com.jabook.app.features.discovery.presentation.components.AudiobookSearchResultCard
import com.jabook.app.features.discovery.presentation.components.AudiobookSectionCard
import com.jabook.app.shared.ui.AppThemeMode
import com.jabook.app.shared.ui.ThemeViewModel
import com.jabook.app.shared.ui.components.EmptyStateType
import com.jabook.app.shared.ui.components.JaBookEmptyState
import com.jabook.app.shared.ui.components.ThemeToggleButton
import com.jabook.app.shared.ui.components.getDynamicVerticalPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    onNavigateToAudiobook: (RuTrackerAudiobook) -> Unit,
    onDownload: (RuTrackerAudiobook) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoveryViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel,
    themeMode: AppThemeMode,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val lazyListState = rememberLazyListState()

    val shouldLoadNextPage by remember {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            val totalItemsNumber = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1
            lastVisibleItemIndex > (totalItemsNumber - 5) &&
                uiState.isSearchActive &&
                !uiState.isLoading &&
                uiState.currentPage < uiState.totalPages
        }
    }

    LaunchedEffect(shouldLoadNextPage) {
        if (shouldLoadNextPage) viewModel.loadNextPage()
    }
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
                title = { Text(stringResource(R.string.discovery_title)) },
                actions = {
                    IconButton(onClick = { viewModel.refreshData() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                    ThemeToggleButton(themeMode = themeMode, onToggle = { themeViewModel.toggleTheme() })
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = getDynamicVerticalPadding()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    SearchBar(
                        query = uiState.searchQuery,
                        onQueryChange = viewModel::updateSearchQuery,
                        onSearch = {
                            keyboardController?.hide()
                            viewModel.performSearch()
                        },
                        onClear = viewModel::clearSearch,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    )
                }
                if (uiState.categories.isNotEmpty()) {
                    item {
                        CategoryFilters(
                            categories = uiState.categories,
                            selectedCategory = uiState.selectedCategory,
                            onCategorySelected = viewModel::selectCategory,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        )
                    }
                }
                item {
                SearchResultsSection(
                    uiState = uiState,
                    onNavigateToAudiobook = onNavigateToAudiobook,
                    onDownload = onDownload,
                )
                }
                item {
                    TrendingSection(
                        trendingAudiobooks = uiState.trendingAudiobooks,
                        onAudiobookClick = onNavigateToAudiobook,
                    )
                }
                item {
                    RecentlyAddedSection(
                        recentlyAdded = uiState.recentlyAdded,
                        onAudiobookClick = onNavigateToAudiobook,
                    )
                }
                item {
                    EmptyStateSection(
                        uiState = uiState,
                    )
                }
                item {
                    PaginationLoader(
                        isLoading = uiState.isLoading,
                        isSearchActive = uiState.isSearchActive,
                        hasResults = uiState.searchResults.isNotEmpty(),
                    )
                }
            }
            MainLoader(
                isLoading = uiState.isLoading,
                isSearchActive = uiState.isSearchActive,
                hasResults = uiState.searchResults.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text(stringResource(R.string.search_audiobooks)) },
        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = stringResource(R.string.search)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(imageVector = Icons.Default.Clear, contentDescription = stringResource(R.string.clear_search))
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilters(
    categories: List<RuTrackerCategory>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.categories),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onCategorySelected(null) },
                    label = { Text(stringResource(R.string.all_categories)) },
                )
            }

            items(categories) { category ->
                FilterChip(
                    selected = selectedCategory == category.id,
                    onClick = { onCategorySelected(category.id) },
                    label = { Text(category.name) },
                )
            }
        }
    }
}

@Composable
private fun AudiobookSection(
    title: String,
    audiobooks: List<RuTrackerAudiobook>,
    onAudiobookClick: (RuTrackerAudiobook) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
            items(audiobooks) { audiobook ->
                AudiobookSectionCard(audiobook = audiobook, onClick = {
                    onAudiobookClick(audiobook)
                }, modifier = Modifier.width(180.dp))
            }
        }
    }
}

@Composable
private fun SearchResultsSection(
    uiState: DiscoveryUiState,
    onNavigateToAudiobook: (RuTrackerAudiobook) -> Unit,
) {
    if (uiState.isSearchActive) {
        if (uiState.searchResults.isEmpty() && !uiState.isLoading) {
            JaBookEmptyState(
                state = EmptyStateType.EmptySearch,
                title = stringResource(R.string.no_search_results),
                subtitle = stringResource(R.string.try_different_search_terms),
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            )
        } else {
            Column {
                uiState.searchResults.forEach { audiobook ->
                    AudiobookSearchResultCard(
                        audiobook = audiobook,
                        onClick = { onNavigateToAudiobook(audiobook) },
                        isGuestMode = uiState.isGuestMode,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendingSection(
    trendingAudiobooks: List<RuTrackerAudiobook>,
    onAudiobookClick: (RuTrackerAudiobook) -> Unit,
) {
    if (trendingAudiobooks.isNotEmpty()) {
        AudiobookSection(
            title = stringResource(R.string.trending_audiobooks),
            audiobooks = trendingAudiobooks,
            onAudiobookClick = onAudiobookClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RecentlyAddedSection(
    recentlyAdded: List<RuTrackerAudiobook>,
    onAudiobookClick: (RuTrackerAudiobook) -> Unit,
) {
    if (recentlyAdded.isNotEmpty()) {
        AudiobookSection(
            title = stringResource(R.string.recently_added),
            audiobooks = recentlyAdded,
            onAudiobookClick = onAudiobookClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EmptyStateSection(uiState: DiscoveryUiState) {
    val shouldShowEmptyState = !uiState.isSearchActive &&
        uiState.trendingAudiobooks.isEmpty() &&
        uiState.recentlyAdded.isEmpty() &&
        !uiState.isLoading

    if (shouldShowEmptyState) {
        JaBookEmptyState(
            state = EmptyStateType.NetworkError,
            title = stringResource(R.string.no_content_available),
            subtitle = stringResource(R.string.check_internet_connection),
            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        )
    }
}

@Composable
private fun PaginationLoader(isLoading: Boolean, isSearchActive: Boolean, hasResults: Boolean) {
    if (isLoading && isSearchActive && hasResults) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun MainLoader(isLoading: Boolean, isSearchActive: Boolean, hasResults: Boolean) {
    if (isLoading && (!isSearchActive || !hasResults)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}
