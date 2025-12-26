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

package com.jabook.app.jabook.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jabook.app.jabook.compose.navigation.JabookAppState
import com.jabook.app.jabook.compose.navigation.JabookNavHost
import com.jabook.app.jabook.compose.navigation.LibraryRoute
import com.jabook.app.jabook.compose.navigation.PlayerRoute
import com.jabook.app.jabook.compose.navigation.TopLevelDestination
import com.jabook.app.jabook.compose.navigation.rememberJabookAppState
import com.jabook.app.jabook.ui.theme.JabookTheme

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
fun JabookApp(
    windowSizeClass: WindowSizeClass,
    intent: android.content.Intent? = null,
    appState: JabookAppState = rememberJabookAppState(),
    viewModel: MainViewModel = hiltViewModel(),
    permissionViewModel: com.jabook.app.jabook.compose.feature.permissions.PermissionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Detect if this is a beta/dev/stage flavor by checking package name
    // Beta: com.jabook.app.jabook.beta, Dev: .dev, Stage: .stage, Prod: com.jabook.app.jabook
    val context = LocalContext.current
    val packageName = context.packageName
    val isBetaFlavor = packageName.endsWith(".beta") || packageName.endsWith(".dev") || packageName.endsWith(".stage")

    // Permission State
    val permissionUiState by permissionViewModel.uiState.collectAsStateWithLifecycle()

    // Check permissions on start
    androidx.compose.runtime.LaunchedEffect(Unit) {
        permissionViewModel.checkPermissions()
    }

    if (!permissionUiState.hasStoragePermission) {
        com.jabook.app.jabook.compose.feature.permissions.PermissionScreen(
            onPermissionsGranted = { permissionViewModel.checkPermissions() },
        )
        return
    }

    // Handle deep links when intent changes
    androidx.compose.runtime.LaunchedEffect(intent) {
        if (intent != null) {
            if (intent.getBooleanExtra("navigate_to_player", false)) {
                // Check if book_id is passed for direct navigation
                val bookId = intent.getStringExtra("book_id")

                if (bookId != null) {
                    // Navigate directly to the player screen for this book
                    appState.navController.navigate(PlayerRoute(bookId = bookId))
                } else {
                    // Fallback to library if no book ID (or maybe last played book?)
                    // Without book ID we can't open the player screen directly as it requires an ID
                    appState.navController.navigate(LibraryRoute)
                }
            }

            appState.navController.handleDeepLink(intent)
        }
    }

    val darkTheme =
        when (uiState) {
            is MainActivityUiState.Loading -> androidx.compose.foundation.isSystemInDarkTheme()
            is MainActivityUiState.Success -> {
                val theme = (uiState as MainActivityUiState.Success).userData.theme
                when (theme) {
                    com.jabook.app.jabook.compose.data.model.AppTheme.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                    com.jabook.app.jabook.compose.data.model.AppTheme.LIGHT -> false
                    com.jabook.app.jabook.compose.data.model.AppTheme.DARK -> true
                }
            }
        }

    val useSystemFont =
        when (uiState) {
            is MainActivityUiState.Loading -> false
            is MainActivityUiState.Success -> {
                (uiState as MainActivityUiState.Success).userData.font ==
                    com.jabook.app.jabook.compose.data.model.AppFont.SYSTEM
            }
        }

    // Setup Lyricist for type-safe localization - REMOVED, using standard Android resources
    JabookTheme(
        darkTheme = darkTheme,
        isBetaFlavor = isBetaFlavor,
        useSystemFont = useSystemFont,
    ) {
        // ✅ Mini-player state management using AudioPlayerController
        // AudioPlayerController is a @Singleton that doesn't require navigation arguments
        val audioPlayerController: com.jabook.app.jabook.compose.feature.player.controller.AudioPlayerController = hiltViewModel()
        val isPlaying by audioPlayerController.isPlaying.collectAsStateWithLifecycle()
        val currentPosition by audioPlayerController.currentPosition.collectAsStateWithLifecycle()
        val duration by audioPlayerController.duration.collectAsStateWithLifecycle()

        // 🎯 NavigationSuiteScaffold automatically adapts navigation to screen size
        // - Compact: Bottom navigation bar
        // - Medium/Expanded: Navigation rail
        // - Large/Extra-large: Wide navigation rail or drawer
        NavigationSuiteScaffold(
            navigationItems = {
                appState.topLevelDestinations.forEach { destination ->
                    val selected = appState.currentDestination.isTopLevelDestinationInHierarchy(destination)

                    NavigationSuiteItem(
                        icon = {
                            Icon(
                                imageVector =
                                    if (selected) {
                                        destination.selectedIcon
                                    } else {
                                        destination.unselectedIcon
                                    },
                                contentDescription = stringResource(destination.iconTextId),
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
            // Main content area
            Box(modifier = Modifier.fillMaxSize()) {
                @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
                androidx.compose.animation.SharedTransitionLayout {
                    JabookNavHost(
                        appState = appState,
                        modifier = Modifier.fillMaxSize(),
                        sharedTransitionScope = this,
                    )
                }

                // Snackbar host positioned above navigation
                androidx.compose.material3.SnackbarHost(
                    hostState = appState.snackbarHostState,
                    modifier =
                        Modifier
                            .align(androidx.compose.ui.Alignment.BottomCenter)
                            .padding(bottom = 16.dp),
                )
            }
        }
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
    this?.route?.contains(destination.name, ignoreCase = true) == true
