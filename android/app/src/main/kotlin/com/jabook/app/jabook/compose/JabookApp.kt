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

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jabook.app.jabook.compose.l10n.LocalStrings
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
    intent: android.content.Intent? = null,
    appState: JabookAppState = rememberJabookAppState(),
    viewModel: MainViewModel =
        androidx.hilt.navigation.compose
            .hiltViewModel(),
) {
    val uiState by androidx.lifecycle.compose.collectAsStateWithLifecycle(viewModel.uiState)

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

    JabookTheme(
        darkTheme = darkTheme,
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { androidx.compose.material3.SnackbarHost(appState.snackbarHostState) },
            bottomBar = {
                JabookBottomBar(
                    destinations = appState.topLevelDestinations,
                    currentDestination = appState.currentDestination,
                    onNavigateToDestination = { destination ->
                        appState.navigateToTopLevelDestination(destination)
                    },
                )
            },
        ) { padding ->
            JabookNavHost(
                appState = appState,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

/**
 * Bottom navigation bar for top-level destinations.
 *
 * @param destinations List of top-level destinations to show
 * @param currentDestination Current navigation destination
 * @param onNavigateToDestination Callback when a destination is selected
 */
@Composable
private fun JabookBottomBar(
    destinations: List<TopLevelDestination>,
    currentDestination: androidx.navigation.NavDestination?,
    onNavigateToDestination: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current

    NavigationBar(
        modifier = modifier,
    ) {
        destinations.forEach { destination ->
            val isSelected = currentDestination.isTopLevelDestinationInHierarchy(destination)

            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigateToDestination(destination) },
                icon = {
                    Icon(
                        imageVector =
                            if (isSelected) {
                                destination.selectedIcon
                            } else {
                                destination.unselectedIcon
                            },
                        contentDescription = destination.iconText(strings),
                    )
                },
                label = { Text(destination.titleText(strings)) },
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
