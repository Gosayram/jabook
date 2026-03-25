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

package com.jabook.app.jabook.audio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import com.jabook.app.jabook.compose.ComposeMainActivity
import com.jabook.app.jabook.util.LogUtils
import com.jabook.app.jabook.utils.capitalizeFirst
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cache
import javax.inject.Inject
import androidx.media.app.NotificationCompat as MediaNotificationCompat

/**
 * Native audio player service using Media3 ExoPlayer.
 *
 * This service extends MediaLibraryService for proper integration with Android's
 * media controls, Android Auto, Wear OS, system notifications, and playback resumption.
 *
 * MediaLibraryService provides:
 * - Automatic notification management (no manual updates needed)
 * - Playback resumption support (onPlaybackResumption callback)
 * - Better integration with Quick Settings media controls
 *
 * Uses Dagger Hilt for dependency injection (ExoPlayer and Cache as singletons).
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
public class AudioPlayerService : MediaLibraryService() {
    @Inject
    public lateinit var exoPlayer: ExoPlayer

    @Inject
    @javax.inject.Named("okhttp")
    public lateinit var mediaCache: okhttp3.Cache

    // Repository for torrent downloads (library content)
    @Inject
    public lateinit var torrentDownloadRepository: com.jabook.app.jabook.compose.data.torrent.TorrentDownloadRepository

    // Media3 cache for streaming (different from network cache)
    @Inject
    public lateinit var media3Cache: androidx.media3.datasource.cache.Cache

    @Inject
    public lateinit var playerPersistenceManager: PlayerPersistenceManager

    // Settings repository for MediaSession synchronization
    @Inject
    public lateinit var settingsRepository: com.jabook.app.jabook.compose.data.preferences.ProtoSettingsRepository

    @Inject
    public lateinit var playbackPositionRepository: com.jabook.app.jabook.audio.data.repository.PlaybackPositionRepository

    @Inject
    public lateinit var audioOutputManager: AudioOutputManager

    @Inject
    public lateinit var playbackEnhancerService: PlaybackEnhancerService

    @Inject
    public lateinit var audioPreferences: com.jabook.app.jabook.audio.data.local.datastore.AudioPreferences

    internal var mediaLibrarySession: MediaLibrarySession? = null

    // Keep mediaSession for backward compatibility during migration
    internal var mediaSession: MediaSession? = null

    // MediaController for internal service use (as in Rhythm)
    // This replaces getInstance() pattern and provides proper Media3 integration
    internal var serviceMediaController: MediaController? = null

    /**
     * Gets the service MediaController for internal use.
     * Returns null if not yet initialized.
     * This replaces getInstance() pattern and provides proper Media3 integration.
     */
    public fun getServiceMediaController(): MediaController? = serviceMediaController

    // Debounced custom layout updates (from Rhythm pattern)
    // Track current custom layout state to avoid unnecessary updates
    private var lastRewindSeconds: Int? = null
    private var lastForwardSeconds: Int? = null

    // Debounce custom layout updates to prevent flickering
    private var updateLayoutJob: kotlinx.coroutines.Job? = null

    // PlayerNotificationManager for direct notification control (androidx.media3.ui)
    // Replaces MediaNotification.Provider which doesn't work with background service warmup
    private var playerNotificationManager: PlayerNotificationManager? = null

    // notificationManager removed - MediaSession handles notifications automatically via AudioPlayerNotificationProvider
    // internal var notificationManager: NotificationManager? = null
    internal var notificationHelper: NotificationHelper? = null
    internal var mediaSessionManager: MediaSessionManager? = null
    internal var playbackTimer: PlaybackTimer? = null
    internal var inactivityTimer: InactivityTimer? = null
    internal var playlistManager: PlaylistManager? = null
    internal var isPlaylistLoading: Boolean
        get() = playlistManager?.isPlaylistLoading ?: false
        set(_) { /* Read-only from service perspective */ }

    // Current metadata delegated to PlaylistManager
    internal var currentMetadata: Map<String, String>?
        get() = playlistManager?.currentMetadata
        set(value) { /* Read-only or handled via PlaylistManager? Service uses it in MetadataManager init */ }

    internal var lifecycleManager: ServiceLifecycleManager? = null
    internal var intentHandler: ServiceIntentHandler? = null
    internal var playerConfigurator: PlayerConfigurator? = null

    internal var embeddedArtworkPath: String? = null // Path to saved embedded artwork
    internal var playbackController: PlaybackController? = null
    internal var positionManager: PositionManager? = null
    internal var metadataManager: MetadataManager? = null

    // Helper for player state
    internal var playerStateHelper: PlayerStateHelper? = null
    internal var unloadManager: UnloadManager? = null

    // Sleep timer manager
    internal var sleepTimerManager: SleepTimerManager? = null

    // Audio visualizer manager
    internal var audioVisualizerManager: AudioVisualizerManager? = null

    // Phone call listener for automatic resume after calls
    internal var phoneCallListener: PhoneCallListener? = null

    // Headset and Media Button handlers (Quick Wins)
    internal var headsetAutoplayHandler: HeadsetAutoplayHandler? = null
    internal var mediaButtonHandler: MediaButtonHandler? = null

    // Track if playback was active before phone call (for auto-resume)
    internal var wasPlayingBeforeCall = false

    // Book completion flag
    internal var isBookCompleted: Boolean
        get() = playlistManager?.isBookCompleted ?: false
        set(value) {
            playlistManager?.isBookCompleted = value
        }

    internal var lastCompletedTrackIndex: Int
        get() = playlistManager?.lastCompletedTrackIndex ?: -1
        set(value) {
            playlistManager?.lastCompletedTrackIndex = value
        }

    // Actual track index from player events (single source of truth)
    // Delegated to PlaylistManager
    internal var actualTrackIndex: Int
        get() = playlistManager?.actualTrackIndex ?: 0
        set(value) {
            playlistManager?.actualTrackIndex = value
        }

    // Track if playlist is currently being loaded to prevent duplicate calls
    // This is now delegated to PlaylistManager
    // internal var isPlaylistLoading = false // Removed as it's now a delegated property

    internal var currentLoadingPlaylist: List<String>?
        get() = playlistManager?.currentLoadingPlaylist
        set(_) { /* Read-only via AudioPlayerService */ }

    // Track when playlist was last loaded
    internal var lastPlaylistLoadTime: Long
        get() = playlistManager?.lastPlaylistLoadTime ?: 0
        set(_) { /* Read-only via AudioPlayerService */ }

    // Periodic position saving designated to PlaybackPositionSaver
    // private var positionSaveJob: kotlinx.coroutines.Job? = null // Removed
    // private var lastPositionSaveTime: Int =  // Removed

    // Store current playlist state for restoration after player recreation
    // Store current playlist state for restoration after player recreation
    internal var currentFilePaths: List<String>?
        get() = playlistManager?.currentFilePaths
        set(_) { /* Read-only via AudioPlayerService - set via SetPlaylist */ }

    private var savedPlaybackState: SavedPlaybackState?
        get() = playlistManager?.savedPlaybackState
        set(value) {
            playlistManager?.savedPlaybackState = value
        }

    // Store current groupPath delegated to PlaylistManager
    internal val currentGroupPath: String?
        get() = playlistManager?.currentGroupPath

    // Cache for file durations (filePath -> duration in ms)
    // According to best practices: cache duration after getting it from player (primary source)
    // or MediaMetadataRetriever (fallback). This avoids repeated calls and improves performance.
    // This cache is synchronized with database via MethodChannel (Flutter side).
    // DurationManager handles caching and database retrieval
    internal val durationManager = DurationManager()

    /**
     * Callback for database duration retrieval
     */
    public fun setGetDurationFromDbCallback(callback: ((String) -> Long?)?) {
        durationManager.setGetDurationFromDbCallback(callback)
    }

    /**
     * Deprecated: Flutter MethodChannel removed.
     */
    public fun setMethodChannel() {
        // No-op: Flutter bridge removed
    }

    /**
     * Gets duration for file path.
     * Checks cache first, then database via callback, then returns null.
     *
     * @param filePath Absolute path to the audio file
     * @return Duration in milliseconds, or null if not found
     */
    public fun getDurationForFile(filePath: String): Long? = durationManager.getDurationForFile(filePath)

    /**
     * Gets cached duration for file path.
     *
     * @param filePath Absolute path to the audio file
     * @return Cached duration in milliseconds, or null if not cached
     */
    public fun getCachedDuration(filePath: String): Long? = durationManager.getCachedDuration(filePath)

    /**
     * Saves duration to cache.
     *
     * @param filePath Absolute path to the audio file
     * @param durationMs Duration in milliseconds
     */
    public fun saveDurationToCache(
        filePath: String,
        durationMs: Long,
    ) {
        durationManager.saveDurationToCache(filePath, durationMs)
    }

    // Audio processing settings
    // internal var audioProcessingSettings = AudioProcessingSettings() // Delegated to PlayerConfigurator

    // Custom ExoPlayer instance (wraps singleton ExoPlayer)
    // internal var customExoPlayer: ExoPlayer? = null // Delegated to PlayerConfigurator
    internal var customExoPlayer: ExoPlayer? = null

    // Crossfade components
    internal var crossFadePlayer: CrossFadePlayer? = null
    internal var crossfadeHandler: CrossfadeHandler? = null

    internal val playerServiceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val foregroundNotificationCoordinator by lazy {
        ForegroundNotificationCoordinator(
            startForegroundCall = { notificationId, notification ->
                startForeground(notificationId, notification)
            },
            logDebug = { message ->
                LogUtils.d("AudioPlayerService", message)
            },
            logWarn = { message, throwable ->
                LogUtils.w("AudioPlayerService", message, throwable)
            },
        )
    }

    // Periodic position saving designated to PlaybackPositionRepository
    private var positionSaveJob: kotlinx.coroutines.Job? = null

    private fun startPeriodicPositionSaving() {
        positionSaveJob?.cancel()
        positionSaveJob =
            playerServiceScope.launch {
                while (coroutineContext[kotlinx.coroutines.Job]?.isActive == true) {
                    kotlinx.coroutines.delay(10000L) // Save every 10 seconds
                    savePositionToRepository()
                }
            }
    }

    private fun stopPeriodicPositionSaving() {
        positionSaveJob?.cancel()
        positionSaveJob = null
    }

    internal fun savePositionToRepository() {
        val player = getActivePlayer()
        val bookId = currentGroupPath
        if (player.mediaItemCount > 0 && !bookId.isNullOrBlank()) {
            playerServiceScope.launch(Dispatchers.IO) {
                playbackPositionRepository.savePosition(
                    bookId = bookId,
                    trackIndex = player.currentMediaItemIndex,
                    position = player.currentPosition,
                )
            }
        }
    }

    // Limited dispatcher for MediaItem creation (max 16 parallel tasks)
    // Increased parallelism for faster loading on modern devices with fast storage
    // Modern devices can handle more concurrent I/O operations efficiently
    @OptIn(ExperimentalCoroutinesApi::class)
    internal val mediaItemDispatcher = Dispatchers.IO.limitedParallelism(16)

    public companion object {
        public const val ACTION_EXIT_APP: String = "com.jabook.app.jabook.audio.EXIT_APP"

        // Playback action constants (migrated from deprecated NotificationManager)
        public const val ACTION_PLAY: String = "com.jabook.app.jabook.audio.PLAY"
        public const val ACTION_PAUSE: String = "com.jabook.app.jabook.audio.PAUSE"
        public const val ACTION_NEXT: String = "com.jabook.app.jabook.audio.NEXT"
        public const val ACTION_PREVIOUS: String = "com.jabook.app.jabook.audio.PREVIOUS"
        public const val ACTION_REWIND: String = "com.jabook.app.jabook.audio.REWIND"
        public const val ACTION_FORWARD: String = "com.jabook.app.jabook.audio.FORWARD"
        public const val ACTION_STOP: String = "com.jabook.app.jabook.audio.STOP"

        @Volatile
        private var instance: AudioPlayerService? = null

        /**
         * @deprecated Use MediaController or getServiceMediaController() instead.
         * This method is kept for backward compatibility during migration.
         */
        @Deprecated(
            "Use MediaController or getServiceMediaController() for proper Media3 integration",
            ReplaceWith("getServiceMediaController()"),
        )
        public fun getInstance(): AudioPlayerService? = instance

        /**
         * Gets flavor suffix for non-prod builds.
         * Returns formatted flavor name (capitalized) or empty string for prod.
         */
        internal fun getFlavorSuffix(context: Context): String {
            val packageName = context.packageName
            val flavor =
                when {
                    packageName.endsWith(".dev") -> "dev"
                    packageName.endsWith(".stage") -> "stage"
                    packageName.endsWith(".beta") -> "beta"
                    else -> "" // prod or unknown
                }
            // Capitalize first letter for display (using utility function)
            return flavor.capitalizeFirst()
        }
    }

    /**
     * Flag indicating if service is fully initialized and ready to use.
     * Service is ready when MediaLibrarySession is created and all components are initialized.
     */
    @Volatile
    internal var isFullyInitializedFlag = false

    // Flag to indicate if "Minimal Notification" mode is enabled
    // If true, artwork loading will be skipped to show a smaller notification
    internal var isMinimalNotification = false

    /**
     * Checks if service is fully initialized and ready to use.
     *
     * @return true if service is ready, false otherwise
     */
    public fun isFullyInitialized(): Boolean = isFullyInitializedFlag && (mediaLibrarySession != null || mediaSession != null)

    /**
     * Gets the MediaLibrarySession instance.
     * Used by AudioPlayerMethodHandler to check if service is fully ready.
     */
    public fun getMediaSession(): MediaSession? = mediaLibrarySession ?: mediaSession

    /**
     * Returns the single top activity. It is used by the notification when the app task is
     * active and an activity is in the fore or background.
     *
     * Tapping the notification then typically should trigger a single top activity. This way, the
     * user navigates to the previous activity when pressing back.
     *
     * Based on Media3 DemoPlaybackService example.
     * Updated to use deep link for direct navigation to PlayerScreen.
     */
    internal fun getSingleTopActivity(): PendingIntent? {
        val immutableFlag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
        return PendingIntent.getActivity(
            this,
            0,
            Intent(this, ComposeMainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                // Use deep link to navigate directly to PlayerScreen
                // This works with Compose Navigation's navDeepLink
                data = android.net.Uri.parse("jabook://player")
            },
            immutableFlag or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * Returns a back stacked session activity that is used by the notification when the service is
     * running standalone as a foreground service. This is typically the case after the app has been
     * dismissed from the recent tasks, or after automatic playback resumption.
     *
     * Typically, a playback activity should be started with a stack of activities underneath. This
     * way, when pressing back, the user doesn't land on the home screen of the device, but on an
     * activity defined in the back stack.
     *
     * Based on Media3 DemoPlaybackService example.
     * Uses TaskStackBuilder to create proper back stack: MainActivity -> (Player Screen via Flutter)
     */
    internal fun getBackStackedActivity(): PendingIntent? {
        val immutableFlag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                0
            }
        return TaskStackBuilder.create(this).run {
            // Add MainActivity to back stack
            addNextIntent(
                Intent(this@AudioPlayerService, ComposeMainActivity::class.java),
            )
            // MainActivity will handle opening player screen via Flutter (open_player flag)
            // Flutter navigation is handled in MainActivity.onNewIntent() and onResume()
            getPendingIntent(0, immutableFlag or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    @OptIn(UnstableApi::class) // MediaSessionService.setListener
    override fun onCreate() {
        // CRITICAL: Use Log.e (ERROR) level - ProGuard won't strip these
        LogUtils.e("JABOOK_SERVICE", "============================================")
        LogUtils.e("JABOOK_SERVICE", "AudioPlayerService.onCreate() START")
        LogUtils.e("JABOOK_SERVICE", "PID: ${android.os.Process.myPid()}, Instance: ${System.identityHashCode(this)}")
        LogUtils.e("JABOOK_SERVICE", "============================================")

        try {
            PlayerPerformanceLogger.start("service_onCreate")
            PlayerPerformanceLogger.log("Service", "onCreate() started")

            // CRITICAL: Clean up existing components if onCreate() is called multiple times
            // Android can call onCreate() multiple times without onDestroy(), causing resource leaks
            cleanupExistingComponents()

            super.onCreate()
            instance = this
            LogUtils.e("JABOOK_SERVICE", "[OK] super.onCreate() completed")

            PlayerPerformanceLogger.log("Service", "super.onCreate() complete")

            // CRITICAL: Start foreground immediately to avoid ANR and timeout issues (following Rhythm pattern)
            // MediaLibraryService will automatically manage notifications later, but we need to start foreground
            // immediately to prevent ForegroundServiceDidNotStartInTimeException
            // Initialize NotificationHelper first (needed for channel creation)
            val helper = NotificationHelper(this)
            notificationHelper = helper
            val initialNotification =
                try {
                    helper.createMinimalNotification()
                } catch (e: Exception) {
                    LogUtils.w("AudioPlayerService", "Failed to create minimal notification, using fallback", e)
                    helper.createFallbackNotification()
                }
            val foregroundStartResult =
                foregroundNotificationCoordinator.startWithFallback(
                    notificationId = NotificationHelper.NOTIFICATION_ID,
                    primaryNotification = initialNotification,
                    fallbackNotificationProvider = { helper.createFallbackNotification() },
                    event = "service_on_create",
                )
            if (foregroundStartResult == ForegroundStartResult.FAILED) {
                LogUtils.e(
                    "JABOOK_SERVICE",
                    "[CRITICAL] Failed to start foreground with both primary and fallback notification",
                )
            } else {
                LogUtils.e("JABOOK_SERVICE", "[OK] startForeground() completed with result=$foregroundStartResult")
            }

            // Set MediaSessionService.Listener for handling foreground service start exceptions
            // This is required for Android 12+ when system doesn't allow foreground service start
            setListener(MediaSessionServiceListener(this))
            LogUtils.e("JABOOK_SERVICE", "[OK] setListener completed")

            PlayerPerformanceLogger.log("Service", "listener set")

            // CRITICAL: Initialize CrossFadePlayer BEFORE AudioPlayerServiceInitializer
            // CrossfadeHandler (created in initializer) requires CrossFadePlayer to be initialized
            LogUtils.e("JABOOK_SERVICE", "Initializing CrossFadePlayer...")
            crossFadePlayer =
                CrossFadePlayer(this) { context ->
                    ExoPlayer
                        .Builder(context)
                        .setRenderersFactory(DefaultRenderersFactory(context))
                        .setWakeMode(C.WAKE_MODE_LOCAL) // CRITICAL: Keep CPU awake during playback
                        .setHandleAudioBecomingNoisy(true)
                        .setAudioAttributes(
                            AudioAttributes
                                .Builder()
                                .setUsage(C.USAGE_MEDIA)
                                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                                .build(),
                            true, // handleAudioFocus=true
                        ).build()
                }
            crossFadePlayer?.onPlayerChanged = { newPlayer ->
                // CRITICAL: Update MediaSession player when crossfade swaps players (following Rhythm pattern)
                // This ensures MediaSessionLegacyStub always has the correct player reference
                try {
                    mediaLibrarySession?.let { session ->
                        session.player = newPlayer
                        LogUtils.d(
                            "AudioPlayerService",
                            "MediaSession player updated after crossfade: ${newPlayer.javaClass.simpleName}",
                        )
                    } ?: LogUtils.w(
                        "AudioPlayerService",
                        "MediaLibrarySession is null, cannot update player after crossfade",
                    )
                } catch (e: Exception) {
                    LogUtils.e("AudioPlayerService", "Error updating MediaSession player after crossfade", e)
                }
            }
            LogUtils.e("JABOOK_SERVICE", "[OK] CrossFadePlayer initialized")

            // Initialize service components using extracted initializer
            // Media3 automatically manages notifications via MediaLibrarySession
            LogUtils.e("JABOOK_SERVICE", "Starting AudioPlayerServiceInitializer...")
            AudioPlayerServiceInitializer(this).initialize()
            LogUtils.e("JABOOK_SERVICE", "[OK] AudioPlayerServiceInitializer completed")

            // Restore playback speed (lissen-android pattern)
            playerServiceScope.launch {
                try {
                    val savedSpeed = audioPreferences.playbackSpeed.first()
                    withContext(Dispatchers.Main) {
                        LogUtils.d("JABOOK_SERVICE", "Restoring playback speed: ${savedSpeed}x")
                        exoPlayer.setPlaybackSpeed(savedSpeed)
                    }
                } catch (e: Exception) {
                    LogUtils.e("JABOOK_SERVICE", "Failed to restore playback speed", e)
                }
            }

            // Set MediaNotificationProvider for MediaLibrarySession (system media player)
            // This ensures system media player notification has priority
            if (mediaLibrarySession != null) {
                setMediaNotificationProvider(AudioPlayerNotificationProvider(this))
                LogUtils.i("AudioPlayerService", "MediaNotificationProvider set for MediaLibrarySession")
            } else {
                LogUtils.w("AudioPlayerService", "MediaLibrarySession is null, cannot set MediaNotificationProvider")
            }

            // Initialize PlayerNotificationManager (androidx.media3.ui) ONLY as fallback
            // This should only be used when MediaLibrarySession is not available
            // CRITICAL: Disable PlayerNotificationManager when MediaLibrarySession is active
            // to prevent duplicate notifications and ensure system media player has priority
            if (mediaLibrarySession == null) {
                LogUtils.w("AudioPlayerService", "MediaLibrarySession not available, using PlayerNotificationManager as fallback")
                setupPlayerNotificationManager()
            } else {
                LogUtils.i(
                    "AudioPlayerService",
                    "MediaLibrarySession active, skipping PlayerNotificationManager to ensure system media player priority",
                )
            }

            // Initialize AudioOutputManager for proximity sensor handling (Speaker/Earpiece switching)
            LogUtils.e("JABOOK_SERVICE", "Setting up AudioOutputManager...")
            setupAudioOutputManager()
            LogUtils.e("JABOOK_SERVICE", "[OK] AudioOutputManager setup completed")

            // Initialize PlaybackEnhancerService for volume boost (LoudnessEnhancer)
            LogUtils.e("JABOOK_SERVICE", "Initializing PlaybackEnhancerService...")
            playbackEnhancerService.initialize()
            LogUtils.e("JABOOK_SERVICE", "[OK] PlaybackEnhancerService initialized")

            // CrossFadePlayer already initialized above (before AudioPlayerServiceInitializer)

            // Initialize AudioVisualizerManager
            LogUtils.e("JABOOK_SERVICE", "Initializing AudioVisualizerManager...")
            audioVisualizerManager = AudioVisualizerManager(this)
            // Visualizer will be enabled when playback starts (requires audio session)
            LogUtils.e("JABOOK_SERVICE", "[OK] AudioVisualizerManager initialized")

            PlayerPerformanceLogger.log("Service", "initialization complete")
            PlayerPerformanceLogger.summary()

            LogUtils.e("JABOOK_SERVICE", "============================================")
            LogUtils.e("JABOOK_SERVICE", "AudioPlayerService.onCreate() COMPLETE")
            LogUtils.e("JABOOK_SERVICE", "============================================")
        } catch (e: Exception) {
            LogUtils.e("JABOOK_SERVICE", "[CRASH] in onCreate()!", e)
            LogUtils.e("JABOOK_SERVICE", "Exception: ${e.message}")
            LogUtils.e("JABOOK_SERVICE", "Stack trace: ${e.stackTraceToString()}")
            throw e // Re-throw to crash properly
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intentHandler?.handleStartCommand(intent, flags, startId) == true) {
            return START_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Starts sleep timer.
     *
     * @param delayInSeconds Timer duration in seconds
     * @param option Timer option (FIXED_DURATION or CURRENT_TRACK)
     */
    public fun startTimer(
        delayInSeconds: Double,
        option: Int = 0,
    ) {
        val timerOption =
            when (option) {
                1 -> PlaybackTimer.TimerOption.CURRENT_TRACK
                else -> PlaybackTimer.TimerOption.FIXED_DURATION
            }
        playbackTimer?.startTimer(delayInSeconds, timerOption)
    }

    /**
     * Stops sleep timer.
     */
    public fun stopTimer() {
        playbackTimer?.stopTimer()
    }

    /**
     * Player event listener instance.
     * Delegated to PlayerConfigurator.
     */
    internal val playerListener: PlayerListener?
        get() = playerConfigurator?.playerListener

    /**
     * Configures ExoPlayer instance (already created via Hilt).
     *
     * ExoPlayer is provided as singleton via Dagger Hilt MediaModule.
     * LoadControl and AudioAttributes are already configured in MediaModule.
     * This method only adds listener and configures additional settings.
     *
     * Inspired by lissen-android: lightweight configuration, no heavy operations.
     */
    internal fun configurePlayer() {
        playerConfigurator?.configurePlayer() ?: run {
            LogUtils.e("AudioPlayerService", "PlayerConfigurator not initialized")
        }
    }

    /**
     * Configures ExoPlayer with AudioProcessors based on settings.
     *
     * In Media3, AudioProcessors must be set during ExoPlayer creation.
     * This method creates a new ExoPlayer instance with processors if needed,
     * or uses the singleton ExoPlayer if no processing is required.
     *
     * @param settings Audio processing settings
     */
    @OptIn(UnstableApi::class)
    public fun configureExoPlayer(settings: com.jabook.app.jabook.audio.processors.AudioProcessingSettings) {
        playerConfigurator?.configureExoPlayer(settings) ?: run {
            LogUtils.e("AudioPlayerService", "PlayerConfigurator not initialized")
        }
    }

    /**
     * Gets the active ExoPlayer instance (custom with processors or singleton).
     */
    internal fun getActivePlayer(): ExoPlayer {
        val settings = playerConfigurator?.audioProcessingSettings
        if (settings?.isCrossfadeEnabled == true) {
            crossFadePlayer?.let {
                return it.getActivePlayer()
            }
        }
        return playerConfigurator?.getActivePlayer(exoPlayer) ?: exoPlayer
    }

    /**
     * Triggers crossfade transition.
     * Called by CrossfadeHandler when condition is met.
     */
    public fun triggerCrossfadeTransition() {
        // Delegate to PlaylistManager to prepare next track on secondary player
        // Then start crossfade
        playerServiceScope.launch {
            val currentPlayer = getActivePlayer()
            val nextSource = playlistManager?.getNextMediaSource(currentPlayer.currentMediaItemIndex)

            if (nextSource != null) {
                withContext(Dispatchers.Main) {
                    crossFadePlayer?.setNextMediaSource(nextSource)
                    crossFadePlayer?.startCrossFade()
                }
            }
        }
    }

    /**
     * Updates the actual track index from onMediaItemTransition events.
     * This is the single source of truth for current track index.
     *
     * @param index The actual track index from ExoPlayer's onMediaItemTransition event
     */
    internal fun updateActualTrackIndex(index: Int) {
        playlistManager?.actualTrackIndex = index
        LogUtils.d("AudioPlayerService", "Updated actualTrackIndex to $index")
    }

    /**
     * Sets playlist from file paths or URLs.
     *
     * Supports both local file paths and HTTP(S) URLs for network streaming.
     * Uses coroutines for async operations (inspired by lissen-android).
     *
     * CRITICAL: This method is asynchronous and uses coroutines to avoid blocking.
     * Flutter should wait for completion via MethodChannel.Result callback.
     *
     * OPTIMIZATION: For fast startup, only the first MediaItem (or saved position track) is created
     * synchronously. Remaining items are added asynchronously in background.
     *
     * @param filePaths List of absolute file paths or HTTP(S) URLs to audio files
     * @param metadata Optional metadata map (title, artist, album, etc.)
     * @param initialTrackIndex Optional track index to load first (for saved position). If null, loads first track.
     * @param initialPosition Optional position in milliseconds to seek to after loading initial track
     * @param groupPath Optional group path for saving playback position (used for fallback saving)
     * @param callback Optional callback to notify when playlist is ready (for Flutter)
     */
    public fun setPlaylist(
        filePaths: List<String>,
        metadata: Map<String, String>? = null,
        initialTrackIndex: Int? = null,
        initialPosition: Long? = null,
        groupPath: String? = null,
        callback: ((Boolean, Exception?) -> Unit)? = null,
    ) {
        playlistManager?.setPlaylist(
            filePaths,
            metadata,
            initialTrackIndex,
            initialPosition,
            groupPath,
            callback,
        ) ?: run {
            LogUtils.e("AudioPlayerService", "PlaylistManager not initialized")
            callback?.invoke(false, IllegalStateException("PlaylistManager not initialized"))
        }
    }

    /**
     * Applies initial position after playlist is loaded.
     * This is called in background to avoid blocking setPlaylist callback.
     *
     * OPTIMIZATION: If the target track is already loaded as the first track (which is the case
     * when initialTrackIndex is provided), we can apply the position immediately without waiting.
     */
    public fun seekToTrackAndPosition(
        trackIndex: Int,
        positionMs: Long,
    ) {
        // Reset book completion flag on manual track switch
        if (isBookCompleted) {
            LogUtils.i(
                "AudioPlayerService",
                "Manual seekToTrackAndPosition($trackIndex, $positionMs) called after book completion, resetting completion flag",
            )
            isBookCompleted = false
            lastCompletedTrackIndex = -1
        }
        playbackController?.seekToTrackAndPosition(trackIndex, positionMs) ?: run {
            LogUtils.e("AudioPlayerService", "PlaybackController not initialized")
        }
    }

    public fun updateMetadata(metadata: Map<String, String>): Unit =
        metadataManager?.updateMetadata(metadata) ?: run {
            LogUtils.e("AudioPlayerService", "MetadataManager not initialized")
        }

    /**
     * Sets notification type (full or minimal).
     *
     * @param isMinimal true for minimal notification (Play/Pause only),
     * false for full notification (all controls)
     */
    public fun setNotificationType() {
        // MediaLibraryService automatically manages notifications based on Player state
        // If we need custom notification types, we should configure MediaButtonPreferences instead
        // notificationManager?.setNotificationType(false)
        // MediaLibraryService automatically updates notification when Player state changes
    }

    public val isPlaying: Boolean
        get() = getActivePlayer().isPlaying

    public fun play() {
        // Reset book completion flag on play (user wants to restart)
        if (isBookCompleted) {
            LogUtils.i(
                "AudioPlayerService",
                "play() called after book completion, resetting completion flag",
            )
            isBookCompleted = false
            lastCompletedTrackIndex = -1
        }

        playbackController?.play() ?: run {
            LogUtils.e("AudioPlayerService", "PlaybackController not initialized")
            return
        }

        // Start listening for phone calls when playback starts
        phoneCallListener?.startListening()

        startPeriodicPositionSaving()
    }

    public fun pause() {
        playbackController?.pause() ?: run {
            LogUtils.e("AudioPlayerService", "PlaybackController not initialized")
            return
        }

        savePositionToRepository()
        // storeCurrentMediaItem()
        stopPeriodicPositionSaving()
    }

    public fun stop() {
        playbackController?.stop() ?: run {
            LogUtils.e("AudioPlayerService", "PlaybackController not initialized")
            return
        }

        // Stop listening for phone calls when playback stops
        phoneCallListener?.stopListening()

        savePositionToRepository()
        // storeCurrentMediaItem()
        stopPeriodicPositionSaving()
    }

    /**
     * Stops playback and releases all resources.
     * Closes notification and stops service.
     *
     * This is a complete cleanup method that should be called when
     * playback is permanently stopped (e.g., from Stop button in notification).
     */
    public fun stopAndCleanup() {
        lifecycleManager?.stopAndCleanup() ?: run {
            LogUtils.e("AudioPlayerService", "ServiceLifecycleManager not initialized for stopAndCleanup")
            // Fallback manual cleanup if needed, or just log error
        }

        headsetAutoplayHandler?.stopListening()
    }

    internal fun saveCurrentPosition() {
        positionManager?.saveCurrentPosition() ?: run {
            LogUtils.e(
                "AudioPlayerService",
                "PositionManager not initialized",
            )
        }
    }

    /**
     * Stores current media item detailed state for persistence.
     *
     * Based on Media3 DemoPlaybackService example.
     */
    @OptIn(UnstableApi::class) // Player.listen, BitmapLoader
    internal fun storeCurrentMediaItem() {
    }

    public fun seekTo(positionMs: Long): Unit =
        playbackController?.seekTo(positionMs) ?: run {
            LogUtils.e("AudioPlayerService", "PlaybackController not initialized")
        }

    public fun setSpeed(speed: Float): Unit =
        playbackController?.setSpeed(speed) ?: run {
            LogUtils.e("AudioPlayerService", "PlaybackController not initialized")
        }

    public fun setRepeatMode(repeatMode: Int): Unit =
        playbackController?.setRepeatMode(repeatMode) ?: run {
            LogUtils.e("AudioPlayerService", "PlaybackController not initialized")
        }

    public fun getRepeatMode(): Int = playbackController?.getRepeatMode() ?: Player.REPEAT_MODE_OFF

    public fun getPlaybackSpeed(): Float = playbackController?.getSpeed() ?: 1.0f

    public fun setShuffleModeEnabled(shuffleModeEnabled: Boolean): Unit =
        playbackController?.setShuffleModeEnabled(shuffleModeEnabled) ?: run {
            LogUtils.e("AudioPlayerService", "PlaybackController not initialized")
        }

    public fun getShuffleModeEnabled(): Boolean = playbackController?.getShuffleModeEnabled() ?: false

    /**
     * Sets sleep timer with specified duration in minutes.
     *
     * Inspired by EasyBook implementation: uses absolute end time instead of periodic timer.
     *
     * @param minutes Timer duration in minutes
     */
    public fun setSleepTimerMinutes(minutes: Int) {
        sleepTimerManager?.setSleepTimerMinutes(minutes)
    }

    /**
     * Sets sleep timer to expire at end of current chapter.
     *
     * Inspired by EasyBook implementation: uses boolean flag for "end of chapter" mode.
     */
    public fun setSleepTimerEndOfChapter() {
        sleepTimerManager?.setSleepTimerEndOfChapter()
    }

    /**
     * Cancels active sleep timer.
     */
    public fun cancelSleepTimer() {
        sleepTimerManager?.cancelSleepTimer()
    }

    /**
     * Gets remaining seconds for sleep timer, or null if not active.
     *
     * @return Remaining seconds, or null if timer is not active or set to "end of chapter"
     */
    public fun getSleepTimerRemainingSeconds(): Int? = sleepTimerManager?.getSleepTimerRemainingSeconds()

    /**
     * Checks if sleep timer is active.
     *
     * @return true if timer is active (either fixed duration or end of chapter)
     */
    public fun isSleepTimerActive(): Boolean = sleepTimerManager?.isSleepTimerActive() ?: false

    /**
     * Checks if sleep timer is set to end of chapter.
     */
    public fun isSleepTimerEndOfChapter(): Boolean = sleepTimerManager?.sleepTimerEndOfChapter == true

    /**
     * Gets the audio session ID from ExoPlayer.
     * Required for audio visualizer to capture audio data.
     */
    public fun getAudioSessionId(): Int = exoPlayer.audioSessionId

    /**
     * Gets the audio visualizer waveform data as a StateFlow.
     */
    public fun getVisualizerWaveformData(): kotlinx.coroutines.flow.StateFlow<FloatArray>? = audioVisualizerManager?.waveformData

    /**
     * Initializes the audio visualizer with the current audio session.
     * Should be called when playback starts.
     */
    public fun initializeVisualizer() {
        val sessionId = exoPlayer.audioSessionId
        if (sessionId != 0) {
            audioVisualizerManager?.initialize(sessionId)
        }
    }

    /**
     * Enables or disables the audio visualizer.
     */
    public fun setVisualizerEnabled(enabled: Boolean) {
        audioVisualizerManager?.setEnabled(enabled)
    }

    public fun next() {
        // Reset book completion flag on manual track switch
        if (isBookCompleted) {
            LogUtils.i(
                "AudioPlayerService",
                "Manual next() called after book completion, resetting completion flag",
            )
            isBookCompleted = false
            lastCompletedTrackIndex = -1
        }
        playbackController?.next() ?: run {
            LogUtils.e("AudioPlayerService", "PlaybackController not initialized")
        }
    }

    public fun previous() {
        // Reset book completion flag on manual track switch
        if (isBookCompleted) {
            LogUtils.i(
                "AudioPlayerService",
                "Manual previous() called after book completion, resetting completion flag",
            )
            isBookCompleted = false
            lastCompletedTrackIndex = -1
        }
        playbackController?.previous() ?: run {
            LogUtils.e("AudioPlayerService", "PlaybackController not initialized")
        }
    }

    public fun seekToTrack(index: Int) {
        // Reset book completion flag on manual track switch
        if (isBookCompleted) {
            LogUtils.i(
                "AudioPlayerService",
                "Manual seekToTrack($index) called after book completion, resetting completion flag",
            )
            isBookCompleted = false
            lastCompletedTrackIndex = -1
        }
        playbackController?.seekToTrack(index) ?: run {
            LogUtils.e("AudioPlayerService", "PlaybackController not initialized")
        }
    }

    public fun setPlaybackProgress(
        filePaths: List<String>,
        progressSeconds: Double?,
    ): Unit =
        positionManager?.setPlaybackProgress(filePaths, progressSeconds) ?: run {
            LogUtils.e("AudioPlayerService", "PositionManager not initialized")
        }

    public fun rewind(seconds: Int = 15): Unit =
        playbackController?.rewind(seconds) ?: run {
            LogUtils.e("AudioPlayerService", "PlaybackController not initialized")
        }

    public fun forward(seconds: Int = 30): Unit =
        playbackController?.forward(seconds) ?: run {
            LogUtils.e("AudioPlayerService", "PlaybackController not initialized")
        }

    /**
     * Stops playback and releases resources.
     * Closes notification and stops service.
     */
    public fun stopAndRelease() {
        val player = getActivePlayer()
        player.stop()
        player.clearMediaItems()
        playbackTimer?.stopTimer()
        inactivityTimer?.stopTimer()

        // Release MediaSession
        mediaSessionManager?.release()
        mediaSession = null

        // Cancel notification
        // notificationManager = null

        LogUtils.d("AudioPlayerService", "Player stopped and resources released")
    }

    /**
     * Updates skip durations for MediaSessionManager.
     *
     * @param rewindSeconds Duration in seconds for rewind action
     * @param forwardSeconds Duration in seconds for forward action
     */
    public fun updateSkipDurations(
        rewindSeconds: Int,
        forwardSeconds: Int,
    ) {
        mediaSessionManager?.updateSkipDurations(
            rewindSeconds.toLong(),
            forwardSeconds.toLong(),
        )
        // Update NotificationManager
        // notificationManager?.updateSkipDurations(
        //     rewindSeconds.toLong(),
        //     forwardSeconds.toLong(),
        // )
        LogUtils.d(
            "AudioPlayerService",
            "Updated skip durations: rewind=${rewindSeconds}s, forward=${forwardSeconds}s",
        )
    }

    /**
     * Updates MediaSession custom layout commands with new durations.
     * Uses debounced updates to prevent flickering (from Rhythm pattern).
     */
    public fun updateMediaSessionCommands(
        rewindSeconds: Int,
        forwardSeconds: Int,
    ) {
        // Use smart update to check if layout actually needs to change
        updateMediaSessionCommandsSmart(rewindSeconds, forwardSeconds)
    }

    /**
     * Smart update that only updates if layout actually changed (from Rhythm pattern).
     * Prevents unnecessary recreations and flickering.
     */
    private fun updateMediaSessionCommandsSmart(
        rewindSeconds: Int,
        forwardSeconds: Int,
    ) {
        mediaSession?.let { session ->
            try {
                // Check if anything actually changed
                if (rewindSeconds == lastRewindSeconds &&
                    forwardSeconds == lastForwardSeconds
                ) {
                    LogUtils.d("AudioPlayerService", "Custom layout state unchanged, skipping update")
                    return
                }

                // Update state tracking
                lastRewindSeconds = rewindSeconds
                lastForwardSeconds = forwardSeconds

                // Use built-in Media3 icons to avoid CustomAction conversion crash
                val rewindCommandButton =
                    CommandButton
                        .Builder(CommandButton.ICON_SKIP_BACK)
                        .setDisplayName("-$rewindSeconds")
                        .setSessionCommand(
                            androidx.media3.session.SessionCommand(
                                AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_REWIND,
                                android.os.Bundle.EMPTY,
                            ),
                        ).build()

                val forwardCommandButton =
                    CommandButton
                        .Builder(CommandButton.ICON_SKIP_FORWARD)
                        .setDisplayName("+$forwardSeconds")
                        .setSessionCommand(
                            androidx.media3.session.SessionCommand(
                                AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_FORWARD,
                                android.os.Bundle.EMPTY,
                            ),
                        ).build()

                session.setCustomLayout(listOf(rewindCommandButton, forwardCommandButton))

                LogUtils.d(
                    "AudioPlayerService",
                    "Smart updated custom layout - Rewind: ${rewindSeconds}s, Forward: ${forwardSeconds}s",
                )
            } catch (e: Exception) {
                LogUtils.e("AudioPlayerService", "Error in smart custom layout update", e)
            }
        }
    }

    /**
     * Schedules a debounced custom layout update (from Rhythm pattern).
     * Prevents flickering when multiple updates happen quickly.
     *
     * @param delayMs Delay in milliseconds before update (default 150ms)
     */
    private fun scheduleCustomLayoutUpdate(
        rewindSeconds: Int,
        forwardSeconds: Int,
        delayMs: Int = 150,
    ) {
        // Cancel any pending update
        updateLayoutJob?.cancel()

        // Schedule a new update with debouncing
        updateLayoutJob =
            playerServiceScope.launch {
                kotlinx.coroutines.delay(delayMs.toLong())
                updateMediaSessionCommandsSmart(rewindSeconds, forwardSeconds)
            }
    }

    /**
     * Forces an immediate custom layout update without debouncing (from Rhythm pattern).
     * Used for initial setup.
     */
    private fun forceCustomLayoutUpdate(
        rewindSeconds: Int,
        forwardSeconds: Int,
    ) {
        playerServiceScope.launch {
            updateMediaSessionCommandsSmart(rewindSeconds, forwardSeconds)
        }
    }

    /**
     * Sets initial CustomLayout for MediaSession (following Rhythm pattern).
     * Called after MediaController initialization to avoid MediaSessionLegacyStub conversion issues.
     * This method sets the initial layout with default rewind/forward durations.
     */
    @OptIn(UnstableApi::class)
    internal fun setInitialCustomLayout() {
        mediaLibrarySession?.let { session ->
            try {
                // Use default durations for initial layout (will be updated when user changes settings)
                val defaultRewindSeconds = 10
                val defaultForwardSeconds = 30

                val rewindCommand =
                    androidx.media3.session.SessionCommand(
                        AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_REWIND,
                        android.os.Bundle.EMPTY,
                    )
                val forwardCommand =
                    androidx.media3.session.SessionCommand(
                        AudioPlayerLibrarySessionCallback.CUSTOM_COMMAND_FORWARD,
                        android.os.Bundle.EMPTY,
                    )

                // Create CommandButtons with built-in Media3 icons
                val rewindButton =
                    CommandButton
                        .Builder(CommandButton.ICON_SKIP_BACK)
                        .setSessionCommand(rewindCommand)
                        .setDisplayName("-$defaultRewindSeconds")
                        .setEnabled(true)
                        .build()

                val forwardButton =
                    CommandButton
                        .Builder(CommandButton.ICON_SKIP_FORWARD)
                        .setSessionCommand(forwardCommand)
                        .setDisplayName("+$defaultForwardSeconds")
                        .setEnabled(true)
                        .build()

                session.setCustomLayout(listOf(rewindButton, forwardButton))

                // Initialize state tracking
                lastRewindSeconds = defaultRewindSeconds
                lastForwardSeconds = defaultForwardSeconds

                LogUtils.d(
                    "AudioPlayerService",
                    "Initial CustomLayout set - Rewind: ${defaultRewindSeconds}s, Forward: ${defaultForwardSeconds}s",
                )
            } catch (e: Exception) {
                LogUtils.e("AudioPlayerService", "Error setting initial CustomLayout", e)
            }
        }
    }

    public fun getCurrentPosition(): Long = playerStateHelper?.getCurrentPosition() ?: 0L

    public fun getDuration(): Long = playerStateHelper?.getDuration() ?: 0L

    /**
     * Sets the inactivity timeout in minutes.
     *
     * @param minutes Timeout in minutes (10-180)
     */
    public fun setInactivityTimeoutMinutes(minutes: Int) {
        inactivityTimer?.setInactivityTimeoutMinutes(minutes)
        LogUtils.d(
            "AudioPlayerService",
            "Inactivity timeout set",
        )
    }

    public fun getPlayerState(): Map<String, Any> = playerStateHelper?.getPlayerState() ?: emptyMap()

    public fun getCurrentMediaItemInfo(): Map<String, Any?> = metadataManager?.getCurrentMediaItemInfo() ?: emptyMap()

    public fun extractArtworkFromFile(filePath: String): String? = metadataManager?.extractArtworkFromFile(filePath)

    public fun getPlaylistInfo(): Map<String, Any> = playerStateHelper?.getPlaylistInfo() ?: emptyMap()

    public fun unloadPlayerDueToInactivity(): Unit =
        unloadManager?.unloadPlayerDueToInactivity() ?: run {
            LogUtils.e("AudioPlayerService", "UnloadManager not initialized")
        }

    // Periodic position saving methods removed (delegated to PlaybackPositionSaver)

    override fun onTaskRemoved(rootIntent: Intent?) {
        lifecycleManager?.onTaskRemoved() ?: super.onTaskRemoved(rootIntent)
    }

    @OptIn(UnstableApi::class)
    private fun setupPlayerNotificationManager() {
        // Guard: Prevent duplicate initialization
        if (playerNotificationManager != null) {
            LogUtils.w("AudioPlayerService", "PlayerNotificationManager already initialized, skipping")
            return
        }

        // CRITICAL: Only use PlayerNotificationManager as fallback when MediaLibrarySession is not available
        // If MediaLibrarySession is active, it should handle notifications via MediaNotificationProvider
        if (mediaLibrarySession != null) {
            LogUtils.w(
                "AudioPlayerService",
                "MediaLibrarySession is active, PlayerNotificationManager should not be used. " +
                    "This may cause duplicate notifications. Disabling PlayerNotificationManager.",
            )
            return
        }

        // CRITICAL: Create notification channel BEFORE PlayerNotificationManager
        // Otherwise Android will crash with "invalid channel for service notification"
        notificationHelper?.ensureNotificationChannel(NotificationHelper.CHANNEL_ID)
            ?: LogUtils.e("AudioPlayerService", "NotificationHelper is null, channel may not be created!")

        playerNotificationManager =
            PlayerNotificationManager
                .Builder(this, NotificationHelper.NOTIFICATION_ID, NotificationHelper.CHANNEL_ID)
                .setMediaDescriptionAdapter(
                    object : PlayerNotificationManager.MediaDescriptionAdapter {
                        override fun getCurrentContentTitle(player: Player): CharSequence {
                            // 1. Prefer player.mediaMetadata (it combines sources)
                            val metadata = player.mediaMetadata
                            val serviceMetadata = this@AudioPlayerService.currentMetadata

                            val rawTitle =
                                metadata.title?.toString()
                                    ?: serviceMetadata?.get("title")
                                    ?: serviceMetadata?.get("trackTitle")
                                    ?: ""

                            // Remove flavor suffix (" - Dev", " - Beta", " - Prod") if present
                            return rawTitle.replace(Regex(" - (Dev|Beta|Prod)$"), "").ifEmpty {
                                this@AudioPlayerService.getString(com.jabook.app.jabook.R.string.app_name)
                            }
                        }

                        override fun createCurrentContentIntent(player: Player): PendingIntent? {
                            val intent =
                                Intent(this@AudioPlayerService, ComposeMainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    putExtra("navigate_to_player", true)
                                    // Add book_id if available for direct navigation
                                    // currentGroupPath usually contains the book ID or path
                                    this@AudioPlayerService.playlistManager?.currentGroupPath?.let { path ->
                                        // Usually path is like "downloads/book_id" or just "book_id"
                                        // For now, pass it as book_id
                                        putExtra("book_id", path.substringAfterLast("/"))
                                    }
                                }
                            return PendingIntent.getActivity(
                                this@AudioPlayerService,
                                0,
                                intent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                            )
                        }

                        override fun getCurrentContentText(player: Player): CharSequence? {
                            // ARTIST
                            val artist = player.mediaMetadata.artist?.toString()
                            val serviceMetadata = this@AudioPlayerService.currentMetadata
                            return artist ?: serviceMetadata?.get("artist") ?: serviceMetadata?.get("author")
                        }

                        override fun getCurrentSubText(player: Player): CharSequence? {
                            // ALBUM / BOOK TITLE
                            val album = player.mediaMetadata.albumTitle?.toString()
                            val serviceMetadata = this@AudioPlayerService.currentMetadata
                            return album ?: serviceMetadata?.get("album") ?: serviceMetadata?.get("bookTitle")
                        }

                        override fun getCurrentLargeIcon(
                            player: Player,
                            callback: PlayerNotificationManager.BitmapCallback,
                        ): android.graphics.Bitmap? {
                            player.mediaMetadata.artworkUri?.let { artworkUri ->
                                com.bumptech.glide.Glide
                                    .with(this@AudioPlayerService)
                                    .asBitmap()
                                    .load(artworkUri)
                                    .into(
                                        object : com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                                            override fun onResourceReady(
                                                resource: android.graphics.Bitmap,
                                                transition: com.bumptech.glide.request.transition.Transition<in android.graphics.Bitmap>?,
                                            ) {
                                                callback.onBitmap(resource)
                                            }

                                            override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {}
                                        },
                                    )
                            }
                            return null
                        }
                    },
                ).setNotificationListener(
                    object : PlayerNotificationManager.NotificationListener {
                        override fun onNotificationCancelled(
                            notificationId: Int,
                            dismissedByUser: Boolean,
                        ) {
                            // USER REQUEST: Prevent notification dismissal by swipe
                            // Always restore notification, never stop service when dismissed
                            // This ensures player continues working even if user accidentally swipes notification
                            LogUtils.d(
                                "AudioPlayerService",
                                "Notification cancelled (dismissedByUser=$dismissedByUser), restoring notification",
                            )

                            // Restore notification immediately by invalidating PlayerNotificationManager
                            // This will trigger onNotificationPosted again
                            playerNotificationManager?.invalidate()

                            // Don't stop service - keep it running
                            // User can only stop playback through app UI or notification controls
                        }

                        override fun onNotificationPosted(
                            notificationId: Int,
                            notification: android.app.Notification,
                            ongoing: Boolean,
                        ) {
                            // CRITICAL FIX: ALWAYS stay in foreground, even when paused
                            // This keeps notification visible like quality music apps (Spotify, YouTube Music)
                            // Previously: stopForeground(DETACH) when ongoing==false (paused) → notification disappeared
                            // Now: Always startForeground → notification persists
                            LogUtils.d(
                                "AudioPlayerService",
                                "onNotificationPosted: ongoing=$ongoing, staying in foreground",
                            )

                            // USER REQUEST: Make notification non-dismissible by swipe
                            // Create new notification with ongoing flag using NotificationCompat
                            // Copy properties from original notification
                            val nonDismissibleNotification =
                                NotificationCompat
                                    .Builder(this@AudioPlayerService, NotificationHelper.CHANNEL_ID)
                                    .apply {
                                        // Copy essential properties from original notification
                                        val title = NotificationCompat.getContentTitle(notification)
                                        val text = NotificationCompat.getContentText(notification)
                                        if (title != null) setContentTitle(title)
                                        if (text != null) setContentText(text)

                                        // Get small icon from original notification
                                        // notification.smallIcon is Icon, need to extract resource ID
                                        val smallIconResId =
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                try {
                                                    val icon = notification.getSmallIcon()
                                                    if (icon != null) {
                                                        val iconType = icon.type
                                                        if (iconType == android.graphics.drawable.Icon.TYPE_RESOURCE) {
                                                            // Use reflection or IconCompat to get resource ID
                                                            // For API 23+, we can use IconCompat
                                                            androidx.core.graphics.drawable.IconCompat
                                                                .createFromIcon(icon)
                                                                .resId
                                                        } else {
                                                            com.jabook.app.jabook.R.drawable.ic_notification_logo
                                                        }
                                                    } else {
                                                        com.jabook.app.jabook.R.drawable.ic_notification_logo
                                                    }
                                                } catch (e: Exception) {
                                                    com.jabook.app.jabook.R.drawable.ic_notification_logo
                                                }
                                            } else {
                                                com.jabook.app.jabook.R.drawable.ic_notification_logo
                                            }
                                        setSmallIcon(smallIconResId)

                                        // Get large icon from original notification
                                        val largeIcon = notification.getLargeIcon()
                                        if (largeIcon != null) {
                                            try {
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                    if (largeIcon.type == android.graphics.drawable.Icon.TYPE_BITMAP) {
                                                        val bitmap =
                                                            largeIcon.loadDrawable(this@AudioPlayerService)?.let { drawable ->
                                                                if (drawable is android.graphics.drawable.BitmapDrawable) {
                                                                    drawable.bitmap
                                                                } else {
                                                                    null
                                                                }
                                                            }
                                                        if (bitmap != null) {
                                                            setLargeIcon(bitmap)
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                LogUtils.w("AudioPlayerService", "Failed to get large icon", e)
                                            }
                                        }

                                        setContentIntent(notification.contentIntent)
                                        setDeleteIntent(null) // Remove delete intent to prevent swipe dismissal

                                        // Copy actions (Play/Pause, Next, Previous)
                                        // Convert android.app.Notification.Action to NotificationCompat.Action
                                        notification.actions?.forEach { action ->
                                            // Try to extract resource ID from action icon
                                            // If extraction fails, use fallback icon
                                            val actionIconResId =
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                    try {
                                                        // Use getIcon() instead of deprecated icon field
                                                        val icon = action.getIcon()
                                                        // Use IconCompat to extract resource ID
                                                        val iconCompat =
                                                            androidx.core.graphics.drawable.IconCompat
                                                                .createFromIcon(icon)
                                                        if (iconCompat.type ==
                                                            androidx.core.graphics.drawable.IconCompat.TYPE_RESOURCE
                                                        ) {
                                                            iconCompat.resId
                                                        } else {
                                                            android.R.drawable.ic_media_play
                                                        }
                                                    } catch (e: Exception) {
                                                        android.R.drawable.ic_media_play
                                                    }
                                                } else {
                                                    android.R.drawable.ic_media_play
                                                }
                                            // Create NotificationCompat.Action with resource ID
                                            addAction(
                                                NotificationCompat.Action(
                                                    actionIconResId,
                                                    action.title,
                                                    action.actionIntent,
                                                ),
                                            )
                                        }

                                        // Copy MediaStyle if present
                                        // Use string keys directly as constants may not be available
                                        val extras = notification.extras
                                        if (extras != null) {
                                            val mediaSessionKey = "android.mediaSession"
                                            val compactActionsKey = "android.media.compactActions"
                                            if (extras.containsKey(mediaSessionKey)) {
                                                // Use getParcelable with type parameter for API 33+
                                                val mediaSessionToken =
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                        extras.getParcelable(
                                                            mediaSessionKey,
                                                            android.os.Parcelable::class.java,
                                                        )
                                                    } else {
                                                        @Suppress("DEPRECATION")
                                                        extras.getParcelable<android.os.Parcelable>(mediaSessionKey)
                                                    }
                                                val compactActions = extras.getIntArray(compactActionsKey) ?: intArrayOf()
                                                setStyle(
                                                    MediaNotificationCompat
                                                        .MediaStyle()
                                                        .setShowActionsInCompactView(*compactActions)
                                                        .setMediaSession(
                                                            mediaSessionToken as? android.support.v4.media.session.MediaSessionCompat.Token,
                                                        ),
                                                )
                                            }
                                        }

                                        // CRITICAL: Set ongoing flag to prevent swipe dismissal
                                        setOngoing(true)
                                        setAutoCancel(false)
                                        priority = NotificationCompat.PRIORITY_LOW
                                        setShowWhen(false)
                                        setOnlyAlertOnce(true)
                                    }.build()

                            foregroundNotificationCoordinator.startWithFallback(
                                notificationId = notificationId,
                                primaryNotification = nonDismissibleNotification,
                                fallbackNotificationProvider = {
                                    notificationHelper?.createFallbackNotification()
                                        ?: NotificationHelper(this@AudioPlayerService).createFallbackNotification()
                                },
                                event = "player_notification_posted",
                            )
                        }
                    },
                ).setSmallIconResourceId(com.jabook.app.jabook.R.drawable.ic_notification_logo)
                .build()

        // listener to force refresh notification when metadata changes
        // CRITICAL: Debounce notification updates to prevent spam
        // Events can fire multiple times rapidly (e.g., onMediaItemTransition + onMediaMetadataChanged)
        var notificationUpdateJob: kotlinx.coroutines.Job? = null
        val debounceDelayMs = 150L // Wait 150ms before updating notification (balanced for responsiveness)

        exoPlayer.addListener(
            object : Player.Listener {
                override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                    // Debounce notification update
                    notificationUpdateJob?.cancel()
                    val service = this@AudioPlayerService
                    notificationUpdateJob =
                        service.playerServiceScope.launch {
                            kotlinx.coroutines.delay(debounceDelayMs)
                            LogUtils.d("PlayerNotification", "onMediaMetadataChanged: invalidating notification (debounced)")
                            service.playerNotificationManager?.invalidate()
                        }
                }

                override fun onMediaItemTransition(
                    mediaItem: androidx.media3.common.MediaItem?,
                    reason: Int,
                ) {
                    // Debounce notification update
                    notificationUpdateJob?.cancel()
                    val service = this@AudioPlayerService
                    notificationUpdateJob =
                        service.playerServiceScope.launch {
                            kotlinx.coroutines.delay(debounceDelayMs)
                            LogUtils.d("PlayerNotification", "onMediaItemTransition: invalidating notification (debounced)")
                            service.playerNotificationManager?.invalidate()
                        }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        // Immediate update for READY state (important for initial load)
                        LogUtils.d("PlayerNotification", "onPlaybackStateChanged: READY, invalidating immediately")
                        this@AudioPlayerService.playerNotificationManager?.invalidate()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    // Immediate update for play/pause (user expects instant feedback)
                    LogUtils.d("PlayerNotification", "onIsPlayingChanged: $isPlaying, invalidating immediately")
                    this@AudioPlayerService.playerNotificationManager?.invalidate()
                }
            },
        )

        playerNotificationManager?.setPlayer(exoPlayer)
        mediaLibrarySession?.let { playerNotificationManager?.setMediaSessionToken(it.platformToken) }
        playerNotificationManager?.setUseNextAction(true)
        playerNotificationManager?.setUsePreviousAction(true)
        playerNotificationManager?.setUsePlayPauseActions(true)
        playerNotificationManager?.setUseStopAction(false)

        // CRITICAL: Force immediate invalidate to ensure startForeground() is called within 5 seconds
        // This prevents ForegroundServiceDidNotStartInTimeException crash
        LogUtils.d("PlayerNotification", "Forcing immediate invalidate for foreground state")
        playerNotificationManager?.invalidate()
    }

    /**
     * Sets up the AudioOutputManager to handle proximity sensor switching.
     * Automatically monitors playback state to enable/disable sensor.
     */
    private fun setupAudioOutputManager() {
        // Initial state check
        if (exoPlayer.isPlaying) {
            audioOutputManager.startMonitoring()
        }

        exoPlayer.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        audioOutputManager.startMonitoring()
                    } else {
                        audioOutputManager.stopMonitoring()
                    }
                }
            },
        )
    }

    /**
     * Cleans up existing components before reinitialization.
     * Called at the start of onCreate() to prevent resource leaks when Android
     * calls onCreate() multiple times without onDestroy().
     */
    private fun cleanupExistingComponents() {
        // Only cleanup if components already exist (onCreate called multiple times)
        if (mediaLibrarySession != null || serviceMediaController != null || crossFadePlayer != null) {
            LogUtils.w(
                "AudioPlayerService",
                "onCreate() called multiple times, cleaning up existing components",
            )

            // Release MediaController
            serviceMediaController?.release()
            serviceMediaController = null

            // Release MediaLibrarySession
            mediaLibrarySession?.release()
            mediaLibrarySession = null
            mediaSession = null

            // Release CrossFadePlayer
            crossFadePlayer?.release()
            crossFadePlayer = null

            // Release MediaSessionManager
            mediaSessionManager?.release()
            mediaSessionManager = null

            // Stop and release other components
            playerNotificationManager?.setPlayer(null)
            playerNotificationManager = null

            audioOutputManager.stopMonitoring()

            playbackEnhancerService.release()

            sleepTimerManager?.release()
            sleepTimerManager = null

            inactivityTimer?.release()
            inactivityTimer = null

            playbackTimer?.release()
            playbackTimer = null

            audioVisualizerManager?.release()
            audioVisualizerManager = null

            phoneCallListener?.stopListening()
            phoneCallListener = null

            headsetAutoplayHandler?.stopListening()
            headsetAutoplayHandler = null

            // Cancel jobs
            updateLayoutJob?.cancel()
            updateLayoutJob = null

            // Reset initialization flag
            isFullyInitializedFlag = false

            LogUtils.i("AudioPlayerService", "Existing components cleaned up")
        }
    }

    override fun onDestroy() {
        // Clean up PlayerNotificationManager
        playerNotificationManager?.setPlayer(null)
        playerNotificationManager = null

        // Stop proximity monitoring
        audioOutputManager.stopMonitoring()

        // Release PlaybackEnhancerService resources
        playbackEnhancerService.release()

        // Release SleepTimerManager (listeners/sensors)
        sleepTimerManager?.release()
        sleepTimerManager = null

        // Release InactivityTimer
        inactivityTimer?.release()
        inactivityTimer = null

        // Release PlaybackTimer
        playbackTimer?.release()
        playbackTimer = null

        // Release audio visualizer
        audioVisualizerManager?.release()
        audioVisualizerManager = null

        // Stop listening for phone calls
        phoneCallListener?.stopListening()
        phoneCallListener = null

        // Release service MediaController
        serviceMediaController?.release()
        serviceMediaController = null

        // Cancel debounced layout updates
        updateLayoutJob?.cancel()
        updateLayoutJob = null

        // Delegate to lifecycle manager
        lifecycleManager?.onDestroy()
        super.onDestroy()
    }

    /**
     * This method is only required to be implemented on Android 12 or above when an attempt is made
     * by a media controller to resume playback when the MediaSessionService is in the background.
     *
     * This can happen when:
     * - Notification permission is not granted (Android 13+)
     */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaLibrarySession
}
