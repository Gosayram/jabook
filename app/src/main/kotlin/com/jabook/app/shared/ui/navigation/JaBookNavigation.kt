package com.jabook.app.shared.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings // Added Settings icon
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jabook.app.R
import com.jabook.app.features.discovery.presentation.DiscoveryScreen
import com.jabook.app.features.downloads.presentation.DownloadsScreen
import com.jabook.app.features.library.presentation.LibraryScreen
import com.jabook.app.features.player.presentation.PlayerScreen
import com.jabook.app.features.settings.presentation.RuTrackerSettingsScreen // Added import for RuTrackerSettingsScreen
import com.jabook.app.shared.ui.AppThemeMode
import com.jabook.app.shared.ui.ThemeViewModel
import com.jabook.app.shared.ui.theme.JaBookAnimations

/** Navigation destinations for the app */
sealed class Screen(
    val route: String,
) {
    object Library : Screen("library")

    object Discovery : Screen("discovery")

    object Downloads : Screen("downloads")

    object Player : Screen("player")

    object Settings : Screen("settings") // New screen for settings
}

/** Main navigation composable with bottom navigation and animated transitions */
@Composable
fun JaBookNavigation(
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier,
    themeViewModel: ThemeViewModel,
    themeMode: AppThemeMode,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface) {
                val items =
                    listOf(
                        Triple(Screen.Library, Icons.Default.Home, R.string.library_title),
                        Triple(Screen.Discovery, Icons.Default.Search, R.string.discovery),
                        Triple(Screen.Downloads, Icons.Default.Download, R.string.downloads),
                        Triple(Screen.Player, Icons.Default.PlayArrow, R.string.player),
                        Triple(Screen.Settings, Icons.Default.Settings, R.string.settings), // Added Settings icon
                    )

                items.forEach { (screen, icon, titleRes) ->
                    NavigationBarItem(
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(stringResource(titleRes)) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        Surface(modifier = Modifier.fillMaxSize().padding(innerPadding), color = MaterialTheme.colorScheme.background) {
            NavHost(
                navController = navController,
                startDestination = Screen.Library.route,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { getEnterTransition() },
                exitTransition = { getExitTransition() },
                popEnterTransition = { getPopEnterTransition() },
                popExitTransition = { getPopExitTransition() },
            ) {
                composable(Screen.Library.route) {
                    LibraryScreen(
                        onAudiobookClick = { audiobook -> navController.navigate(Screen.Player.route) },
                        themeViewModel = themeViewModel,
                        themeMode = themeMode,
                    )
                }

                composable(Screen.Discovery.route) {
                    DiscoveryScreen(
                        onNavigateToAudiobook = { audiobook -> navController.navigate(Screen.Player.route) },
                        themeViewModel = themeViewModel,
                        themeMode = themeMode,
                    )
                }

                composable(Screen.Downloads.route) {
                    DownloadsScreen(
                        themeViewModel = themeViewModel,
                        themeMode = themeMode,
                    )
                }

                composable(Screen.Player.route) {
                    PlayerScreen(
                        themeViewModel = themeViewModel,
                        themeMode = themeMode,
                    )
                }

                composable(Screen.Settings.route) {
                    RuTrackerSettingsScreen() // New settings screen
                }
            }
        }
    }
}

/** Get enter transition for forward navigation */
private fun getEnterTransition(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(durationMillis = JaBookAnimations.DURATION_MEDIUM, easing = JaBookAnimations.EMPHASIZED_EASING),
        initialOffsetX = { it },
    ) + fadeIn(animationSpec = tween(durationMillis = JaBookAnimations.DURATION_MEDIUM, easing = JaBookAnimations.STANDARD_EASING))

/** Get exit transition for forward navigation */
private fun getExitTransition(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(durationMillis = JaBookAnimations.DURATION_SHORT, easing = JaBookAnimations.STANDARD_EASING),
        targetOffsetX = { -it / 3 },
    ) + fadeOut(animationSpec = tween(durationMillis = JaBookAnimations.DURATION_SHORT, easing = JaBookAnimations.STANDARD_EASING))

/** Get enter transition for back navigation */
private fun getPopEnterTransition(): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(durationMillis = JaBookAnimations.DURATION_MEDIUM, easing = JaBookAnimations.EMPHASIZED_EASING),
        initialOffsetX = { -it / 3 },
    ) + fadeIn(animationSpec = tween(durationMillis = JaBookAnimations.DURATION_MEDIUM, easing = JaBookAnimations.STANDARD_EASING))

/** Get exit transition for back navigation */
private fun getPopExitTransition(): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(durationMillis = JaBookAnimations.DURATION_SHORT, easing = JaBookAnimations.STANDARD_EASING),
        targetOffsetX = { it },
    ) + fadeOut(animationSpec = tween(durationMillis = JaBookAnimations.DURATION_SHORT, easing = JaBookAnimations.STANDARD_EASING))
