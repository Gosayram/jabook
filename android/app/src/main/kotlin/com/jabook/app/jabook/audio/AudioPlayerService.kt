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

package com.jabook.app.jabook.audio

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.jabook.app.jabook.audio.processors.AudioProcessingSettings
import com.jabook.app.jabook.audio.processors.AudioProcessorFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Native audio player service using Media3 ExoPlayer.
 *
 * This service extends MediaSessionService for proper integration with Android's
 * media controls, Android Auto, Wear OS, and system notifications.
 *
 * Uses Dagger Hilt for dependency injection (ExoPlayer and Cache as singletons).
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class AudioPlayerService : MediaSessionService() {
    @Inject
    lateinit var exoPlayer: ExoPlayer

    @Inject
    lateinit var mediaCache: Cache
    private var mediaSession: MediaSession? = null
    private var notificationManager: NotificationManager? = null
    private var mediaSessionManager: MediaSessionManager? = null
    private var playbackTimer: PlaybackTimer? = null
    private var inactivityTimer: InactivityTimer? = null
    private var currentMetadata: Map<String, String>? = null
    private var embeddedArtworkPath: String? = null // Path to saved embedded artwork
    private var playlistManager: PlaylistManager? = null
    private var playbackController: PlaybackController? = null
    private var notificationHelper: NotificationHelper? = null
    private var positionManager: PositionManager? = null
    private var metadataManager: MetadataManager? = null
    private var playerStateHelper: PlayerStateHelper? = null
    private var unloadManager: UnloadManager? = null

    // Sleep timer manager
    private var sleepTimerManager: SleepTimerManager? = null

    // Book completion flag to prevent auto-resume after book is finished
    private var isBookCompleted = false
    private var lastCompletedTrackIndex: Int = -1 // Store last track index when book is completed

    // Store current playlist state for restoration after player recreation
    private var currentFilePaths: List<String>? = null
    private var savedPlaybackState: SavedPlaybackState? = null

    // Cache for file durations (filePath -> duration in ms)
    // According to best practices: cache duration after getting it from player (primary source)
    // or MediaMetadataRetriever (fallback). This avoids repeated calls and improves performance.
    // This cache is synchronized with database via MethodChannel (Flutter side).
    private val durationCache = mutableMapOf<String, Long>()

    // Callback for getting duration from database (set from Flutter via MethodChannel)
    // This allows PlayerStateHelper to request durations from database when cache miss
    private var getDurationFromDbCallback: ((String) -> Long?)? = null

    /**
     * Sets callback for getting duration from database.
     * This is called from Flutter via MethodChannel to enable database lookup.
     *
     * @param callback Callback that takes file path and returns duration in ms, or null
     */
    fun setGetDurationFromDbCallback(callback: ((String) -> Long?)?) {
        getDurationFromDbCallback = callback
    }

    /**
     * Gets duration for file path.
     * Checks cache first, then database via callback, then returns null.
     *
     * @param filePath Absolute path to the audio file
     * @return Duration in milliseconds, or null if not found
     */
    fun getDurationForFile(filePath: String): Long? {
        // Check cache first (fast path)
        val cached = durationCache[filePath]
        if (cached != null && cached > 0) {
            return cached
        }

        // Cache miss - try database via callback
        val dbDuration = getDurationFromDbCallback?.invoke(filePath)
        if (dbDuration != null && dbDuration > 0) {
            // Cache it for future use
            durationCache[filePath] = dbDuration
            return dbDuration
        }

        return null
    }

    /**
     * Gets cached duration for file path.
     *
     * @param filePath Absolute path to the audio file
     * @return Cached duration in milliseconds, or null if not cached
     */
    fun getCachedDuration(filePath: String): Long? = durationCache[filePath]

    /**
     * Saves duration to cache.
     *
     * @param filePath Absolute path to the audio file
     * @param durationMs Duration in milliseconds
     */
    fun saveDurationToCache(
        filePath: String,
        durationMs: Long,
    ) {
        durationCache[filePath] = durationMs
    }

    // Audio processing settings (default: all disabled except normalization)
    private var audioProcessingSettings: AudioProcessingSettings = AudioProcessingSettings.defaults()

    /**
     * Data class to store playback state before player recreation.
     */
    private data class SavedPlaybackState(
        val currentIndex: Int,
        val currentPosition: Long,
        val isPlaying: Boolean,
    )

    // Custom ExoPlayer with processors (if audio processing is enabled)
    // Note: We keep the singleton exoPlayer for cases without processing
    private var customExoPlayer: ExoPlayer? = null

    private val playerServiceScope = MainScope()

    // Limited dispatcher for MediaItem creation (max 4 parallel tasks)
    // This prevents overwhelming the system with too many concurrent I/O operations
    @OptIn(ExperimentalCoroutinesApi::class)
    private val mediaItemDispatcher = Dispatchers.IO.limitedParallelism(4)

    companion object {
        const val ACTION_EXIT_APP = "com.jabook.app.jabook.audio.EXIT_APP"

        @Volatile
        private var instance: AudioPlayerService? = null

        fun getInstance(): AudioPlayerService? = instance

        /**
         * Gets flavor suffix for non-prod builds.
         * Returns formatted flavor name (capitalized) or empty string for prod.
         */
        private fun getFlavorSuffix(context: Context): String {
            val packageName = context.packageName
            val flavor =
                when {
                    packageName.endsWith(".dev") -> "dev"
                    packageName.endsWith(".stage") -> "stage"
                    packageName.endsWith(".beta") -> "beta"
                    else -> "" // prod or unknown
                }
            // Capitalize first letter for display
            return if (flavor.isEmpty()) "" else flavor.substring(0, 1).uppercase() + flavor.substring(1)
        }
    }

    /**
     * Flag indicating if service is fully initialized and ready to use.
     * Service is ready when MediaSession is created and all components are initialized.
     */
    @Volatile
    private var isFullyInitializedFlag = false

    /**
     * Checks if service is fully initialized and ready to use.
     *
     * @return true if service is ready, false otherwise
     */
    fun isFullyInitialized(): Boolean = isFullyInitializedFlag && mediaSession != null

    /**
     * Gets the MediaSession instance.
     * Used by AudioPlayerMethodHandler to check if service is fully ready.
     */
    fun getMediaSession(): MediaSession? = mediaSession

    override fun onCreate() {
        super.onCreate()
        instance = this

        val onCreateStartTime = System.currentTimeMillis()
        android.util.Log.d("AudioPlayerService", "onCreate started")

        // Initialize NotificationHelper FIRST - it's needed for startForeground()
        notificationHelper = NotificationHelper(this)

        // CRITICAL FIX: Call startForeground() IMMEDIATELY for ALL Android 8.0+ (O and above)
        // Android 8.0+ requires startForeground() within 5 seconds or service will be killed
        // This MUST be called FIRST, before any other operations, to prevent crashes
        // This prevents crash: "Context.startForegroundService() did not then call Service.startForeground()"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Android 8.0+
            try {
                // Create notification BEFORE calling startForeground() (required)
                val tempNotification =
                    notificationHelper?.createMinimalNotification()
                        ?: throw IllegalStateException("NotificationHelper not initialized")
                startForeground(NotificationHelper.NOTIFICATION_ID, tempNotification)
                android.util.Log.d(
                    "AudioPlayerService",
                    "startForeground() called immediately for Android ${Build.VERSION.SDK_INT} (critical fix)",
                )
            } catch (e: Exception) {
                android.util.Log.e(
                    "AudioPlayerService",
                    "Failed to call startForeground() immediately",
                    e,
                )
                // Try fallback notification
                try {
                    val fallbackNotification =
                        notificationHelper?.createFallbackNotification()
                            ?: throw IllegalStateException("NotificationHelper not initialized")
                    startForeground(NotificationHelper.NOTIFICATION_ID, fallbackNotification)
                    android.util.Log.w("AudioPlayerService", "Used fallback notification after exception")
                } catch (e2: Exception) {
                    android.util.Log.e(
                        "AudioPlayerService",
                        "CRITICAL: Failed to call startForeground() with fallback - service may crash",
                        e2,
                    )
                    // Service will likely crash, but we tried our best
                }
            }
        }

        try {
            // Check Hilt initialization before using @Inject fields
            // This prevents crashes if Hilt is not ready
            // CRITICAL: For local files, we don't need cache, so we can proceed even if cache is slow
            try {
                if (!::exoPlayer.isInitialized) {
                    android.util.Log.w("AudioPlayerService", "ExoPlayer not initialized, waiting...")
                    throw IllegalStateException("ExoPlayer not ready")
                }
                // Cache is optional - for local files it's not used, so we don't block on it
                // But we still check it to ensure Hilt is ready
                if (!::mediaCache.isInitialized) {
                    android.util.Log.w("AudioPlayerService", "MediaCache not initialized yet (may be slow), but continuing for local files")
                    // Don't throw - cache is not needed for local files
                }
            } catch (e: UninitializedPropertyAccessException) {
                ErrorHandler.handleGeneralError("AudioPlayerService", e, "Hilt not initialized")
                throw IllegalStateException("Hilt dependencies not ready", e)
            }

            // Validate Android 14+ requirements before initialization (fast check)
            // These checks are lightweight and should not block initialization
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (!ErrorHandler.validateAndroid14Requirements(this)) {
                    android.util.Log.e("AudioPlayerService", "Android 14+ requirements validation failed")
                    throw IllegalStateException("Android 14+ requirements not met")
                }
            }

            // Validate Color OS specific requirements (if applicable) - fast check
            if (ErrorHandler.isColorOS()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    if (!ErrorHandler.validateColorOSRequirements(this)) {
                        android.util.Log.e("AudioPlayerService", "Color OS requirements validation failed")
                        throw IllegalStateException("Color OS requirements not met")
                    }
                }
                android.util.Log.d("AudioPlayerService", "Color OS detected, special handling enabled")
            }

            // Inspired by lissen-android: minimize synchronous operations in onCreate()
            // Hilt dependencies (ExoPlayer and Cache) are injected automatically
            // They are created lazily on first access, not in onCreate()

            // Configure ExoPlayer (already created via Hilt, accessed lazily)
            // This is lightweight - just adding listener and setting flags
            configurePlayer()

            // Create MediaSessionManager with callbacks for play/pause and rewind/forward
            // Note: MediaSession automatically handles play/pause through Player,
            // but we intercept these commands to ensure notification is updated
            mediaSessionManager =
                MediaSessionManager(
                    this,
                    getActivePlayer(),
                    playCallback = {
                        android.util.Log.d("AudioPlayerService", "MediaSession play callback called")
                        play()
                        // Update notification immediately to show pause button
                        notificationManager?.updateNotification()
                    },
                    pauseCallback = {
                        android.util.Log.d("AudioPlayerService", "MediaSession pause callback called")
                        pause()
                        // Update notification immediately to show play button
                        notificationManager?.updateNotification()
                    },
                )
            mediaSessionManager?.setCallbacks(
                rewindCallback = {
                    // Use current rewind duration from MediaSessionManager
                    val duration = mediaSessionManager?.getRewindDuration() ?: 15L
                    rewind(duration.toInt())
                },
                forwardCallback = {
                    // Use current forward duration from MediaSessionManager
                    val duration = mediaSessionManager?.getForwardDuration() ?: 30L
                    forward(duration.toInt())
                },
            )

            // Create MediaSession once in onCreate (inspired by lissen-android)
            // MediaSessionService will use it via onGetSession()
            mediaSession = mediaSessionManager!!.getMediaSession()
            android.util.Log.i("AudioPlayerService", "MediaSession created, session: ${mediaSession != null}")

            // Create notification manager with MediaSession
            // Get skip durations from MediaSessionManager (uses actual settings: book-specific or global)
            // MediaSessionManager is initialized with defaults, but will be updated when settings are applied
            val currentRewindSeconds =
                mediaSessionManager?.getRewindDuration()
                    ?: throw IllegalStateException("MediaSessionManager not initialized")
            val currentForwardSeconds =
                mediaSessionManager?.getForwardDuration()
                    ?: throw IllegalStateException("MediaSessionManager not initialized")

            notificationManager =
                NotificationManager(
                    context = this,
                    player = getActivePlayer(),
                    mediaSession = mediaSession,
                    metadata = currentMetadata,
                    embeddedArtworkPath = embeddedArtworkPath,
                    rewindSeconds = currentRewindSeconds,
                    forwardSeconds = currentForwardSeconds,
                )

            // Update notification with full media controls after MediaSession is created
            // This replaces the minimal notification used for startForeground()
            // Do this asynchronously to avoid blocking onCreate() - notification update can happen in background
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Post notification update to handler to avoid blocking onCreate()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    try {
                        notificationManager?.updateNotification()
                        android.util.Log.d("AudioPlayerService", "Notification updated with media controls")
                    } catch (e: Exception) {
                        android.util.Log.w("AudioPlayerService", "Failed to update notification, using minimal notification", e)
                        // Continue with minimal notification - service is still functional
                    }
                }
            }

            // Initialize playback timer (inspired by lissen-android)
            playbackTimer = PlaybackTimer(this, getActivePlayer())

            // Initialize inactivity timer for automatic resource cleanup
            inactivityTimer =
                InactivityTimer(
                    context = this,
                    player = getActivePlayer(),
                    onTimerExpired = {
                        android.util.Log.i("AudioPlayerService", "Inactivity timer expired, unloading player")
                        // Check if service is still alive before unloading
                        if (instance != null && isFullyInitializedFlag) {
                            unloadPlayerDueToInactivity()
                        } else {
                            android.util.Log.w("AudioPlayerService", "Service already destroyed, skipping unload")
                        }
                    },
                )

            // ExoPlayer manages AudioFocus automatically when handleAudioFocus=true
            // No need for manual AudioFocus management

            // Initialize SleepTimerManager
            sleepTimerManager =
                SleepTimerManager(
                    context = this,
                    packageName = packageName,
                    playerServiceScope = playerServiceScope,
                    getActivePlayer = { getActivePlayer() },
                    sendBroadcast = { sendBroadcast(it) },
                )

            // Restore sleep timer state after service is initialized
            sleepTimerManager?.restoreTimerState()

            // Initialize PlaylistManager
            playlistManager =
                PlaylistManager(
                    context = this,
                    mediaCache = mediaCache,
                    getActivePlayer = { getActivePlayer() },
                    getNotificationManager = { notificationManager },
                    playerServiceScope = playerServiceScope,
                    mediaItemDispatcher = mediaItemDispatcher,
                    getFlavorSuffix = { Companion.getFlavorSuffix(this) },
                )

            // Initialize PlaybackController
            playbackController =
                PlaybackController(
                    getActivePlayer = { getActivePlayer() },
                    playerServiceScope = playerServiceScope,
                    resetInactivityTimer = { inactivityTimer?.resetTimer() },
                )

            // Initialize PositionManager
            positionManager =
                PositionManager(
                    context = this,
                    getActivePlayer = { getActivePlayer() },
                    packageName = packageName,
                    sendBroadcast = { sendBroadcast(it) },
                )

            // Initialize MetadataManager
            metadataManager =
                MetadataManager(
                    context = this,
                    getActivePlayer = { getActivePlayer() },
                    getNotificationManager = { notificationManager },
                    getEmbeddedArtworkPath = { embeddedArtworkPath },
                    setEmbeddedArtworkPath = { embeddedArtworkPath = it },
                    getCurrentMetadata = { currentMetadata },
                    setCurrentMetadata = { currentMetadata = it },
                )

            // Initialize PlayerStateHelper
            playerStateHelper =
                PlayerStateHelper(
                    getActivePlayer = { getActivePlayer() },
                    getDurationCache = { durationCache },
                    getDurationForFile = { filePath -> getDurationForFile(filePath) },
                    getLastCompletedTrackIndex = { lastCompletedTrackIndex },
                    getActualPlaylistSize = { currentFilePaths?.size ?: 0 },
                )

            // Initialize UnloadManager
            unloadManager =
                UnloadManager(
                    context = this,
                    getActivePlayer = { getActivePlayer() },
                    getCustomExoPlayer = { customExoPlayer },
                    releaseCustomExoPlayer = {
                        customExoPlayer?.release()
                        customExoPlayer = null
                    },
                    getMediaSession = { mediaSession },
                    releaseMediaSession = {
                        mediaSession?.release()
                        mediaSession = null
                    },
                    getMediaSessionManager = { mediaSessionManager },
                    releaseMediaSessionManager = {
                        mediaSessionManager?.release()
                        mediaSessionManager = null
                    },
                    getInactivityTimer = { inactivityTimer },
                    releaseInactivityTimer = {
                        inactivityTimer?.release()
                        inactivityTimer = null
                    },
                    getPlaybackTimer = { playbackTimer },
                    releasePlaybackTimer = {
                        playbackTimer?.release()
                        playbackTimer = null
                    },
                    getCurrentMetadata = { currentMetadata },
                    setCurrentMetadata = { currentMetadata = it },
                    getEmbeddedArtworkPath = { embeddedArtworkPath },
                    setEmbeddedArtworkPath = { embeddedArtworkPath = it },
                    saveCurrentPosition = { saveCurrentPosition() },
                    stopForeground = { flags -> stopForeground(flags) },
                    stopSelf = { stopSelf() },
                )

            // Mark service as fully initialized after all components are ready
            // MediaSession is created, so service is ready to use
            isFullyInitializedFlag = true

            val onCreateDuration = System.currentTimeMillis() - onCreateStartTime
            android.util.Log.i(
                "AudioPlayerService",
                "Service onCreate completed successfully in ${onCreateDuration}ms, fully initialized: $isFullyInitializedFlag",
            )
        } catch (e: Exception) {
            ErrorHandler.handleGeneralError("AudioPlayerService", e, "Service initialization failed")
            isFullyInitializedFlag = false
            throw e
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        // Return MediaSession for MediaSessionService
        // MediaSessionService automatically manages foreground service and notifications
        // Inspired by lissen-android: return already created session from onCreate()
        android.util.Log.i(
            "AudioPlayerService",
            "onGetSession() called by: ${controllerInfo.packageName}, mediaSession: ${mediaSession != null}",
        )
        if (mediaSession == null) {
            android.util.Log.w("AudioPlayerService", "MediaSession is null in onGetSession(), creating fallback")
            // Fallback: create session if somehow it wasn't created in onCreate()
            // This should not happen in normal flow, but provides safety
            mediaSession = mediaSessionManager?.getMediaSession()
            android.util.Log.w("AudioPlayerService", "Fallback MediaSession created: ${mediaSession != null}")
        }
        return mediaSession
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)

        // CRITICAL: For ALL Android 8.0+, ensure startForeground() is called
        // This is especially important for problematic devices (Xiaomi, etc.)
        // If onCreate() didn't call it (e.g., service was restarted), call it here
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Android 8.0+
            try {
                // Check if notification manager is ready
                if (notificationManager != null) {
                    // Update notification (this will call startForeground if needed)
                    notificationManager?.updateNotification()
                } else {
                    // Fallback: create minimal notification and call startForeground
                    val tempNotification =
                        notificationHelper?.createMinimalNotification()
                            ?: throw IllegalStateException("NotificationHelper not initialized")
                    startForeground(NotificationHelper.NOTIFICATION_ID, tempNotification)
                    android.util.Log.d(
                        "AudioPlayerService",
                        "startForeground() called in onStartCommand for Android ${Build.VERSION.SDK_INT}",
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w(
                    "AudioPlayerService",
                    "Failed to ensure startForeground() in onStartCommand",
                    e,
                )
                // Try fallback notification
                try {
                    val fallbackNotification =
                        notificationHelper?.createFallbackNotification()
                            ?: throw IllegalStateException("NotificationHelper not initialized")
                    startForeground(NotificationHelper.NOTIFICATION_ID, fallbackNotification)
                    android.util.Log.w(
                        "AudioPlayerService",
                        "Used fallback notification in onStartCommand after exception",
                    )
                } catch (e2: Exception) {
                    android.util.Log.e(
                        "AudioPlayerService",
                        "CRITICAL: Failed to call startForeground() in onStartCommand - service may crash",
                        e2,
                    )
                }
            }
        }

        // Handle actions from notification and timer
        // CRITICAL: Enhanced logging for Play/Pause to diagnose Samsung issues
        val action = intent?.action
        android.util.Log.d(
            "AudioPlayerService",
            "onStartCommand called with action: $action, intent: $intent, flags: $flags, startId: $startId",
        )

        when (action) {
            com.jabook.app.jabook.audio.NotificationManager.ACTION_PLAY -> {
                android.util.Log.i(
                    "AudioPlayerService",
                    "ACTION_PLAY received from notification. Current state: playWhenReady=${getActivePlayer().playWhenReady}, " +
                        "playbackState=${getActivePlayer().playbackState}",
                )
                try {
                    play()
                    android.util.Log.d("AudioPlayerService", "play() called successfully, updating notification")
                    // Immediately update notification to show pause button
                    // This provides instant feedback before player state actually changes
                    notificationManager?.updateNotification()
                } catch (e: Exception) {
                    android.util.Log.e("AudioPlayerService", "Failed to execute ACTION_PLAY", e)
                }
            }
            com.jabook.app.jabook.audio.NotificationManager.ACTION_PAUSE -> {
                android.util.Log.i(
                    "AudioPlayerService",
                    "ACTION_PAUSE received from notification. Current state: playWhenReady=${getActivePlayer().playWhenReady}, " +
                        "playbackState=${getActivePlayer().playbackState}",
                )
                try {
                    pause()
                    android.util.Log.d("AudioPlayerService", "pause() called successfully, updating notification")
                    // Immediately update notification to show play button
                    // This provides instant feedback before player state actually changes
                    notificationManager?.updateNotification()
                } catch (e: Exception) {
                    android.util.Log.e("AudioPlayerService", "Failed to execute ACTION_PAUSE", e)
                }
            }
            com.jabook.app.jabook.audio.NotificationManager.ACTION_NEXT -> {
                android.util.Log.d("AudioPlayerService", "User action detected from notification: NEXT, resetting inactivity timer")
                next()
            }
            com.jabook.app.jabook.audio.NotificationManager.ACTION_PREVIOUS -> {
                android.util.Log.d("AudioPlayerService", "User action detected from notification: PREVIOUS, resetting inactivity timer")
                previous()
            }
            com.jabook.app.jabook.audio.NotificationManager.ACTION_REWIND -> {
                android.util.Log.d("AudioPlayerService", "User action detected from notification: REWIND, resetting inactivity timer")
                val rewindSeconds = mediaSessionManager?.getRewindDuration()?.toInt() ?: 15
                rewind(rewindSeconds)
            }
            com.jabook.app.jabook.audio.NotificationManager.ACTION_FORWARD -> {
                android.util.Log.d("AudioPlayerService", "User action detected from notification: FORWARD, resetting inactivity timer")
                val forwardSeconds = mediaSessionManager?.getForwardDuration()?.toInt() ?: 30
                forward(forwardSeconds)
            }
            com.jabook.app.jabook.audio.NotificationManager.ACTION_STOP -> {
                android.util.Log.d("AudioPlayerService", "User action detected from notification: STOP")
                stopAndCleanup()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            // Handle timer actions (inspired by lissen-android)
            PlaybackTimer.ACTION_TIMER_EXPIRED -> {
                // Timer expired - playback should already be paused by PlaybackTimer
                android.util.Log.d("AudioPlayerService", "Timer expired, playback paused")
            }
            InactivityTimer.ACTION_INACTIVITY_TIMER_EXPIRED -> {
                // Inactivity timer expired - unload player
                android.util.Log.i("AudioPlayerService", "Inactivity timer expired, unloading player")
                unloadPlayerDueToInactivity()
            }
            ACTION_EXIT_APP -> {
                // Sleep timer expired - stop service and exit app
                // Only process if service is fully initialized to avoid stopping during initialization
                android.util.Log.d(
                    "AudioPlayerService",
                    "ACTION_EXIT_APP received: isFullyInitialized=$isFullyInitializedFlag, mediaSession=${mediaSession != null}",
                )
                if (isFullyInitializedFlag && mediaSession != null) {
                    android.util.Log.i("AudioPlayerService", "Exit app requested by sleep timer, service is initialized, proceeding")
                    try {
                        stopAndCleanup()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        // Send broadcast to finish activity
                        val exitIntent =
                            Intent("com.jabook.app.jabook.EXIT_APP").apply {
                                setPackage(packageName) // Set package for explicit broadcast
                            }
                        android.util.Log.d("AudioPlayerService", "Sending EXIT_APP broadcast")
                        sendBroadcast(exitIntent)
                        android.util.Log.i("AudioPlayerService", "EXIT_APP broadcast sent successfully")
                    } catch (e: Exception) {
                        android.util.Log.e("AudioPlayerService", "Error during exit app cleanup", e)
                        // Try to stop service anyway
                        try {
                            stopSelf()
                        } catch (e2: Exception) {
                            android.util.Log.e("AudioPlayerService", "Failed to stop service", e2)
                        }
                    }
                } else {
                    android.util.Log.w(
                        "AudioPlayerService",
                        "Exit app requested but service not initialized yet (isFullyInitialized=$isFullyInitializedFlag, mediaSession=${mediaSession != null}), ignoring to prevent white screen",
                    )
                    // Don't process exit request if service is not ready - this prevents
                    // accidental app exit during initialization
                }
            }
        }

        // Return START_STICKY to restart service if killed by system
        // This is important for problematic devices that kill background services
        return START_STICKY
    }

    /**
     * Starts sleep timer.
     *
     * @param delayInSeconds Timer duration in seconds
     * @param option Timer option (FIXED_DURATION or CURRENT_TRACK)
     */
    fun startTimer(
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
    fun stopTimer() {
        playbackTimer?.stopTimer()
    }

    /**
     * Player event listener instance.
     */
    private var playerListener: PlayerListener? = null

    /**
     * Configures ExoPlayer instance (already created via Hilt).
     *
     * ExoPlayer is provided as singleton via Dagger Hilt MediaModule.
     * LoadControl and AudioAttributes are already configured in MediaModule.
     * This method only adds listener and configures additional settings.
     *
     * Inspired by lissen-android: lightweight configuration, no heavy operations.
     */
    private fun configurePlayer() {
        try {
            // Match lissen-android: just add listener, no additional configuration
            // ExoPlayer is already configured in MediaModule with AudioAttributes
            val activePlayer = getActivePlayer()

            // Create PlayerListener with dependencies
            playerListener =
                PlayerListener(
                    context = this,
                    getActivePlayer = { getActivePlayer() },
                    getNotificationManager = { notificationManager },
                    getIsBookCompleted = { isBookCompleted },
                    setIsBookCompleted = { isBookCompleted = it },
                    getSleepTimerEndOfChapter = { sleepTimerManager?.sleepTimerEndOfChapter ?: false },
                    getSleepTimerEndTime = { sleepTimerManager?.sleepTimerEndTime ?: 0L },
                    cancelSleepTimer = { sleepTimerManager?.cancelSleepTimer() },
                    sendTimerExpiredEvent = { /* Handled by SleepTimerManager */ },
                    saveCurrentPosition = { saveCurrentPosition() },
                    startSleepTimerCheck = { sleepTimerManager?.startSleepTimerCheck() },
                    getEmbeddedArtworkPath = { embeddedArtworkPath },
                    setEmbeddedArtworkPath = { embeddedArtworkPath = it },
                    getCurrentMetadata = { currentMetadata },
                    setLastCompletedTrackIndex = { index -> lastCompletedTrackIndex = index },
                    getLastCompletedTrackIndex = { lastCompletedTrackIndex },
                    getActualPlaylistSize = { currentFilePaths?.size ?: 0 },
                )

            activePlayer.addListener(playerListener!!)

            // Match lissen-android: don't set WakeMode or ScrubbingMode
            // These may interfere with AudioFocus handling

            // Initialize repeat and shuffle modes (lissen-android doesn't set these either, but it's safe)
            activePlayer.repeatMode = Player.REPEAT_MODE_OFF
            activePlayer.shuffleModeEnabled = false

            android.util.Log.d("AudioPlayerService", "ExoPlayer configured (provided via Hilt)")
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to configure ExoPlayer", e)
            throw e
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
    fun configureExoPlayer(settings: AudioProcessingSettings) {
        try {
            // Store settings
            audioProcessingSettings = settings

            // Create processor chain
            val processors = AudioProcessorFactory.createProcessorChain(settings)

            android.util.Log.d(
                "AudioPlayerService",
                "Audio processing settings updated: " +
                    "normalizeVolume=${settings.normalizeVolume}, " +
                    "volumeBoost=${settings.volumeBoostLevel}, " +
                    "drc=${settings.drcLevel}, " +
                    "speechEnhancer=${settings.speechEnhancer}, " +
                    "autoLeveling=${settings.autoVolumeLeveling}, " +
                    "processors=${processors.size}",
            )

            // Save current playback state before recreating player
            val activePlayer = getActivePlayer()
            val wasPlaying = activePlayer.isPlaying
            val currentIndex = activePlayer.currentMediaItemIndex
            val currentPosition = activePlayer.currentPosition
            val hasPlaylist = activePlayer.mediaItemCount > 0

            // Save state if we have a playlist
            if (hasPlaylist && currentFilePaths != null && currentFilePaths!!.isNotEmpty()) {
                savedPlaybackState =
                    SavedPlaybackState(
                        currentIndex = currentIndex,
                        currentPosition = currentPosition,
                        isPlaying = wasPlaying,
                    )
                android.util.Log.d(
                    "AudioPlayerService",
                    "Saved playback state before player recreation: index=$currentIndex, position=$currentPosition, isPlaying=$wasPlaying",
                )
            }

            // If processors are needed, create custom ExoPlayer
            if (processors.isNotEmpty()) {
                // Release old custom player if exists
                customExoPlayer?.release()
                customExoPlayer = null

                // Create new ExoPlayer with processors
                customExoPlayer = MediaModule.createExoPlayerWithProcessors(this, settings)

                // Copy listener from singleton player
                playerListener?.let { customExoPlayer?.addListener(it) }

                android.util.Log.i(
                    "AudioPlayerService",
                    "Created custom ExoPlayer with ${processors.size} AudioProcessors",
                )
            } else {
                // No processors needed, release custom player if exists
                customExoPlayer?.release()
                customExoPlayer = null
                android.util.Log.d("AudioPlayerService", "No processors needed, using singleton ExoPlayer")
            }

            // Update NotificationManager with new player reference
            notificationManager?.updatePlayer(getActivePlayer())
            android.util.Log.d("AudioPlayerService", "Updated NotificationManager with new player reference")

            // Restore playlist and position if we had a playlist before
            if (savedPlaybackState != null && currentFilePaths != null && currentFilePaths!!.isNotEmpty()) {
                val savedState = savedPlaybackState!!
                android.util.Log.d(
                    "AudioPlayerService",
                    "Restoring playlist and position: ${currentFilePaths!!.size} items, index=${savedState.currentIndex}, position=${savedState.currentPosition}",
                )

                // Restore playlist asynchronously
                playerServiceScope.launch {
                    try {
                        playlistManager?.preparePlaybackOptimized(
                            currentFilePaths!!,
                            currentMetadata,
                            savedState.currentIndex,
                        ) ?: throw IllegalStateException("PlaylistManager not initialized")

                        // Wait for player to be ready
                        var attempts = 0
                        while (attempts < 50) {
                            val newPlayer = getActivePlayer()
                            if (newPlayer.playbackState == Player.STATE_READY || newPlayer.playbackState == Player.STATE_BUFFERING) {
                                break
                            }
                            delay(100)
                            attempts++
                        }

                        // Restore position
                        val newPlayer = getActivePlayer()
                        if (newPlayer.mediaItemCount > savedState.currentIndex) {
                            newPlayer.seekTo(savedState.currentIndex, savedState.currentPosition)
                            android.util.Log.d(
                                "AudioPlayerService",
                                "Restored position: index=${savedState.currentIndex}, position=${savedState.currentPosition}",
                            )

                            // Restore playback state
                            if (savedState.isPlaying) {
                                newPlayer.playWhenReady = true
                                android.util.Log.d("AudioPlayerService", "Restored playback: playing")
                            }
                        }

                        // Clear saved state
                        savedPlaybackState = null

                        // Update notification to reflect new player state
                        notificationManager?.updateNotification()
                    } catch (e: Exception) {
                        android.util.Log.e("AudioPlayerService", "Failed to restore playlist after player recreation", e)
                        savedPlaybackState = null
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to configure ExoPlayer with processors", e)
        }
    }

    /**
     * Gets the active ExoPlayer instance (custom with processors or singleton).
     */
    private fun getActivePlayer(): ExoPlayer = customExoPlayer ?: exoPlayer

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
     * @param callback Optional callback to notify when playlist is ready (for Flutter)
     */
    fun setPlaylist(
        filePaths: List<String>,
        metadata: Map<String, String>? = null,
        initialTrackIndex: Int? = null,
        callback: ((Boolean, Exception?) -> Unit)? = null,
    ) {
        // Reset book completion flag when setting new playlist
        isBookCompleted = false
        lastCompletedTrackIndex = -1 // Reset saved index for new book

        // Clear duration cache when setting new playlist
        durationCache.clear()

        // Store file paths for potential restoration after player recreation
        currentFilePaths = filePaths
        currentMetadata = metadata
        android.util.Log.d("AudioPlayerService", "Setting playlist with ${filePaths.size} items, initialTrackIndex=$initialTrackIndex")

        playerServiceScope.launch {
            try {
                playlistManager?.preparePlaybackOptimized(filePaths, metadata, initialTrackIndex)
                    ?: throw IllegalStateException("PlaylistManager not initialized")
                android.util.Log.d("AudioPlayerService", "Playlist prepared successfully")
                withContext(Dispatchers.Main) {
                    callback?.invoke(true, null)
                }
            } catch (e: Exception) {
                android.util.Log.e("AudioPlayerService", "Failed to prepare playback", e)
                ErrorHandler.handleGeneralError("AudioPlayerService", e, "preparePlayback failed")
                withContext(Dispatchers.Main) {
                    callback?.invoke(false, e)
                }
            }
        }
    }

    fun seekToTrackAndPosition(
        trackIndex: Int,
        positionMs: Long,
    ) {
        // Reset book completion flag on manual track switch
        if (isBookCompleted) {
            android.util.Log.i(
                "AudioPlayerService",
                "Manual seekToTrackAndPosition($trackIndex, $positionMs) called after book completion, resetting completion flag",
            )
            isBookCompleted = false
            lastCompletedTrackIndex = -1
        }
        playbackController?.seekToTrackAndPosition(trackIndex, positionMs) ?: run {
            android.util.Log.e("AudioPlayerService", "PlaybackController not initialized")
        }
    }

    fun updateMetadata(metadata: Map<String, String>) =
        metadataManager?.updateMetadata(metadata) ?: run {
            android.util.Log.e("AudioPlayerService", "MetadataManager not initialized")
        }

    fun play() =
        playbackController?.play() ?: run {
            android.util.Log.e("AudioPlayerService", "PlaybackController not initialized")
        }

    fun pause() =
        playbackController?.pause() ?: run {
            android.util.Log.e("AudioPlayerService", "PlaybackController not initialized")
        }

    fun stop() =
        playbackController?.stop() ?: run {
            android.util.Log.e("AudioPlayerService", "PlaybackController not initialized")
        }

    /**
     * Stops playback and releases all resources.
     * Closes notification and stops service.
     *
     * This is a complete cleanup method that should be called when
     * playback is permanently stopped (e.g., from Stop button in notification).
     */
    fun stopAndCleanup() {
        // Clear duration cache to free memory
        durationCache.clear()
        val player = getActivePlayer()
        try {
            android.util.Log.d("AudioPlayerService", "stopAndCleanup() called, stopping player and releasing resources")
            player.stop()
            player.clearMediaItems()
            playbackTimer?.stopTimer()
            inactivityTimer?.stopTimer()

            // Release MediaSession
            mediaSessionManager?.release()
            mediaSession = null

            // Cancel notification
            notificationManager = null

            android.util.Log.d("AudioPlayerService", "Player stopped and resources released")
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to stop and cleanup", e)
            ErrorHandler.handleGeneralError("AudioPlayerService", e, "Stop and cleanup execution")
        }
    }

    private fun saveCurrentPosition() =
        positionManager?.saveCurrentPosition() ?: run {
            android.util.Log.e("AudioPlayerService", "PositionManager not initialized")
        }

    fun seekTo(positionMs: Long) =
        playbackController?.seekTo(positionMs) ?: run {
            android.util.Log.e("AudioPlayerService", "PlaybackController not initialized")
        }

    fun setSpeed(speed: Float) =
        playbackController?.setSpeed(speed) ?: run {
            android.util.Log.e("AudioPlayerService", "PlaybackController not initialized")
        }

    fun setRepeatMode(repeatMode: Int) =
        playbackController?.setRepeatMode(repeatMode) ?: run {
            android.util.Log.e("AudioPlayerService", "PlaybackController not initialized")
        }

    fun getRepeatMode(): Int = playbackController?.getRepeatMode() ?: Player.REPEAT_MODE_OFF

    fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) =
        playbackController?.setShuffleModeEnabled(shuffleModeEnabled) ?: run {
            android.util.Log.e("AudioPlayerService", "PlaybackController not initialized")
        }

    fun getShuffleModeEnabled(): Boolean = playbackController?.getShuffleModeEnabled() ?: false

    /**
     * Sets sleep timer with specified duration in minutes.
     *
     * Inspired by EasyBook implementation: uses absolute end time instead of periodic timer.
     *
     * @param minutes Timer duration in minutes
     */
    fun setSleepTimerMinutes(minutes: Int) {
        sleepTimerManager?.setSleepTimerMinutes(minutes)
    }

    /**
     * Sets sleep timer to expire at end of current chapter.
     *
     * Inspired by EasyBook implementation: uses boolean flag for "end of chapter" mode.
     */
    fun setSleepTimerEndOfChapter() {
        sleepTimerManager?.setSleepTimerEndOfChapter()
    }

    /**
     * Cancels active sleep timer.
     */
    fun cancelSleepTimer() {
        sleepTimerManager?.cancelSleepTimer()
    }

    /**
     * Gets remaining seconds for sleep timer, or null if not active.
     *
     * @return Remaining seconds, or null if timer is not active or set to "end of chapter"
     */
    fun getSleepTimerRemainingSeconds(): Int? = sleepTimerManager?.getSleepTimerRemainingSeconds()

    /**
     * Checks if sleep timer is active.
     *
     * @return true if timer is active (either fixed duration or end of chapter)
     */
    fun isSleepTimerActive(): Boolean = sleepTimerManager?.isSleepTimerActive() ?: false

    fun next() {
        // Reset book completion flag on manual track switch
        if (isBookCompleted) {
            android.util.Log.i("AudioPlayerService", "Manual next() called after book completion, resetting completion flag")
            isBookCompleted = false
            lastCompletedTrackIndex = -1
        }
        playbackController?.next() ?: run {
            android.util.Log.e("AudioPlayerService", "PlaybackController not initialized")
        }
    }

    fun previous() {
        // Reset book completion flag on manual track switch
        if (isBookCompleted) {
            android.util.Log.i("AudioPlayerService", "Manual previous() called after book completion, resetting completion flag")
            isBookCompleted = false
            lastCompletedTrackIndex = -1
        }
        playbackController?.previous() ?: run {
            android.util.Log.e("AudioPlayerService", "PlaybackController not initialized")
        }
    }

    fun seekToTrack(index: Int) {
        // Reset book completion flag on manual track switch
        if (isBookCompleted) {
            android.util.Log.i("AudioPlayerService", "Manual seekToTrack($index) called after book completion, resetting completion flag")
            isBookCompleted = false
            lastCompletedTrackIndex = -1
        }
        playbackController?.seekToTrack(index) ?: run {
            android.util.Log.e("AudioPlayerService", "PlaybackController not initialized")
        }
    }

    fun setPlaybackProgress(
        filePaths: List<String>,
        progressSeconds: Double?,
    ) = positionManager?.setPlaybackProgress(filePaths, progressSeconds) ?: run {
        android.util.Log.e("AudioPlayerService", "PositionManager not initialized")
    }

    fun rewind(seconds: Int = 15) =
        playbackController?.rewind(seconds) ?: run {
            android.util.Log.e("AudioPlayerService", "PlaybackController not initialized")
        }

    fun forward(seconds: Int = 30) =
        playbackController?.forward(seconds) ?: run {
            android.util.Log.e("AudioPlayerService", "PlaybackController not initialized")
        }

    /**
     * Stops playback and releases resources.
     * Closes notification and stops service.
     */
    fun stopAndRelease() {
        val player = getActivePlayer()
        player.stop()
        player.clearMediaItems()
        playbackTimer?.stopTimer()
        inactivityTimer?.stopTimer()

        // Release MediaSession
        mediaSessionManager?.release()
        mediaSession = null

        // Cancel notification
        notificationManager = null

        android.util.Log.d("AudioPlayerService", "Player stopped and resources released")
    }

    /**
     * Updates skip durations for MediaSessionManager.
     *
     * @param rewindSeconds Duration in seconds for rewind action
     * @param forwardSeconds Duration in seconds for forward action
     */
    fun updateSkipDurations(
        rewindSeconds: Int,
        forwardSeconds: Int,
    ) {
        mediaSessionManager?.updateSkipDurations(
            rewindSeconds.toLong(),
            forwardSeconds.toLong(),
        )
        // Update NotificationManager
        notificationManager?.updateSkipDurations(
            rewindSeconds.toLong(),
            forwardSeconds.toLong(),
        )
        android.util.Log.d(
            "AudioPlayerService",
            "Updated skip durations: rewind=${rewindSeconds}s, forward=${forwardSeconds}s",
        )
    }

    fun getCurrentPosition(): Long = playerStateHelper?.getCurrentPosition() ?: 0L

    fun getDuration(): Long = playerStateHelper?.getDuration() ?: 0L

    /**
     * Sets the inactivity timeout in minutes.
     *
     * @param minutes Timeout in minutes (10-180)
     */
    fun setInactivityTimeoutMinutes(minutes: Int) {
        inactivityTimer?.setInactivityTimeoutMinutes(minutes)
        android.util.Log.d(
            "AudioPlayerService",
            "Inactivity timeout set to $minutes minutes",
        )
    }

    fun getPlayerState(): Map<String, Any> = playerStateHelper?.getPlayerState() ?: emptyMap()

    fun getCurrentMediaItemInfo(): Map<String, Any?> = metadataManager?.getCurrentMediaItemInfo() ?: emptyMap()

    fun extractArtworkFromFile(filePath: String): String? = metadataManager?.extractArtworkFromFile(filePath)

    fun getPlaylistInfo(): Map<String, Any> = playerStateHelper?.getPlaylistInfo() ?: emptyMap()

    fun unloadPlayerDueToInactivity() =
        unloadManager?.unloadPlayerDueToInactivity() ?: run {
            android.util.Log.e("AudioPlayerService", "UnloadManager not initialized")
        }

    override fun onDestroy() {
        instance = null
        isFullyInitializedFlag = false

        // ExoPlayer manages AudioFocus automatically, no need to abandon manually

        // Stop sleep timer check
        sleepTimerManager?.stopSleepTimerCheck()

        // Cancel coroutine scope (inspired by lissen-android)
        playerServiceScope.cancel()

        // IMPORTANT: Do NOT call exoPlayer.release() - it's a singleton via Hilt!
        // Hilt automatically manages the lifecycle of ExoPlayer
        // Just clear MediaItems, but don't release the player
        try {
            getActivePlayer().clearMediaItems()
        } catch (e: Exception) {
            android.util.Log.w("AudioPlayerService", "Error clearing media items", e)
        }

        // Release custom player if exists
        customExoPlayer?.release()
        customExoPlayer = null

        // Cleanup other resources
        // NOTE: Do NOT release mediaCache - it's a singleton via Hilt and will be managed by Hilt

        mediaSession?.release()
        mediaSession = null
        mediaSessionManager?.release()
        playbackTimer?.release()
        inactivityTimer?.release()
        super.onDestroy()
    }
}
