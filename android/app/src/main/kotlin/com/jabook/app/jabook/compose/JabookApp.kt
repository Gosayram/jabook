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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jabook.app.jabook.compose.navigation.JabookAppState
import com.jabook.app.jabook.compose.navigation.JabookNavHost
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
        // TODO: Implement proper mini-player state management
        // PlayerViewModel cannot be instantiated at app root level because it requires
        // navigation arguments (PlayerRoute) from SavedStateHandle.
        // Options:
        // 1. Create MiniPlayerViewModel without navigation dependencies
        // 2. Use AudioPlayerController + repository to get current book
        // For now, mini-player is disabled to fix crash.
        // val playerViewModel: PlayerViewModel = hiltViewModel()
        // val playerUiState by playerViewModel.uiState.collectAsStateWithLifecycle()

        val showNavRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { androidx.compose.material3.SnackbarHost(appState.snackbarHostState) },
            bottomBar = {
                if (!showNavRail) {
                    androidx.compose.foundation.layout.Column {
                        // Mini player (Compact view: above navigation bar)
                        // TODO: Re-enable after implementing proper state management
                        // Mini-player temporarily disabled until MiniPlayerViewModel is created

                        // Bottom navigation bar
                        JabookBottomBar(
                            appState = appState,
                            onNavigateToDestination = appState::navigateToTopLevelDestination,
                        )
                    }
                }
            },
        ) { padding ->
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
            ) {
                if (showNavRail) {
                    com.jabook.app.jabook.compose.designsystem.component.JabookNavigationRail(
                        destinations = appState.topLevelDestinations,
                        currentDestination = appState.currentDestination,
                        onNavigateToDestination = { destination ->
                            appState.navigateToTopLevelDestination(destination)
                        },
                        modifier = Modifier.fillMaxHeight(), // Should be safe inside Row
                    )
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f)) {
                        @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
                        androidx.compose.animation.SharedTransitionLayout {
                            JabookNavHost(
                                appState = appState,
                                modifier = Modifier.fillMaxSize(),
                                sharedTransitionScope = this,
                            )
                        }
                    }

                    // Mini player (Expanded view: bottom of content area)
                    // TODO: Re-enable after implementing proper state management
                    // Mini-player temporarily disabled until MiniPlayerViewModel is created
                }
            }
        }
    }
}

/**
 * Bottom navigation bar for top-level destinations.
 *
 * @param currentDestination Current navigation destination
 * @param onNavigateToDestination Callback when a destination is selected
 */
@Composable
private fun JabookBottomBar(
    appState: JabookAppState,
    onNavigateToDestination: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = Modifier.height(88.dp),
        tonalElevation = 0.dp,
    ) {
        appState.topLevelDestinations.forEach { destination ->
            val selected = appState.currentDestination.isTopLevelDestinationInHierarchy(destination)

            NavigationBarItem(
                selected = selected,
                onClick = { onNavigateToDestination(destination) },
                icon = {
                    Icon(
                        imageVector =
                            if (selected) {
                                destination.selectedIcon
                            } else {
                                destination.unselectedIcon
                            },
                        contentDescription =
                            androidx.compose.ui.res
                                .stringResource(destination.iconTextId),
                    )
                },
                label = {
                    Text(
                        androidx.compose.ui.res
                            .stringResource(destination.titleTextId),
                    )
                },
            )
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
