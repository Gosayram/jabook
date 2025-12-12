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

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import cafe.adriel.lyricist.ProvideStrings
import cafe.adriel.lyricist.rememberStrings
import dagger.hilt.android.AndroidEntryPoint

/**
 * Compose UI Activity for testing parallel Compose implementation.
 *
 * This activity allows testing the Compose UI in parallel with the existing
 * Flutter implementation. Once the Compose UI is complete and tested,
 * MainActivity will be migrated to use this pattern.
 *
 * This activity also handles deep links:
 * - magnet: links for torrent downloads
 * - jabook:// custom scheme for app navigation
 *
 * @see MainActivity for the current Flutter-based implementation
 */
@AndroidEntryPoint
class ComposeMainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "ComposeMainActivity"
    }

    private var deepLinkIntent by androidx.compose.runtime.mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Handle splash screen transition
        // This must be called before super.onCreate()
        installSplashScreen()

        // Enable edge-to-edge display (Android 15+ recommended pattern)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Handle initial intent
        deepLinkIntent = intent
        handleIntent(intent)

        setContent {
            // Setup Lyricist for type-safe localization
            ProvideStrings(rememberStrings()) {
                JabookApp(intent = deepLinkIntent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Update state to trigger recomposition in JabookApp
        deepLinkIntent = intent
        // Handle intent when activity is already running (singleTop mode)
        handleIntent(intent)
    }

    /**
     * Handles incoming intents, including deep links.
     *
     * Supported schemes:
     * - magnet: - Torrent magnet links (starts download)
     * - jabook: - App-specific navigation (e.g., jabook://library, jabook://player/bookId)
     *
     * @param intent The intent to handle
     */
    private fun handleIntent(intent: Intent?) {
        val data: Uri = intent?.data ?: return

        Log.d(TAG, "Handling intent with data: $data, scheme: ${data.scheme}")

        when (data.scheme) {
            "magnet" -> {
                handleMagnetLink(data)
            }
            "jabook" -> {
                handleJabookDeepLink(data)
            }
            else -> {
                Log.w(TAG, "Unknown scheme: ${data.scheme}")
            }
        }
    }

    /**
     * Handles magnet: links by starting the download service.
     *
     * @param uri The magnet URI
     */
    private fun handleMagnetLink(uri: Uri) {
        val magnetUrl = uri.toString()
        Log.d(TAG, "Handling magnet link: $magnetUrl")

        // Get default save path (app-specific storage)
        val savePath = "${getExternalFilesDir(null)}/JabookAudio/downloads"

        // Start download service
        com.jabook.app.jabook.download.DownloadForegroundService.startDownload(
            context = this,
            magnetUri = magnetUrl,
            savePath = savePath,
        )

        Log.i(TAG, "Started torrent download: $magnetUrl")
        // TODO: Show toast/snackbar confirming download started
        // TODO: Optionally navigate to downloads screen
    }

    /**
     * Handles jabook:// deep links for app navigation.
     *
     * Supported paths:
     * - jabook://library - Navigate to library
     * - jabook://settings - Navigate to settings
     * - jabook://player/{bookId} - Navigate to player for specific book
     * - jabook://webview?url={url} - Open WebView with URL
     *
     * @param uri The jabook URI
     */
    private fun handleJabookDeepLink(uri: Uri) {
        val host = uri.host
        val path = uri.path
        Log.d(TAG, "Handling jabook deep link - host: $host, path: $path")

        // TODO: Implement navigation based on URI
        // For now, just log the deep link
        // In the future, this should use the navigation controller to navigate

        // Example future implementation:
        // when (host) {
        //     "library" -> navController.navigate(LibraryRoute)
        //     "settings" -> navController.navigate(SettingsRoute)
        //     "player" -> {
        //         val bookId = path?.removePrefix("/")
        //         if (bookId != null) {
        //             navController.navigate(PlayerRoute(bookId))
        //         }
        //     }
        //     "webview" -> {
        //         val url = uri.getQueryParameter("url")
        //         if (url != null) {
        //             navController.navigate(WebViewRoute(url))
        //         }
        //     }
        // }
    }
}
