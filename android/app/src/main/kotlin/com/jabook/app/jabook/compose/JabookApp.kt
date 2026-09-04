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

package com.jabook.app.jabook.compose

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.core.util.LocalWindowSizeClass
import com.jabook.app.jabook.compose.navigation.JabookAppState
import com.jabook.app.jabook.compose.navigation.JabookNavHost
import com.jabook.app.jabook.compose.navigation.LibraryRoute
import com.jabook.app.jabook.compose.navigation.PlayerRoute
import com.jabook.app.jabook.compose.navigation.SettingsRoute
import com.jabook.app.jabook.compose.navigation.TopLevelDestination
import com.jabook.app.jabook.compose.navigation.rememberJabookAppState
import com.jabook.app.jabook.ui.theme.JabookTheme
import kotlinx.coroutines.launch

internal const val SETTINGS_BADGE_TEST_TAG: String = "settings_badge"

/**
 * Root composable for the Jabook app.
 *
 * This is the main entry point for the Compose UI, containing:
 * - Theme wrapper
 * - Bottom navigation bar
 * - Navigation graph
 *
 * Based on Now in Android's app structure.
 *
 * @param appState App state holder, defaults to remembered state
 */
@Composable
public fun JabookApp(
    windowSizeClass: WindowSizeClass,
    intent: android.content.Intent? = null,
    onFirstMeaningfulContentDrawn: () -> Unit = {},
    onPlayerScreenVisibilityChanged: (Boolean) -> Unit = {},
    appState: JabookAppState = rememberJabookAppState(),
    viewModel: MainViewModel = hiltViewModel(),
    permissionViewModel: com.jabook.app.jabook.compose.feature.permissions.PermissionViewModel = hiltViewModel(),
    settingsViewModel: com.jabook.app.jabook.compose.feature.settings.SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeDownloadsCount by settingsViewModel.activeDownloadsCount.collectAsStateWithLifecycle()
    val authStatus by settingsViewModel.authStatus.collectAsStateWithLifecycle()

    // Detect if this is a beta/dev/stage flavor by checking package name
    // Beta: com.jabook.app.jabook.beta, Dev: .dev, Stage: .stage, Prod: com.jabook.app.jabook
    val context = LocalContext.current
    val packageName = context.packageName
    val isBetaFlavor = packageName.endsWith(".beta") || packageName.endsWith(".dev") || packageName.endsWith(".stage")

    // Permission State
    val permissionUiState by permissionViewModel.uiState.collectAsStateWithLifecycle()
    var permissionSkipped by androidx.compose.runtime.saveable
        .rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }

    // Check permissions on start and when returning to the app
    androidx.lifecycle.compose.LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionViewModel.checkPermissions()
    }

    val onboardingCompleted =
        when (uiState) {
            is MainActivityUiState.Success -> (uiState as MainActivityUiState.Success).userData.onboardingCompleted
            else -> false // Default to false during loading to avoid flickering
        }
    val useDynamicColors =
        when (uiState) {
            is MainActivityUiState.Success -> (uiState as MainActivityUiState.Success).useDynamicColors
            else -> false
        }

    // If onboarding is not completed, we show it.
    // It will handle its own internal navigation and permissions.
    if (!onboardingCompleted && uiState is MainActivityUiState.Success) {
        JabookTheme(
            darkTheme = isSystemInDarkTheme(),
            dynamicColor = useDynamicColors,
            isBetaFlavor = isBetaFlavor,
        ) {
            com.jabook.app.jabook.compose.feature.onboarding.OnboardingScreen(
                isBeta = isBetaFlavor,
                onFinish = {
                    permissionSkipped = true
                },
            )
        }
        return
    }

    // Existing check for storage permission
    if (!permissionUiState.hasStoragePermission && onboardingCompleted && !permissionSkipped) {
        com.jabook.app.jabook.compose.feature.permissions.PermissionScreen(
            onPermissionsGranted = { permissionViewModel.checkPermissions() },
            onSkip = { permissionSkipped = true },
        )
        return
    }

    // Handle deep links when intent changes
    androidx.compose.runtime.LaunchedEffect(intent) {
        if (intent != null) {
            appState.navController.handleDeepLink(intent)
        }
    }

    val darkTheme =
        when (uiState) {
            is MainActivityUiState.Loading -> androidx.compose.foundation.isSystemInDarkTheme()
            is MainActivityUiState.Success -> {
                val theme = (uiState as MainActivityUiState.Success).userData.theme
                when (theme) {
                    com.jabook.app.jabook.compose.data.model.AppTheme.SYSTEM ->
                        androidx.compose.foundation
                            .isSystemInDarkTheme()
                    com.jabook.app.jabook.compose.data.model.AppTheme.LIGHT -> false
                    com.jabook.app.jabook.compose.data.model.AppTheme.DARK,
                    com.jabook.app.jabook.compose.data.model.AppTheme.AMOLED,
                    -> true
                }
            }
        }

    val isAmoledMode =
        when (uiState) {
            is MainActivityUiState.Success ->
                (uiState as MainActivityUiState.Success).userData.theme ==
                    com.jabook.app.jabook.compose.data.model.AppTheme.AMOLED
            else -> false
        }

    val selectedFont =
        when (uiState) {
            is MainActivityUiState.Loading -> com.jabook.app.jabook.compose.data.model.AppFont.DEFAULT
            is MainActivityUiState.Success -> {
                (uiState as MainActivityUiState.Success).userData.font
            }
        }

    // Setup Lyricist for type-safe localization - REMOVED, using standard Android resources
    JabookTheme(
        darkTheme = darkTheme,
        amoledMode = isAmoledMode,
        dynamicColor = useDynamicColors,
        isBetaFlavor = isBetaFlavor,
        selectedFont = selectedFont,
    ) {
        CompositionLocalProvider(LocalWindowSizeClass provides windowSizeClass) {
            // Mini-player state management using MiniPlayerViewModel
            // MiniPlayerViewModel is a lightweight wrapper around AudioPlayerController
            // Safe to instantiate at app root (no navigation dependencies)
            val miniPlayerViewModel: com.jabook.app.jabook.compose.feature.miniplayer.MiniPlayerViewModel = hiltViewModel()
            val isPlaying by miniPlayerViewModel.isPlaying.collectAsStateWithLifecycle()
            val hasNextChapter by miniPlayerViewModel.hasNextChapter.collectAsStateWithLifecycle()
            val hasPreviousChapter by miniPlayerViewModel.hasPreviousChapter.collectAsStateWithLifecycle()
            val currentBook by miniPlayerViewModel.currentBook.collectAsStateWithLifecycle()
            val currentDestination = appState.currentDestination // Hoist to Composable scope
            val currentOnPlayerScreenVisibilityChanged by rememberUpdatedState(onPlayerScreenVisibilityChanged)

            // Check if we're on the player screen - hide mini player in that case
            val isOnPlayerScreen = currentDestination?.hierarchy?.any { it.hasRoute<PlayerRoute>() } == true
            androidx.compose.runtime.DisposableEffect(isOnPlayerScreen) {
                currentOnPlayerScreenVisibilityChanged(isOnPlayerScreen)
                onDispose {
                    currentOnPlayerScreenVisibilityChanged(false)
                }
            }

            // State for mini player visibility (can be hidden by swipe)
            var isMiniPlayerVisible by remember { mutableStateOf(true) }

            // Reset visibility when book changes
            LaunchedEffect(currentBook?.id) {
                isMiniPlayerVisible = true
            }

            // Drawer State
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            val onMenuClick: () -> Unit = { scope.launch { drawerState.open() } }

            // Back closes the drawer instead of popping the nav stack (Reply pattern).
            androidx.activity.compose.BackHandler(enabled = drawerState.isOpen) {
                scope.launch { drawerState.close() }
            }

            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = false,
                drawerContent = {
                    com.jabook.app.jabook.compose.navigation.JabookDrawerContent(
                        destinations = appState.topLevelDestinations,
                        currentDestination = currentDestination,
                        onNavigateToDestination = { destination ->
                            appState.navigateToTopLevelDestination(destination)
                            scope.launch { drawerState.close() }
                        },
                        onNavigateToSettings = {
                            appState.navController.navigate(com.jabook.app.jabook.compose.navigation.SettingsRoute)
                            scope.launch { drawerState.close() }
                        },
                        onNavigateToAuth = {
                            appState.navController.navigate(com.jabook.app.jabook.compose.feature.auth.AuthRoute)
                            scope.launch { drawerState.close() }
                        },
                        onNavigateToAbout = {
                            appState.navController.navigate(com.jabook.app.jabook.compose.navigation.SettingsRoute)
                            scope.launch { drawerState.close() }
                        },
                        accountProfile =
                            when (val status = authStatus) {
                                is com.jabook.app.jabook.compose.domain.model.AuthStatus.Authenticated ->
                                    com.jabook.app.jabook.compose.navigation
                                        .AccountProfile(status.username, "")
                                else ->
                                    com.jabook.app.jabook.compose.navigation.AccountProfile(
                                        stringResource(R.string.settingsProfileGuest),
                                        "",
                                    )
                            },
                    )
                },
            ) {
                // NavigationSuiteScaffold automatically adapts navigation to screen size
                // - Compact: Bottom navigation bar
                // - Medium/Expanded: Navigation rail
                // - Large/Extra-large: Wide navigation rail or drawer
                NavigationSuiteScaffold(
                    navigationSuiteItems = {
                        appState.topLevelDestinations.forEach { destination ->
                            val selected = currentDestination.isTopLevelDestinationInHierarchy(destination)

                            item(
                                icon = {
                                    val icon =
                                        if (selected) {
                                            destination.selectedIcon
                                        } else {
                                            destination.unselectedIcon
                                        }
                                    TopLevelDestinationIcon(
                                        destination = destination,
                                        icon = icon,
                                        activeDownloadsCount = activeDownloadsCount,
                                    )
                                },
                                label = { Text(stringResource(destination.titleTextId)) },
                                selected = selected,
                                onClick = { appState.navigateToTopLevelDestination(destination) },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // ponytail: SharedTransitionLayout wraps both NavHost and mini-player so cover
                    // morphs via sharedElement ("cover_${bookId}") instead of crossfade.
                    @OptIn(ExperimentalSharedTransitionApi::class)
                    androidx.compose.animation.SharedTransitionLayout(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxSize(),
                            ) {
                                JabookNavHost(
                                    appState = appState,
                                    modifier = Modifier.fillMaxSize(),
                                    sharedTransitionScope = this@SharedTransitionLayout,
                                    onFirstMeaningfulContentDrawn = onFirstMeaningfulContentDrawn,
                                    onMenuClick = onMenuClick,
                                )

                                // Snackbar host positioned above mini player
                                androidx.compose.material3.SnackbarHost(
                                    hostState = appState.snackbarHostState,
                                    modifier =
                                        Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(
                                                bottom =
                                                    if (currentBook != null && isMiniPlayerVisible && !isOnPlayerScreen) 72.dp else 16.dp,
                                            ),
                                )
                            }

                            // Mini player — inside same SharedTransitionLayout for cover morph
                            if (!isOnPlayerScreen && isMiniPlayerVisible) {
                                currentBook?.let { book ->
                                    androidx.compose.animation.AnimatedVisibility(
                                        visible = true,
                                        enter = androidx.compose.animation.EnterTransition.None,
                                        exit = androidx.compose.animation.ExitTransition.None,
                                    ) {
                                        com.jabook.app.jabook.compose.feature.player.MiniPlayer(
                                            coverUrl = book.coverUrl,
                                            title = book.title,
                                            author = book.author,
                                            isPlaying = isPlaying,
                                            onPlayPauseClick = { miniPlayerViewModel.togglePlayPause() },
                                            onNextClick = { miniPlayerViewModel.skipToNext() },
                                            onPreviousClick = { miniPlayerViewModel.skipToPrevious() },
                                            hasNextChapter = hasNextChapter,
                                            hasPreviousChapter = hasPreviousChapter,
                                            onMiniPlayerClick = {
                                                appState.navController.navigate(PlayerRoute(bookId = book.id))
                                            },
                                            onDismiss = {
                                                miniPlayerViewModel.pause()
                                                isMiniPlayerVisible = false
                                                scope.launch {
                                                    val result =
                                                        appState.snackbarHostState.showSnackbar(
                                                            message = context.getString(R.string.mini_player_dismissed_undo),
                                                            actionLabel = context.getString(R.string.mini_player_dismissed_resume),
                                                            duration = SnackbarDuration.Indefinite,
                                                        )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        miniPlayerViewModel.play()
                                                        isMiniPlayerVisible = true
                                                    }
                                                }
                                            },
                                            modifier =
                                                Modifier.fillMaxWidth().let { m ->
                                                    if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact) {
                                                        m
                                                    } else {
                                                        m.navigationBarsPadding()
                                                    }
                                                },
                                            currentPositionMs = miniPlayerViewModel.currentPosition,
                                            durationMs = miniPlayerViewModel.duration,
                                            bookId = book.id,
                                            sharedTransitionScope = this@SharedTransitionLayout,
                                            animatedVisibilityScope = this,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TopLevelDestinationIcon(
    destination: TopLevelDestination,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    activeDownloadsCount: Int,
) {
    val context = LocalContext.current
    if (destination == TopLevelDestination.SETTINGS && activeDownloadsCount > 0) {
        val downloadsStateDescription =
            context.resources.getQuantityString(
                com.jabook.app.jabook.R.plurals.downloads_active_plural,
                activeDownloadsCount,
                activeDownloadsCount,
            )
        BadgedBox(
            badge = {
                // ponytail: badge count labelSmallEmphasized ceiling — Badge defaults to labelSmall, swap to EmphasizedTypography.labelSmall if badge needs emphasis
                Badge(modifier = Modifier.testTag(SETTINGS_BADGE_TEST_TAG)) {
                    Text(if (activeDownloadsCount > 99) "99+" else activeDownloadsCount.toString())
                }
            },
        ) {
            Icon(
                imageVector = icon,
                contentDescription = stringResource(destination.iconTextId),
                modifier =
                    Modifier.semantics {
                        stateDescription = downloadsStateDescription
                    },
            )
        }
    } else {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(destination.iconTextId),
        )
    }
}

/**
 * Checks if the current destination is in the hierarchy of a top-level destination.
 *
 * This matches based on route name, checking if the destination route contains
 * the top-level destination name (case-insensitive).
 *
 * @param destination The top-level destination to check against
 * @return true if the current destination is part of this top-level destination's hierarchy
 */
private fun androidx.navigation.NavDestination?.isTopLevelDestinationInHierarchy(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { navDestination ->
        when (destination) {
            TopLevelDestination.LIBRARY -> navDestination.hasRoute<LibraryRoute>()
            TopLevelDestination.SETTINGS -> navDestination.hasRoute<SettingsRoute>()
        }
    } == true
