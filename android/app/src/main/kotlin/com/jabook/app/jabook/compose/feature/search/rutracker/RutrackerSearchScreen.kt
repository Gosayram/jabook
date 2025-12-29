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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.asImage
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.request.transformations
import coil3.transform.RoundedCornersTransformation
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.data.remote.model.SearchResult

/**
 * RuTracker search screen.
 *
 * Demonstrates integration of all RuTracker components:
 * - RutrackerSimpleDecoder (simple encoding decoder matching Flutter)
 * - RutrackerParser with cascading selectors
 * - ParsingResult error handling
 * - MirrorManager, proper headers
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("DEPRECATION") // hiltViewModel is from correct package
@Composable
fun RutrackerSearchScreen(
    onNavigateBack: () -> Unit,
    onTopicClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RutrackerSearchViewModel = hiltViewModel(),
    indexingViewModel: com.jabook.app.jabook.compose.feature.indexing.IndexingViewModel = hiltViewModel(),
) {
    var searchQuery by remember { mutableStateOf("") }
    val searchState by viewModel.searchState.collectAsState()
    val filters by viewModel.filters.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()

    // Indexing state
    val indexingProgress by indexingViewModel.indexingProgress.collectAsState()
    val isIndexing by indexingViewModel.isIndexing.collectAsState()
    var showIndexingDialog by remember { mutableStateOf(false) }

    var showFilters by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Check if indexing is needed on first load
    LaunchedEffect(Unit) {
        val indexSize = indexingViewModel.getIndexSize()
        if (indexSize == 0) {
            // No index, show dialog to start indexing
            showIndexingDialog = true
        } else {
            val needsUpdate = indexingViewModel.needsUpdate()
            if (needsUpdate) {
                // Index is old, suggest update
                showIndexingDialog = true
            }
        }
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
                    IconButton(onClick = onNavigateBack) {
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
                    .padding(16.dp),
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

            Spacer(modifier = Modifier.height(16.dp))

            // Index status card
            val indexSize = remember { mutableStateOf(0) }
            LaunchedEffect(Unit) {
                indexSize.value = indexingViewModel.getIndexSize()
            }

            if (indexSize.value == 0) {
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
                            text = "Индекс не создан",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Для быстрого поиска необходимо создать индекс форумов",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                showIndexingDialog = true
                                indexingViewModel.startIndexing()
                            },
                        ) {
                            Text("Начать индексацию")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else {
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
                            Text(
                                text = "Индекс: ${indexSize.value} тем",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "Поиск работает офлайн",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            onClick = {
                                showIndexingDialog = true
                                indexingViewModel.startIndexing()
                            },
                        ) {
                            Text("Обновить")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
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

            Spacer(modifier = Modifier.height(16.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SearchResultCard(
    result: SearchResult,
    isInLibrary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = context.resources.configuration
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Cover image - adaptive size based on orientation
            result.coverUrl?.let { coverUrl ->
                val density = context.resources.displayMetrics.density
                val cornerRadiusPx = 8f * density

                // Smaller cover in landscape to save space
                val coverWidth = if (isLandscape) 60.dp else 80.dp
                val coverHeight = if (isLandscape) 90.dp else 120.dp

                val imageRequest =
                    ImageRequest
                        .Builder(context)
                        .data(coverUrl)
                        .crossfade(true)
                        .allowHardware(true)
                        .transformations(RoundedCornersTransformation(cornerRadiusPx))
                        .placeholder(
                            android.graphics.drawable
                                .ColorDrawable(
                                    MaterialTheme.colorScheme.surfaceVariant.toArgb(),
                                ).asImage(),
                        ).error(
                            android.graphics.drawable
                                .ColorDrawable(
                                    MaterialTheme.colorScheme.error.toArgb(),
                                ).asImage(),
                        ).fallback(
                            android.graphics.drawable
                                .ColorDrawable(
                                    MaterialTheme.colorScheme.surfaceVariant.toArgb(),
                                ).asImage(),
                        ).build()

                AsyncImage(
                    model = imageRequest,
                    contentDescription = result.title,
                    modifier =
                        Modifier
                            .width(coverWidth)
                            .height(coverHeight),
                    contentScale = ContentScale.Crop,
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
