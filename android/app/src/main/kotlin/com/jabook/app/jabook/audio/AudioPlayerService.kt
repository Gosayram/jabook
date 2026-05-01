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
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerNotificationManager
import com.jabook.app.jabook.audio.processors.BookLoudnessCompensator
import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.util.LogUtils
import com.jabook.app.jabook.utils.capitalizeFirst
import com.jabook.app.jabook.utils.loggingCoroutineExceptionHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject

/** Audio player service using Media3 ExoPlayer with Dagger Hilt DI. */
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
    internal var playerNotificationManager: PlayerNotificationManager? = null

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
    internal var visualizerBridgeJob: kotlinx.coroutines.Job? = null

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
    /** Notification content intent factory (extracted from service). */
    internal val notificationIntentFactory = NotificationIntentFactory(this)

    internal val mediaSessionLayoutHelper =
        MediaSessionLayoutHelper(playerServiceScope) { mediaSession }

    internal val foregroundNotificationCoordinator by lazy {
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

    internal val listeningSessionTracker: ListeningSessionTracker by lazy {
        ListeningSessionTracker(
            repository = listeningSessionRepository,
            scope = playerServiceScope,
            getCurrentBookId = { currentGroupPath },
            getCurrentPositionMs = { getActivePlayer().currentPosition },
            getCurrentSpeed = { getActivePlayer().playbackParameters.speed },
            getCurrentChapterIndex = { getActivePlayer().currentMediaItemIndex },
        )
    }

    internal val periodicPositionSaver: PeriodicPositionSaver by lazy {
        PeriodicPositionSaver(
            scope = playerServiceScope,
            repository = playbackPositionRepository,
            getActivePlayer = { getActivePlayer() },
            getCurrentBookId = { currentGroupPath },
        )
    }

    // TASK-VERM-04: Extracted facades for delegation reduction
    private val sleepTimerFacade: SleepTimerFacade by lazy {
        SleepTimerFacade(
            getSleepTimerManager = { sleepTimerManager },
            getPlaybackTimer = { playbackTimer },
            getActivePlayer = { getActivePlayer() },
            updateCrashContext = { updateCrashPlaybackContext() },
        )
    }

    private val playbackLifecycleActions: PlaybackLifecycleActions by lazy {
        PlaybackLifecycleActions(
            getPhoneCallListener = { phoneCallListener },
            getListeningSessionTracker = { listeningSessionTracker },
            getPeriodicPositionSaver = { periodicPositionSaver },
            updateCrashContext = { updateCrashPlaybackContext() },
        )
    }

    private val visualizerFacade: VisualizerFacade by lazy {
        VisualizerFacade(
            getAudioVisualizerManager = { audioVisualizerManager },
            getExoPlayerAudioSessionId = { exoPlayer.audioSessionId },
        )
    }

    private val releaseHandler: AudioServiceReleaseHandler by lazy {
        AudioServiceReleaseHandler(getService = { this })
    }

    /** Facade for player configuration and active player resolution. */
    internal val playerFacade =
        PlayerFacade(
            getPlayerConfigurator = { playerConfigurator },
            getExoPlayer = { exoPlayer },
            getCrossFadePlayer = { crossFadePlayer },
            getCrossfadeHandler = { crossfadeHandler },
        )

    /** Manages crash diagnostics context and book completion tracking. */
    internal val playbackContextHelper =
        PlaybackContextHelper(
            getActivePlayer = { getActivePlayer() },
            getCurrentMetadata = { currentMetadata },
            getPlaylistManager = { playlistManager },
            isSleepTimerEndOfChapter = { isSleepTimerEndOfChapter() },
            isSleepTimerEndOfTrack = { isSleepTimerEndOfTrack() },
            isSleepTimerActive = { isSleepTimerActive() },
        )

    private val commandRouter: AudioServiceCommandRouter by lazy {
        AudioServiceCommandRouter(
            getPlaybackController = { playbackController },
            getPositionManager = { positionManager },
            getMetadataManager = { metadataManager },
            getPlayerStateHelper = { playerStateHelper },
            getUnloadManager = { unloadManager },
            getActivePlayer = { getActivePlayer() },
            getPlaybackLifecycleActions = { playbackLifecycleActions },
            resetBookCompletionIfNeeded = { resetBookCompletionIfNeeded(it) },
            updateCrashPlaybackContext = { updateCrashPlaybackContext() },
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

    @Volatile
    internal var isFullyInitializedFlag = false

    /** Checks if the service is fully initialized and ready to use. */
    public fun isFullyInitialized(): Boolean = isFullyInitializedFlag

    // Helper methods for AudioServiceReleaseHandler to check lateinit initialization
    internal fun isPlaybackPositionRepositoryInitialized(): Boolean = ::playbackPositionRepository.isInitialized

    internal fun isAudioOutputManagerInitialized(): Boolean = ::audioOutputManager.isInitialized

    internal fun isPlaybackEnhancerServiceInitialized(): Boolean = ::playbackEnhancerService.isInitialized

    internal fun isAudioVisualizerStateBridgeInitialized(): Boolean = ::audioVisualizerStateBridge.isInitialized

    // Flag to indicate if "Minimal Notification" mode is enabled
    // If true, artwork loading will be skipped to show a smaller notification
    internal var isMinimalNotification = false

    public fun getMediaSession(): MediaSession? = mediaLibrarySession ?: mediaSession

    /** Delegates to [NotificationIntentFactory.getSingleTopActivity]. */
    internal fun getSingleTopActivity(): PendingIntent? = notificationIntentFactory.getSingleTopActivity()

    /** Delegates to [NotificationIntentFactory.getBackStackedActivity]. */
    internal fun getBackStackedActivity(): PendingIntent? = notificationIntentFactory.getBackStackedActivity()

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

            AudioPlayerServiceInitializer(this).let { initializer ->
                initializer.initialize()
                initializer.postInitialize()
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

    public fun startTimer(
        delayInSeconds: Double,
        option: Int = 0,
    ): Unit = sleepTimerFacade.startTimer(delayInSeconds, option)

    public fun stopTimer(): Unit = sleepTimerFacade.stopTimer()

    internal val playerListener: PlayerListener?
        get() = playerFacade.playerListener

    internal fun configurePlayer(): Unit = playerFacade.configurePlayer()

    @OptIn(UnstableApi::class)
    public fun configureExoPlayer(settings: com.jabook.app.jabook.audio.processors.AudioProcessingSettings): Unit =
        playerFacade.configureExoPlayer(settings)

    internal fun getActivePlayer(): ExoPlayer = playerFacade.getActivePlayer()

    public fun triggerCrossfadeTransition(): Unit = playerFacade.triggerCrossfadeTransition()

    /** Delegates to [PlaybackContextHelper.updateActualTrackIndex]. */
    internal fun updateActualTrackIndex(index: Int) = playbackContextHelper.updateActualTrackIndex(index)

    private fun updateCrashPlaybackContext() = playbackContextHelper.updateCrashPlaybackContext()

    private fun resetBookCompletionIfNeeded(actionLabel: String) = playbackContextHelper.resetBookCompletionIfNeeded(actionLabel)

    /** Delegates playlist setup to [PlaylistManager] with loudness compensation. */
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

    public fun seekToTrackAndPosition(
        trackIndex: Int,
        positionMs: Long,
    ): Unit = commandRouter.seekToTrackAndPosition(trackIndex, positionMs)

    public fun updateMetadata(metadata: Map<String, String>): Unit = commandRouter.updateMetadata(metadata)

    public val isPlaying: Boolean
        get() = commandRouter.isPlaying

    public fun play(): Unit = commandRouter.play()

    public fun pause(): Unit = commandRouter.pause()

    public fun stop(): Unit = commandRouter.stop()

    internal fun savePositionToRepository() {
        periodicPositionSaver.save()
    }

    internal fun finishListeningSessionIfActive(reason: String) {
        listeningSessionTracker.onPlaybackStopped(reason)
    }

    /** Delegates to [ServiceLifecycleManager.stopAndCleanup]. */
    public fun stopAndCleanup() {
        lifecycleManager?.stopAndCleanup() ?: run {
            LogUtils.e("AudioPlayerService", "ServiceLifecycleManager not initialized for stopAndCleanup")
            // Fallback manual cleanup if needed, or just log error
        }

        headsetAutoplayHandler?.stopListening()
    }

    internal fun saveCurrentPosition(): Unit = commandRouter.saveCurrentPosition()

    public fun seekTo(positionMs: Long): Unit = commandRouter.seekTo(positionMs)

    public fun setSpeed(speed: Float): Unit = commandRouter.setSpeed(speed)

    public fun setRepeatMode(repeatMode: Int): Unit = commandRouter.setRepeatMode(repeatMode)

    public fun getRepeatMode(): Int = commandRouter.getRepeatMode()

    public fun getPlaybackSpeed(): Float = commandRouter.getSpeed()

    public fun setShuffleModeEnabled(shuffleModeEnabled: Boolean): Unit = commandRouter.setShuffleModeEnabled(shuffleModeEnabled)

    public fun getShuffleModeEnabled(): Boolean = commandRouter.getShuffleModeEnabled()

    // --- Sleep timer delegation (via SleepTimerFacade) ---
    public fun setSleepTimerMinutes(minutes: Int): Unit = sleepTimerFacade.setSleepTimerMinutes(minutes)

    public fun setSleepTimerEndOfChapter(): Unit = sleepTimerFacade.setSleepTimerEndOfChapter()

    public fun setSleepTimerEndOfChapterOrFallback(): Boolean = sleepTimerFacade.setSleepTimerEndOfChapterOrFallback()

    public fun setSleepTimerEndOfTrack(): Unit = sleepTimerFacade.setSleepTimerEndOfTrack()

    public fun cancelSleepTimer(): Unit = sleepTimerFacade.cancelSleepTimer()

    public fun getSleepTimerRemainingSeconds(): Int? = sleepTimerFacade.getSleepTimerRemainingSeconds()

    public fun isSleepTimerActive(): Boolean = sleepTimerFacade.isSleepTimerActive()

    public fun isSleepTimerEndOfChapter(): Boolean = sleepTimerFacade.isSleepTimerEndOfChapter()

    public fun isSleepTimerEndOfTrack(): Boolean = sleepTimerFacade.isSleepTimerEndOfTrack()

    // --- Visualizer delegation (via VisualizerFacade) ---
    public fun getAudioSessionId(): Int = visualizerFacade.getAudioSessionId()

    public fun getVisualizerWaveformData(): kotlinx.coroutines.flow.StateFlow<FloatArray>? = visualizerFacade.getWaveformData()

    public fun initializeVisualizer(): Unit = visualizerFacade.initialize()

    public fun setVisualizerEnabled(enabled: Boolean): Unit = visualizerFacade.setEnabled(enabled)

    public fun next(): Unit = commandRouter.next()

    public fun previous(): Unit = commandRouter.previous()

    public fun seekToTrack(index: Int): Unit = commandRouter.seekToTrack(index)

    public fun setPlaybackProgress(
        filePaths: List<String>,
        progressSeconds: Double?,
    ): Unit = commandRouter.setPlaybackProgress(filePaths, progressSeconds)

    public fun rewind(seconds: Int = 15): Unit = commandRouter.rewind(seconds)

    public fun forward(seconds: Int = 30): Unit = commandRouter.forward(seconds)

    /** Stops playback and releases resources. Delegates to [AudioServiceReleaseHandler]. */
    public fun stopAndRelease() {
        releaseHandler.stopAndRelease()
    }

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

    public fun getCurrentPosition(): Long = commandRouter.getCurrentPosition()

    public fun getDuration(): Long = commandRouter.getDuration()

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

    public fun getPlayerState(): Map<String, Any> = commandRouter.getPlayerState()

    public fun getCurrentMediaItemInfo(): Map<String, Any?> = commandRouter.getCurrentMediaItemInfo()

    public fun extractArtworkFromFile(filePath: String): String? = commandRouter.extractArtworkFromFile(filePath)

    public fun getPlaylistInfo(): Map<String, Any> = commandRouter.getPlaylistInfo()

    public fun unloadPlayerDueToInactivity(): Unit = commandRouter.unloadPlayerDueToInactivity()

    override fun onTaskRemoved(rootIntent: Intent?) {
        lifecycleManager?.onTaskRemoved() ?: super.onTaskRemoved(rootIntent)
    }

    /** Public wrapper for protected [setMediaNotificationProvider]. Called by [AudioPlayerServiceInitializer]. */
    @OptIn(UnstableApi::class)
    internal fun setNotificationProvider(provider: MediaNotification.Provider) {
        setMediaNotificationProvider(provider)
    }

    @OptIn(UnstableApi::class)
    internal fun setupPlayerNotificationManager() {
        // Guard: Prevent duplicate initialization
        if (playerNotificationManager != null) {
            LogUtils.w("AudioPlayerService", "PlayerNotificationManager already initialized, skipping")
            return
        }
        playerNotificationManager =
            PlayerNotificationSetup(
                service = this,
                scope = playerServiceScope,
                notificationHelper = notificationHelper ?: NotificationHelper(this),
                foregroundNotificationCoordinator = foregroundNotificationCoordinator,
                getActivePlayer = { exoPlayer },
                getMediaLibrarySession = { mediaLibrarySession },
            ).setup()
        return
    }

    private fun cleanupExistingComponents() {
        releaseHandler.cleanupExistingComponents()
    }

    override fun onDestroy() {
        releaseHandler.releaseRuntimeComponents(cancelServiceScopeChildren = true)
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
