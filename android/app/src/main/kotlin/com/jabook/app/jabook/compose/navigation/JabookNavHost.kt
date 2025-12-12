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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.jabook.app.jabook.compose.feature.downloads.DownloadsScreen
import com.jabook.app.jabook.compose.feature.library.LibraryScreen
import com.jabook.app.jabook.compose.feature.player.PlayerScreen
import com.jabook.app.jabook.compose.feature.search.SearchScreen
import com.jabook.app.jabook.compose.feature.settings.SettingsScreen
import com.jabook.app.jabook.compose.feature.webview.WebViewScreen

/**
 * Jabook app navigation graph.
 *
 * Defines navigation between all app screens using Compose Navigation.
 * Based on Now in Android's navigation pattern.
 *
 * @param appState App state containing navigation controller
 * @param modifier Modifier to be applied to the NavHost
 */
@Composable
fun JabookNavHost(
    appState: JabookAppState,
    modifier: Modifier = Modifier,
) {
    val navController = appState.navController

    NavHost(
        navController = navController,
        startDestination = LibraryRoute,
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(
                androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec =
                    androidx.compose.animation.core
                        .tween(300),
            ) +
                androidx.compose.animation.fadeIn(
                    animationSpec =
                        androidx.compose.animation.core
                            .tween(300),
                )
        },
        exitTransition = {
            slideOutOfContainer(
                androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec =
                    androidx.compose.animation.core
                        .tween(300),
            ) +
                androidx.compose.animation.fadeOut(
                    animationSpec =
                        androidx.compose.animation.core
                            .tween(300),
                )
        },
        popEnterTransition = {
            slideIntoContainer(
                androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec =
                    androidx.compose.animation.core
                        .tween(300),
            ) +
                androidx.compose.animation.fadeIn(
                    animationSpec =
                        androidx.compose.animation.core
                            .tween(300),
                )
        },
        popExitTransition = {
            slideOutOfContainer(
                androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec =
                    androidx.compose.animation.core
                        .tween(300),
            ) +
                androidx.compose.animation.fadeOut(
                    animationSpec =
                        androidx.compose.animation.core
                            .tween(300),
                )
        },
    ) {
        // Library screen - shows list of audiobooks
        composable<LibraryRoute>(
            deepLinks =
                listOf(
                    androidx.navigation.navDeepLink { uriPattern = "jabook://library" },
                ),
        ) {
            LibraryScreen(
                onBookClick = { bookId ->
                    navController.navigate(PlayerRoute(bookId = bookId))
                },
                onNavigateToSearch = {
                    navController.navigate(SearchRoute)
                },
                onNavigateToDownloads = {
                    navController.navigate(DownloadsRoute)
                },
            )
        }

        // Player screen - shows audio player
        composable<PlayerRoute>(
            deepLinks =
                listOf(
                    androidx.navigation.navDeepLink { uriPattern = "jabook://player/{bookId}" },
                ),
        ) { backStackEntry ->
            PlayerScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        // WebView screen - shows web content
        composable<WebViewRoute>(
            deepLinks =
                listOf(
                    androidx.navigation.navDeepLink { uriPattern = "jabook://webview?url={url}" },
                ),
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<WebViewRoute>()
            WebViewScreen(
                route = route,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onMagnetLinkDetected = { magnetUrl ->
                    // TODO: Handle magnet link - start download
                    // For now, just log or show toast
                },
            )
        }

        // Settings screen - shows app settings
        composable<SettingsRoute>(
            deepLinks =
                listOf(
                    androidx.navigation.navDeepLink { uriPattern = "jabook://settings" },
                ),
        ) {
            SettingsScreen(
                onNavigateToAuth = {
                    navController.navigate(com.jabook.app.jabook.compose.feature.auth.AuthRoute)
                },
            )
        }

        // Auth Screen
        composable<com.jabook.app.jabook.compose.feature.auth.AuthRoute> {
            com.jabook.app.jabook.compose.feature.auth.AuthScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToWebView = { url ->
                    navController.navigate(
                        com.jabook.app.jabook.compose.navigation
                            .WebViewRoute(url),
                    )
                },
            )
        }

        // Search screen - search for books
        composable<SearchRoute> {
            SearchScreen(
                onNavigateBack = {
                    navController.navigateUp()
                },
                onBookClick = { bookId ->
                    navController.navigate(PlayerRoute(bookId = bookId))
                },
                onOnlineBookClick = { searchResult ->
                    // TODO: Handle online search result click
                    // Could navigate to details screen or start download
                },
            )
        }

        // Downloads screen - shows active downloads
        composable<DownloadsRoute>(
            deepLinks =
                listOf(
                    androidx.navigation.navDeepLink { uriPattern = "jabook://downloads" },
                ),
        ) {
            DownloadsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }
    }
}

/**
 * Temporary placeholder screen for navigation testing.
 * Will be replaced with actual screens in later phases.
 */
@Composable
private fun PlaceholderScreen(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
