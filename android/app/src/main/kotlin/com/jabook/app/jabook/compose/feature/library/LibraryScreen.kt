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

package com.jabook.app.jabook.compose.feature.library

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldPredictiveBackHandler
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.SideEffect
import com.jabook.app.jabook.compose.core.navigation.NavigationClickGuard
import com.jabook.app.jabook.compose.core.util.AdaptiveUtils
import com.jabook.app.jabook.compose.core.util.LocalWindowSizeClass
import com.jabook.app.jabook.compose.data.model.BookSortOrder
import com.jabook.app.jabook.compose.data.model.LibraryViewMode
import com.jabook.app.jabook.compose.designsystem.component.BookActionsBottomSheet
import com.jabook.app.jabook.compose.designsystem.component.ChipRow
import com.jabook.app.jabook.compose.designsystem.component.EmptyState
import com.jabook.app.jabook.compose.designsystem.component.ErrorScreen
import com.jabook.app.jabook.compose.designsystem.component.JabookModalBottomSheet
import com.jabook.app.jabook.compose.designsystem.component.LibraryFilterChip
import com.jabook.app.jabook.compose.designsystem.component.LibraryLoadingSkeleton
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.BookActionsProvider
import com.jabook.app.jabook.compose.domain.model.BookDisplayMode
import com.jabook.app.jabook.compose.feature.discovery.DiscoveryGenre
import com.jabook.app.jabook.compose.feature.discovery.DiscoveryScreen
import com.jabook.app.jabook.compose.feature.discovery.DiscoveryUiState
import com.jabook.app.jabook.compose.feature.discovery.ListeningMood
import com.jabook.app.jabook.compose.feature.onboarding.SpotlightOverlay
import com.jabook.app.jabook.ui.theme.GenreAccentColors
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Library screen - displays the user's audiobook collection.
 *
 * This is the main entry point for the library feature.
 * It handles the different UI states and delegates to specific composables.
 * Uses Material 3 Adaptive ListDetailPaneScaffold for proper list-detail pattern on larger screens.
 *
 * @param onBookClick Callback when a book is clicked
 * @param onNavigateToSearch Callback to navigate to search screen
 * @param onNavigateToDownloads Callback to navigate to downloads screen
 * @param onNavigateToFavorites Callback to navigate to favorites screen
 * @param onNavigateToAudioSettings Callback to navigate to audio settings screen
 * @param onFirstMeaningfulContentDrawn Callback for performance tracking
 * @param viewModel ViewModel provided by Hilt
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3AdaptiveApi::class,
    ExperimentalMaterial3WindowSizeClassApi::class,
)
@Composable
public fun LibraryScreen(
    onBookClick: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToFavorites: () -> Unit = {},
    onNavigateToAudioSettings: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToAuth: () -> Unit = {},
    onFirstMeaningfulContentDrawn: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val favoriteBooks by viewModel.favoriteBooks.collectAsStateWithLifecycle()
    val selectedBook by viewModel.selectedBookForProperties.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val navigationClickGuard = remember { NavigationClickGuard() }
    val safeNavigateToFavorites = dropUnlessResumed { navigationClickGuard.run(onNavigateToFavorites) }
    val safeNavigateToSearch = dropUnlessResumed { navigationClickGuard.run(onNavigateToSearch) }
    val safeNavigateToDownloads = dropUnlessResumed { navigationClickGuard.run(onNavigateToDownloads) }
    val safeNavigateToSettings = dropUnlessResumed { navigationClickGuard.run(onNavigateToSettings) }
    val safeNavigateToAuth = dropUnlessResumed { navigationClickGuard.run(onNavigateToAuth) }
    var activeQuickFilter by rememberSaveable { mutableStateOf(LibraryQuickFilter.ALL) }
    var showSortBottomSheet by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchBarExpanded by rememberSaveable { mutableStateOf(false) }
    val spotlightCompleted by viewModel.spotlightCompleted.collectAsStateWithLifecycle()
    var spotlightStep by rememberSaveable { mutableStateOf(0) }
    var selectedBookForActions by remember { mutableStateOf<Book?>(null) }
    var showDiscovery by rememberSaveable { mutableStateOf(false) }
    var showOverflowMenu by rememberSaveable { mutableStateOf(false) }

    var listeningMood by rememberSaveable { mutableStateOf(ListeningMood.RELAXING) }

    val storagePermissionText = stringResource(R.string.storagePermissionRequired)
    val coverUpdatedMessage = stringResource(R.string.coverUpdated)
    val coverUpdateFailedMessage = stringResource(R.string.coverUpdateFailed)
    val spotlightSkipText = stringResource(R.string.spotlightSkip)
    val spotlightNextText = stringResource(R.string.spotlightNext)
    val spotlightSearchTitle = stringResource(R.string.spotlightSearchTitle)
    val spotlightSearchDescription = stringResource(R.string.spotlightSearchDescription)
    val spotlightDownloadsTitle = stringResource(R.string.spotlightDownloadsTitle)
    val spotlightDownloadsDescription = stringResource(R.string.spotlightDownloadsDescription)
    var hasReportedMeaningfulContent by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (hasReportedMeaningfulContent) return@LaunchedEffect
        val isMeaningfulState =
            uiState is LibraryUiState.Success ||
                uiState is LibraryUiState.Empty ||
                uiState is LibraryUiState.Error
        if (isMeaningfulState) {
            hasReportedMeaningfulContent = true
            onFirstMeaningfulContentDrawn()
        }
    }

    // Permission launcher for scanning
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                viewModel.startLibraryScan()
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar(storagePermissionText)
                }
            }
        }

    val coverPickerLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .PickVisualMedia(),
        ) { uri ->
            val selectedBookId = selectedBook?.id ?: return@rememberLauncherForActivityResult
            if (uri == null) return@rememberLauncherForActivityResult

            scope.launch {
                val result = viewModel.importBookCoverFromPicker(selectedBookId, uri)
                val message =
                    if (result.isSuccess) {
                        coverUpdatedMessage
                    } else {
                        result.exceptionOrNull()?.localizedMessage?.takeIf { it.isNotBlank() }
                            ?: coverUpdateFailedMessage
                    }
                snackbarHostState.showSnackbar(message)
            }
        }

    // One-shot UI side effects (scan result snackbars) — each consumed once.
    LaunchedEffect(Unit) {
        viewModel.sideEffects.collect { effect ->
            if (effect is SideEffect.ShowSnackbar) {
                snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    // Get context for permission check in pull-to-refresh
    val context = LocalContext.current

    // Compute WindowSizeClass once at screen level
    val wsc = LocalWindowSizeClass.current
    val windowSizeClass =
        wsc?.let {
            AdaptiveUtils
                .resolveWindowSizeClassOrNull(it, context)
        } ?: wsc
    val isCompact =
        windowSizeClass?.widthSizeClass == WindowWidthSizeClass.Compact

    // 🎯 Navigator for ListDetailPaneScaffold
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()

    // The selected book id is the navigator's current detail contentKey — the
    // navigator is saveable, so this survives configuration change (no mirror state).
    val selectedBookId: String? = navigator.currentDestination?.contentKey

    // Predictive back: animated detail-pane closure (adaptive-navigation 1.3.0)
    ThreePaneScaffoldPredictiveBackHandler(
        navigator,
        BackNavigationBehavior.PopUntilContentChange,
    )

    // Clear detail selection when collapsing to compact — the detail pane is
    // hidden but navigator state persists, causing stale detail on re-expand.
    LaunchedEffect(isCompact) {
        if (isCompact && navigator.canNavigateBack()) {
            navigator.navigateBack()
        }
    }

    // Premium Background Gradient
    val gradientBg = MaterialTheme.colorScheme.background
    val gradientSurface = MaterialTheme.colorScheme.surface
    val backgroundGradient =
        remember(gradientBg, gradientSurface) {
            Brush.verticalGradient(
                colors = listOf(gradientBg, gradientSurface),
            )
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(backgroundGradient)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (keyEvent.isCtrlPressed && keyEvent.key == Key.F) {
                        searchBarExpanded = true
                        true
                    } else {
                        false
                    }
                },
    ) {
        if (isCompact) {
            // Direct Scaffold on compact screens — skip ListDetailPaneScaffold to avoid double insets.
            // NavigationSuiteScaffold does not consume status-bar insets on this branch; the TopAppBar
            // applies statusBars insets itself (windowInsets below), so they are zeroed here to
            // prevent double inset padding.
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.libraryTitle),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onMenuClick) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = stringResource(R.string.app_name),
                                )
                            }
                        },
                        windowInsets = WindowInsets.statusBars,
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                scrolledContainerColor = Color.Transparent,
                            ),
                        actions = {
                            IconButton(onClick = safeNavigateToSearch) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(R.string.search),
                                )
                            }
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = stringResource(R.string.overflowMenu),
                                    )
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false },
                                ) {
                                    val currentSortLabel =
                                        when (sortOrder) {
                                            BookSortOrder.BY_ACTIVITY ->
                                                stringResource(
                                                    R.string.sort_by_activity,
                                                )
                                            BookSortOrder.TITLE_ASC ->
                                                stringResource(
                                                    R.string.sort_title_asc,
                                                )
                                            BookSortOrder.TITLE_DESC ->
                                                stringResource(
                                                    R.string.sort_title_desc,
                                                )
                                            BookSortOrder.AUTHOR_ASC ->
                                                stringResource(
                                                    R.string.sort_author_asc,
                                                )
                                            BookSortOrder.AUTHOR_DESC ->
                                                stringResource(
                                                    R.string.sort_author_desc,
                                                )
                                            BookSortOrder.RECENTLY_ADDED ->
                                                stringResource(
                                                    R.string.sort_recently_added,
                                                )
                                            BookSortOrder.OLDEST_FIRST ->
                                                stringResource(
                                                    R.string.sort_oldest_first,
                                                )
                                        }
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(text = stringResource(R.string.sort_by))
                                                Text(
                                                    text = currentSortLabel,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        onClick = {
                                            showOverflowMenu = false
                                            showSortBottomSheet = true
                                        },
                                        leadingIcon = {
                                            Icon(imageVector = Icons.AutoMirrored.Filled.Sort, contentDescription = null)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(text = stringResource(R.string.viewModeList)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.onViewModeChanged(LibraryViewMode.LIST_COMPACT)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.List,
                                                contentDescription = null,
                                            )
                                        },
                                        trailingIcon = {
                                            if (!viewMode.isGrid()) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(text = stringResource(R.string.viewModeGrid)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            viewModel.onViewModeChanged(LibraryViewMode.GRID_COMPACT)
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Filled.GridView,
                                                contentDescription = null,
                                            )
                                        },
                                        trailingIcon = {
                                            if (viewMode.isGrid()) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                )
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(text = stringResource(R.string.discovery)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            showDiscovery = !showDiscovery
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Whatshot,
                                                contentDescription = null,
                                                tint =
                                                    if (showDiscovery) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                            )
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(text = stringResource(R.string.account)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            safeNavigateToAuth()
                                        },
                                        leadingIcon = {
                                            Icon(imageVector = Icons.Filled.Person, contentDescription = null)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(text = stringResource(R.string.downloads)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            safeNavigateToDownloads()
                                        },
                                        leadingIcon = {
                                            Icon(imageVector = Icons.Filled.Download, contentDescription = null)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(text = stringResource(R.string.settings)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            safeNavigateToSettings()
                                        },
                                        leadingIcon = {
                                            Icon(imageVector = Icons.Default.Settings, contentDescription = null)
                                        },
                                    )
                                }
                            }
                        },
                    )
                },
                modifier = Modifier.fillMaxSize(),
            ) { padding ->
                val isRefreshing = scanState is ScanState.Scanning
                val pullToRefreshState = rememberPullToRefreshState()
                Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                    PullToRefreshBox(
                        state = pullToRefreshState,
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            if (isRefreshing) {
                                return@PullToRefreshBox
                            }
                            val permission =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    Manifest.permission.READ_MEDIA_AUDIO
                                } else {
                                    Manifest.permission.READ_EXTERNAL_STORAGE
                                }
                            // Check permission and start scan using pre-obtained context
                            val hasPermission =
                                ContextCompat.checkSelfPermission(
                                    context,
                                    permission,
                                ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                viewModel.startLibraryScan()
                            } else {
                                permissionLauncher.launch(permission)
                            }
                        },
                        indicator = {
                            PullToRefreshDefaults.Indicator(
                                state = pullToRefreshState,
                                isRefreshing = isRefreshing,
                                modifier = Modifier.align(Alignment.TopCenter),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        when (uiState) {
                            is LibraryUiState.Loading -> {
                                LibraryLoadingSkeleton(message = stringResource(R.string.loadingLibrary))
                            }

                            is LibraryUiState.Success -> {
                                val books = (uiState as LibraryUiState.Success).books
                                val filteredBooks =
                                    remember(books, activeQuickFilter, searchQuery) {
                                        books
                                            .filterBy(activeQuickFilter)
                                            .filterByQuery(searchQuery)
                                    }
                                val discoveryUiState =
                                    remember(books, listeningMood, context) {
                                        buildDiscoveryUiState(books, listeningMood, context)
                                    }
                                val actionsProvider =
                                    remember(viewModel, onBookClick, books, favoriteBooks) {
                                        viewModel.createBookActionsProvider(
                                            onBookClick = onBookClick,
                                            onBookLongPress = { bookId ->
                                                selectedBookForActions = books.firstOrNull { it.id == bookId }
                                            },
                                        )
                                    }

                                Column(modifier = Modifier.fillMaxSize()) {
                                    if (showDiscovery) {
                                        DiscoveryScreen(
                                            uiState = discoveryUiState,
                                            selectedMood = listeningMood,
                                            onMoodChange = { listeningMood = it },
                                            onBookClick = { onBookClick(it.id) },
                                            onGenreClick = { genre ->
                                                searchQuery = genre.title
                                                showDiscovery = false
                                            },
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                        )
                                    } else {
                                        SearchBar(
                                            inputField = {
                                                SearchBarDefaults.InputField(
                                                    query = searchQuery,
                                                    onQueryChange = { searchQuery = it },
                                                    onSearch = { searchBarExpanded = false },
                                                    expanded = searchBarExpanded,
                                                    onExpandedChange = { searchBarExpanded = it },
                                                    placeholder = { Text(text = stringResource(R.string.searchBooks)) },
                                                    leadingIcon = {
                                                        Icon(
                                                            imageVector = Icons.Filled.Search,
                                                            contentDescription = null,
                                                        )
                                                    },
                                                    trailingIcon = {
                                                        if (searchQuery.isNotEmpty()) {
                                                            IconButton(onClick = { searchQuery = "" }) {
                                                                Icon(
                                                                    imageVector = Icons.Filled.Clear,
                                                                    contentDescription = stringResource(R.string.clearSearch),
                                                                )
                                                            }
                                                        }
                                                    },
                                                )
                                            },
                                            expanded = searchBarExpanded,
                                            onExpandedChange = { searchBarExpanded = it },
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp)
                                                    .padding(top = 8.dp, bottom = 8.dp),
                                        ) {}
                                        val bookCounts =
                                            remember(books) {
                                                mapOf(
                                                    LibraryQuickFilter.ALL to books.size,
                                                    LibraryQuickFilter.IN_PROGRESS to
                                                        books.filterBy(LibraryQuickFilter.IN_PROGRESS).size,
                                                    LibraryQuickFilter.COMPLETED to
                                                        books.filterBy(LibraryQuickFilter.COMPLETED).size,
                                                    LibraryQuickFilter.NEW to books.filterBy(LibraryQuickFilter.NEW).size,
                                                    LibraryQuickFilter.FAVORITES to
                                                        books.filterBy(LibraryQuickFilter.FAVORITES).size,
                                                )
                                            }
                                        LibraryQuickFilterChips(
                                            activeFilter = activeQuickFilter,
                                            onFilterChanged = { activeQuickFilter = it },
                                            bookCounts = bookCounts,
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp)
                                                    .padding(top = 4.dp, bottom = 8.dp),
                                        )
                                        if (filteredBooks.isEmpty()) {
                                            EmptyState(
                                                message = stringResource(R.string.noBooksMatchFilter),
                                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                            )
                                        } else {
                                            UnifiedBooksView(
                                                books = filteredBooks,
                                                displayMode = viewMode.toBookDisplayMode(),
                                                actionsProvider = actionsProvider,
                                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                            }

                            is LibraryUiState.Empty -> {
                                EmptyState(
                                    message = stringResource(R.string.noBooksInLibrary),
                                    subtitle = stringResource(R.string.noFoldersConfiguredPleaseAddInSettings),
                                    ctaText = stringResource(R.string.retry),
                                    onCta = { viewModel.startLibraryScan() },
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

                // Book properties dialog
                selectedBook?.let { book ->
                    BookPropertiesDialog(
                        book = book,
                        onPickCover = {
                            coverPickerLauncher.launch(
                                PickVisualMediaRequest(
                                    mediaType =
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                        onDismiss = viewModel::hideBookProperties,
                    )
                }
            }
        } else {
            // 🎯 ListDetailPaneScaffold - Material 3 Adaptive component
            ListDetailPaneScaffold(
                directive = navigator.scaffoldDirective,
                value = navigator.scaffoldValue,
                listPane = {
                    AnimatedPane {
                        // List pane content - book library
                        Scaffold(
                            containerColor = Color.Transparent,
                            topBar = {
                                TopAppBar(
                                    title = {
                                        Text(
                                            text = stringResource(R.string.libraryTitle),
                                            style = MaterialTheme.typography.headlineSmall,
                                        )
                                    },
                                    navigationIcon = {
                                        IconButton(onClick = onMenuClick) {
                                            Icon(
                                                imageVector = Icons.Default.Menu,
                                                contentDescription = stringResource(R.string.app_name),
                                            )
                                        }
                                    },
                                    colors =
                                        TopAppBarDefaults.topAppBarColors(
                                            containerColor = Color.Transparent,
                                            scrolledContainerColor = Color.Transparent,
                                        ),
                                    actions = {
                                        IconButton(onClick = safeNavigateToSearch) {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = stringResource(R.string.search),
                                            )
                                        }
                                        Box {
                                            IconButton(onClick = { showOverflowMenu = true }) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = stringResource(R.string.overflowMenu),
                                                )
                                            }
                                            DropdownMenu(
                                                expanded = showOverflowMenu,
                                                onDismissRequest = { showOverflowMenu = false },
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(text = stringResource(R.string.sort_by)) },
                                                    onClick = {
                                                        showOverflowMenu = false
                                                        showSortBottomSheet = true
                                                    },
                                                    leadingIcon = {
                                                        Icon(imageVector = Icons.AutoMirrored.Filled.Sort, contentDescription = null)
                                                    },
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(text = stringResource(R.string.viewModeList)) },
                                                    onClick = {
                                                        showOverflowMenu = false
                                                        viewModel.onViewModeChanged(LibraryViewMode.LIST_COMPACT)
                                                    },
                                                    leadingIcon = {
                                                        Icon(imageVector = Icons.AutoMirrored.Filled.List, contentDescription = null)
                                                    },
                                                    trailingIcon = {
                                                        if (!viewMode.isGrid()) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                            )
                                                        }
                                                    },
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(text = stringResource(R.string.viewModeGrid)) },
                                                    onClick = {
                                                        showOverflowMenu = false
                                                        viewModel.onViewModeChanged(LibraryViewMode.GRID_COMPACT)
                                                    },
                                                    leadingIcon = {
                                                        Icon(imageVector = Icons.Filled.GridView, contentDescription = null)
                                                    },
                                                    trailingIcon = {
                                                        if (viewMode.isGrid()) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                            )
                                                        }
                                                    },
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(text = stringResource(R.string.account)) },
                                                    onClick = {
                                                        showOverflowMenu = false
                                                        safeNavigateToAuth()
                                                    },
                                                    leadingIcon = {
                                                        Icon(imageVector = Icons.Filled.Person, contentDescription = null)
                                                    },
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(text = stringResource(R.string.downloads)) },
                                                    onClick = {
                                                        showOverflowMenu = false
                                                        safeNavigateToDownloads()
                                                    },
                                                    leadingIcon = {
                                                        Icon(imageVector = Icons.Default.Download, contentDescription = null)
                                                    },
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(text = stringResource(R.string.settingsTitle)) },
                                                    onClick = {
                                                        showOverflowMenu = false
                                                        safeNavigateToSettings()
                                                    },
                                                    leadingIcon = {
                                                        Icon(imageVector = Icons.Default.Settings, contentDescription = null)
                                                    },
                                                )
                                            }
                                        }
                                    },
                                )
                            },
                        ) { padding ->
                            val isRefreshing = scanState is ScanState.Scanning
                            val pullToRefreshState = rememberPullToRefreshState()
                            PullToRefreshBox(
                                state = pullToRefreshState,
                                isRefreshing = isRefreshing,
                                onRefresh = {
                                    if (isRefreshing) {
                                        return@PullToRefreshBox
                                    }
                                    val permission =
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            Manifest.permission.READ_MEDIA_AUDIO
                                        } else {
                                            Manifest.permission.READ_EXTERNAL_STORAGE
                                        }
                                    val hasPermission =
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            permission,
                                        ) == PackageManager.PERMISSION_GRANTED

                                    if (hasPermission) {
                                        viewModel.startLibraryScan()
                                    } else {
                                        permissionLauncher.launch(permission)
                                    }
                                },
                                indicator = {
                                    PullToRefreshDefaults.Indicator(
                                        state = pullToRefreshState,
                                        isRefreshing = isRefreshing,
                                        modifier = Modifier.align(Alignment.TopCenter),
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                modifier = Modifier.padding(padding).fillMaxSize(),
                            ) {
                                when (uiState) {
                                    is LibraryUiState.Loading -> {
                                        LibraryLoadingSkeleton(message = stringResource(R.string.loadingLibrary))
                                    }

                                    is LibraryUiState.Success -> {
                                        val books = (uiState as LibraryUiState.Success).books
                                        val filteredBooks =
                                            remember(books, activeQuickFilter, searchQuery) {
                                                books
                                                    .filterBy(activeQuickFilter)
                                                    .filterByQuery(searchQuery)
                                            }
                                        val discoveryUiState =
                                            remember(books, listeningMood, context) {
                                                buildDiscoveryUiState(books, listeningMood, context)
                                            }
                                        val actionsProvider =
                                            remember(viewModel, onBookClick, books, favoriteBooks) {
                                                viewModel.createBookActionsProvider(
                                                    onBookClick = { bookId ->
                                                        scope.launch {
                                                            navigator.navigateTo(
                                                                ListDetailPaneScaffoldRole.Detail,
                                                                bookId,
                                                            )
                                                        }
                                                    },
                                                    onBookLongPress = { bookId ->
                                                        selectedBookForActions = books.firstOrNull { it.id == bookId }
                                                    },
                                                )
                                            }

                                        Column(modifier = Modifier.fillMaxSize()) {
                                            if (showDiscovery) {
                                                DiscoveryScreen(
                                                    uiState = discoveryUiState,
                                                    selectedMood = listeningMood,
                                                    onMoodChange = { listeningMood = it },
                                                    onBookClick = { onBookClick(it.id) },
                                                    onGenreClick = { genre ->
                                                        searchQuery = genre.title
                                                        showDiscovery = false
                                                    },
                                                    modifier = Modifier.weight(1f).fillMaxWidth(),
                                                )
                                            } else {
                                                SearchBar(
                                                    inputField = {
                                                        SearchBarDefaults.InputField(
                                                            query = searchQuery,
                                                            onQueryChange = { searchQuery = it },
                                                            onSearch = { searchBarExpanded = false },
                                                            expanded = searchBarExpanded,
                                                            onExpandedChange = { searchBarExpanded = it },
                                                            placeholder = { Text(text = stringResource(R.string.searchBooks)) },
                                                            leadingIcon = {
                                                                Icon(
                                                                    imageVector = Icons.Filled.Search,
                                                                    contentDescription = null,
                                                                )
                                                            },
                                                            trailingIcon = {
                                                                if (searchQuery.isNotEmpty()) {
                                                                    IconButton(onClick = { searchQuery = "" }) {
                                                                        Icon(
                                                                            imageVector = Icons.Filled.Clear,
                                                                            contentDescription = stringResource(R.string.clearSearch),
                                                                        )
                                                                    }
                                                                }
                                                            },
                                                        )
                                                    },
                                                    expanded = searchBarExpanded,
                                                    onExpandedChange = { searchBarExpanded = it },
                                                    modifier =
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 16.dp)
                                                            .padding(top = 8.dp, bottom = 8.dp),
                                                ) {}
                                                val bookCounts =
                                                    remember(books) {
                                                        mapOf(
                                                            LibraryQuickFilter.ALL to books.size,
                                                            LibraryQuickFilter.IN_PROGRESS to
                                                                books.filterBy(LibraryQuickFilter.IN_PROGRESS).size,
                                                            LibraryQuickFilter.COMPLETED to
                                                                books.filterBy(LibraryQuickFilter.COMPLETED).size,
                                                            LibraryQuickFilter.NEW to books.filterBy(LibraryQuickFilter.NEW).size,
                                                            LibraryQuickFilter.FAVORITES to
                                                                books.filterBy(LibraryQuickFilter.FAVORITES).size,
                                                        )
                                                    }
                                                LibraryQuickFilterChips(
                                                    activeFilter = activeQuickFilter,
                                                    onFilterChanged = { activeQuickFilter = it },
                                                    bookCounts = bookCounts,
                                                    modifier =
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 16.dp)
                                                            .padding(top = 4.dp, bottom = 8.dp),
                                                )
                                                if (filteredBooks.isEmpty()) {
                                                    EmptyState(
                                                        message = stringResource(R.string.noBooksMatchFilter),
                                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                                    )
                                                } else {
                                                    UnifiedBooksView(
                                                        books = filteredBooks,
                                                        displayMode = viewMode.toBookDisplayMode(),
                                                        actionsProvider = actionsProvider,
                                                        modifier = Modifier.weight(1f).fillMaxWidth(),
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    is LibraryUiState.Empty -> {
                                        EmptyState(
                                            message = stringResource(R.string.noBooksInLibrary),
                                            subtitle = stringResource(R.string.noFoldersConfiguredPleaseAddInSettings),
                                            ctaText = stringResource(R.string.retry),
                                            onCta = { viewModel.startLibraryScan() },
                                        )
                                    }

                                    is LibraryUiState.Error -> {
                                        ErrorScreen(
                                            message = (uiState as LibraryUiState.Error).message,
                                        )
                                    }
                                }
                            }

                            selectedBook?.let { book ->
                                BookPropertiesDialog(
                                    book = book,
                                    onPickCover = {
                                        coverPickerLauncher.launch(
                                            PickVisualMediaRequest(
                                                mediaType =
                                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                            ),
                                        )
                                    },
                                    onDismiss = viewModel::hideBookProperties,
                                )
                            }
                        }
                    }
                },
                detailPane = {
                    AnimatedPane {
                        if (selectedBookId != null && uiState is LibraryUiState.Success) {
                            val books = (uiState as LibraryUiState.Success).books
                            val selectedBook = books.find { it.id == selectedBookId }
                            val selectedBookChapters by
                                remember(selectedBookId) {
                                    viewModel.observeBookChapters(selectedBookId)
                                }.collectAsStateWithLifecycle(initialValue = emptyList())

                            BookDetailPane(
                                book = selectedBook,
                                chapters = selectedBookChapters,
                                onPlayClick = {
                                    onBookClick(selectedBookId)
                                },
                                onClose = {
                                    scope.launch {
                                        navigator.navigateBack()
                                    }
                                },
                                onToggleFavorite = {
                                    selectedBook?.let { book ->
                                        viewModel.toggleFavorite(book.id, !book.isFavorite)
                                    }
                                },
                                onNavigateToAudioSettings = {
                                    onNavigateToAudioSettings()
                                },
                            )
                        }
                    }
                },
            )
        }

        // Adaptive Snackbar (bottom, compact, themed)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            snackbar = { snackbarData ->
                Snackbar(
                    snackbarData = snackbarData,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape =
                        RoundedCornerShape(12.dp),
                    // Compact on tablets
                    modifier = Modifier.widthIn(max = 600.dp),
                )
            },
        )

        selectedBookForActions?.let { book ->
            val contextMenuActionsProvider =
                BookActionsProvider(
                    onBookClick = onBookClick,
                    onBookLongPress = {},
                    onToggleFavorite = viewModel::toggleFavorite,
                    favoriteIds =
                        (uiState as? LibraryUiState.Success)
                            ?.books
                            ?.filter { it.isFavorite }
                            ?.map { it.id }
                            ?.toSet()
                            .orEmpty(),
                    onShareBook = {
                        val shareIntent =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${book.title} — ${book.author}")
                            }
                        context.startActivity(
                            Intent.createChooser(
                                shareIntent,
                                context.getString(R.string.share),
                            ),
                        )
                    },
                    onDeleteBook = viewModel::deleteBook,
                    onShowBookInfo = { viewModel.showBookProperties(it) },
                )
            BookActionsBottomSheet(
                book = book,
                actionsProvider = contextMenuActionsProvider,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                onDismiss = { selectedBookForActions = null },
            )
        }

        if (uiState is LibraryUiState.Success && !spotlightCompleted && spotlightStep in 1..2) {
            val overlayCenterPx =
                with(androidx.compose.ui.platform.LocalDensity.current) {
                    if (spotlightStep == 1) {
                        Offset(x = 72.dp.toPx(), y = 180.dp.toPx())
                    } else {
                        Offset(x = 128.dp.toPx(), y = 180.dp.toPx())
                    }
                }
            SpotlightOverlay(
                title = if (spotlightStep == 1) spotlightSearchTitle else spotlightDownloadsTitle,
                description = if (spotlightStep == 1) spotlightSearchDescription else spotlightDownloadsDescription,
                skipText = spotlightSkipText,
                nextText = spotlightNextText,
                targetCenter = overlayCenterPx,
                targetRadius = 30.dp,
                onSkip = {
                    spotlightStep = 0
                    viewModel.completeSpotlight()
                },
                onNext = {
                    if (spotlightStep == 1) {
                        spotlightStep = 2
                    } else {
                        spotlightStep = 0
                        viewModel.completeSpotlight()
                    }
                },
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

    if (showSortBottomSheet) {
        SortOrderBottomSheet(
            currentSortOrder = sortOrder,
            onSortOrderChanged = { order ->
                viewModel.onSortOrderChanged(order)
                showSortBottomSheet = false
            },
            onDismiss = { showSortBottomSheet = false },
        )
    }

    LaunchedEffect(uiState) {
        if (uiState is LibraryUiState.Success && spotlightStep == 0 && !spotlightCompleted) {
            spotlightStep = 1
        }
    }
}

private fun buildDiscoveryUiState(
    books: List<Book>,
    mood: ListeningMood,
    context: android.content.Context,
): DiscoveryUiState {
    val continueListening = books.filter { !it.isCompleted && (it.isStarted || it.progress > 0f) }.take(12)
    val trending = books.sortedByDescending { it.addedDate }.take(12)
    val personalized =
        books
            .filter { isMoodMatch(it, mood) }
            .sortedByDescending { if (it.isFavorite) 1 else 0 }
            .ifEmpty { books }
            .take(12)
    val genresByTitle = books.groupBy { inferGenreFromBook(it, context) }
    val colorPalette = GenreAccentColors
    val genres =
        genresByTitle.entries
            .sortedByDescending { it.value.size }
            .take(8)
            .mapIndexed { index, entry ->
                DiscoveryGenre(
                    id = "genre-${entry.key}",
                    title = entry.key,
                    color = colorPalette[index % colorPalette.size],
                    coverHints = entry.value.map { it.title.take(1).ifBlank { "?" } }.take(2),
                )
            }
    return DiscoveryUiState(
        continueListening = continueListening,
        trending = trending,
        personalized = personalized,
        genres = genres,
    )
}

private fun isMoodMatch(
    book: Book,
    mood: ListeningMood,
): Boolean {
    val source = listOf(book.title, book.author, book.description.orEmpty()).joinToString(" ").lowercase()
    return when (mood) {
        ListeningMood.WALKING -> "подкаст" in source || "short" in source || "рассказ" in source
        ListeningMood.DRIVING -> "детектив" in source || "триллер" in source || "боевик" in source
        ListeningMood.SLEEPING -> "медитац" in source || "сказк" in source || "класс" in source
        ListeningMood.WORKOUT -> "мотива" in source || "биограф" in source || "action" in source
        ListeningMood.RELAXING -> "роман" in source || "повесть" in source || "драма" in source
        ListeningMood.WORKING -> "бизнес" in source || "история" in source || "science" in source
    }
}

private fun inferGenreFromBook(
    book: Book,
    context: android.content.Context,
): String {
    val source = listOf(book.title, book.author, book.description.orEmpty(), book.sourceUrl.orEmpty()).joinToString(" ").lowercase()
    return when {
        "фантаст" in source || "sci-fi" in source || "fantasy" in source -> context.getString(R.string.genreSciFi)
        "детектив" in source || "detective" in source -> context.getString(R.string.genreDetective)
        "истор" in source || "history" in source -> context.getString(R.string.genreHistory)
        "бизнес" in source || "business" in source -> context.getString(R.string.genreBusiness)
        "психолог" in source || "self" in source -> context.getString(R.string.genreSelfDevelopment)
        "класс" in source || "classic" in source -> context.getString(R.string.genreClassics)
        else -> context.getString(R.string.genreMisc)
    }
}

/**
 * Helper extension to check if view mode is a grid variant.
 */
private fun LibraryViewMode.isGrid(): Boolean = this == LibraryViewMode.GRID_COMPACT || this == LibraryViewMode.GRID_COMFORTABLE

/**
 * Sort order bottom sheet.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SortOrderBottomSheet(
    currentSortOrder: BookSortOrder,
    onSortOrderChanged: (BookSortOrder) -> Unit,
    onDismiss: () -> Unit,
) {
    JabookModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.sort_by),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        BookSortOrder.entries.forEach { order ->
            ListItem(
                headlineContent = {
                    Text(
                        text =
                            when (order) {
                                BookSortOrder.BY_ACTIVITY ->
                                    stringResource(R.string.sort_by_activity)
                                BookSortOrder.TITLE_ASC ->
                                    stringResource(R.string.sort_title_asc)
                                BookSortOrder.TITLE_DESC ->
                                    stringResource(R.string.sort_title_desc)
                                BookSortOrder.AUTHOR_ASC ->
                                    stringResource(R.string.sort_author_asc)
                                BookSortOrder.AUTHOR_DESC ->
                                    stringResource(R.string.sort_author_desc)
                                BookSortOrder.RECENTLY_ADDED ->
                                    stringResource(R.string.sort_recently_added)
                                BookSortOrder.OLDEST_FIRST ->
                                    stringResource(R.string.sort_oldest_first)
                            },
                    )
                },
                leadingContent = {
                    if (order == currentSortOrder) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null)
                    } else {
                        Spacer(modifier = Modifier.size(24.dp))
                    }
                },
                modifier = Modifier.combinedClickable(onClick = { onSortOrderChanged(order) }),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

private enum class LibraryQuickFilter {
    ALL,
    IN_PROGRESS,
    COMPLETED,
    NEW,
    FAVORITES,
    DOWNLOADED,
}

private fun List<Book>.filterBy(filter: LibraryQuickFilter): List<Book> =
    when (filter) {
        LibraryQuickFilter.ALL -> this
        LibraryQuickFilter.IN_PROGRESS -> filter { it.progress > 0f && !it.isCompleted }
        LibraryQuickFilter.COMPLETED -> filter { it.isCompleted }
        LibraryQuickFilter.NEW -> filter { it.progress == 0f }
        LibraryQuickFilter.FAVORITES -> filter { it.isFavorite }
        LibraryQuickFilter.DOWNLOADED -> filter { it.isDownloaded }
    }

private fun List<Book>.filterByQuery(query: String): List<Book> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) return this
    return filter { book ->
        book.title.contains(normalizedQuery, ignoreCase = true) ||
            book.author.contains(normalizedQuery, ignoreCase = true)
    }
}

@Composable
private fun LibraryQuickFilterChips(
    activeFilter: LibraryQuickFilter,
    onFilterChanged: (LibraryQuickFilter) -> Unit,
    bookCounts: Map<LibraryQuickFilter, Int>,
    modifier: Modifier = Modifier,
) {
    val currentCount = bookCounts[activeFilter] ?: 0
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ChipRow {
            LibraryFilterChip(
                selected = activeFilter == LibraryQuickFilter.ALL,
                onClick = { onFilterChanged(LibraryQuickFilter.ALL) },
                label = stringResource(R.string.allFilter),
            )
            LibraryFilterChip(
                selected = activeFilter == LibraryQuickFilter.IN_PROGRESS,
                onClick = { onFilterChanged(LibraryQuickFilter.IN_PROGRESS) },
                label = stringResource(R.string.inProgress),
            )
            LibraryFilterChip(
                selected = activeFilter == LibraryQuickFilter.COMPLETED,
                onClick = { onFilterChanged(LibraryQuickFilter.COMPLETED) },
                label = stringResource(R.string.completed),
            )
            LibraryFilterChip(
                selected = activeFilter == LibraryQuickFilter.NEW,
                onClick = { onFilterChanged(LibraryQuickFilter.NEW) },
                label = stringResource(R.string.newFilter),
            )
            LibraryFilterChip(
                selected = activeFilter == LibraryQuickFilter.FAVORITES,
                onClick = { onFilterChanged(LibraryQuickFilter.FAVORITES) },
                label = stringResource(R.string.favoritesTooltip),
            )
        }
        Text(
            text = pluralStringResource(R.plurals.booksCount, currentCount, currentCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/**
 * Converts LibraryViewMode to BookDisplayMode.
 * Temporary helper during migration period.
 */
private fun LibraryViewMode.toBookDisplayMode(): BookDisplayMode =
    when (this) {
        LibraryViewMode.LIST_COMPACT -> BookDisplayMode.LIST_COMPACT
        LibraryViewMode.GRID_COMPACT -> BookDisplayMode.GRID_COMPACT
        LibraryViewMode.GRID_COMFORTABLE -> BookDisplayMode.GRID_COMFORTABLE
    }
