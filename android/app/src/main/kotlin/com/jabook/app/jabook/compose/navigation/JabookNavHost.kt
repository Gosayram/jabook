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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.jabook.app.jabook.BuildConfig
import com.jabook.app.jabook.compose.core.logger.LoggerFactoryImpl
import com.jabook.app.jabook.compose.core.theme.MotionTokens
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
private val navigationLogger by lazy { LoggerFactoryImpl().get("Navigation") }

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
    onFirstMeaningfulContentDrawn: () -> Unit = {},
    onMenuClick: () -> Unit = {},
) {
    val navController = appState.navController

    // NavHost handles back navigation internally via its own BackHandler (dispatcher
    // callback). No manual handler needed — it would shadow predictive-back support.

    // Log navigation changes
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentBackStackEntry?.destination?.route) {
        currentBackStackEntry?.destination?.route?.let { route ->
            navigationLogger.d { "Navigation: Current screen = $route" }
        }
    }

    NavHost(
        navController = navController,
        startDestination = LibraryRoute,
        modifier = modifier,
        enterTransition = {
            when {
                initialState.destination.isTopLevelRoute() && targetState.destination.isTopLevelRoute() ->
                    androidx.compose.animation.fadeIn(
                        animationSpec =
                            androidx.compose.animation.core.tween(
                                durationMillis = MotionTokens.SHORT2,
                                easing = MotionTokens.Emphasized,
                            ),
                    )
                targetState.destination.isPlayerRoute() ->
                    slideIntoContainer(
                        androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec =
                            androidx.compose.animation.core.tween(
                                durationMillis = MotionTokens.MEDIUM2,
                                easing = MotionTokens.Emphasized,
                            ),
                    ) +
                        androidx.compose.animation.fadeIn(
                            animationSpec =
                                androidx.compose.animation.core.tween(
                                    durationMillis = MotionTokens.MEDIUM2,
                                    easing = MotionTokens.Emphasized,
                                ),
                        )
                else ->
                    slideIntoContainer(
                        androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec =
                            androidx.compose.animation.core.tween(
                                durationMillis = MotionTokens.MEDIUM2,
                                easing = MotionTokens.Emphasized,
                            ),
                    ) +
                        androidx.compose.animation.fadeIn(
                            animationSpec =
                                androidx.compose.animation.core.tween(
                                    durationMillis = MotionTokens.MEDIUM2,
                                    easing = MotionTokens.Emphasized,
                                ),
                        )
            }
        },
        exitTransition = {
            when {
                initialState.destination.isTopLevelRoute() && targetState.destination.isTopLevelRoute() ->
                    androidx.compose.animation.fadeOut(
                        animationSpec =
                            androidx.compose.animation.core.tween(
                                durationMillis = MotionTokens.SHORT2,
                                easing = MotionTokens.Emphasized,
                            ),
                    )
                targetState.destination.isPlayerRoute() ->
                    slideOutOfContainer(
                        androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Up,
                        animationSpec =
                            androidx.compose.animation.core.tween(
                                durationMillis = MotionTokens.MEDIUM2,
                                easing = MotionTokens.Emphasized,
                            ),
                    ) +
                        androidx.compose.animation.fadeOut(
                            animationSpec =
                                androidx.compose.animation.core.tween(
                                    durationMillis = MotionTokens.MEDIUM2,
                                    easing = MotionTokens.Emphasized,
                                ),
                        )
                else ->
                    slideOutOfContainer(
                        androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec =
                            androidx.compose.animation.core.tween(
                                durationMillis = MotionTokens.MEDIUM2,
                                easing = MotionTokens.Emphasized,
                            ),
                    ) +
                        androidx.compose.animation.fadeOut(
                            animationSpec =
                                androidx.compose.animation.core.tween(
                                    durationMillis = MotionTokens.MEDIUM2,
                                    easing = MotionTokens.Emphasized,
                                ),
                        )
            }
        },
        popEnterTransition = {
            when {
                initialState.destination.isTopLevelRoute() && targetState.destination.isTopLevelRoute() ->
                    androidx.compose.animation.fadeIn(
                        animationSpec =
                            androidx.compose.animation.core.tween(
                                durationMillis = MotionTokens.SHORT2,
                                easing = MotionTokens.Emphasized,
                            ),
                    )
                initialState.destination.isPlayerRoute() ->
                    slideIntoContainer(
                        androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec =
                            androidx.compose.animation.core.tween(
                                durationMillis = MotionTokens.MEDIUM2,
                                easing = MotionTokens.Emphasized,
                            ),
                    ) +
                        androidx.compose.animation.fadeIn(
                            animationSpec =
                                androidx.compose.animation.core.tween(
                                    durationMillis = MotionTokens.MEDIUM2,
                                    easing = MotionTokens.Emphasized,
                                ),
                        )
                else ->
                    slideIntoContainer(
                        androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec =
                            androidx.compose.animation.core.tween(
                                durationMillis = MotionTokens.MEDIUM2,
                                easing = MotionTokens.Emphasized,
                            ),
                    ) +
                        androidx.compose.animation.fadeIn(
                            animationSpec =
                                androidx.compose.animation.core.tween(
                                    durationMillis = MotionTokens.MEDIUM2,
                                    easing = MotionTokens.Emphasized,
                                ),
                        )
            }
        },
        popExitTransition = {
            when {
                initialState.destination.isTopLevelRoute() && targetState.destination.isTopLevelRoute() ->
                    androidx.compose.animation.fadeOut(
                        animationSpec =
                            androidx.compose.animation.core.tween(
                                durationMillis = MotionTokens.SHORT2,
                                easing = MotionTokens.Emphasized,
                            ),
                    )
                initialState.destination.isPlayerRoute() ->
                    slideOutOfContainer(
                        androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Down,
                        animationSpec =
                            androidx.compose.animation.core.tween(
                                durationMillis = MotionTokens.MEDIUM2,
                                easing = MotionTokens.Emphasized,
                            ),
                    ) +
                        androidx.compose.animation.fadeOut(
                            animationSpec =
                                androidx.compose.animation.core.tween(
                                    durationMillis = MotionTokens.MEDIUM2,
                                    easing = MotionTokens.Emphasized,
                                ),
                        )
                else ->
                    slideOutOfContainer(
                        androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec =
                            androidx.compose.animation.core.tween(
                                durationMillis = MotionTokens.MEDIUM2,
                                easing = MotionTokens.Emphasized,
                            ),
                    ) +
                        androidx.compose.animation.fadeOut(
                            animationSpec =
                                androidx.compose.animation.core.tween(
                                    durationMillis = MotionTokens.MEDIUM2,
                                    easing = MotionTokens.Emphasized,
                                ),
                        )
            }
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
                    navController.navigate(PlayerRoute(bookId = bookId)) {
                        launchSingleTop = true
                    }
                },
                onNavigateToSearch = {
                    navController.navigate(SearchRoute) {
                        launchSingleTop = true
                    }
                },
                onNavigateToDownloads = {
                    navController.navigate(DownloadsRoute()) {
                        launchSingleTop = true
                    }
                },
                onNavigateToFavorites = {
                    navController.navigate(FavoritesRoute) {
                        launchSingleTop = true
                    }
                },
                onNavigateToAudioSettings = {
                    navController.navigate(AudioSettingsRoute) {
                        launchSingleTop = true
                    }
                },
                onNavigateToSettings = {
                    navController.navigate(SettingsRoute) {
                        launchSingleTop = true
                    }
                },
                onNavigateToAuth = {
                    navController.navigate(com.jabook.app.jabook.compose.feature.auth.AuthRoute) {
                        launchSingleTop = true
                    }
                },
                onFirstMeaningfulContentDrawn = onFirstMeaningfulContentDrawn,
                onMenuClick = onMenuClick,
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
                    androidx.navigation.navDeepLink<PlayerRoute>(basePath = "jabook://player/{bookId}"),
                    androidx.navigation.navDeepLink<PlayerRoute>(basePath = "jabook://player/{bookId}/chapter/{chapterIndex}"),
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
                    navController.popBackStack()
                },
                onNavigateToBook = { bookId ->
                    navController.navigate(PlayerRoute(bookId = bookId)) {
                        launchSingleTop = true
                        popUpTo<PlayerRoute> { inclusive = true }
                    }
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
                    navController.navigate(DownloadsRoute(magnetLink = magnetUrl)) {
                        launchSingleTop = true
                    }
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
                    navController.navigate(com.jabook.app.jabook.compose.feature.auth.AuthRoute) {
                        launchSingleTop = true
                    }
                },
                onNavigateToDebug = {
                    navController.navigate(DebugRoute) {
                        launchSingleTop = true
                    }
                },
                onNavigateToScanSettings = {
                    navController.navigate(ScanSettingsRoute) {
                        launchSingleTop = true
                    }
                },
                onNavigateToAudioSettings = {
                    navController.navigate(AudioSettingsRoute) {
                        launchSingleTop = true
                    }
                },
                onNavigateToDownloads = {
                    navController.navigate(DownloadsRoute()) {
                        launchSingleTop = true
                    }
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
                            .WebViewRoute(url, isAuthentication = true),
                    ) {
                        launchSingleTop = true
                    }
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
                    navController.navigate(PlayerRoute(bookId = bookId)) {
                        launchSingleTop = true
                    }
                },
                onOnlineBookClick = { searchResult ->
                    // Navigate to Topic Screen
                    navController.navigate(TopicRoute(topicId = searchResult.topicId)) {
                        launchSingleTop = true
                    }
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
                    navigationLogger.d { "Navigating to Topic: topicId=$topicId" }
                    navController.navigate(TopicRoute(topicId = topicId)) {
                        launchSingleTop = true
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
                    navController.navigate(TorrentDetailsRoute(hash)) {
                        launchSingleTop = true
                    }
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
                    navController.navigate(PlayerRoute(bookId = bookId)) {
                        launchSingleTop = true
                    }
                },
            )
        }

        if (BuildConfig.DEBUG) {
            // Debug tools are intentionally unavailable from production builds.
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
                    navController.navigate(TopicRoute(topicId = topicId)) {
                        launchSingleTop = true
                    }
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
                    navController.navigate(TopicRoute(topicId = topicId)) {
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}

private fun NavDestination.isTopLevelRoute(): Boolean = hasRoute<LibraryRoute>() || hasRoute<SearchRoute>() || hasRoute<SettingsRoute>()

private fun NavDestination.isPlayerRoute(): Boolean = hasRoute<PlayerRoute>()
