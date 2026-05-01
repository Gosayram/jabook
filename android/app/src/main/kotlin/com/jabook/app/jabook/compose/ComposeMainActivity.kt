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

import android.app.PictureInPictureParams
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.jabook.app.jabook.R
import com.jabook.app.jabook.audio.AudioPlayerService
import com.jabook.app.jabook.audio.MediaControllerConstants
import com.jabook.app.jabook.audio.MediaControllerExtensions
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.preferences.SettingsRepository
import com.jabook.app.jabook.compose.data.torrent.MagnetUriValidationPolicy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
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

    @Inject
    public lateinit var settingsRepository: SettingsRepository

    private val logger by lazy { loggerFactory.get("ComposeMainActivity") }

    private var deepLinkIntent by androidx.compose.runtime.mutableStateOf<Intent?>(null)
    private var hasReportedFullyDrawn: Boolean = false
    private var isPlayerScreenVisible: Boolean = false
    private var autoPipEnabled: Boolean = false

    private companion object {
        private val ALLOWED_JABOOK_HOSTS =
            setOf(
                "library",
                "settings",
                "downloads",
                "player",
                "webview",
                "search",
                "favorites",
                "auth",
            )
        private val ALLOWED_JABOOK_PATH_PREFIXES =
            listOf(
                "/library",
                "/settings",
                "/downloads",
                "/player",
                "/webview",
                "/search",
                "/favorites",
                "/auth",
            )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Handle splash screen transition
        // This must be called before super.onCreate()
        installSplashScreen()

        // Enable edge-to-edge display (Android 15+ recommended pattern)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Handle initial intent
        deepLinkIntent = sanitizeNavigableIntent(intent)
        handleIntent(intent)
        handleIntentExtras(intent)
        observeAutoPipSettings()

        setContent {
            val windowSizeClass =
                androidx.compose.material3.windowsizeclass
                    .calculateWindowSizeClass(this)
            JabookApp(
                windowSizeClass = windowSizeClass,
                intent = deepLinkIntent,
                onFirstMeaningfulContentDrawn = {
                    if (!hasReportedFullyDrawn) {
                        reportFullyDrawn()
                        hasReportedFullyDrawn = true
                    }
                },
                onPlayerScreenVisibilityChanged = { isVisible ->
                    isPlayerScreenVisible = isVisible
                },
            )
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        tryEnterPipFromPlayer()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Update state to trigger recomposition in JabookApp
        deepLinkIntent = sanitizeNavigableIntent(intent)
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
                if (isValidMagnetUri(data)) {
                    handleMagnetLink(data)
                } else {
                    logger.w { "Rejected invalid magnet URI: $data" }
                }
            }
            "jabook" -> {
                if (isAllowedJabookDeepLink(data)) {
                    handleJabookDeepLink(data)
                } else {
                    logger.w { "Rejected untrusted jabook deep link: $data" }
                }
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
                getString(R.string.downloadStarted),
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

    private fun observeAutoPipSettings() {
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                settingsRepository.userPreferences
                    .map { it.autoPipEnabled }
                    .collect { enabled ->
                        autoPipEnabled = enabled
                    }
            }
        }
    }

    private fun tryEnterPipFromPlayer() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!autoPipEnabled || !isPlayerScreenVisible || isInPictureInPictureMode) return
        try {
            val params = PictureInPictureParams.Builder().build()
            enterPictureInPictureMode(params)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            logger.w { "Failed to enter Picture-in-Picture: ${e.message}" }
        }
    }

    private fun sanitizeNavigableIntent(intent: Intent?): Intent? {
        val incomingIntent = intent ?: return null
        if (incomingIntent.action != Intent.ACTION_VIEW) {
            return incomingIntent
        }
        val uri = incomingIntent.data ?: return incomingIntent
        return when (uri.scheme) {
            "jabook" -> {
                if (isAllowedJabookDeepLink(uri)) {
                    incomingIntent
                } else {
                    logger.w { "Dropping untrusted jabook navigation intent: $uri" }
                    null
                }
            }
            "magnet" -> {
                if (isValidMagnetUri(uri)) {
                    incomingIntent
                } else {
                    logger.w { "Dropping invalid magnet navigation intent: $uri" }
                    null
                }
            }
            else -> incomingIntent
        }
    }

    private fun isAllowedJabookDeepLink(uri: Uri): Boolean {
        if (uri.scheme != "jabook") return false
        val host = uri.host?.lowercase().orEmpty()
        val path = uri.path.orEmpty()
        val hasAllowedHost = host in ALLOWED_JABOOK_HOSTS
        val hasAllowedPath =
            ALLOWED_JABOOK_PATH_PREFIXES.any { prefix ->
                path.startsWith(prefix, ignoreCase = true)
            }
        return hasAllowedHost || hasAllowedPath
    }

    private fun isValidMagnetUri(uri: Uri): Boolean = MagnetUriValidationPolicy.isValidMagnetUri(uri)

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
                            getString(R.string.playingSharedAudio),
                            Toast.LENGTH_SHORT,
                        ).show()
                } else {
                    logger.e { "Failed to play shared audio: resultCode=${result.resultCode}" }
                    Toast
                        .makeText(
                            this@ComposeMainActivity,
                            getString(R.string.cannotPlaySharedAudio),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            } catch (t: Throwable) {
                logger.e(t) { "Failed to handle external audio intent" }
                Toast
                    .makeText(
                        this@ComposeMainActivity,
                        getString(R.string.cannotPlaySharedAudio),
                        Toast.LENGTH_SHORT,
                    ).show()
            } finally {
                controller?.release()
                controllerFuture?.let { MediaController.releaseFuture(it) }
            }
        }
    }
}
