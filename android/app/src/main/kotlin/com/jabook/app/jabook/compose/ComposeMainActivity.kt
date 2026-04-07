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

package com.jabook.app.jabook.compose

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.jabook.app.jabook.audio.AudioPlayerService
import com.jabook.app.jabook.audio.MediaControllerConstants
import com.jabook.app.jabook.audio.MediaControllerExtensions
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

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
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@AndroidEntryPoint
public class ComposeMainActivity : ComponentActivity() {
    @Inject
    public lateinit var loggerFactory: LoggerFactory

    private val logger by lazy { loggerFactory.get("ComposeMainActivity") }

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
        handleIntentExtras(intent)

        setContent {
            val windowSizeClass =
                androidx.compose.material3.windowsizeclass
                    .calculateWindowSizeClass(this)
            JabookApp(
                windowSizeClass = windowSizeClass,
                intent = deepLinkIntent,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Update state to trigger recomposition in JabookApp
        deepLinkIntent = intent
        // Handle intent when activity is already running (singleTop mode)
        handleIntent(intent)
        handleIntentExtras(intent)
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
        val externalAudioUris = ExternalAudioIntentPolicy.extractAudioUris(intent)
        if (externalAudioUris.isNotEmpty()) {
            handleExternalAudioIntent(externalAudioUris)
            return
        }

        val data: Uri = intent?.data ?: return

        logger.d { "Handling intent with data: $data, scheme: ${data.scheme}" }

        when (data.scheme) {
            "magnet" -> {
                handleMagnetLink(data)
            }
            "jabook" -> {
                handleJabookDeepLink(data)
            }
            else -> {
                logger.w { "Unknown scheme: ${data.scheme}" }
            }
        }
    }

    // Handle special intent extras that don't use a scheme
    private fun handleIntentExtras(intent: Intent?) {
        if (intent?.getBooleanExtra("navigate_to_player", false) == true) {
            logger.d { "Handling navigate_to_player extra" }
            // Navigation provided by JabookApp.LaunchedEffect(intent) which handles
            // this specific extra and navigates to PlayerRoute.
            // deepLinkIntent is already updated in onNewIntent/onCreate.
        }
    }

    /**
     * Handles magnet: links by starting the download service.
     *
     * @param uri The magnet URI
     */
    private fun handleMagnetLink(uri: Uri) {
        val magnetUrl = uri.toString()
        logger.d { "Handling magnet link: $magnetUrl" }

        // Get default save path (app-specific storage)
        val savePath = "${getExternalFilesDir(null)}/JabookAudio/downloads"

        // Start download service
        com.jabook.app.jabook.download.DownloadForegroundService.startDownload(
            context = this,
            magnetUri = magnetUrl,
            savePath = savePath,
        )

        logger.i { "Started torrent download: $magnetUrl" }

        // Show feedback
        android.widget.Toast
            .makeText(
                this,
                "Download started",
                android.widget.Toast.LENGTH_SHORT,
            ).show()

        // Navigate to downloads screen by creating a deep link intent
        // that JabookApp will handle
        val downloadsIntent = Intent(Intent.ACTION_VIEW, Uri.parse("jabook://downloads"))
        deepLinkIntent = downloadsIntent
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
        logger.d { "Handling jabook deep link - host: $host, path: $path" }

        // Navigation is handled by JabookApp's NavHost which observes deepLinkIntent.
        // This method serves as an interception point for logging or analytics.
    }

    private fun handleExternalAudioIntent(audioUris: List<Uri>) {
        val urisAsPaths = audioUris.map { it.toString() }
        val groupPath = ExternalAudioIntentPolicy.buildExternalGroupPath(audioUris)
        logger.i { "Handling external audio intent: uris=${audioUris.size}, groupPath=$groupPath" }

        // Keep playback path unified through AudioPlayerService + MediaController custom command.
        startService(Intent(this, AudioPlayerService::class.java))

        lifecycleScope.launch {
            var controllerFuture: ListenableFuture<MediaController>? = null
            var controller: MediaController? = null
            try {
                val sessionToken =
                    SessionToken(
                        this@ComposeMainActivity,
                        android.content.ComponentName(this@ComposeMainActivity, AudioPlayerService::class.java),
                    )
                controllerFuture =
                    MediaController
                        .Builder(this@ComposeMainActivity, sessionToken)
                        .buildAsync()
                controller =
                    withContext(Dispatchers.IO) {
                        controllerFuture.get(
                            MediaControllerConstants.DEFAULT_TIMEOUT_SECONDS.toLong(),
                            TimeUnit.SECONDS,
                        )
                    }

                val result =
                    withContext(Dispatchers.IO) {
                        val playlistResultFuture =
                            MediaControllerExtensions.setPlaylist(
                                controller = controller,
                                filePaths = urisAsPaths,
                                initialTrackIndex = 0,
                                initialPosition = 0L,
                                groupPath = groupPath,
                            )
                        playlistResultFuture.get(
                            MediaControllerConstants.DEFAULT_TIMEOUT_SECONDS.toLong(),
                            TimeUnit.SECONDS,
                        )
                    }

                if (result.resultCode == SessionResult.RESULT_SUCCESS) {
                    controller.play()
                    Toast
                        .makeText(
                            this@ComposeMainActivity,
                            "Playing shared audio",
                            Toast.LENGTH_SHORT,
                        ).show()
                } else {
                    logger.e { "Failed to play shared audio: resultCode=${result.resultCode}" }
                    Toast
                        .makeText(
                            this@ComposeMainActivity,
                            "Cannot play this audio",
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            } catch (t: Throwable) {
                logger.e(t) { "Failed to handle external audio intent" }
                Toast
                    .makeText(
                        this@ComposeMainActivity,
                        "Cannot play this audio",
                        Toast.LENGTH_SHORT,
                    ).show()
            } finally {
                controller?.release()
                controllerFuture?.let { MediaController.releaseFuture(it) }
            }
        }
    }
}
