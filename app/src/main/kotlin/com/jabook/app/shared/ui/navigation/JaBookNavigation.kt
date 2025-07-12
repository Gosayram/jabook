package com.jabook.app.shared.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.jabook.app.features.discovery.presentation.DiscoveryScreen
import com.jabook.app.features.downloads.presentation.DownloadsScreen
import com.jabook.app.features.player.presentation.PlayerScreen

/** Main navigation destinations for JaBook application. */
sealed class JaBookDestination(val route: String, val icon: ImageVector, val title: String) {
    object Library : JaBookDestination("library", Icons.Default.Home, "Library")

    object Discovery : JaBookDestination("discovery", Icons.Default.Search, "Discover")

    object Player : JaBookDestination("player", Icons.Default.PlayArrow, "Player")

    object Downloads : JaBookDestination("downloads", Icons.Default.Download, "Downloads")
}

private val bottomNavDestinations =
    listOf(JaBookDestination.Library, JaBookDestination.Discovery, JaBookDestination.Player, JaBookDestination.Downloads)

/** Main navigation component for JaBook. Provides bottom navigation and screen routing. */
@Composable
fun JaBookNavigation(navController: NavHostController, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        bottomBar = { JaBookBottomNavigation(navController = navController, destinations = bottomNavDestinations) },
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = JaBookDestination.Library.route,
            modifier = Modifier.padding(paddingValues),
        ) {
            composable(JaBookDestination.Library.route) {
                com.jabook.app.features.library.presentation.LibraryScreen(
                    onAudiobookClick = { audiobook -> navController.navigate(JaBookDestination.Player.route) }
                )
            }
            composable(JaBookDestination.Discovery.route) {
                DiscoveryScreen(onNavigateToAudiobook = { /* navigate maybe to player or details later */ })
            }
            composable(JaBookDestination.Player.route) { PlayerScreen() }
            composable(JaBookDestination.Downloads.route) { DownloadsScreen() }
        }
    }
}

@Composable
private fun JaBookBottomNavigation(navController: NavController, destinations: List<JaBookDestination>) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        for (destination in destinations) {
            NavigationBarItem(
                icon = { Icon(imageVector = destination.icon, contentDescription = destination.title) },
                label = { Text(destination.title) },
                selected = currentRoute == destination.route,
                onClick = {
                    if (currentRoute != destination.route) {
                        navController.navigate(destination.route) {
                            // Pop up to the start destination of the graph to
                            // avoid building up a large stack of destinations
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            // Avoid multiple copies of the same destination when
                            // reselecting the same item
                            launchSingleTop = true
                            // Restore state when reselecting a previously selected item
                            restoreState = true
                        }
                    }
                },
            )
        }
    }
}

// Placeholder composable removed – real screens are used now
