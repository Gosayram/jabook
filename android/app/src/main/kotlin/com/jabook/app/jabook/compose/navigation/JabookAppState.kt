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

/**
 * Remembers and creates a [JabookAppState] instance.
 *
 * @param navController Navigation controller for the app
 */
@Composable
fun rememberJabookAppState(navController: NavHostController = rememberNavController()): JabookAppState =
    remember(navController) {
        JabookAppState(navController)
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
class JabookAppState(
    val navController: NavHostController,
) {
    /**
     * Current navigation destination.
     */
    val currentDestination: NavDestination?
        @Composable get() =
            navController
                .currentBackStackEntryAsState()
                .value
                ?.destination

    /**
     * Top-level destinations in the app (shown in bottom nav).
     */
    val topLevelDestinations: List<TopLevelDestination> = TopLevelDestination.entries

    /**
     * Navigates to a top-level destination.
     *
     * Handles single-top navigation to avoid multiple copies of the same destination.
     *
     * @param topLevelDestination The destination to navigate to
     */
    fun navigateToTopLevelDestination(topLevelDestination: TopLevelDestination) {
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

        when (topLevelDestination) {
            TopLevelDestination.LIBRARY -> navController.navigate(LibraryRoute, topLevelNavOptions)
            TopLevelDestination.SETTINGS -> navController.navigate(SettingsRoute, topLevelNavOptions)
        }
    }
}
