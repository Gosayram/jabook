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

package com.jabook.app.jabook.compose.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.jabook.app.jabook.compose.core.logger.LoggerFactoryImpl

/**
 * Logger for Navigation.
 */
private val navigationLogger = LoggerFactoryImpl().get("Navigation")

/**
 * Remembers and creates a [JabookAppState] instance.
 *
 * @param navController Navigation controller for the app
 */
@Composable
public fun rememberJabookAppState(
    navController: NavHostController = rememberNavController(),
    snackbarHostState: androidx.compose.material3.SnackbarHostState =
        remember { androidx.compose.material3.SnackbarHostState() },
): JabookAppState =
    remember(navController, snackbarHostState) {
        JabookAppState(navController, snackbarHostState)
    }

/**
 * State holder for the Jabook app.
 *
 * Manages:
 * - Navigation state
 * - Current destination
 * - Top-level destinations
 *
 * Based on Now in Android's NiaAppState pattern.
 */
@Stable
public class JabookAppState(
    public val navController: NavHostController,
    public val snackbarHostState: androidx.compose.material3.SnackbarHostState,
) {
    /**
     * Current navigation destination.
     */
    public val currentDestination: NavDestination?
        @Composable get() =
            navController
                .currentBackStackEntryAsState()
                .value
                ?.destination

    /**
     * Top-level destinations in the app (shown in bottom nav).
     */
    public val topLevelDestinations: List<TopLevelDestination> = TopLevelDestination.entries

    /**
     * Navigates to a top-level destination.
     *
     * Handles single-top navigation to avoid multiple copies of the same destination.
     *
     * @param topLevelDestination The destination to navigate to
     */
    public fun navigateToTopLevelDestination(topLevelDestination: TopLevelDestination) {
        navigationLogger.d { "🧭 Navigating to top-level destination: ${topLevelDestination.name}" }
        when (topLevelDestination) {
            TopLevelDestination.LIBRARY -> {
                // For library, use dedicated function to ensure proper navigation
                // This handles cases where we're navigating from player or other screens
                navigateToLibrary()
            }
            TopLevelDestination.SETTINGS -> {
                val topLevelNavOptions =
                    navOptions {
                        // Pop up to the start destination of the graph to
                        // avoid building up a large stack of destinations
                        // on the back stack as users select items
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination when
                        // reselecting the same item
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item
                        restoreState = true
                    }
                navController.navigate(SettingsRoute, topLevelNavOptions)
                navigationLogger.d { "✅ Navigated to Settings" }
            }
        }
    }

    /**
     * Navigates to the Library screen.
     *
     * This clears the back stack and navigates to the library as the root destination.
     * Useful for returning to the main screen from anywhere in the app.
     */
    public fun navigateToLibrary() {
        navigationLogger.d { "🧭 Navigating to Library (clearing back stack)" }
        navController.navigate(LibraryRoute) {
            // Clear the entire back stack INCLUDING the current destination
            popUpTo(navController.graph.findStartDestination().id) {
                inclusive = true // Remove everything including start destination
                saveState = false
            }
            // Single instance of library
            launchSingleTop = true
            // Don't restore state - fresh start
            restoreState = false
        }
        navigationLogger.d { "✅ Navigated to Library" }
    }
}
