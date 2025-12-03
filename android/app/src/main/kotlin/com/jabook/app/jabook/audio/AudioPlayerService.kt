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

import android.app.Notification
import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.jabook.app.jabook.MainActivity
import com.jabook.app.jabook.audio.processors.AudioProcessingSettings
import com.jabook.app.jabook.audio.processors.AudioProcessorFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import javax.inject.Inject
import android.app.NotificationManager as AndroidNotificationManager

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

    // Store current playlist state for restoration after player recreation
    private var currentFilePaths: List<String>? = null
    private var savedPlaybackState: SavedPlaybackState? = null

    // Cache for file durations (filePath -> duration in ms)
    // This avoids repeated MediaMetadataRetriever calls which can be slow
    private val durationCache = mutableMapOf<String, Long>()

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
    private val mediaItemDispatcher = Dispatchers.IO.limitedParallelism(4)

    // Manual AudioFocus management
    private var audioManager: AudioManager? = null
    private var audioFocusListener: AudioManager.OnAudioFocusChangeListener? = null
    private var audioFocusRequest: android.media.AudioFocusRequest? = null
    private var hasAudioFocus = false

    companion object {
        private const val NOTIFICATION_ID = 1

        /**
         * Action for saving position before unload.
         * This broadcast will be handled by MainActivity or AudioPlayerMethodHandler
         * to trigger position saving through MethodChannel.
         */
        const val ACTION_SAVE_POSITION_BEFORE_UNLOAD = "com.jabook.app.jabook.audio.SAVE_POSITION_BEFORE_UNLOAD"
        const val ACTION_EXIT_APP = "com.jabook.app.jabook.audio.EXIT_APP"
        const val EXTRA_TRACK_INDEX = "trackIndex"
        const val EXTRA_POSITION_MS = "positionMs"

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

    /**
     * Creates a minimal notification for startForeground() call.
     * This is required for Android 8.0+ to prevent crashes.
     * The notification will be updated later with full media controls.
     *
     * @return Minimal notification for foreground service
     */
    private fun createMinimalNotification(): Notification {
        // Create notification channel if not exists
        val channelId = "jabook_audio_playback"
        ensureNotificationChannel(channelId)

        val intent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        return NotificationCompat
            .Builder(this, channelId)
            .setContentTitle("JaBook Audio")
            .setContentText("Initializing audio player...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Creates a basic fallback notification when createMinimalNotification() fails.
     * This is used as last resort to prevent service crash.
     *
     * @return Basic fallback notification
     */
    private fun createFallbackNotification(): Notification {
        val channelId = "jabook_audio_playback"
        ensureNotificationChannel(channelId)

        return NotificationCompat
            .Builder(this, channelId)
            .setContentTitle("JaBook Audio")
            .setContentText("Initializing...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Ensures notification channel exists. Creates it if it doesn't exist.
     *
     * @param channelId Channel ID to ensure exists
     */
    private fun ensureNotificationChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel =
                    NotificationChannel(
                        channelId,
                        "JaBook Audio Playback",
                        AndroidNotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = "Audio playback controls"
                        setShowBadge(false)
                    }
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
                notificationManager.createNotificationChannel(channel)
            } catch (e: Exception) {
                android.util.Log.e("AudioPlayerService", "Failed to create notification channel", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        val onCreateStartTime = System.currentTimeMillis()
        android.util.Log.d("AudioPlayerService", "onCreate started")

        // CRITICAL FIX: Call startForeground() IMMEDIATELY for ALL Android 8.0+ (O and above)
        // Android 8.0+ requires startForeground() within 5 seconds or service will be killed
        // This MUST be called FIRST, before any other operations, to prevent crashes
        // This prevents crash: "Context.startForegroundService() did not then call Service.startForeground()"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Android 8.0+
            try {
                // Create notification BEFORE calling startForeground() (required)
                val tempNotification = createMinimalNotification()

                // Verify notification is not null before starting foreground
                if (tempNotification != null) {
                    startForeground(NOTIFICATION_ID, tempNotification)
                    android.util.Log.d(
                        "AudioPlayerService",
                        "startForeground() called immediately for Android ${Build.VERSION.SDK_INT} (critical fix)",
                    )
                } else {
                    android.util.Log.e(
                        "AudioPlayerService",
                        "Failed to create minimal notification for startForeground()",
                    )
                    // Try to create a basic notification as fallback
                    try {
                        val fallbackNotification = createFallbackNotification()
                        startForeground(NOTIFICATION_ID, fallbackNotification)
                        android.util.Log.w("AudioPlayerService", "Used fallback notification for startForeground()")
                    } catch (e: Exception) {
                        android.util.Log.e(
                            "AudioPlayerService",
                            "Failed to call startForeground() with fallback notification",
                            e,
                        )
                        // Service will likely crash, but we tried our best
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(
                    "AudioPlayerService",
                    "Failed to call startForeground() immediately",
                    e,
                )
                // Try fallback notification
                try {
                    val fallbackNotification = createFallbackNotification()
                    startForeground(NOTIFICATION_ID, fallbackNotification)
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
                    val tempNotification = createMinimalNotification()
                    if (tempNotification != null) {
                        startForeground(NOTIFICATION_ID, tempNotification)
                        android.util.Log.d(
                            "AudioPlayerService",
                            "startForeground() called in onStartCommand for Android ${Build.VERSION.SDK_INT}",
                        )
                    } else {
                        // Last resort: create basic fallback notification
                        try {
                            val fallbackNotification = createFallbackNotification()
                            startForeground(NOTIFICATION_ID, fallbackNotification)
                            android.util.Log.w(
                                "AudioPlayerService",
                                "Used fallback notification in onStartCommand for Android ${Build.VERSION.SDK_INT}",
                            )
                        } catch (e2: Exception) {
                            android.util.Log.e(
                                "AudioPlayerService",
                                "Failed to call startForeground() in onStartCommand even with fallback",
                                e2,
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w(
                    "AudioPlayerService",
                    "Failed to ensure startForeground() in onStartCommand",
                    e,
                )
                // Try fallback notification
                try {
                    val fallbackNotification = createFallbackNotification()
                    startForeground(NOTIFICATION_ID, fallbackNotification)
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
            activePlayer.addListener(playerListener)

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
                customExoPlayer?.addListener(playerListener)

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
                        preparePlaybackOptimized(
                            currentFilePaths!!,
                            currentMetadata,
                            savedState.currentIndex,
                        )

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
        // Clear duration cache when setting new playlist
        durationCache.clear()

        // Store file paths for potential restoration after player recreation
        currentFilePaths = filePaths
        currentMetadata = metadata
        android.util.Log.d("AudioPlayerService", "Setting playlist with ${filePaths.size} items, initialTrackIndex=$initialTrackIndex")

        playerServiceScope.launch {
            try {
                preparePlaybackOptimized(filePaths, metadata, initialTrackIndex)
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

    /**
     * Prepares playback asynchronously with optimized lazy loading.
     *
     * CRITICAL OPTIMIZATION: Only creates the first MediaItem (or saved position track) synchronously.
     * Remaining items are added asynchronously in background to avoid blocking startup.
     * This dramatically speeds up player initialization, especially for large playlists.
     *
     * @param filePaths List of file paths or URLs
     * @param metadata Optional metadata
     * @param initialTrackIndex Optional track index to load first (for saved position). If null, loads first track (index 0).
     */
    @OptIn(UnstableApi::class)
    private suspend fun preparePlaybackOptimized(
        filePaths: List<String>,
        metadata: Map<String, String>?,
        initialTrackIndex: Int? = null,
    ) = withContext(Dispatchers.IO) {
        try {
            val dataSourceFactory = MediaDataSourceFactory(this@AudioPlayerService, mediaCache)

            // Determine which track to load first (saved position or first track)
            val firstTrackIndex = initialTrackIndex?.coerceIn(0, filePaths.size - 1) ?: 0
            android.util.Log.d("AudioPlayerService", "Loading first track: index=$firstTrackIndex (total=${filePaths.size})")

            // CRITICAL: Create only the first MediaSource synchronously for fast startup
            // This allows player to start immediately while other tracks load in background
            val firstMediaSource =
                createMediaSourceForIndex(
                    filePaths,
                    firstTrackIndex,
                    metadata,
                    dataSourceFactory,
                )

            // Set first MediaSource and prepare player immediately
            withContext(Dispatchers.Main) {
                val activePlayer = getActivePlayer()
                activePlayer.playWhenReady = false

                // Clear any existing items first
                activePlayer.clearMediaItems()

                // Add first item and prepare - this allows immediate playback
                activePlayer.addMediaSource(firstMediaSource)
                activePlayer.prepare()

                // Seek to saved position if needed (will be done after prepare completes)
                if (initialTrackIndex != null && initialTrackIndex != 0) {
                    // Seek will be handled by caller after player is ready
                    android.util.Log.d("AudioPlayerService", "First track loaded, will seek to index $initialTrackIndex after ready")
                }

                notificationManager?.updateNotification()

                android.util.Log.i(
                    "AudioPlayerService",
                    "First MediaItem loaded and prepared: index=$firstTrackIndex, " +
                        "state=${activePlayer.playbackState}, " +
                        "remaining items will load asynchronously",
                )
            }

            // Load remaining MediaSources asynchronously in background (non-blocking)
            // This doesn't block playback startup
            // OPTIMIZATION: Use limited dispatcher to control parallel MediaItem creation (max 4)
            playerServiceScope.launch(mediaItemDispatcher) {
                try {
                    val remainingIndices = filePaths.indices.filter { it != firstTrackIndex }
                    val totalItems = filePaths.size
                    val isLargePlaylist = totalItems > 100
                    android.util.Log.d(
                        "AudioPlayerService",
                        "Loading ${remainingIndices.size} remaining MediaItems asynchronously (large playlist: $isLargePlaylist)",
                    )

                    // OPTIMIZATION: If initialTrackIndex > 0, prioritize loading tracks before it
                    // This ensures smooth navigation if user wants to go back
                    val priorityIndices =
                        if (firstTrackIndex > 0) {
                            (0 until firstTrackIndex).toList()
                        } else {
                            emptyList()
                        }
                    val otherIndices =
                        remainingIndices
                            .filter { it !in priorityIndices }
                            .sorted() // Ensure ascending order to maintain correct track sequence

                    // Load priority indices first (tracks before initialTrackIndex)
                    for ((priorityIndex, index) in priorityIndices.withIndex()) {
                        try {
                            val mediaSource =
                                createMediaSourceForIndex(
                                    filePaths,
                                    index,
                                    metadata,
                                    dataSourceFactory,
                                )

                            // Add to player asynchronously (non-blocking)
                            withContext(Dispatchers.Main) {
                                val activePlayer = getActivePlayer()
                                // Insert at correct position to maintain order
                                activePlayer.addMediaSource(index, mediaSource)
                                android.util.Log.d(
                                    "AudioPlayerService",
                                    "Added priority MediaItem at index $index (playlist size: ${activePlayer.mediaItemCount})",
                                )
                            }

                            // Yield for large playlists to prevent blocking
                            if (isLargePlaylist && priorityIndex % 10 == 0) {
                                yield()
                            }

                            // Small delay to avoid overwhelming the system
                            kotlinx.coroutines.delay(5) // 5ms delay for priority items
                        } catch (e: Exception) {
                            android.util.Log.w("AudioPlayerService", "Failed to create MediaItem $index, skipping: ${e.message}")
                            // Continue with other items - one failure shouldn't stop the rest
                        }
                    }

                    // Load other indices (tracks after initialTrackIndex)
                    for ((otherIndex, index) in otherIndices.withIndex()) {
                        try {
                            val mediaSource =
                                createMediaSourceForIndex(
                                    filePaths,
                                    index,
                                    metadata,
                                    dataSourceFactory,
                                )

                            // Add to player asynchronously (non-blocking)
                            withContext(Dispatchers.Main) {
                                val activePlayer = getActivePlayer()
                                // CRITICAL: Use index parameter to insert at correct position
                                // This ensures tracks are added in the correct order, not at the end
                                activePlayer.addMediaSource(index, mediaSource)
                                android.util.Log.d(
                                    "AudioPlayerService",
                                    "Added MediaItem at index $index (playlist size: ${activePlayer.mediaItemCount})",
                                )
                            }

                            // Yield for large playlists to prevent blocking (every 10 items)
                            if (isLargePlaylist && otherIndex % 10 == 0) {
                                yield()
                            }

                            // Small delay to avoid overwhelming the system
                            if (otherIndex % 10 == 0) {
                                kotlinx.coroutines.delay(10) // 10ms delay every 10 items
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("AudioPlayerService", "Failed to create MediaItem $index, skipping: ${e.message}")
                            // Continue with other items - one failure shouldn't stop the rest
                        }
                    }

                    android.util.Log.i(
                        "AudioPlayerService",
                        "All ${filePaths.size} MediaItems loaded asynchronously (priority: ${priorityIndices.size}, other: ${otherIndices.size})",
                    )

                    // Verify playlist order after all items are loaded
                    withContext(Dispatchers.Main) {
                        val activePlayer = getActivePlayer()
                        if (activePlayer.mediaItemCount == filePaths.size) {
                            var orderMismatchCount = 0
                            for (i in 0 until activePlayer.mediaItemCount) {
                                val item = activePlayer.getMediaItemAt(i)
                                val expectedPath = filePaths[i]
                                val actualPath = item.localConfiguration?.uri?.path
                                if (actualPath != expectedPath) {
                                    orderMismatchCount++
                                    android.util.Log.w(
                                        "AudioPlayerService",
                                        "Playlist order mismatch at index $i: expected $expectedPath, got $actualPath",
                                    )
                                }
                            }
                            if (orderMismatchCount == 0) {
                                android.util.Log.d(
                                    "AudioPlayerService",
                                    "Playlist order verified: all ${activePlayer.mediaItemCount} items are in correct order",
                                )
                            } else {
                                android.util.Log.w(
                                    "AudioPlayerService",
                                    "Playlist order verification found $orderMismatchCount mismatches out of ${activePlayer.mediaItemCount} items",
                                )
                            }
                        } else {
                            android.util.Log.w(
                                "AudioPlayerService",
                                "Playlist size mismatch: expected ${filePaths.size}, got ${activePlayer.mediaItemCount}",
                            )
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AudioPlayerService", "Error loading remaining MediaItems asynchronously", e)
                    // Don't throw - player is already working with first item
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to prepare playback", e)
            throw e
        }
    }

    /**
     * Creates a MediaSource for a specific file index.
     * Helper method to avoid code duplication.
     */
    @OptIn(UnstableApi::class)
    private fun createMediaSourceForIndex(
        filePaths: List<String>,
        index: Int,
        metadata: Map<String, String>?,
        dataSourceFactory: MediaDataSourceFactory,
    ): MediaSource {
        val path = filePaths[index]

        // Determine if path is a URL or file path
        val isUrl = path.startsWith("http://") || path.startsWith("https://")
        val uri: Uri

        if (isUrl) {
            // Handle HTTP(S) URL
            uri = Uri.parse(path)
            android.util.Log.d("AudioPlayerService", "Creating MediaItem $index from URL: $path")
        } else {
            // Handle local file path
            val file = File(path)
            if (!file.exists()) {
                android.util.Log.w("AudioPlayerService", "File does not exist: $path")
            }
            uri = Uri.fromFile(file)
            android.util.Log.d("AudioPlayerService", "Creating MediaItem $index from file: $path")
        }

        val metadataBuilder =
            androidx.media3.common.MediaMetadata
                .Builder()

        val fileName =
            if (isUrl) {
                val urlPath = Uri.parse(path).lastPathSegment ?: "Track ${index + 1}"
                urlPath.substringBeforeLast('.', urlPath)
            } else {
                File(path).nameWithoutExtension
            }
        val providedTitle = metadata?.get("title") ?: metadata?.get("trackTitle")
        val providedArtist = metadata?.get("artist") ?: metadata?.get("author")
        val providedAlbum = metadata?.get("album") ?: metadata?.get("bookTitle")

        // Get flavor suffix for title
        val flavorSuffix = Companion.getFlavorSuffix(this)
        val flavorText = if (flavorSuffix.isEmpty()) "" else " - $flavorSuffix"

        // Always add flavor suffix to title for quick settings player
        val baseTitle = providedTitle ?: fileName.ifEmpty { "Track ${index + 1}" }
        val titleWithFlavor = if (flavorText.isEmpty()) baseTitle else "$baseTitle$flavorText"
        metadataBuilder.setTitle(titleWithFlavor)

        if (providedArtist != null) {
            metadataBuilder.setArtist(providedArtist)
        }

        if (providedAlbum != null) {
            metadataBuilder.setAlbumTitle(providedAlbum)
        }

        val artworkUriString = metadata?.get("artworkUri")?.takeIf { it.isNotEmpty() }
        if (artworkUriString != null) {
            try {
                val artworkUri = android.net.Uri.parse(artworkUriString)
                metadataBuilder.setArtworkUri(artworkUri)
            } catch (e: Exception) {
                android.util.Log.w("AudioPlayerService", "Failed to parse artwork URI: $artworkUriString", e)
            }
        }

        val mediaItem =
            MediaItem
                .Builder()
                .setUri(uri)
                .setMediaMetadata(metadataBuilder.build())
                .build()

        val sourceFactory = dataSourceFactory.createDataSourceFactoryForUri(uri)
        return ProgressiveMediaSource
            .Factory(sourceFactory)
            .createMediaSource(mediaItem)
    }

    /**
     * Prepares playback asynchronously (legacy method - kept for compatibility).
     *
     * @deprecated Use preparePlaybackOptimized() instead for better performance
     */
    @OptIn(UnstableApi::class)
    @Deprecated("Use preparePlaybackOptimized() for better performance with large playlists")
    private suspend fun preparePlayback(
        filePaths: List<String>,
        metadata: Map<String, String>?,
    ) = preparePlaybackOptimized(filePaths, metadata, null)

    /**
     * Seeks to specific track and position.
     *
     * @param trackIndex Track index in playlist
     * @param positionMs Position in milliseconds within the track
     */
    fun seekToTrackAndPosition(
        trackIndex: Int,
        positionMs: Long,
    ) {
        val player = getActivePlayer()

        if (trackIndex < 0 || trackIndex >= player.mediaItemCount) {
            android.util.Log.w("AudioPlayerService", "Invalid track index: $trackIndex (mediaItemCount: ${player.mediaItemCount})")
            return
        }

        if (positionMs < 0) {
            android.util.Log.w("AudioPlayerService", "Seek position cannot be negative: $positionMs")
            return
        }

        try {
            val playWhenReadyBeforeSeek = player.playWhenReady
            player.seekTo(trackIndex, positionMs)

            // Reset inactivity timer (user action)
            inactivityTimer?.resetTimer()

            if (playWhenReadyBeforeSeek) {
                playerServiceScope.launch {
                    delay(100)
                    if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) {
                        if (!player.playWhenReady) {
                            player.playWhenReady = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(
                "AudioPlayerService",
                "Failed to seek to track and position: trackIndex=$trackIndex, positionMs=$positionMs",
                e,
            )
        }
    }

    /**
     * Updates metadata for current track.
     *
     * @param metadata Metadata map (title, artist, album)
     * Note: Artwork is automatically extracted from audio file metadata by ExoPlayer
     * We don't update MediaItem here to preserve embedded artwork extracted by ExoPlayer
     */
    fun updateMetadata(metadata: Map<String, String>) {
        currentMetadata = metadata
        // Just update notification - ExoPlayer already has the embedded artwork in MediaItem
        // Don't replace MediaItem to avoid losing embedded artwork
        // MediaSession automatically updates metadata from ExoPlayer, no manual update needed
        notificationManager?.updateMetadata(metadata, embeddedArtworkPath)
    }

    /**
     * Requests AudioFocus for playback.
     *
     * @return true if AudioFocus was granted, false otherwise
     */
    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return false
        val listener = audioFocusListener ?: return false

        if (hasAudioFocus) {
            android.util.Log.d("AudioPlayerService", "AudioFocus already held")
            return true
        }

        val result =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Create AudioFocusRequest once and reuse it
                if (audioFocusRequest == null) {
                    audioFocusRequest =
                        android.media.AudioFocusRequest
                            .Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(
                                android.media.AudioAttributes
                                    .Builder()
                                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build(),
                            ).setOnAudioFocusChangeListener(listener)
                            .setAcceptsDelayedFocusGain(true) // Allow delayed focus gain
                            .build()
                    android.util.Log.d("AudioPlayerService", "Created AudioFocusRequest")
                }
                am.requestAudioFocus(audioFocusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    listener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN,
                )
            }

        val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        hasAudioFocus = granted
        android.util.Log.i("AudioPlayerService", "AudioFocus request result: $result (granted=$granted)")
        return granted
    }

    /**
     * Abandons AudioFocus when playback stops.
     */
    private fun abandonAudioFocus() {
        val am = audioManager ?: return

        if (!hasAudioFocus) {
            android.util.Log.d("AudioPlayerService", "AudioFocus not held, nothing to abandon")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Use the same AudioFocusRequest that was used for request
            audioFocusRequest?.let { request ->
                am.abandonAudioFocusRequest(request)
                android.util.Log.i("AudioPlayerService", "AudioFocus abandoned using saved request")
            } ?: run {
                android.util.Log.w("AudioPlayerService", "AudioFocusRequest is null, cannot abandon properly")
            }
        } else {
            @Suppress("DEPRECATION")
            audioFocusListener?.let { listener ->
                am.abandonAudioFocus(listener)
                android.util.Log.i("AudioPlayerService", "AudioFocus abandoned (deprecated API)")
            }
        }

        hasAudioFocus = false
    }

    /**
     * Handles AudioFocus changes.
     */
    private fun handleAudioFocusChange(focusChange: Int) {
        android.util.Log.i("AudioPlayerService", "AudioFocus change: $focusChange")

        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // AudioFocus regained - resume playback
                android.util.Log.i("AudioPlayerService", "AudioFocus GAIN - resuming playback")
                hasAudioFocus = true
                val player = getActivePlayer()
                if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) {
                    player.playWhenReady = true
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss - pause playback
                android.util.Log.i("AudioPlayerService", "AudioFocus LOSS - pausing playback")
                hasAudioFocus = false
                getActivePlayer().playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Temporary loss - pause playback
                android.util.Log.i("AudioPlayerService", "AudioFocus LOSS_TRANSIENT - pausing playback")
                getActivePlayer().playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Can duck - reduce volume
                android.util.Log.i("AudioPlayerService", "AudioFocus LOSS_TRANSIENT_CAN_DUCK - reducing volume")
                // For audiobooks, we pause instead of ducking
                getActivePlayer().playWhenReady = false
            }
        }
    }

    /**
     * Starts or resumes playback.
     *
     * Simplified implementation matching lissen-android approach.
     */
    fun play() {
        android.util.Log.i("AudioPlayerService", "play() called")

        val player = getActivePlayer()
        if (player.mediaItemCount == 0) {
            android.util.Log.w("AudioPlayerService", "Cannot play: no media items loaded")
            // Service might have been unloaded - state will be restored when playlist is set
            return
        }
        if (player.mediaItemCount == 0) {
            android.util.Log.w("AudioPlayerService", "Cannot play: no media items loaded")
            // Service might have been unloaded - state will be restored when playlist is set
            return
        }

        // Note: If service was unloaded and recreated, state should be restored
        // by Media3PlayerService before calling play()

        // Match lissen-android: simple approach - just set playWhenReady=true in coroutine
        // ExoPlayer will handle AudioFocus automatically
        playerServiceScope.launch(Dispatchers.Main) {
            try {
                // Only call prepare() if player is in IDLE state
                if (player.playbackState == Player.STATE_IDLE) {
                    android.util.Log.d("AudioPlayerService", "play() - player is IDLE, calling prepare()")
                    player.prepare()
                }

                // Match lissen-android: simply set playWhenReady=true
                // ExoPlayer manages AudioFocus automatically when handleAudioFocus=true
                player.playWhenReady = true
                android.util.Log.d("AudioPlayerService", "play() - set playWhenReady=true, letting ExoPlayer handle AudioFocus")

                // Reset inactivity timer (user action)
                inactivityTimer?.resetTimer()
            } catch (e: Exception) {
                android.util.Log.e("AudioPlayerService", "Failed to start playback", e)
                e.printStackTrace()
                ErrorHandler.handleGeneralError("AudioPlayerService", e, "Play method execution")
            }
        }
    }

    /**
     * Pauses playback.
     */
    fun pause() {
        playerServiceScope.launch {
            try {
                getActivePlayer().playWhenReady = false
                // Note: We don't abandon AudioFocus on pause - we keep it for quick resume
                // AudioFocus will be abandoned when service is stopped

                // Reset inactivity timer (user action - pause is also an interaction)
                inactivityTimer?.resetTimer()
            } catch (e: Exception) {
                ErrorHandler.handleGeneralError("AudioPlayerService", e, "Pause method execution")
            }
        }
    }

    /**
     * Stops playback and resets player.
     *
     * This method stops the player but does not release all resources.
     * For full cleanup, use stopAndRelease() instead.
     */
    fun stop() {
        val player = getActivePlayer()
        try {
            android.util.Log.d("AudioPlayerService", "stop() called, current playbackState: ${player.playbackState}")
            player.stop()
            // ExoPlayer manages AudioFocus automatically, no need to abandon manually
        } catch (e: Exception) {
            ErrorHandler.handleGeneralError("AudioPlayerService", e, "Stop method execution")
        }
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

    /**
     * Saves current playback position via broadcast.
     *
     * This method broadcasts the current position to trigger saving through MethodChannel.
     * Position is also saved periodically, so this is an additional safety measure.
     */
    private fun saveCurrentPosition() {
        try {
            val activePlayer = getActivePlayer()
            if (activePlayer.mediaItemCount > 0) {
                val currentIndex = activePlayer.currentMediaItemIndex
                val currentPosition = activePlayer.currentPosition

                // Broadcast intent to trigger position saving through MethodChannel
                val saveIntent =
                    Intent(ACTION_SAVE_POSITION_BEFORE_UNLOAD).apply {
                        putExtra(EXTRA_TRACK_INDEX, currentIndex)
                        putExtra(EXTRA_POSITION_MS, currentPosition)
                        setPackage(packageName) // Set package for explicit broadcast
                    }
                sendBroadcast(saveIntent)
                android.util.Log.d(
                    "AudioPlayerService",
                    "Position save broadcast sent: track=$currentIndex, position=${currentPosition}ms",
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("AudioPlayerService", "Failed to send save position broadcast", e)
            // Not critical - position is already saved periodically
        }
    }

    /**
     * Seeks to specific position.
     *
     * @param positionMs Position in milliseconds
     */
    fun seekTo(positionMs: Long) {
        val player = getActivePlayer()

        try {
            if (positionMs < 0) {
                android.util.Log.w("AudioPlayerService", "Seek position cannot be negative: $positionMs")
                return
            }

            if (player.mediaItemCount == 0) {
                android.util.Log.w("AudioPlayerService", "Cannot seek: no media items loaded")
                return
            }

            val playWhenReadyBeforeSeek = player.playWhenReady
            val duration = player.duration
            val seekPosition =
                if (duration != C.TIME_UNSET && positionMs > duration) {
                    duration
                } else {
                    positionMs
                }

            player.seekTo(seekPosition)

            // Reset inactivity timer (user action)
            inactivityTimer?.resetTimer()

            if (playWhenReadyBeforeSeek) {
                playerServiceScope.launch {
                    delay(100)
                    if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) {
                        if (!player.playWhenReady) {
                            player.playWhenReady = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Failed to seek to position: $positionMs", e)
        }
    }

    /**
     * Sets playback speed.
     *
     * @param speed Playback speed (0.5x to 2.0x)
     */
    fun setSpeed(speed: Float) {
        getActivePlayer().setPlaybackSpeed(speed)
        // Reset inactivity timer (user action)
        inactivityTimer?.resetTimer()
    }

    /**
     * Sets repeat mode.
     *
     * @param repeatMode Repeat mode:
     *   - REPEAT_MODE_OFF: No repeat
     *   - REPEAT_MODE_ONE: Repeat current track
     *   - REPEAT_MODE_ALL: Repeat all tracks
     */
    fun setRepeatMode(repeatMode: Int) {
        getActivePlayer().repeatMode = repeatMode
        android.util.Log.d("AudioPlayerService", "Repeat mode set to: $repeatMode")
        // Reset inactivity timer (user action)
        inactivityTimer?.resetTimer()
    }

    /**
     * Gets current repeat mode.
     *
     * @return Current repeat mode
     */
    fun getRepeatMode(): Int = getActivePlayer().repeatMode

    /**
     * Sets shuffle mode.
     *
     * @param shuffleModeEnabled true to enable shuffle, false to disable
     */
    fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        getActivePlayer().shuffleModeEnabled = shuffleModeEnabled
        android.util.Log.d("AudioPlayerService", "Shuffle mode set to: $shuffleModeEnabled")
        // Reset inactivity timer (user action)
        inactivityTimer?.resetTimer()
    }

    /**
     * Gets current shuffle mode.
     *
     * @return true if shuffle is enabled, false otherwise
     */
    fun getShuffleModeEnabled(): Boolean = getActivePlayer().shuffleModeEnabled

    /**
     * Skips to next track.
     */
    fun next() {
        getActivePlayer().seekToNextMediaItem()
        android.util.Log.d("AudioPlayerService", "Skipping to next track")
        // Reset inactivity timer (user action)
        inactivityTimer?.resetTimer()
    }

    /**
     * Skips to previous track.
     */
    fun previous() {
        getActivePlayer().seekToPreviousMediaItem()
        android.util.Log.d("AudioPlayerService", "Skipping to previous track")
        // Reset inactivity timer (user action)
        inactivityTimer?.resetTimer()
    }

    /**
     * Seeks to specific track by index.
     *
     * @param index Track index in playlist
     */
    fun seekToTrack(index: Int) {
        val player = getActivePlayer()
        if (index >= 0 && index < player.mediaItemCount) {
            val playWhenReadyBeforeSeek = player.playWhenReady
            player.seekTo(index, 0L)

            // Reset inactivity timer (user action)
            inactivityTimer?.resetTimer()

            if (playWhenReadyBeforeSeek) {
                playerServiceScope.launch {
                    delay(100)
                    if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) {
                        if (!player.playWhenReady) {
                            player.playWhenReady = true
                        }
                    }
                }
            }
        }
    }

    /**
     * Sets playback progress from saved position.
     *
     * Inspired by lissen-android: restores playback position across multiple tracks/chapters
     * using cumulative durations for accurate seeking.
     *
     * @param filePaths List of file paths (for reference, actual durations come from MediaItems)
     * @param progressSeconds Progress in seconds (overall position across all tracks)
     */
    fun setPlaybackProgress(
        filePaths: List<String>,
        progressSeconds: Double?,
    ) {
        val player = getActivePlayer()

        if (filePaths.isEmpty() || player.mediaItemCount == 0) {
            android.util.Log.w("AudioPlayerService", "Cannot set playback progress: empty file list or no media items")
            return
        }

        when (progressSeconds) {
            null, 0.0 -> {
                // No saved progress, start from beginning
                player.seekTo(0, 0L)
                android.util.Log.d("AudioPlayerService", "No saved progress, starting from beginning")
            }
            else -> {
                // Calculate which track and position to seek to
                // Use ChapterUtils functions inspired by lissen-android
                val positionMs = (progressSeconds * 1000).toLong()

                // Get actual durations from MediaItems (more accurate than estimation)
                val durationsMs = mutableListOf<Long>()

                // Collect actual durations from MediaItems if available
                for (i in 0 until player.mediaItemCount) {
                    // Note: mediaItem is retrieved but duration is not available in metadata
                    // Actual duration is only available after MediaItem is loaded
                    // For now, we'll use a hybrid approach: try to get from player if available
                    val itemDuration =
                        if (i == player.currentMediaItemIndex && player.duration != C.TIME_UNSET) {
                            // For current item, use player's duration
                            player.duration
                        } else {
                            // For other items, estimate based on average or use a default
                            // In a full implementation, you'd preload durations or store them
                            // For now, estimate 5 minutes per track (conservative estimate)
                            5 * 60 * 1000L
                        }
                    durationsMs.add(itemDuration)
                }

                // Use ChapterUtils to calculate target chapter index and position
                val targetChapterIndex = calculateChapterIndexMs(durationsMs, positionMs)
                val chapterPositionMs = calculateChapterPositionMs(durationsMs, positionMs)

                when {
                    targetChapterIndex >= 0 && targetChapterIndex < player.mediaItemCount -> {
                        // Found valid chapter
                        val clampedProgress = chapterPositionMs.coerceAtLeast(0L)
                        player.seekTo(targetChapterIndex, clampedProgress)
                        android.util.Log.d(
                            "AudioPlayerService",
                            "Restored playback: track=$targetChapterIndex, position=${clampedProgress}ms (from ${progressSeconds}s)",
                        )
                    }
                    else -> {
                        // Position is beyond all tracks or at the end, seek to last track
                        val lastChapterIndex = player.mediaItemCount - 1
                        if (lastChapterIndex >= 0) {
                            val lastChapterDuration = durationsMs.lastOrNull() ?: 0L
                            player.seekTo(lastChapterIndex, lastChapterDuration)
                            android.util.Log.d(
                                "AudioPlayerService",
                                "Progress beyond all tracks, seeking to last track $lastChapterIndex at end",
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Rewinds playback by specified seconds.
     *
     * @param seconds Number of seconds to rewind (default: 15)
     */
    fun rewind(seconds: Int = 15) {
        val player = getActivePlayer()
        val currentPosition = player.currentPosition
        val newPosition = (currentPosition - seconds * 1000L).coerceAtLeast(0L)
        player.seekTo(newPosition)
        android.util.Log.d("AudioPlayerService", "Rewind: ${seconds}s (from ${currentPosition}ms to ${newPosition}ms)")
        // Reset inactivity timer (user action)
        inactivityTimer?.resetTimer()
    }

    /**
     * Forwards playback by specified seconds.
     *
     * @param seconds Number of seconds to forward (default: 30)
     */
    fun forward(seconds: Int = 30) {
        val player = getActivePlayer()
        val currentPosition = player.currentPosition
        val duration = player.duration
        if (duration != C.TIME_UNSET) {
            val newPosition = (currentPosition + seconds * 1000L).coerceAtMost(duration)
            player.seekTo(newPosition)
            android.util.Log.d("AudioPlayerService", "Forward: ${seconds}s (from ${currentPosition}ms to ${newPosition}ms)")
            // Reset inactivity timer (user action)
            inactivityTimer?.resetTimer()
        }
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

    /**
     * Gets current playback position.
     *
     * @return Current position in milliseconds
     */
    fun getCurrentPosition(): Long = getActivePlayer().currentPosition

    /**
     * Gets total duration of current media.
     *
     * @return Duration in milliseconds, or 0 if unknown
     */
    fun getDuration(): Long = getActivePlayer().duration

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

    /**
     * Gets current player state.
     *
     * @return Map with player state information
     */
    fun getPlayerState(): Map<String, Any> {
        val player = getActivePlayer()

        // Get duration - prefer MediaMetadataRetriever (most accurate from file metadata),
        // then player.duration, then MediaItem metadata
        var duration: Long = C.TIME_UNSET

        val currentItem = player.currentMediaItem
        if (currentItem != null) {
            val uri = currentItem.localConfiguration?.uri

            // First, try to get duration from file using MediaMetadataRetriever (most accurate)
            if (uri != null && uri.scheme == "file") {
                val filePath = uri.path
                if (filePath != null) {
                    // Check cache first
                    val cachedDuration = durationCache[filePath]
                    if (cachedDuration != null && cachedDuration > 0) {
                        duration = cachedDuration
                        android.util.Log.d("AudioPlayerService", "Using cached duration for $filePath: ${duration}ms")
                    } else {
                        // Get from MediaMetadataRetriever (accurate source from file metadata)
                        try {
                            val retriever = android.media.MediaMetadataRetriever()
                            retriever.setDataSource(filePath)
                            val retrieverDuration =
                                retriever.extractMetadata(
                                    android.media.MediaMetadataRetriever.METADATA_KEY_DURATION,
                                )
                            retriever.release()
                            if (retrieverDuration != null) {
                                val parsedDuration = retrieverDuration.toLongOrNull()
                                if (parsedDuration != null && parsedDuration > 0) {
                                    duration = parsedDuration
                                    // Cache it for future use
                                    durationCache[filePath] = duration
                                    android.util.Log.d(
                                        "AudioPlayerService",
                                        "Got duration from MediaMetadataRetriever for $filePath: ${duration}ms",
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w(
                                "AudioPlayerService",
                                "Failed to get duration from MediaMetadataRetriever for $filePath: ${e.message}",
                            )
                        }
                    }
                }
            }

            // Fallback to player.duration if MediaMetadataRetriever didn't work
            if (duration == C.TIME_UNSET) {
                duration = player.duration
                if (duration != C.TIME_UNSET && duration > 0) {
                    android.util.Log.d("AudioPlayerService", "Using player.duration: ${duration}ms")
                }
            }

            // Last resort: MediaItem metadata doesn't have duration in Media3
            // Duration is only available from player after media is loaded
            // So we skip this fallback and rely on player.duration above
        } else {
            // No current item, use player.duration
            duration = player.duration
        }

        // If still unset, return 0
        if (duration == C.TIME_UNSET || duration < 0) {
            duration = 0L
        }

        return mapOf(
            "isPlaying" to player.isPlaying,
            "playWhenReady" to player.playWhenReady, // Added for debugging
            "playbackState" to player.playbackState,
            "currentPosition" to player.currentPosition,
            "duration" to duration,
            "currentIndex" to (player.currentMediaItemIndex),
            "playbackSpeed" to player.playbackParameters.speed,
            "repeatMode" to player.repeatMode,
            "shuffleModeEnabled" to player.shuffleModeEnabled,
            "mediaItemCount" to player.mediaItemCount,
        )
    }

    /**
     * Gets information about current media item.
     *
     * @return Map with current media item information, or empty map if no item
     */
    fun getCurrentMediaItemInfo(): Map<String, Any?> {
        val player = getActivePlayer()
        val currentItem = player.currentMediaItem ?: return emptyMap()
        val metadata = currentItem.mediaMetadata

        // Get artwork path - prefer embedded artwork if available
        val artworkPath =
            embeddedArtworkPath?.takeIf {
                val file = File(it)
                file.exists() && file.length() > 0
            } ?: metadata.artworkUri?.toString()

        return mapOf(
            "mediaId" to currentItem.mediaId,
            "uri" to currentItem.localConfiguration?.uri?.toString(),
            "title" to (metadata.title?.toString()),
            "artist" to (metadata.artist?.toString()),
            "albumTitle" to (metadata.albumTitle?.toString()),
            "hasArtwork" to (metadata.artworkUri != null || metadata.artworkData != null),
            "artworkPath" to artworkPath, // Path to artwork (embedded or external)
        )
    }

    /**
     * Extracts embedded artwork from audio file metadata using Android MediaMetadataRetriever.
     *
     * This method runs in a background thread to avoid blocking the main thread.
     *
     * @param filePath Path to the audio file
     * @return Path to saved artwork file, or null if no artwork found
     */
    fun extractArtworkFromFile(filePath: String): String? {
        return runBlocking {
            withContext(Dispatchers.IO) {
                try {
                    val file = File(filePath)
                    if (!file.exists()) {
                        android.util.Log.w("AudioPlayerService", "File does not exist: $filePath")
                        return@withContext null
                    }

                    // Use Android MediaMetadataRetriever to extract embedded artwork
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(filePath)

                        // Try to get embedded picture (album art)
                        val picture = retriever.embeddedPicture
                        if (picture != null && picture.isNotEmpty()) {
                            // Save artwork to cache
                            val cacheDir = applicationContext.cacheDir
                            val artworkFile = File(cacheDir, "embedded_artwork_${filePath.hashCode()}.jpg")
                            artworkFile.outputStream().use { it.write(picture) }
                            android.util.Log.i(
                                "AudioPlayerService",
                                "Extracted and saved artwork from $filePath to ${artworkFile.absolutePath}",
                            )
                            return@withContext artworkFile.absolutePath
                        }

                        android.util.Log.d("AudioPlayerService", "No embedded artwork found in $filePath")
                        null
                    } finally {
                        retriever.release()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AudioPlayerService", "Failed to extract artwork from $filePath", e)
                    null
                }
            }
        }
    }

    /**
     * Gets playlist information.
     *
     * @return Map with playlist information
     */
    fun getPlaylistInfo(): Map<String, Any> {
        val player = getActivePlayer()
        return mapOf(
            "itemCount" to player.mediaItemCount,
            "currentIndex" to player.currentMediaItemIndex,
            "hasNext" to player.hasNextMediaItem(),
            "hasPrevious" to player.hasPreviousMediaItem(),
            "repeatMode" to player.repeatMode,
            "shuffleModeEnabled" to player.shuffleModeEnabled,
        )
    }

    /**
     * Player event listener with improved error handling and retry logic.
     *
     * Inspired by lissen-android implementation for better error recovery.
     * Uses onEvents() for more efficient event handling (Media3 1.8+).
     */
    private val playerListener =
        object : Player.Listener {
            private var retryCount = 0
            private val maxRetries = 3
            private val retryDelayMs = 2000L // 2 seconds

            // Use onEvents() for more efficient event handling (inspired by lissen-android)
            // This allows handling multiple events in one callback for better performance
            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                // Log all events for debugging
                android.util.Log.d("AudioPlayerService", "onEvents called: $events")

                // Handle playback state changes
                if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                    val playbackState = player.playbackState
                    val stateName =
                        when (playbackState) {
                            Player.STATE_IDLE -> "IDLE"
                            Player.STATE_BUFFERING -> "BUFFERING"
                            Player.STATE_READY -> "READY"
                            Player.STATE_ENDED -> "ENDED"
                            else -> "UNKNOWN($playbackState)"
                        }
                    android.util.Log.i(
                        "AudioPlayerService",
                        "EVENT_PLAYBACK_STATE_CHANGED: $stateName, playWhenReady=${player.playWhenReady}, isPlaying=${player.isPlaying}, mediaItemCount=${player.mediaItemCount}",
                    )

                    // Update notification when state changes
                    // MediaSession automatically updates from ExoPlayer state
                    notificationManager?.updateNotification()

                    // Reset retry count on successful playback
                    if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                        retryCount = 0
                    }

                    // Handle book completion - when last track ends
                    if (playbackState == Player.STATE_ENDED) {
                        val currentIndex = player.currentMediaItemIndex
                        val totalTracks = player.mediaItemCount

                        if (currentIndex >= totalTracks - 1) {
                            // Last track finished - book completed
                            android.util.Log.i(
                                "AudioPlayerService",
                                "Book completed: last track finished (track $currentIndex of ${totalTracks - 1})",
                            )

                            // Stop playback completely to prevent auto-advance
                            // Use stop() instead of just playWhenReady = false to prevent ExoPlayer
                            // from automatically advancing to next track
                            try {
                                player.stop()
                                player.playWhenReady = false
                            } catch (e: Exception) {
                                android.util.Log.e("AudioPlayerService", "Error stopping player on book completion", e)
                                // Fallback to just pausing
                                player.playWhenReady = false
                            }

                            // Save final position
                            saveCurrentPosition()

                            // Update notification to show completion
                            notificationManager?.updateNotification()

                            // Send broadcast to UI to show completion message
                            val intent =
                                Intent("com.jabook.app.jabook.BOOK_COMPLETED").apply {
                                    setPackage(packageName) // Set package for explicit broadcast
                                }
                            sendBroadcast(intent)
                        } else {
                            // Not last track - ExoPlayer will auto-advance (normal behavior)
                            android.util.Log.d(
                                "AudioPlayerService",
                                "Track ended, will auto-advance to next (track $currentIndex of ${totalTracks - 1})",
                            )
                        }
                    }

                    // Handle errors
                    if (playbackState == Player.STATE_IDLE) {
                        val error = player.playerError
                        if (error != null) {
                            android.util.Log.e("AudioPlayerService", "Playback error: ${error.message}", error)
                            handlePlayerError(error)
                        }
                    }
                }

                // Handle playWhenReady changes (important for AudioFocus debugging)
                if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED)) {
                    android.util.Log.i(
                        "AudioPlayerService",
                        "EVENT_PLAY_WHEN_READY_CHANGED: playWhenReady=${player.playWhenReady}, isPlaying=${player.isPlaying}, playbackState=${player.playbackState}, mediaItemCount=${player.mediaItemCount}",
                    )
                    // Match lissen-android: just log, don't interfere with ExoPlayer's AudioFocus handling
                    // Post notification update to main thread to ensure player state is fully updated
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        notificationManager?.updateNotification()
                    }
                }

                // Handle playing state changes
                if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                    val isPlaying = player.isPlaying
                    val playbackState = player.playbackState
                    val stateName =
                        when (playbackState) {
                            Player.STATE_IDLE -> "IDLE"
                            Player.STATE_BUFFERING -> "BUFFERING"
                            Player.STATE_READY -> "READY"
                            Player.STATE_ENDED -> "ENDED"
                            else -> "UNKNOWN($playbackState)"
                        }
                    android.util.Log.i(
                        "AudioPlayerService",
                        "EVENT_IS_PLAYING_CHANGED: isPlaying=$isPlaying, playWhenReady=${player.playWhenReady}, playbackState=$stateName, mediaItemCount=${player.mediaItemCount}",
                    )

                    // Don't reset playWhenReady automatically - let ExoPlayer handle AudioFocus
                    // The previous check was too aggressive and was preventing playback from starting

                    // Post notification update to main thread to ensure player state is fully updated
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        notificationManager?.updateNotification()
                    }
                }

                // Handle media item transitions
                if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                    // Track changed - update notification to show new track's embedded artwork
                    // MediaSession automatically updates from ExoPlayer
                    notificationManager?.updateNotification()

                    // Log track transition for debugging (inspired by lissen-android logging)
                    val currentIndex = player.currentMediaItemIndex
                    val currentItem = player.currentMediaItem
                    val title = currentItem?.mediaMetadata?.title?.toString() ?: "Unknown"
                    android.util.Log.d("AudioPlayerService", "Media item transition:")
                    android.util.Log.d("AudioPlayerService", "  - Index: $currentIndex")
                    android.util.Log.d("AudioPlayerService", "  - Title: $title")
                    android.util.Log.d("AudioPlayerService", "  - Total items: ${player.mediaItemCount}")

                    // Reset retry count on track change (new track might work even if previous failed)
                    retryCount = 0
                }

                // Handle playback parameters changes (speed, pitch, etc.)
                if (events.contains(Player.EVENT_PLAYBACK_PARAMETERS_CHANGED)) {
                    val params = player.playbackParameters
                    android.util.Log.d("AudioPlayerService", "Playback parameters changed: speed=${params.speed}, pitch=${params.pitch}")
                }

                // Handle repeat mode changes
                if (events.contains(Player.EVENT_REPEAT_MODE_CHANGED)) {
                    android.util.Log.d("AudioPlayerService", "Repeat mode changed: ${player.repeatMode}")
                }

                // Handle shuffle mode changes
                if (events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)) {
                    android.util.Log.d("AudioPlayerService", "Shuffle mode changed: ${player.shuffleModeEnabled}")
                }
            }

            // Keep individual listeners for backward compatibility and specific handling
            override fun onPlaybackStateChanged(playbackState: Int) {
                // This is also handled in onEvents, but kept for explicit handling
                notificationManager?.updateNotification()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // This is also handled in onEvents, but kept for explicit handling
                notificationManager?.updateNotification()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("AudioPlayerService", "Player error occurred", error)
                handlePlayerError(error)
            }

            /**
             * Handles player errors with automatic retry for network errors.
             *
             * Inspired by lissen-android: improved error handling with detailed messages.
             *
             * @param error The playback error that occurred
             */
            private fun handlePlayerError(error: androidx.media3.common.PlaybackException) {
                ErrorHandler.handlePlaybackError("AudioPlayerService", error, "Player error during playback")

                val errorCode = error.errorCode
                val userFriendlyMessage =
                    when (errorCode) {
                        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> {
                            // Network errors - try to retry automatically
                            if (retryCount < maxRetries) {
                                retryCount++
                                android.util.Log.w("AudioPlayerService", "Network connection failed, retrying ($retryCount/$maxRetries)...")

                                // Retry after delay with exponential backoff
                                val backoffDelay = retryDelayMs * retryCount
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    getActivePlayer().prepare()
                                    android.util.Log.d(
                                        "AudioPlayerService",
                                        "Retry attempt $retryCount after network error (delay: ${backoffDelay}ms)",
                                    )
                                }, backoffDelay)

                                return // Don't show error message yet, wait for retry
                            }
                            "Network error: Unable to connect. Please check your internet connection."
                        }
                        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                            if (retryCount < maxRetries) {
                                retryCount++
                                android.util.Log.w("AudioPlayerService", "Network timeout, retrying ($retryCount/$maxRetries)...")
                                val backoffDelay = retryDelayMs * retryCount
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    getActivePlayer().prepare()
                                }, backoffDelay)
                                return
                            }
                            "Network timeout: Connection timed out. Please try again."
                        }
                        androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                            "Server error: Unable to load audio from server. Please try again later."
                        }
                        androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> {
                            // File not found - try to skip to next available track
                            handleFileNotFound()
                            "File not found: Audio file is missing or has been moved."
                        }
                        androidx.media3.common.PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> {
                            "Permission denied: Cannot access audio file. Please check file permissions."
                        }
                        androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                        androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                        -> {
                            "Format error: Audio file is corrupted or in an unsupported format."
                        }
                        androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                        androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                        -> {
                            "Decoder error: Unable to decode audio. The format may not be supported on this device."
                        }
                        androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> {
                            "Audio error: Unable to initialize audio playback. Please try again."
                        }
                        androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> {
                            "Audio error: Failed to write audio data. Please try again."
                        }
                        else -> {
                            val errorMessage = error.message ?: "Unknown error"
                            "Playback error: $errorMessage (code: $errorCode)"
                        }
                    }

                android.util.Log.e("AudioPlayerService", "Player error (user-friendly): $userFriendlyMessage")
                notificationManager?.updateNotification()

                // Store error for retrieval via MethodChannel if needed
                // Error will be automatically propagated through state stream
            }

            /**
             * Handles file not found errors by attempting to skip to next available track.
             *
             * Inspired by lissen-android's approach to handle missing files gracefully.
             */
            private fun handleFileNotFound() {
                val player = getActivePlayer()
                val currentIndex = player.currentMediaItemIndex
                val totalTracks = player.mediaItemCount

                if (totalTracks <= 1) {
                    // Only one track or no tracks, can't skip
                    android.util.Log.w("AudioPlayerService", "Cannot skip: only one track or no tracks available")
                    return
                }

                // Don't advance if we're at the last track
                if (currentIndex >= totalTracks - 1) {
                    android.util.Log.w("AudioPlayerService", "Last track, cannot skip forward")
                    player.playWhenReady = false
                    return
                }

                // Try to skip to next track (not using modulo to prevent circular navigation)
                val nextIndex = currentIndex + 1
                if (nextIndex < totalTracks) {
                    android.util.Log.w("AudioPlayerService", "File not found at index $currentIndex, skipping to next track $nextIndex")
                    try {
                        player.seekTo(nextIndex, 0L)
                        // Auto-play next track if player was playing
                        // Use playWhenReady instead of play() for better compatibility
                        if (player.isPlaying || player.playWhenReady) {
                            player.playWhenReady = true
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AudioPlayerService", "Failed to skip to next track", e)
                        // Don't rethrow - log and continue
                    }
                } else {
                    // No more tracks available, pause playback
                    android.util.Log.w("AudioPlayerService", "No more tracks available, pausing playback")
                    try {
                        player.playWhenReady = false
                    } catch (e: Exception) {
                        android.util.Log.e("AudioPlayerService", "Failed to pause playback", e)
                    }
                }
            }

            // onMediaItemTransition is now handled in onEvents() for better performance
            // Keeping this for backward compatibility if needed
            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int,
            ) {
                // This is also handled in onEvents, but kept for explicit handling if needed
                val currentIndex = getActivePlayer().currentMediaItemIndex
                android.util.Log.d("AudioPlayerService", "Media item transition (explicit): index=$currentIndex, reason=$reason")
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                // Handle position discontinuities (e.g., track changes, seeks)
                // Inspired by lissen-android: handle unavailable tracks gracefully
                val previousIndex = oldPosition.mediaItemIndex
                val currentIndex = newPosition.mediaItemIndex

                if (currentIndex != previousIndex) {
                    android.util.Log.d("AudioPlayerService", "Position discontinuity: $previousIndex -> $currentIndex, reason=$reason")

                    // Reset retry count on track change
                    retryCount = 0

                    // Inspired by lissen-android: check if current track is available
                    // If track is not available, try to find next available track
                    val currentItem = getActivePlayer().currentMediaItem
                    if (currentItem != null) {
                        // Check if track URI is accessible
                        val uri = currentItem.localConfiguration?.uri
                        if (uri != null) {
                            // For file URIs, check if file exists
                            if (uri.scheme == "file") {
                                val file = File(uri.path ?: "")
                                if (!file.exists() || !file.canRead()) {
                                    android.util.Log.w(
                                        "AudioPlayerService",
                                        "Current track file not accessible: ${uri.path}, trying to skip",
                                    )
                                    // Try to skip to next available track
                                    skipToNextAvailableTrack(currentIndex, previousIndex)
                                }
                            }
                        }
                    }
                }
            }

            /**
             * Skips to next available track if current track is unavailable.
             * Inspired by lissen-android's PlaybackNotificationService.
             *
             * @param currentIndex Current track index
             * @param previousIndex Previous track index
             */
            private fun skipToNextAvailableTrack(
                currentIndex: Int,
                previousIndex: Int,
            ) {
                val player = getActivePlayer()

                if (player.mediaItemCount <= 1) {
                    // Only one track or no tracks, can't skip
                    android.util.Log.w("AudioPlayerService", "Cannot skip: only one track or no tracks available")
                    return
                }

                // Determine direction (forward or backward)
                val direction =
                    when {
                        currentIndex > previousIndex || (currentIndex == 0 && previousIndex == player.mediaItemCount - 1) -> 1 // FORWARD
                        else -> -1 // BACKWARD
                    }

                // Try to find next available track
                var nextIndex = currentIndex
                var attempts = 0
                val maxAttempts = player.mediaItemCount

                while (attempts < maxAttempts) {
                    nextIndex =
                        when (direction) {
                            1 -> {
                                // Forward: don't use modulo, check bounds
                                if (nextIndex + 1 < player.mediaItemCount) {
                                    nextIndex + 1
                                } else {
                                    // Reached end, can't go forward
                                    android.util.Log.w("AudioPlayerService", "Reached last track, cannot skip forward")
                                    player.playWhenReady = false
                                    return
                                }
                            }
                            else -> {
                                // Backward: can use modulo or bounds check
                                if (nextIndex - 1 >= 0) nextIndex - 1 else player.mediaItemCount - 1
                            }
                        }

                    // Check if this track is available
                    val item = player.getMediaItemAt(nextIndex)
                    val uri = item.localConfiguration?.uri

                    if (uri != null) {
                        val isAvailable =
                            when (uri.scheme) {
                                "file" -> {
                                    val file = File(uri.path ?: "")
                                    file.exists() && file.canRead()
                                }
                                "http", "https" -> true // Assume network URLs are available
                                else -> true // Assume other schemes are available
                            }

                        if (isAvailable) {
                            android.util.Log.d("AudioPlayerService", "Found available track at index $nextIndex, seeking to it")
                            try {
                                player.seekTo(nextIndex, 0L)
                                // Restore playWhenReady if was playing
                                if (player.playWhenReady) {
                                    player.playWhenReady = true
                                }
                                return
                            } catch (e: Exception) {
                                android.util.Log.e("AudioPlayerService", "Failed to seek to available track", e)
                            }
                        }
                    }

                    attempts++
                }

                // No available tracks found, pause playback
                android.util.Log.w("AudioPlayerService", "No available tracks found, pausing playback")
                try {
                    player.playWhenReady = false
                } catch (e: Exception) {
                    android.util.Log.e("AudioPlayerService", "Failed to pause playback", e)
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                // Metadata (including embedded artwork) was extracted from audio file
                // Media3 1.8: artworkData and artworkUri are NOT deprecated - use them directly
                // Inspired by lissen-android: prefer artworkUri over artworkData for better performance
                val title = mediaMetadata.title?.toString() ?: "Unknown"
                val artist = mediaMetadata.artist?.toString() ?: "Unknown"
                val album = mediaMetadata.albumTitle?.toString()

                // Log metadata extraction for debugging
                android.util.Log.d("AudioPlayerService", "Metadata changed: title=$title, artist=$artist, album=$album")

                // Check if artwork is available (prefer URI, then data)
                val artworkUri = mediaMetadata.artworkUri
                val artworkData = mediaMetadata.artworkData
                val hasArtworkData = artworkData != null && artworkData.isNotEmpty()
                val hasArtworkUri = artworkUri != null

                if (artworkUri != null) {
                    android.util.Log.d("AudioPlayerService", "Artwork URI available: $artworkUri")
                    // Clear embedded artwork path if external URI is available
                    embeddedArtworkPath = null
                } else if (hasArtworkData) {
                    android.util.Log.d("AudioPlayerService", "Embedded artwork data available: ${artworkData?.size ?: 0} bytes")
                    // Save embedded artwork to temporary file for Flutter access
                    try {
                        val cacheDir = applicationContext.cacheDir
                        val artworkFile = File(cacheDir, "embedded_artwork_${System.currentTimeMillis()}.jpg")
                        artworkFile.outputStream().use { it.write(artworkData) }
                        embeddedArtworkPath = artworkFile.absolutePath
                        android.util.Log.i("AudioPlayerService", "Saved embedded artwork to: $embeddedArtworkPath")
                    } catch (e: Exception) {
                        android.util.Log.e("AudioPlayerService", "Failed to save embedded artwork", e)
                        embeddedArtworkPath = null
                    }
                } else {
                    android.util.Log.d("AudioPlayerService", "No artwork available")
                    embeddedArtworkPath = null
                }

                android.util.Log.d("AudioPlayerService", "Media metadata changed:")
                android.util.Log.d("AudioPlayerService", "  Title: $title")
                android.util.Log.d("AudioPlayerService", "  Artist: $artist")
                android.util.Log.d("AudioPlayerService", "  Has artworkData: $hasArtworkData (${artworkData?.size ?: 0} bytes)")
                android.util.Log.d("AudioPlayerService", "  Has artworkUri: $hasArtworkUri (${mediaMetadata.artworkUri})")

                if (hasArtworkData || hasArtworkUri) {
                    android.util.Log.i("AudioPlayerService", "Artwork found! Updating notification...")
                } else {
                    android.util.Log.w("AudioPlayerService", "No artwork found in metadata")
                }

                // Update notification to show artwork
                // MediaSession automatically updates from ExoPlayer
                notificationManager?.updateMetadata(currentMetadata, embeddedArtworkPath)
            }
        }

    /**
     * Unloads player due to inactivity timer expiration.
     *
     * This method:
     * 1. Saves current position (position is already saved periodically and on pause)
     * 2. Stops ExoPlayer and clears MediaItems
     * 3. Releases MediaSession and other resources
     * 4. Removes notification
     * 5. Stops foreground service
     * 6. Stops the service itself
     *
     * Note: Position saving is handled by Media3PlayerService (Dart) which saves
     * periodically and on app lifecycle events. This method focuses on resource cleanup.
     */
    fun unloadPlayerDueToInactivity() {
        android.util.Log.i("AudioPlayerService", "Unloading player due to inactivity")

        try {
            // Log memory usage before unloading (for debugging)
            val runtime = Runtime.getRuntime()
            val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024 // MB
            val maxMemory = runtime.maxMemory() / 1024 / 1024 // MB
            android.util.Log.d(
                "AudioPlayerService",
                "Memory usage before unload: ${usedMemory}MB / ${maxMemory}MB",
            )

            // Save position before unloading (attempt to trigger save through broadcast)
            val activePlayer = getActivePlayer()
            if (activePlayer.mediaItemCount > 0) {
                val currentIndex = activePlayer.currentMediaItemIndex
                val currentPosition = activePlayer.currentPosition
                android.util.Log.d(
                    "AudioPlayerService",
                    "Saving position before unload: track=$currentIndex, position=${currentPosition}ms",
                )

                // Broadcast intent to trigger position saving through MethodChannel
                // This will be handled by MainActivity or AudioPlayerMethodHandler if available
                try {
                    val saveIntent =
                        Intent(ACTION_SAVE_POSITION_BEFORE_UNLOAD).apply {
                            putExtra(EXTRA_TRACK_INDEX, currentIndex)
                            putExtra(EXTRA_POSITION_MS, currentPosition)
                            setPackage(packageName) // Set package for explicit broadcast
                        }
                    sendBroadcast(saveIntent)
                    android.util.Log.d(
                        "AudioPlayerService",
                        "Broadcast sent to trigger position save: track=$currentIndex, position=${currentPosition}ms",
                    )
                } catch (e: Exception) {
                    android.util.Log.w("AudioPlayerService", "Failed to send save position broadcast", e)
                    // Continue with unload even if broadcast fails - position is already saved periodically
                }

                // Note: Position is also saved periodically by Media3PlayerService (every 10-15 seconds)
                // and will be saved on next app resume/pause event, so this is an additional safety measure
            }

            // Stop ExoPlayer and clear MediaItems
            try {
                activePlayer.stop()
                activePlayer.clearMediaItems()
                android.util.Log.d("AudioPlayerService", "ExoPlayer stopped and MediaItems cleared")
            } catch (e: Exception) {
                android.util.Log.w("AudioPlayerService", "Error stopping player", e)
            }

            // Release custom player if exists
            customExoPlayer?.release()
            customExoPlayer = null

            // Release MediaSession
            mediaSession?.release()
            mediaSession = null
            android.util.Log.d("AudioPlayerService", "MediaSession released")

            // Release MediaSessionManager
            mediaSessionManager?.release()
            mediaSessionManager = null
            android.util.Log.d("AudioPlayerService", "MediaSessionManager released")

            // Release timers
            inactivityTimer?.release()
            inactivityTimer = null
            playbackTimer?.release()
            playbackTimer = null
            android.util.Log.d("AudioPlayerService", "Timers released")

            // Remove notification
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    android.util.Log.d("AudioPlayerService", "Foreground service stopped and notification removed")
                } else {
                    // Use AndroidNotificationManager to cancel notification
                    val androidNotificationManager =
                        getSystemService(Context.NOTIFICATION_SERVICE) as AndroidNotificationManager
                    androidNotificationManager.cancel(NOTIFICATION_ID)
                    android.util.Log.d("AudioPlayerService", "Notification cancelled")
                }
            } catch (e: Exception) {
                android.util.Log.w("AudioPlayerService", "Failed to remove notification", e)
            }

            // Clear metadata
            currentMetadata = null
            embeddedArtworkPath = null

            // Stop the service
            android.util.Log.i("AudioPlayerService", "Stopping service due to inactivity")

            // Log memory usage after cleanup (for debugging)
            val runtimeAfter = Runtime.getRuntime()
            val usedMemoryAfter = (runtimeAfter.totalMemory() - runtimeAfter.freeMemory()) / 1024 / 1024 // MB
            android.util.Log.d(
                "AudioPlayerService",
                "Memory usage after cleanup: ${usedMemoryAfter}MB / ${runtimeAfter.maxMemory() / 1024 / 1024}MB",
            )

            stopSelf()
        } catch (e: Exception) {
            android.util.Log.e("AudioPlayerService", "Error unloading player due to inactivity", e)
            // Still try to stop the service even if there was an error
            try {
                stopSelf()
            } catch (e2: Exception) {
                android.util.Log.e("AudioPlayerService", "Failed to stop service", e2)
            }
        }
    }

    override fun onDestroy() {
        instance = null
        isFullyInitializedFlag = false

        // ExoPlayer manages AudioFocus automatically, no need to abandon manually

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
