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
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.TaskStackBuilder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import com.jabook.app.jabook.audio.processors.BookLoudnessCompensator
import com.jabook.app.jabook.compose.ComposeMainActivity
import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.crash.CrashDiagnostics
import com.jabook.app.jabook.util.LogUtils
import com.jabook.app.jabook.utils.capitalizeFirst
import com.jabook.app.jabook.utils.loggingCoroutineExceptionHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

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
    public lateinit var playbackPositionRepository:
        com.jabook.app.jabook.audio.data.repository.PlaybackPositionRepository

    @Inject
    public lateinit var listeningSessionRepository:
        com.jabook.app.jabook.audio.data.repository.ListeningSessionRepository

    @Inject
    public lateinit var audioOutputManager: AudioOutputManager

    @Inject
    public lateinit var playbackEnhancerService: PlaybackEnhancerService

    @Inject
    public lateinit var audioPreferences: com.jabook.app.jabook.audio.data.local.datastore.AudioPreferences

    @Inject
    public lateinit var audioVisualizerStateBridge: AudioVisualizerStateBridge

    // AppDispatchers for testable coroutine dispatchers
    @Inject
    public lateinit var dispatchers: com.jabook.app.jabook.compose.core.di.AppDispatchers

    // Book loudness compensation for consistent volume across books
    @Inject
    public lateinit var booksDao: BooksDao

    internal val bookLoudnessCompensator: BookLoudnessCompensator = BookLoudnessCompensator()

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

    // PlayerNotificationManager for direct notification control (androidx.media3.ui)
    // Replaces MediaNotification.Provider which doesn't work with background service warmup
    private var playerNotificationManager: PlayerNotificationManager? = null

    internal var notificationHelper: NotificationHelper? = null
    internal var mediaSessionManager: MediaSessionManager? = null
    internal var playbackTimer: PlaybackTimer? = null
    internal var inactivityTimer: InactivityTimer? = null
    internal var playlistManager: PlaylistManager? = null

    // Current metadata delegated to PlaylistManager
    internal val currentMetadata: Map<String, String>?
        get() = playlistManager?.currentMetadata

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
    private var visualizerBridgeJob: kotlinx.coroutines.Job? = null

    // Phone call listener for automatic resume after calls
    internal var phoneCallListener: PhoneCallListener? = null

    // Headset and Media Button handlers (Quick Wins)
    internal var headsetAutoplayHandler: HeadsetAutoplayHandler? = null
    internal var mediaButtonHandler: MediaButtonHandler? = null

    /** BP-13.3: Audio output device routing monitor. */
    internal var audioOutputDeviceMonitor: AudioOutputDeviceMonitor? = null

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

    internal val currentFilePaths: List<String>?
        get() = playlistManager?.currentFilePaths

    // Store current groupPath delegated to PlaylistManager
    internal val currentGroupPath: String?
        get() = playlistManager?.currentGroupPath

    // Cache for file durations (filePath -> duration in ms)
    // According to best practices: cache duration after getting it from player (primary source)
    // or MediaMetadataRetriever (fallback). This avoids repeated calls and improves performance.
    // This cache is synchronized with database via MethodChannel (Flutter side).
    // DurationManager handles caching and database retrieval
    internal val durationManager = DurationManager()


    internal var customExoPlayer: ExoPlayer? = null

    // Crossfade components
    internal var crossFadePlayer: CrossFadePlayer? = null
    internal var crossfadeHandler: CrossfadeHandler? = null

    internal val playerServiceScope =
        CoroutineScope(
            Dispatchers.Main + SupervisorJob() + loggingCoroutineExceptionHandler("AudioPlayerService"),
        )

    // MediaSession custom layout helper (extracted from service)
    private val mediaSessionLayoutHelper =
        MediaSessionLayoutHelper(playerServiceScope) { mediaSession }

    private val foregroundNotificationCoordinator by lazy {
        ForegroundNotificationCoordinator(
            policy =
                ForegroundServiceStartPolicy(
                    logDebug = { message ->
                        LogUtils.d("AudioPlayerService", message)
                    },
                    logWarn = { message, throwable ->
                        LogUtils.w("AudioPlayerService", message, throwable)
                    },
                ),
            serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private val listeningSessionTracker: ListeningSessionTracker by lazy {
        ListeningSessionTracker(
            repository = listeningSessionRepository,
            scope = playerServiceScope,
            getCurrentBookId = { currentGroupPath },
            getCurrentPositionMs = { getActivePlayer().currentPosition },
            getCurrentSpeed = { getActivePlayer().playbackParameters.speed },
            getCurrentChapterIndex = { getActivePlayer().currentMediaItemIndex },
        )
    }

    private val periodicPositionSaver: PeriodicPositionSaver by lazy {
        PeriodicPositionSaver(
            scope = playerServiceScope,
            repository = playbackPositionRepository,
            getActivePlayer = { getActivePlayer() },
            getCurrentBookId = { currentGroupPath },
        )
    }

    internal fun markStoppedBySleepTimer() {
        SleepTimerPersistence.markStoppedBySleepTimer(
            getSharedPreferences(SleepTimerPersistence.PREFS_NAME, Context.MODE_PRIVATE),
        )
    }

    internal fun consumeStoppedBySleepTimerFlag(): Boolean =
        SleepTimerPersistence.consumeStoppedBySleepTimerFlag(
            getSharedPreferences(SleepTimerPersistence.PREFS_NAME, Context.MODE_PRIVATE),
        )

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

    /** Checks if the service is fully initialized and ready to use. */
    public fun isFullyInitialized(): Boolean = isFullyInitializedFlag

    // Flag to indicate if "Minimal Notification" mode is enabled
    // If true, artwork loading will be skipped to show a smaller notification
    internal var isMinimalNotification = false

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
        LogUtils.i("AudioPlayerService", "onCreate() started (PID=${android.os.Process.myPid()})")

        try {
            PlayerPerformanceLogger.start("service_onCreate")

            // Clean up existing components if onCreate() is called multiple times
            cleanupExistingComponents()

            super.onCreate()
            instance = this
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
                    service = this,
                    notificationId = NotificationHelper.NOTIFICATION_ID,
                    primaryNotification = initialNotification,
                    fallbackNotificationProvider = { helper.createFallbackNotification() },
                    event = "service_on_create",
                )
            if (foregroundStartResult == ForegroundStartResult.FAILED) {
                LogUtils.e("AudioPlayerService", "Failed to start foreground with both notifications")
            } else {
                LogUtils.d("AudioPlayerService", "startForeground() completed: $foregroundStartResult")
            }

            // Set MediaSessionService.Listener for handling foreground service start exceptions
            // This is required for Android 12+ when system doesn't allow foreground service start
            setListener(MediaSessionServiceListener(this))
            PlayerPerformanceLogger.log("Service", "listener set")

            // Initialize CrossFadePlayer BEFORE AudioPlayerServiceInitializer
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
            AudioPlayerServiceInitializer(this).initialize()

            // Restore playback speed (lissen-android pattern)
            playerServiceScope.launch {
                try {
                    val savedSpeed = audioPreferences.playbackSpeed.first()
                    withContext(Dispatchers.Main) {
                        LogUtils.d("AudioPlayerService", "Restoring playback speed: ${savedSpeed}x")
                        exoPlayer.setPlaybackSpeed(savedSpeed)
                    }
                } catch (e: Exception) {
                    LogUtils.e("AudioPlayerService", "Failed to restore playback speed", e)
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
            }

            setupAudioOutputManager()
            playbackEnhancerService.initialize()

            // Initialize AudioVisualizerManager
            audioVisualizerManager = AudioVisualizerManager(this)
            visualizerBridgeJob?.cancel()
            visualizerBridgeJob =
                playerServiceScope.launch {
                    audioVisualizerManager
                        ?.waveformData
                        ?.collect { waveform ->
                            audioVisualizerStateBridge.updateWaveform(waveform)
                        }
                }
            PlayerPerformanceLogger.log("Service", "initialization complete")
            PlayerPerformanceLogger.summary()
            LogUtils.i("AudioPlayerService", "onCreate() completed successfully")
        } catch (e: Exception) {
            LogUtils.e("AudioPlayerService", "onCreate() failed", e)
            throw e
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
        updateCrashPlaybackContext()
    }

    private fun updateCrashPlaybackContext() {
        val player = getActivePlayer()
        val effectiveTitle =
            currentMetadata?.get("title")
                ?: currentMetadata?.get("bookTitle")
                ?: currentMetadata?.get("album")
                ?: player.mediaMetadata.albumTitle?.toString()
                ?: player.mediaMetadata.title?.toString()
        val sleepMode =
            when {
                isSleepTimerEndOfChapter() -> "chapter_end"
                isSleepTimerEndOfTrack() -> "track_end"
                isSleepTimerActive() -> "fixed"
                else -> "none"
            }
        CrashDiagnostics.setPlaybackContext(
            bookTitle = effectiveTitle,
            playerState = player.playbackState.toString(),
            playbackSpeed = player.playbackParameters.speed,
            sleepMode = sleepMode,
        )
    }

    private fun resetBookCompletionIfNeeded(actionLabel: String) {
        if (playlistManager?.isBookCompleted != true) return
        LogUtils.i("AudioPlayerService", "$actionLabel resetting completion flag")
        playlistManager?.isBookCompleted = false
        playlistManager?.lastCompletedTrackIndex = -1
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
        // Apply book loudness compensation when switching to a different book
        if (groupPath != null && groupPath != currentGroupPath) {
            val bookId = groupPath.substringAfterLast("/").takeIf { it.isNotBlank() } ?: groupPath
            bookLoudnessCompensator.applyCompensation(bookId, booksDao, playerServiceScope) { getActivePlayer() }
        }

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
        resetBookCompletionIfNeeded("Manual seekToTrackAndPosition($trackIndex, $positionMs)")
        playbackController?.seekToTrackAndPosition(trackIndex, positionMs) ?: run {
            LogUtils.e("AudioPlayerService", "PlaybackController not initialized")
        }
    }

    public fun updateMetadata(metadata: Map<String, String>) {
        metadataManager?.updateMetadata(metadata) ?: run {
            LogUtils.e("AudioPlayerService", "MetadataManager not initialized")
        }
        updateCrashPlaybackContext()
    }

    public val isPlaying: Boolean
        get() = getActivePlayer().isPlaying

    public fun play() {
        resetBookCompletionIfNeeded("play()")

        playbackController?.play() ?: run {
            LogUtils.e("AudioPlayerService", "PlaybackController not initialized")
            return
        }

        // Start listening for phone calls when playback starts
        phoneCallListener?.startListening()

        listeningSessionTracker.onPlaybackStarted()
        periodicPositionSaver.start()
        updateCrashPlaybackContext()
    }

    public fun pause() {
        playbackController?.pause() ?: run {
            LogUtils.e("AudioPlayerService", "PlaybackController not initialized")
            return
        }

        listeningSessionTracker.onPlaybackStopped(reason = "pause")
        periodicPositionSaver.save()
        periodicPositionSaver.stop()
        updateCrashPlaybackContext()
    }

    public fun stop() {
        playbackController?.stop() ?: run {
            LogUtils.e("AudioPlayerService", "PlaybackController not initialized")
            return
        }

        // Stop listening for phone calls when playback stops
        phoneCallListener?.stopListening()

        listeningSessionTracker.onPlaybackStopped(reason = "stop")
        periodicPositionSaver.save()
        periodicPositionSaver.stop()
        updateCrashPlaybackContext()
    }

    internal fun savePositionToRepository() { periodicPositionSaver.save() }

    internal fun finishListeningSessionIfActive(reason: String) {
        listeningSessionTracker.onPlaybackStopped(reason)
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

    public fun seekTo(positionMs: Long) { playbackController?.seekTo(positionMs) }

    public fun setSpeed(speed: Float) {
        playbackController?.setSpeed(speed)
        updateCrashPlaybackContext()
    }

    public fun setRepeatMode(repeatMode: Int) { playbackController?.setRepeatMode(repeatMode) }
    public fun getRepeatMode(): Int = playbackController?.getRepeatMode() ?: Player.REPEAT_MODE_OFF
    public fun getPlaybackSpeed(): Float = playbackController?.getSpeed() ?: 1.0f
    public fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) { playbackController?.setShuffleModeEnabled(shuffleModeEnabled) }
    public fun getShuffleModeEnabled(): Boolean = playbackController?.getShuffleModeEnabled() ?: false

    // --- Sleep timer delegation ---
    public fun setSleepTimerMinutes(minutes: Int) { sleepTimerManager?.setSleepTimerMinutes(minutes); updateCrashPlaybackContext() }
    public fun setSleepTimerEndOfChapter() { sleepTimerManager?.setSleepTimerEndOfChapter(); updateCrashPlaybackContext() }
    public fun setSleepTimerEndOfChapterOrFallback(): Boolean {
        return sleepTimerManager?.setSleepTimerEndOfChapterOrFallback(getActivePlayer().mediaItemCount > 1) ?: false
    }
    public fun setSleepTimerEndOfTrack() { sleepTimerManager?.setSleepTimerEndOfTrack(); updateCrashPlaybackContext() }
    public fun cancelSleepTimer() { sleepTimerManager?.cancelSleepTimer(); updateCrashPlaybackContext() }
    public fun getSleepTimerRemainingSeconds(): Int? = sleepTimerManager?.getSleepTimerRemainingSeconds()
    public fun isSleepTimerActive(): Boolean = sleepTimerManager?.isSleepTimerActive() ?: false
    public fun isSleepTimerEndOfChapter(): Boolean = sleepTimerManager?.sleepTimerEndOfChapter == true
    public fun isSleepTimerEndOfTrack(): Boolean = sleepTimerManager?.sleepTimerEndOfTrack == true

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
        resetBookCompletionIfNeeded("Manual next()")
        playbackController?.next() ?: run {
            LogUtils.e("AudioPlayerService", "PlaybackController not initialized")
        }
    }

    public fun previous() {
        resetBookCompletionIfNeeded("Manual previous()")
        playbackController?.previous() ?: run {
            LogUtils.e("AudioPlayerService", "PlaybackController not initialized")
        }
    }

    public fun seekToTrack(index: Int) {
        resetBookCompletionIfNeeded("Manual seekToTrack($index)")
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
        LogUtils.d(
            "AudioPlayerService",
            "Updated skip durations: rewind=${rewindSeconds}s, forward=${forwardSeconds}s",
        )
    }

    /** Updates MediaSession custom layout via [MediaSessionLayoutHelper]. */
    public fun updateMediaSessionCommands(
        rewindSeconds: Int,
        forwardSeconds: Int,
    ) {
        mediaSessionLayoutHelper.updateSmart(rewindSeconds, forwardSeconds)
    }

    /** Sets initial CustomLayout for MediaSession via [MediaSessionLayoutHelper]. */
    internal fun setInitialCustomLayout() {
        mediaSessionLayoutHelper.setInitialLayout()
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
        playerNotificationManager = PlayerNotificationSetup(
            service = this,
            scope = playerServiceScope,
            notificationHelper = notificationHelper ?: NotificationHelper(this),
            foregroundNotificationCoordinator = foregroundNotificationCoordinator,
            getActivePlayer = { exoPlayer },
            getMediaLibrarySession = { mediaLibrarySession },
        ).setup()
        return
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
        val hasExistingComponents =
            mediaLibrarySession != null ||
                serviceMediaController != null ||
                crossFadePlayer != null ||
                audioVisualizerManager != null ||
                visualizerBridgeJob != null
        if (hasExistingComponents) {
            LogUtils.w(
                "AudioPlayerService",
                "onCreate() called with existing runtime components, performing pre-init cleanup",
            )
        }
        releaseRuntimeComponents(cancelServiceScopeChildren = true)
        if (hasExistingComponents) {
            LogUtils.i("AudioPlayerService", "Existing components cleaned up")
        }
    }

    override fun onDestroy() {
        releaseRuntimeComponents(cancelServiceScopeChildren = true)
        // Delegate to lifecycle manager
        lifecycleManager?.onDestroy()
        super.onDestroy()
    }

    private fun releaseRuntimeComponents(cancelServiceScopeChildren: Boolean) {
        periodicPositionSaver.stop()
        if (cancelServiceScopeChildren) {
            playerServiceScope.coroutineContext.cancelChildren()
        }

        playerNotificationManager?.setPlayer(null)
        playerNotificationManager = null

        if (::audioOutputManager.isInitialized) {
            audioOutputManager.stopMonitoring()
        }
        if (::playbackEnhancerService.isInitialized) {
            playbackEnhancerService.release()
        }

        sleepTimerManager?.release()
        sleepTimerManager = null

        inactivityTimer?.release()
        inactivityTimer = null

        playbackTimer?.release()
        playbackTimer = null
        crossfadeHandler?.stopMonitoring()
        crossfadeHandler = null
        crossFadePlayer?.release()
        crossFadePlayer = null

        audioVisualizerManager?.release()
        audioVisualizerManager = null
        visualizerBridgeJob?.cancel()
        visualizerBridgeJob = null
        if (::audioVisualizerStateBridge.isInitialized) {
            audioVisualizerStateBridge.reset()
        }

        phoneCallListener?.stopListening()
        phoneCallListener = null

        headsetAutoplayHandler?.stopListening()
        headsetAutoplayHandler = null

        // BP-13.3: Unregister audio output device monitor
        audioOutputDeviceMonitor?.unregister()
        audioOutputDeviceMonitor = null

        serviceMediaController?.release()
        serviceMediaController = null

        mediaSessionManager?.release()
        mediaSessionManager = null

        mediaLibrarySession?.release()
        mediaLibrarySession = null
        mediaSession = null

        mediaSessionLayoutHelper.release()

        playerConfigurator?.release()

        isFullyInitializedFlag = false
    }

    /**
     * This method is only required to be implemented on Android 12 or above when an attempt is made
     * by a media controller to resume playback when the MediaSessionService is in the background.
     *
     * This can happen when:
     * - Notification permission is not granted (Android 13+)
     */
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        val session = mediaLibrarySession
        if (session == null) {
            LogUtils.w(
                "AudioPlayerService",
                "Rejecting controller ${controllerInfo.packageName}: MediaLibrarySession is not ready yet",
            )
            return null
        }
        if (!isFullyInitializedFlag) {
            LogUtils.w(
                "AudioPlayerService",
                "Accepting controller ${controllerInfo.packageName} with partially initialized service; session is available",
            )
        }
        return session
    }
}
