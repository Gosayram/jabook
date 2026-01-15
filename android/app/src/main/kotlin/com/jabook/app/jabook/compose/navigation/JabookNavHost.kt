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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.jabook.app.jabook.compose.core.logger.LoggerFactoryImpl
import com.jabook.app.jabook.compose.feature.favorites.FavoritesScreen
import com.jabook.app.jabook.compose.feature.library.LibraryScreen
import com.jabook.app.jabook.compose.feature.player.PlayerScreen
import com.jabook.app.jabook.compose.feature.search.SearchScreen
import com.jabook.app.jabook.compose.feature.settings.SettingsScreen
import com.jabook.app.jabook.compose.feature.topic.TopicScreen
import com.jabook.app.jabook.compose.feature.webview.WebViewScreen

/**
 * Logger for Navigation.
 */
private val navigationLogger = LoggerFactoryImpl().get("Navigation")

/**
 * Jabook app navigation graph.
 *
 * Defines navigation between all app screens using Compose Navigation.
 * Based on Now in Android's navigation pattern.
 *
 * @param appState App state containing navigation controller
 * @param modifier Modifier to be applied to the NavHost
 */
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
public fun JabookNavHost(
    appState: JabookAppState,
    modifier: Modifier = Modifier,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
) {
    val navController = appState.navController

    // Log navigation changes
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentBackStackEntry?.destination?.route) {
        currentBackStackEntry?.destination?.route?.let { route ->
            navigationLogger.d { "📍 Navigation: Current screen = $route" }
        }
    }

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
                    navController.navigate(DownloadsRoute())
                },
                onNavigateToFavorites = {
                    navController.navigate(FavoritesRoute)
                },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = this,
            )
        }

        // Onboarding screen - introduces the app
        composable<OnboardingRoute> {
            com.jabook.app.jabook.compose.feature.onboarding.OnboardingScreen(
                onFinish = {
                    navController.navigate(LibraryRoute) {
                        popUpTo(OnboardingRoute) { inclusive = true }
                    }
                },
            )
        }

        // Player screen - shows audio player
        composable<PlayerRoute>(
            deepLinks =
                listOf(
                    androidx.navigation.navDeepLink<PlayerRoute>(basePath = "jabook://player"),
                ),
            // Disable exit animations to prevent blank screen on back navigation
            popExitTransition = {
                androidx.compose.animation.fadeOut(
                    animationSpec =
                        androidx.compose.animation.core
                            .tween(0),
                )
            },
            exitTransition = {
                androidx.compose.animation.fadeOut(
                    animationSpec =
                        androidx.compose.animation.core
                            .tween(0),
                )
            },
        ) { backStackEntry ->
            PlayerScreen(
                onNavigateBack = {
                    appState.navigateToLibrary()
                },
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = this,
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
                    navController.navigate(DownloadsRoute(magnetLink = magnetUrl))
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
                onNavigateToDebug = {
                    navController.navigate(DebugRoute)
                },
                onNavigateToScanSettings = {
                    navController.navigate(ScanSettingsRoute)
                },
                onNavigateToAudioSettings = {
                    navController.navigate(AudioSettingsRoute)
                },
                onNavigateToDownloads = {
                    navController.navigate(DownloadsRoute())
                },
            )
        }

        // Scan Settings Screen
        composable<ScanSettingsRoute> {
            com.jabook.app.jabook.compose.feature.settings.ScanSettingsScreen(
                onNavigateUp = {
                    navController.navigateUp()
                },
            )
        }

        // Audio Settings Screen
        composable<AudioSettingsRoute> {
            com.jabook.app.jabook.compose.feature.settings.AudioSettingsScreen(
                onNavigateUp = {
                    navController.navigateUp()
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
                    // Navigate to Topic Screen
                    navController.navigate(TopicRoute(topicId = searchResult.topicId))
                },
            )
        }

        // RuTracker Search screen - dedicated RuTracker search
        composable<RutrackerSearchRoute>(
            deepLinks =
                listOf(
                    androidx.navigation.navDeepLink { uriPattern = "jabook://rutracker/search" },
                ),
        ) {
            com.jabook.app.jabook.compose.feature.search.rutracker.RutrackerSearchScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onTopicClick = { topicId ->
                    navigationLogger.d { "🧭 Navigating to Topic: topicId=$topicId" }
                    navController.navigate(TopicRoute(topicId = topicId))
                },
            )
        }

        // Migration screen - data migration from legacy app
        composable<MigrationRoute>(
            deepLinks =
                listOf(
                    androidx.navigation.navDeepLink { uriPattern = "jabook://migration" },
                ),
        ) {
            com.jabook.app.jabook.compose.feature.migration.MigrationScreen(
                onMigrationComplete = {
                    navController.navigate(LibraryRoute) {
                        popUpTo(MigrationRoute) { inclusive = true }
                    }
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
            com.jabook.app.jabook.compose.feature.torrent.TorrentDownloadsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDetails = { hash ->
                    navController.navigate(TorrentDetailsRoute(hash))
                },
            )
        }

        // Torrent details screen
        composable<TorrentDetailsRoute> {
            com.jabook.app.jabook.compose.feature.torrent.TorrentDetailsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onPlayBook = { bookId ->
                    navController.navigate(PlayerRoute(bookId = bookId))
                },
            )
        }

        // Debug screen - shows debug tools and logs
        composable<DebugRoute>(
            deepLinks =
                listOf(
                    androidx.navigation.navDeepLink { uriPattern = "jabook://debug" },
                ),
        ) {
            com.jabook.app.jabook.compose.feature.debug.DebugScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        // Topic details screen - shows RuTracker topic information
        composable<TopicRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<TopicRoute>()
            TopicScreen(
                topicId = route.topicId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToTopic = { topicId ->
                    navController.navigate(TopicRoute(topicId = topicId))
                },
            )
        }

        // Favorites screen - shows user's favorite audiobooks
        composable<FavoritesRoute>(
            deepLinks =
                listOf(
                    androidx.navigation.navDeepLink { uriPattern = "jabook://favorites" },
                ),
        ) {
            FavoritesScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToTopic = { topicId: String ->
                    navController.navigate(TopicRoute(topicId = topicId))
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
