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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.navigation.NavigationClickGuard
import com.jabook.app.jabook.compose.core.theme.SurfaceElevationTokens
import com.jabook.app.jabook.compose.data.model.LibraryViewMode
import com.jabook.app.jabook.compose.designsystem.component.BookActionsBottomSheet
import com.jabook.app.jabook.compose.designsystem.component.EmptyState
import com.jabook.app.jabook.compose.designsystem.component.ErrorScreen
import com.jabook.app.jabook.compose.designsystem.component.JabookModalBottomSheet
import com.jabook.app.jabook.compose.designsystem.component.LibraryLoadingSkeleton
import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.BookActionsProvider
import com.jabook.app.jabook.compose.domain.model.BookDisplayMode
import com.jabook.app.jabook.compose.feature.achievements.AchievementOverlay
import com.jabook.app.jabook.compose.feature.achievements.AchievementUiModel
import com.jabook.app.jabook.compose.feature.discovery.DiscoveryGenre
import com.jabook.app.jabook.compose.feature.discovery.DiscoveryScreen
import com.jabook.app.jabook.compose.feature.discovery.DiscoveryUiState
import com.jabook.app.jabook.compose.feature.discovery.ListeningMood
import com.jabook.app.jabook.compose.feature.onboarding.SpotlightOverlay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Library screen - displays the user's audiobook collection.
 *
 * This is the main entry point for the library feature.
 * It handles the different UI states and delegates to specific composables.
 * Uses Material 3 Adaptive ListDetailPaneScaffold for proper list-detail pattern on larger screens.
 *
 * @param onBookClick Callback when a book is clicked
 * @param viewModel ViewModel provided by Hilt
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
public fun LibraryScreen(
    onBookClick: (String) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToFavorites: () -> Unit = {},
    onFirstMeaningfulContentDrawn: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val selectedBook by viewModel.selectedBookForProperties.collectAsStateWithLifecycle()
    val weeklyRecap by viewModel.weeklyRecapState.collectAsStateWithLifecycle()
    val yearRecap by viewModel.yearRecapState.collectAsStateWithLifecycle()
    val snackbarHostState = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val navigationClickGuard = remember { NavigationClickGuard() }
    val safeNavigateToFavorites = dropUnlessResumed { navigationClickGuard.run(onNavigateToFavorites) }
    val safeNavigateToSearch = dropUnlessResumed { navigationClickGuard.run(onNavigateToSearch) }
    val safeNavigateToDownloads = dropUnlessResumed { navigationClickGuard.run(onNavigateToDownloads) }
    var activeQuickFilter by remember { mutableStateOf(LibraryQuickFilter.ALL) }
    var showSortBottomSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchBarExpanded by remember { mutableStateOf(false) }
    var spotlightStep by rememberSaveable { mutableStateOf(0) }
    var selectedBookForActions by remember { mutableStateOf<Book?>(null) }
    var showDiscovery by rememberSaveable { mutableStateOf(false) }
    var listeningMood by rememberSaveable { mutableStateOf(ListeningMood.RELAXING) }
    var activeAchievement by remember { mutableStateOf<AchievementUiModel?>(null) }
    var hasShownFirstBookAchievement by rememberSaveable { mutableStateOf(false) }
    var hasShownStreakAchievement by rememberSaveable { mutableStateOf(false) }

    val storagePermissionText = stringResource(R.string.storagePermissionRequired)
    val foundBooksMessageTemplate = stringResource(R.string.foundBooksMessage)
    val scanFailedMessageTemplate = stringResource(R.string.scanFailedMessage)
    val noFoldersConfiguredMessage = stringResource(R.string.noFoldersConfiguredPleaseAddInSettings)
    val scanCompleteNoBooksMessage = stringResource(R.string.scanCompleteNoBooks)
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
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract =
                androidx.activity.result.contract.ActivityResultContracts
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
        androidx.activity.compose.rememberLauncherForActivityResult(
            contract =
                androidx.activity.result.contract.ActivityResultContracts
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

    // Observe scan state changes with enhanced feedback
    androidx.compose.runtime.LaunchedEffect(scanState) {
        when (val state = scanState) {
            is ScanState.Completed -> {
                val message =
                    when {
                        state.booksFound == 0 && state.noFoldersConfigured -> {
                            noFoldersConfiguredMessage
                        }
                        state.booksFound == 0 -> {
                            scanCompleteNoBooksMessage
                        }
                        else -> {
                            foundBooksMessageTemplate.format(state.booksFound)
                        }
                    }
                snackbarHostState.showSnackbar(message)
            }
            is ScanState.Failed -> {
                snackbarHostState.showSnackbar(scanFailedMessageTemplate.format(state.error))
            }
            else -> {}
        }
    }

    // Get context for permission check in pull-to-refresh
    val context = LocalContext.current

    // 🎯 Navigator for ListDetailPaneScaffold
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()

    // Track selected book ID locally since navigator's currentDestination is internal
    var selectedBookId by remember { mutableStateOf<String?>(null) }

    // Handle back navigation from detail pane
    BackHandler(navigator.canNavigateBack()) {
        scope.launch {
            navigator.navigateBack()
            selectedBookId = null
        }
    }

    // Premium Background Gradient
    val backgroundGradient =
        androidx.compose.ui.graphics.Brush.verticalGradient(
            colors =
                listOf(
                    androidx.compose.material3.MaterialTheme.colorScheme.background,
                    androidx.compose.material3.MaterialTheme.colorScheme.surface,
                ),
        )

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
        // 🎯 ListDetailPaneScaffold - Material 3 Adaptive component
        ListDetailPaneScaffold(
            directive = navigator.scaffoldDirective,
            value = navigator.scaffoldValue,
            listPane = {
                AnimatedPane {
                    // List pane content - book library
                    Scaffold(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent, // Transparent to show gradient
                        topBar = {
                            TopAppBar(
                                title = { },
                                colors =
                                    androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                                        scrolledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    ),
                                actions = {
                                    IconButton(onClick = { showSortBottomSheet = true }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Sort,
                                            contentDescription = stringResource(R.string.sort_by),
                                        )
                                    }

                                    // View mode toggle
                                    ViewModeToggle(
                                        currentMode = viewMode,
                                        onModeChanged = { mode -> viewModel.onViewModeChanged(mode) },
                                    )
                                    IconButton(onClick = { showDiscovery = !showDiscovery }) {
                                        Icon(
                                            imageVector = Icons.Default.Whatshot,
                                            contentDescription = "Discovery",
                                            tint =
                                                if (showDiscovery) {
                                                    androidx.compose.material3.MaterialTheme.colorScheme.primary
                                                } else {
                                                    androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                        )
                                    }

                                    // Favorites button
                                    IconButton(onClick = safeNavigateToFavorites) {
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = stringResource(R.string.favoritesTooltip),
                                        )
                                    }
                                    // Search button
                                    IconButton(onClick = safeNavigateToSearch) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = stringResource(R.string.search),
                                        )
                                    }
                                    // Downloads button
                                    IconButton(onClick = safeNavigateToDownloads) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = stringResource(R.string.downloads),
                                        )
                                    }
                                },
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) { padding ->
                        val isRefreshing = scanState is ScanState.Scanning
                        val pullToRefreshState = rememberPullToRefreshState()
                        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                            state = pullToRefreshState,
                            isRefreshing = isRefreshing,
                            onRefresh = {
                                if (isRefreshing) {
                                    return@PullToRefreshBox
                                }
                                val permission =
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        android.Manifest.permission.READ_MEDIA_AUDIO
                                    } else {
                                        android.Manifest.permission.READ_EXTERNAL_STORAGE
                                    }
                                // Check permission and start scan using pre-obtained context
                                val hasPermission =
                                    androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        permission,
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

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
                                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
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
                                        remember(books, listeningMood) {
                                            buildDiscoveryUiState(books, listeningMood)
                                        }
                                    val actionsProvider =
                                        viewModel.createBookActionsProvider(
                                            onBookClick = { bookId ->
                                                // Navigate to detail pane and track selection
                                                selectedBookId = bookId
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

                                    Column(modifier = Modifier.fillMaxSize()) {
                                        weeklyRecap?.let { recap ->
                                            WeeklyRecapCard(
                                                stats = recap,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            )
                                            yearRecap?.let { recapYear ->
                                                YearRecapPromptCard(
                                                    yearRecap = recapYear,
                                                    onShareClick = {
                                                        shareYearRecap(context, recapYear)
                                                    },
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                )
                                            }
                                            ListeningHeatmap(
                                                data = buildListeningHeatmapData(books),
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            )
                                            SpeedDonutChart(
                                                distribution = buildSpeedDistribution(books),
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            )
                                        }
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
                                                modifier = Modifier.fillMaxSize(),
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
                                                    )
                                                },
                                                expanded = searchBarExpanded,
                                                onExpandedChange = { searchBarExpanded = it },
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp)
                                                        .padding(bottom = 8.dp),
                                            ) {}
                                            LibraryQuickFilterChips(
                                                activeFilter = activeQuickFilter,
                                                onFilterChanged = { activeQuickFilter = it },
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp)
                                                        .padding(top = 4.dp, bottom = 8.dp),
                                            )
                                            UnifiedBooksView(
                                                books = filteredBooks,
                                                displayMode = viewMode.toBookDisplayMode(),
                                                actionsProvider = actionsProvider,
                                                modifier = Modifier.fillMaxSize(),
                                            )
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

                        // Book properties dialog
                        selectedBook?.let { book ->
                            BookPropertiesDialog(
                                book = book,
                                onPickCover = {
                                    coverPickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            mediaType =
                                                androidx.activity.result.contract
                                                    .ActivityResultContracts.PickVisualMedia.ImageOnly,
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
                    // Detail pane content - use locally tracked selection
                    if (selectedBookId != null && uiState is LibraryUiState.Success) {
                        val books = (uiState as LibraryUiState.Success).books
                        val selectedBook = books.find { it.id == selectedBookId }
                        val selectedBookChapters by
                            remember(selectedBookId) {
                                selectedBookId?.let { viewModel.observeBookChapters(it) } ?: flowOf(emptyList())
                            }.collectAsStateWithLifecycle(initialValue = emptyList())

                        BookDetailPane(
                            book = selectedBook,
                            chapters = selectedBookChapters,
                            onPlayClick = {
                                // Navigate to player when play is clicked
                                selectedBookId?.let { onBookClick(it) }
                            },
                            onClose = {
                                scope.launch {
                                    navigator.navigateBack()
                                    selectedBookId = null
                                }
                            },
                            onToggleFavorite = {
                                // Toggle favorite for this book
                                selectedBook?.let { book ->
                                    viewModel.toggleFavorite(book.id, !book.isFavorite)
                                }
                            },
                        )
                    }
                }
            },
            modifier = modifier,
        )

        // Adaptive Snackbar (bottom, compact, themed)
        androidx.compose.material3.SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            snackbar = { snackbarData ->
                androidx.compose.material3.Snackbar(
                    snackbarData = snackbarData,
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.inverseSurface,
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.inverseOnSurface,
                    shape =
                        androidx.compose.foundation.shape
                            .RoundedCornerShape(12.dp),
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
                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, "${book.title} — ${book.author}")
                            }
                        context.startActivity(
                            android.content.Intent.createChooser(
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

        if (uiState is LibraryUiState.Success && spotlightStep in 1..2) {
            val overlayCenter =
                if (spotlightStep == 1) {
                    Offset(x = 72f, y = 180f)
                } else {
                    Offset(x = 128f, y = 180f)
                }
            SpotlightOverlay(
                title = if (spotlightStep == 1) spotlightSearchTitle else spotlightDownloadsTitle,
                description = if (spotlightStep == 1) spotlightSearchDescription else spotlightDownloadsDescription,
                skipText = spotlightSkipText,
                nextText = spotlightNextText,
                targetCenter = overlayCenter,
                targetRadius = 30.dp,
                onSkip = { spotlightStep = 0 },
                onNext = {
                    spotlightStep = if (spotlightStep == 1) 2 else 0
                },
                modifier = Modifier.align(Alignment.Center),
            )
        }

        activeAchievement?.let { achievement ->
            AchievementOverlay(
                achievement = achievement,
                onDismiss = { activeAchievement = null },
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
        if (uiState is LibraryUiState.Success && spotlightStep == 0) {
            spotlightStep = 1
        }
    }

    LaunchedEffect(weeklyRecap) {
        val recap = weeklyRecap ?: return@LaunchedEffect
        if (recap.booksCompleted >= 1 && !hasShownFirstBookAchievement) {
            hasShownFirstBookAchievement = true
            activeAchievement =
                AchievementUiModel(
                    id = "first-book",
                    title = "Первая страница",
                    description = "Вы завершили первую книгу за неделю.",
                )
            return@LaunchedEffect
        }
        if (recap.streakDays >= 7 && !hasShownStreakAchievement) {
            hasShownStreakAchievement = true
            activeAchievement =
                AchievementUiModel(
                    id = "week-streak",
                    title = "Неделя слова",
                    description = "Вы слушаете уже 7 дней подряд.",
                )
        }
    }
}

@Composable
private fun YearRecapPromptCard(
    yearRecap: YearRecapState,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = SurfaceElevationTokens.Level1),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.yearRecapTitle, yearRecap.year),
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(R.string.yearRecapShareHint, yearRecap.totalMinutesListened),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            OutlinedButton(onClick = onShareClick) {
                Icon(
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp),
                )
                Text(text = stringResource(R.string.share))
            }
        }
    }
}

private fun buildDiscoveryUiState(
    books: List<Book>,
    mood: ListeningMood,
): DiscoveryUiState {
    val continueListening = books.filter { !it.isCompleted && (it.isStarted || it.progress > 0f) }.take(12)
    val trending = books.sortedByDescending { it.addedDate }.take(12)
    val personalized =
        books
            .filter { isMoodMatch(it, mood) }
            .sortedByDescending { if (it.isFavorite) 1 else 0 }
            .ifEmpty { books }
            .take(12)
    val genresByTitle = books.groupBy { inferGenreFromBook(it) }
    val colorPalette =
        listOf(
            androidx.compose.ui.graphics
                .Color(0xFF0D6EFD),
            androidx.compose.ui.graphics
                .Color(0xFF00A884),
            androidx.compose.ui.graphics
                .Color(0xFFFF7A00),
            androidx.compose.ui.graphics
                .Color(0xFFE91E63),
            androidx.compose.ui.graphics
                .Color(0xFF6F42C1),
            androidx.compose.ui.graphics
                .Color(0xFF0099CC),
        )
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

private fun buildListeningHeatmapData(books: List<Book>): Map<LocalDate, Int> {
    val zoneId = ZoneId.systemDefault()
    return books
        .mapNotNull { book ->
            val lastPlayed = book.lastPlayedDate ?: return@mapNotNull null
            val day = Instant.ofEpochMilli(lastPlayed).atZone(zoneId).toLocalDate()
            day to ((book.progress * 60f).toInt().coerceAtLeast(1))
        }.groupBy({ it.first }, { it.second })
        .mapValues { (_, values) -> values.sum().coerceAtLeast(1) }
}

private fun buildSpeedDistribution(books: List<Book>): Map<Float, Long> {
    if (books.isEmpty()) {
        return emptyMap()
    }
    val base = books.size.toLong().coerceAtLeast(1L)
    val fast = books.count { (it.forwardDuration ?: 0) >= 30 }.toLong()
    val slow = books.count { (it.rewindDuration ?: 0) >= 20 }.toLong()
    val normal = (base - fast - slow).coerceAtLeast(1L)
    return mapOf(
        1.0f to normal * 60_000L,
        1.25f to fast.coerceAtLeast(1L) * 40_000L,
        0.9f to slow.coerceAtLeast(1L) * 35_000L,
    )
}

private fun inferGenreFromBook(book: Book): String {
    val source = listOf(book.title, book.author, book.description.orEmpty(), book.sourceUrl.orEmpty()).joinToString(" ").lowercase()
    return when {
        "фантаст" in source || "sci-fi" in source || "fantasy" in source -> "Фантастика"
        "детектив" in source || "detective" in source -> "Детективы"
        "истор" in source || "history" in source -> "История"
        "бизнес" in source || "business" in source -> "Бизнес"
        "психолог" in source || "self" in source -> "Саморазвитие"
        "класс" in source || "classic" in source -> "Классика"
        else -> "Разное"
    }
}

@Composable
private fun WeeklyRecapCard(
    stats: WeeklyRecapState,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = SurfaceElevationTokens.Level2),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.weeklyRecapTitle),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                WeeklyStatItem(
                    icon = Icons.Filled.Headphones,
                    value = stats.minutesListened.toString(),
                    label = stringResource(R.string.minutesLabel),
                )
                WeeklyStatItem(
                    icon = Icons.Filled.Check,
                    value = stats.booksCompleted.toString(),
                    label = stringResource(R.string.booksLabel),
                )
                WeeklyStatItem(
                    icon = Icons.Filled.Whatshot,
                    value = stats.streakDays.toString(),
                    label = stringResource(R.string.streakDaysLabel),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    stringResource(
                        R.string.productivePeriodLabel,
                        when (stats.productivePeriod) {
                            ProductivePeriod.MORNING -> stringResource(R.string.productiveMorning)
                            ProductivePeriod.DAY -> stringResource(R.string.productiveDay)
                            ProductivePeriod.EVENING -> stringResource(R.string.productiveEvening)
                            ProductivePeriod.NIGHT -> stringResource(R.string.productiveNight)
                            ProductivePeriod.UNKNOWN -> stringResource(R.string.unknown)
                        },
                    ),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun WeeklyStatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = value,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer,
        )
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
                LibraryLoadingSkeleton(message = stringResource(R.string.loadingLibrary))
            }

            is LibraryUiState.Success -> {
                // Using simple provider since this deprecated composable
                // doesn't have access to favorites or other state
                val simpleProvider =
                    BookActionsProvider(
                        onBookClick = onBookClick,
                        onBookLongPress = {},
                        onToggleFavorite = { _, _ -> },
                    )

                UnifiedBooksView(
                    books = uiState.books,
                    displayMode = BookDisplayMode.GRID_COMPACT,
                    actionsProvider = simpleProvider,
                )
            }

            is LibraryUiState.Empty -> {
                EmptyState(
                    message = stringResource(R.string.noBooksInLibrary),
                    subtitle = stringResource(R.string.noFoldersConfiguredPleaseAddInSettings),
                    ctaText = stringResource(R.string.retry),
                    onCta = {},
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

/**
 * View mode toggle buttons for switching between list and grid views.
 */
@Composable
private fun ViewModeToggle(
    currentMode: LibraryViewMode,
    onModeChanged: (LibraryViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Row(modifier = modifier) {
        // List view
        IconButton(
            onClick = { onModeChanged(LibraryViewMode.LIST_COMPACT) },
        ) {
            Icon(
                imageVector =
                    if (currentMode == LibraryViewMode.LIST_COMPACT) {
                        Icons.AutoMirrored.Filled.List
                    } else {
                        Icons.AutoMirrored.Outlined.List
                    },
                contentDescription = stringResource(R.string.viewModeList),
                tint =
                    if (currentMode == LibraryViewMode.LIST_COMPACT) {
                        androidx.compose.material3.MaterialTheme.colorScheme.primary
                    } else {
                        androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }

        // Grid view - toggle between compact and comfortable
        IconButton(
            onClick = {
                onModeChanged(
                    if (currentMode.isGrid()) {
                        // Cycle between GRID_COMPACT and GRID_COMFORTABLE
                        if (currentMode == LibraryViewMode.GRID_COMPACT) {
                            LibraryViewMode.GRID_COMFORTABLE
                        } else {
                            LibraryViewMode.GRID_COMPACT
                        }
                    } else {
                        LibraryViewMode.GRID_COMPACT
                    },
                )
            },
        ) {
            Icon(
                imageVector =
                    if (currentMode.isGrid()) {
                        Icons.Filled.GridView
                    } else {
                        Icons.Outlined.GridView
                    },
                contentDescription = stringResource(R.string.viewModeGrid),
                tint =
                    if (currentMode.isGrid()) {
                        androidx.compose.material3.MaterialTheme.colorScheme.primary
                    } else {
                        androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
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
    currentSortOrder: com.jabook.app.jabook.compose.data.model.BookSortOrder,
    onSortOrderChanged: (com.jabook.app.jabook.compose.data.model.BookSortOrder) -> Unit,
    onDismiss: () -> Unit,
) {
    JabookModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.sort_by),
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        com.jabook.app.jabook.compose.data.model.BookSortOrder.entries.forEach { order ->
            ListItem(
                headlineContent = {
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
    FAVORITES,
    DOWNLOADED,
    IN_PROGRESS,
}

private fun List<Book>.filterBy(filter: LibraryQuickFilter): List<Book> =
    when (filter) {
        LibraryQuickFilter.ALL -> this
        LibraryQuickFilter.FAVORITES -> filter { it.isFavorite }
        LibraryQuickFilter.DOWNLOADED -> filter { it.isDownloaded }
        LibraryQuickFilter.IN_PROGRESS -> filter { it.progress > 0f && !it.isCompleted }
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
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = activeFilter == LibraryQuickFilter.ALL,
            onClick = { onFilterChanged(LibraryQuickFilter.ALL) },
            label = { Text(stringResource(R.string.allFilter)) },
        )
        FilterChip(
            selected = activeFilter == LibraryQuickFilter.FAVORITES,
            onClick = { onFilterChanged(LibraryQuickFilter.FAVORITES) },
            label = { Text(stringResource(R.string.favoritesTooltip)) },
        )
        FilterChip(
            selected = activeFilter == LibraryQuickFilter.DOWNLOADED,
            onClick = { onFilterChanged(LibraryQuickFilter.DOWNLOADED) },
            label = { Text(stringResource(R.string.downloadedLabel)) },
        )
        FilterChip(
            selected = activeFilter == LibraryQuickFilter.IN_PROGRESS,
            onClick = { onFilterChanged(LibraryQuickFilter.IN_PROGRESS) },
            label = { Text(stringResource(R.string.inProgress)) },
        )
    }
}

/**
 * Converts LibraryViewMode to BookDisplayMode.
 * Temporary helper during migration period.
 */
private fun LibraryViewMode.toBookDisplayMode(): com.jabook.app.jabook.compose.domain.model.BookDisplayMode =
    when (this) {
        LibraryViewMode.LIST_COMPACT -> com.jabook.app.jabook.compose.domain.model.BookDisplayMode.LIST_COMPACT
        LibraryViewMode.GRID_COMPACT -> com.jabook.app.jabook.compose.domain.model.BookDisplayMode.GRID_COMPACT
        LibraryViewMode.GRID_COMFORTABLE -> com.jabook.app.jabook.compose.domain.model.BookDisplayMode.GRID_COMFORTABLE
    }
