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
    ) {
        // Library screen - shows list of audiobooks
        composable<LibraryRoute> {
            // TODO: Replace with actual LibraryScreen when implemented
            PlaceholderScreen(text = "Library Screen\n(Coming in Phase 3)")
        }

        // Player screen - shows audio player
        composable<PlayerRoute> { backStackEntry ->
            // TODO: Replace with actual PlayerScreen when implemented
            PlaceholderScreen(text = "Player Screen\n(Coming in Phase 4)")
        }

        // WebView screen - shows web content
        composable<WebViewRoute> { backStackEntry ->
            // TODO: Replace with actual WebViewScreen when implemented
            PlaceholderScreen(text = "WebView Screen\n(Coming in Phase 5)")
        }

        // Settings screen - shows app settings
        composable<SettingsRoute> {
            // TODO: Replace with actual SettingsScreen when implemented
            PlaceholderScreen(text = "Settings Screen\n(Coming in Phase 6)")
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
