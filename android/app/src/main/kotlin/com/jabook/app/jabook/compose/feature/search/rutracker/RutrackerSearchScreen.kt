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

package com.jabook.app.jabook.compose.feature.search.rutracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.navigation.NavigationClickGuard
import com.jabook.app.jabook.compose.core.util.AdaptiveUtils
import com.jabook.app.jabook.compose.core.util.CoverWaterfallPolicy
import com.jabook.app.jabook.compose.designsystem.component.RemoteImage
import com.jabook.app.jabook.compose.domain.model.RutrackerSearchResult
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * RuTracker search screen.
 *
 * Demonstrates integration of all RuTracker components:
 * - RutrackerSimpleDecoder (simple encoding decoder matching Flutter)
 * - RutrackerParser with cascading selectors
 * - ParsingResult error handling
 * - MirrorManager, proper headers
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
public fun RutrackerSearchScreen(
    onNavigateBack: () -> Unit,
    onTopicClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RutrackerSearchViewModel = hiltViewModel(),
    indexingViewModel: com.jabook.app.jabook.compose.feature.indexing.IndexingViewModel = hiltViewModel(),
) {
    // Get window size class for adaptive sizing
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity =
        context as? android.app.Activity
            ?: (context as? androidx.appcompat.view.ContextThemeWrapper)?.baseContext as? android.app.Activity
    val rawWindowSizeClass = activity?.let { calculateWindowSizeClass(it) }
    val windowSizeClass = AdaptiveUtils.resolveWindowSizeClassOrNull(rawWindowSizeClass, context)
    val contentPadding = AdaptiveUtils.getContentPaddingOrDefault(windowSizeClass)
    val itemSpacing = AdaptiveUtils.getItemSpacingOrDefault(windowSizeClass)

    var searchQuery by remember { mutableStateOf("") }
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()

    // Indexing state
    val indexingProgress by indexingViewModel.indexingProgress.collectAsStateWithLifecycle()
    val isIndexing by indexingViewModel.isIndexing.collectAsStateWithLifecycle()
    val indexSize by indexingViewModel.indexSize.collectAsStateWithLifecycle()
    val navigationClickGuard = remember { NavigationClickGuard() }
    val safeNavigateBack = dropUnlessResumed { navigationClickGuard.run(onNavigateBack) }
    var showIndexingDialog by remember { mutableStateOf(false) }

    var showFilters by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var indexCheckCompleted by remember { mutableStateOf(false) }

    // Initial index check for UI status; do not auto-open indexing dialog here to avoid
    // false-positive prompts while foreground indexing state is still synchronizing.
    LaunchedEffect(Unit) {
        indexingViewModel.getIndexSize()
        indexCheckCompleted = true
    }

    // Show indexing dialog when indexing is active
    if (showIndexingDialog && indexingProgress !is com.jabook.app.jabook.compose.data.indexing.IndexingProgress.Idle) {
        com.jabook.app.jabook.compose.feature.indexing.IndexingProgressDialog(
            progress = indexingProgress,
            onDismiss = {
                if (indexingProgress is com.jabook.app.jabook.compose.data.indexing.IndexingProgress.Completed ||
                    indexingProgress is com.jabook.app.jabook.compose.data.indexing.IndexingProgress.Error
                ) {
                    showIndexingDialog = false
                }
            },
            onHide = {
                // Hide dialog and start foreground service to continue indexing in background
                showIndexingDialog = false
                indexingViewModel.startIndexingInBackground(context)
            },
        )
    }

    // Filter state for modal
    var tempMinSeeders by remember { mutableIntStateOf(filters.minSeeders ?: 0) }
    var tempMinSizeMb by remember { mutableIntStateOf(filters.minSizeMb ?: 0) }
    var tempMaxSizeMb by remember { mutableIntStateOf(filters.maxSizeMb ?: 0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_rutracker)) },
                navigationIcon = {
                    IconButton(onClick = safeNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                },
                actions = {
                    // Sort button with menu
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.sort))
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_relevance)) },
                                onClick = {
                                    viewModel.updateSortOrder(RutrackerSortOrder.RELEVANCE)
                                    showSortMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_seeders_desc)) },
                                onClick = {
                                    viewModel.updateSortOrder(RutrackerSortOrder.SEEDERS_DESC)
                                    showSortMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_size_desc)) },
                                onClick = {
                                    viewModel.updateSortOrder(RutrackerSortOrder.SIZE_DESC)
                                    showSortMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_size_asc)) },
                                onClick = {
                                    viewModel.updateSortOrder(RutrackerSortOrder.SIZE_ASC)
                                    showSortMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_title_asc)) },
                                onClick = {
                                    viewModel.updateSortOrder(RutrackerSortOrder.TITLE_ASC)
                                    showSortMenu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sort_title_desc)) },
                                onClick = {
                                    viewModel.updateSortOrder(RutrackerSortOrder.TITLE_DESC)
                                    showSortMenu = false
                                },
                            )
                        }
                    }

                    // Filter button
                    IconButton(onClick = { showFilters = true }) {
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = stringResource(R.string.filters_and_sort),
                        )
                    }
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(contentPadding),
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_query)) },
                trailingIcon = {
                    IconButton(onClick = { viewModel.search(searchQuery) }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                    }
                },
                singleLine = true,
            )

            Spacer(modifier = Modifier.height(itemSpacing))

            // Index status card
            when {
                indexCheckCompleted &&
                    indexSize == 0 &&
                    (isIndexing || indexingProgress is com.jabook.app.jabook.compose.data.indexing.IndexingProgress.InProgress) -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.indexingInProgressTitle),
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.indexingInProgressDescription),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(itemSpacing))
                }
                indexCheckCompleted &&
                    indexSize == 0 &&
                    !isIndexing &&
                    indexingProgress !is com.jabook.app.jabook.compose.data.indexing.IndexingProgress.InProgress -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
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
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.indexNotCreatedDescriptionShort),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    showIndexingDialog = true
                                    indexingViewModel.startIndexing(context)
                                },
                            ) {
                                Text(stringResource(R.string.startIndexing))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(itemSpacing))
                }
                else -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val indexTopicsCount =
                                    pluralStringResource(
                                        R.plurals.indexTopicsCount,
                                        indexSize,
                                        indexSize,
                                    )
                                Text(
                                    text = stringResource(R.string.indexStatusWithTopics, indexTopicsCount),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = stringResource(R.string.searchWorksOffline),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(
                                onClick = {
                                    showIndexingDialog = true
                                    indexingViewModel.startIndexing(context)
                                },
                            ) {
                                Text(stringResource(R.string.updateAction))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(itemSpacing))
                }
            }

            // Results
            when (val state = searchState) {
                is SearchState.Empty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.enter_search_query),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is SearchState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is SearchState.Success -> {
                    val resultsListState = rememberLazyListState()
                    LaunchedEffect(state.results) {
                        snapshotFlow {
                            resultsListState.layoutInfo.visibleItemsInfo
                                .mapNotNull { visibleItem ->
                                    state.results.getOrNull(visibleItem.index)?.result?.let { result ->
                                        result.topicId.takeIf {
                                            it.isNotBlank() && result.coverUrl.isNullOrBlank()
                                        }
                                    }
                                }.filter(String::isNotBlank)
                        }.distinctUntilChanged()
                            .collect(viewModel::requestCoverLoads)
                    }

                    LazyColumn(
                        state = resultsListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(itemSpacing),
                        contentPadding = PaddingValues(vertical = itemSpacing),
                    ) {
                        if (state.isCached) {
                            item {
                                OfflineIndicator()
                            }
                        }
                        items(state.results, key = { it.result.topicId }) { uiModel ->
                            SearchResultCard(
                                result = uiModel.result,
                                isInLibrary = uiModel.isInLibrary,
                                onClick = { onTopicClick(uiModel.result.topicId) },
                                onCoverNeeded = viewModel::requestCoverLoad,
                            )
                        }
                    }
                }

                is SearchState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.error),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                state.message ?: stringResource(R.string.unknownError),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    // Show filters modal if enabled
    if (showFilters) {
        FilterBottomSheet(
            filters = filters,
            onDismiss = { showFilters = false },
            onApply = { newFilters ->
                viewModel.updateFilters(newFilters)
            },
            itemSpacing = itemSpacing,
        )
    }
}

/**
 * Filter bottom sheet for RuTracker search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    filters: RutrackerSearchFilters,
    onDismiss: () -> Unit,
    onApply: (RutrackerSearchFilters) -> Unit,
    itemSpacing: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    var tempMinSeeders by remember { mutableIntStateOf(filters.minSeeders ?: 0) }
    var tempMinSizeMb by remember { mutableIntStateOf(filters.minSizeMb ?: 0) }
    var tempMaxSizeMb by remember { mutableIntStateOf(filters.maxSizeMb ?: 0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Text(
                stringResource(R.string.filters_and_sort),
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(modifier = Modifier.height(itemSpacing))

            // Min Seeders
            OutlinedTextField(
                value = if (tempMinSeeders == 0) "" else tempMinSeeders.toString(),
                onValueChange = { tempMinSeeders = it.toIntOrNull() ?: 0 },
                label = { Text(stringResource(R.string.min_seeders)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Min Size
            OutlinedTextField(
                value = if (tempMinSizeMb == 0) "" else tempMinSizeMb.toString(),
                onValueChange = { tempMinSizeMb = it.toIntOrNull() ?: 0 },
                label = { Text(stringResource(R.string.min_size_mb)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Max Size
            OutlinedTextField(
                value = if (tempMaxSizeMb == 0) "" else tempMaxSizeMb.toString(),
                onValueChange = { tempMaxSizeMb = it.toIntOrNull() ?: 0 },
                label = { Text(stringResource(R.string.max_size_mb)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(itemSpacing))

            // Buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Reset button
                TextButton(
                    onClick = {
                        tempMinSeeders = 0
                        tempMinSizeMb = 0
                        tempMaxSizeMb = 0
                        onApply(RutrackerSearchFilters())
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.reset_filters))
                }

                // Apply button
                androidx.compose.material3.Button(
                    onClick = {
                        onApply(
                            RutrackerSearchFilters(
                                minSeeders = tempMinSeeders.takeIf { it > 0 },
                                minSizeMb = tempMinSizeMb.takeIf { it > 0 },
                                maxSizeMb = tempMaxSizeMb.takeIf { it > 0 },
                            ),
                        )
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.apply_filters))
                }
            }

            Spacer(modifier = Modifier.height(itemSpacing))
        }
    }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun SearchResultCard(
    result: RutrackerSearchResult,
    isInLibrary: Boolean,
    onClick: () -> Unit,
    onCoverNeeded: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity =
        context as? android.app.Activity
            ?: (context as? androidx.appcompat.view.ContextThemeWrapper)?.baseContext as? android.app.Activity
    val rawWindowSizeClass = activity?.let { calculateWindowSizeClass(it) }
    val windowSizeClass = AdaptiveUtils.resolveWindowSizeClassOrNull(rawWindowSizeClass, context)
    val isCompact = windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Compact

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        // Use adaptive padding and spacing
        val cardPadding = AdaptiveUtils.getCardPaddingOrDefault(windowSizeClass)
        val itemSpacing = AdaptiveUtils.getItemSpacingOrDefault(windowSizeClass)
        Row(
            modifier = Modifier.padding(cardPadding),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            if (result.coverUrl.isNullOrBlank()) {
                LaunchedEffect(result.topicId) {
                    onCoverNeeded(result.topicId)
                }
            }
            // Cover image - adaptive size based on screen size
            // Adaptive cover size: smaller on compact screens
            val coverWidth = if (isCompact) 60.dp else 80.dp
            val coverHeight = if (isCompact) 90.dp else 120.dp
            result.coverUrl?.takeIf { it.isNotBlank() }?.let { coverUrl ->
                val normalizedCoverUrl =
                    (CoverWaterfallPolicy.resolveOnlineUrl(coverUrl)?.data as? String)
                        ?: coverUrl

                RemoteImage(
                    src = normalizedCoverUrl,
                    contentDescription = result.title,
                    modifier =
                        Modifier
                            .width(coverWidth)
                            .height(coverHeight),
                    contentScale = ContentScale.Crop,
                    cornerRadius = 8f,
                )
            } ?: Box(
                modifier =
                    Modifier
                        .width(coverWidth)
                        .height(coverHeight)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Book,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Content
            Column(
                modifier = Modifier.weight(1f),
            ) {
                // Title
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        result.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    if (isInLibrary) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.in_library),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(20.dp).height(20.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Author
                Text(
                    result.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Size
                    Text(
                        result.size,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    // Seeders/Leechers
                    Row {
                        Text(
                            "↑ ${result.seeders}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "↓ ${result.leechers}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineIndicator(modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier =
                    Modifier
                        .width(16.dp)
                        .height(16.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.results_from_cache),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
