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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.jabook.app.jabook.audio.processors.BookLoudnessCompensator
import com.jabook.app.jabook.audio.processors.LufsAnalysisWorker
import com.jabook.app.jabook.compose.data.local.dao.BooksDao
import com.jabook.app.jabook.util.LogUtils
import com.jabook.app.jabook.utils.loggingCoroutineExceptionHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/** Audio player service using Media3 ExoPlayer with Dagger Hilt DI. */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
public class AudioPlayerService : MediaLibraryService() {
    @Inject
    public lateinit var exoPlayer: ExoPlayer

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
    public lateinit var updatePlaybackProgressUseCase:
        com.jabook.app.jabook.compose.domain.usecase.player.UpdatePlaybackProgressUseCase

    @Inject
    internal lateinit var crashSafePositionWriter: CrashSafePositionWriter

    @Inject
    public lateinit var listeningSessionRepository:
        com.jabook.app.jabook.audio.data.repository.ListeningSessionRepository

    @Inject
    public lateinit var audioOutputManager: AudioOutputManager

    internal var audioOutputPlayerListener: Player.Listener? = null
    internal var audioOutputPlayerTarget: ExoPlayer? = null

    @Inject
    public lateinit var audioPreferences: com.jabook.app.jabook.audio.data.local.datastore.AudioPreferences

    @Inject
    public lateinit var audioVisualizerStateBridge: AudioVisualizerStateBridge

    // Audio fader for smooth volume transitions (P-14: fade out before sleep timer expiry)
    @Inject
    public lateinit var audioFader: AudioFader

    @Inject
    public lateinit var autoBookmarkTrigger: AutoBookmarkTrigger

    // AppDispatchers for testable coroutine dispatchers
    @Inject
    public lateinit var dispatchers: com.jabook.app.jabook.compose.core.di.AppDispatchers

    // Book loudness compensation for consistent volume across books
    @Inject
    public lateinit var booksDao: BooksDao

    @Inject
    public lateinit var workManager: WorkManager

    internal val bookLoudnessCompensator: BookLoudnessCompensator = BookLoudnessCompensator()

    @Volatile
    internal var mediaLibrarySession: MediaLibrarySession? = null

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
    internal var sessionExtrasJob: kotlinx.coroutines.Job? = null

    // Phone call listener for automatic resume after calls
    internal var phoneCallListener: PhoneCallListener? = null

    // Headset and Media Button handlers (Quick Wins)
    internal var headsetAutoplayHandler: HeadsetAutoplayHandler? = null
    internal var mediaButtonHandler: MediaButtonHandler? = null

    /** BP-13.3: Audio output device routing monitor. */

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

    private var chapterNotificationJob: Job? = null
    private var notificationProviderRef: AudioPlayerNotificationProvider? = null

    // ponytail: mutable field for notification subtitle override, stored on AudioPlayerNotificationProvider
    @Volatile
    internal var notificationSubtitleOverride: String? = null

    // MediaSession custom layout helper (extracted from service)
    /** Notification content intent factory (extracted from service). */
    internal val notificationIntentFactory = NotificationIntentFactory(this) { currentGroupPath }

    internal val mediaSessionLayoutHelper =
        MediaSessionLayoutHelper(this, playerServiceScope) { mediaLibrarySession }

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
            updateCanonicalProgress = { bookId, position, chapterIndex ->
                updatePlaybackProgressUseCase(bookId, position, chapterIndex)
                Unit
            },
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
            getCurrentBookId = { currentGroupPath },
            isAudioOffloaded = { isAudioVisualizerStateBridgeInitialized() && audioVisualizerStateBridge.isAudioOffloaded.value },
        )

    private val commandRouter: AudioServiceCommandRouter by lazy {
        AudioServiceCommandRouter(
            getPlaybackController = { playbackController },
            getPositionManager = { positionManager },
            getMetadataManager = { metadataManager },
            getPlayerStateHelper = { playerStateHelper },
            getUnloadManager = { unloadManager },
            getActivePlayer = { getActivePlayer() },
            getCrossFadePlayer = { crossFadePlayer },
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

    internal fun consumePhoneCallBookmarkCreatedFlag(): Boolean {
        val wasSet = phoneCallBookmarkCreated
        if (wasSet) phoneCallBookmarkCreated = false
        return wasSet
    }

    // Limited dispatcher for MediaItem creation (max 16 parallel tasks)
    // Increased parallelism for faster loading on modern devices with fast storage
    // Modern devices can handle more concurrent I/O operations efficiently
    @OptIn(ExperimentalCoroutinesApi::class)
    internal val mediaItemDispatcher = Dispatchers.IO.limitedParallelism(16)

    public companion object {
        // TASK-PLAYER-40: set when auto-bookmark was created during a call
        @Volatile
        internal var phoneCallBookmarkCreated = false

        public const val ACTION_EXIT_APP: String = "com.jabook.app.jabook.audio.EXIT_APP"

        // Playback action constants (migrated from deprecated NotificationManager)
        public const val ACTION_PLAY: String = "com.jabook.app.jabook.audio.PLAY"
        public const val ACTION_PAUSE: String = "com.jabook.app.jabook.audio.PAUSE"
        public const val ACTION_PLAY_PAUSE: String = "com.jabook.app.jabook.audio.PLAY_PAUSE"
        public const val ACTION_NEXT: String = "com.jabook.app.jabook.audio.NEXT"
        public const val ACTION_PREVIOUS: String = "com.jabook.app.jabook.audio.PREVIOUS"
        public const val ACTION_REWIND: String = "com.jabook.app.jabook.audio.REWIND"
        public const val ACTION_FORWARD: String = "com.jabook.app.jabook.audio.FORWARD"
        public const val ACTION_STOP: String = "com.jabook.app.jabook.audio.STOP"

        @Volatile
        private var instance: AudioPlayerService? = null

        /**
         * @deprecated Hold a lifecycle-aware [MediaController] in the UI layer instead.
         * This method is kept for backward compatibility during migration.
         */
        @Deprecated(
            "Hold a lifecycle-aware MediaController in the UI layer instead",
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
            return flavor.replaceFirstChar { it.titlecase() }
        }

        // Session extras keys for Auto / Wear
        public const val EXTRA_SLEEP_TIMER_REMAINING_MS: String = "sleep_timer_remaining_ms"
        public const val EXTRA_PLAYBACK_SPEED: String = "playback_speed"
        public const val EXTRA_IS_SLEEP_TIMER_ACTIVE: String = "is_sleep_timer_active"
    }

    @Volatile
    internal var isFullyInitializedFlag = false

    /** Checks if the service is fully initialized and ready to use. */
    public fun isFullyInitialized(): Boolean = isFullyInitializedFlag

    // Helper methods for AudioServiceReleaseHandler to check lateinit initialization
    internal fun isPlaybackPositionRepositoryInitialized(): Boolean = ::playbackPositionRepository.isInitialized

    internal fun isAudioOutputManagerInitialized(): Boolean = ::audioOutputManager.isInitialized

    internal fun isAudioVisualizerStateBridgeInitialized(): Boolean = ::audioVisualizerStateBridge.isInitialized

    // Flag to indicate if "Minimal Notification" mode is enabled
    // If true, artwork loading will be skipped to show a smaller notification
    internal var isMinimalNotification = false

    public fun getMediaSession(): MediaSession? = mediaLibrarySession

    /** Sends a user-safe error only after the playback recovery policy is exhausted. */
    internal fun reportTerminalPlaybackError(message: String) {
        getMediaSession()?.sendError(SessionError(SessionError.ERROR_IO, message))
    }

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
            // User actions must never be silently dropped on the outgoing player during
            // a crossfade: finalize (or cancel) an in-flight transition first.
            playbackController?.finalizeActiveTransition = { crossFadePlayer?.finalizeTransitionNow() }
            playbackController?.cancelActiveTransition = { crossFadePlayer?.pause() }
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

    internal fun rebindActivePlayer(player: ExoPlayer = getActivePlayer()) {
        mediaLibrarySession?.player = player
        mediaSessionManager?.updatePlayer(player)
        playerConfigurator?.rebindListeners(player)
        rebindAudioOutputPlayer(player)
        audioVisualizerManager?.initialize(player.audioSessionId)
    }

    internal fun rebindAudioOutputPlayer(player: ExoPlayer = getActivePlayer()) {
        val listener = audioOutputPlayerListener ?: return
        if (audioOutputPlayerTarget === player) return
        audioOutputPlayerTarget?.removeListener(listener)
        player.addListener(listener)
        audioOutputPlayerTarget = player
        if (player.isPlaying) audioOutputManager.startMonitoring() else audioOutputManager.stopMonitoring()
    }

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
        playlistItems: List<PlaylistItem> = filePaths.map(::PlaylistItem),
    ) {
        // Apply book loudness compensation when switching to a different book
        if (groupPath != null && groupPath != currentGroupPath) {
            val bookId = groupPath.substringAfterLast("/").takeIf { it.isNotBlank() } ?: groupPath
            // ponytail: first play of an unanalyzed book gets no gain (lufsValue null);
            // compensation kicks in on the next play after the background worker runs.
            bookLoudnessCompensator.applyCompensation(bookId, booksDao, playerServiceScope) { getActivePlayer() }
            maybeScheduleLufsAnalysis(bookId)
        }

        // Crossfade to a different book if crossfade is enabled and currently playing
        if (groupPath != null &&
            groupPath != currentGroupPath &&
            playerConfigurator?.audioProcessingSettings?.isCrossfadeEnabled == true &&
            isPlaying &&
            crossFadePlayer != null
        ) {
            performBookSwitchCrossfade(
                filePaths = filePaths,
                playlistItems = playlistItems,
                metadata = metadata,
                initialTrackIndex = initialTrackIndex,
                initialPosition = initialPosition,
                groupPath = groupPath,
                callback = callback,
            )
            return
        }

        playlistManager?.setPlaylist(
            filePaths = filePaths,
            playlistItems = playlistItems,
            metadata = metadata,
            initialTrackIndex = initialTrackIndex,
            initialPosition = initialPosition,
            groupPath = groupPath,
            callback = callback,
        ) ?: run {
            LogUtils.e("AudioPlayerService", "PlaylistManager not initialized")
            callback?.invoke(false, IllegalStateException("PlaylistManager not initialized"))
        }
    }

    /**
     * Enqueues background LUFS analysis for [bookId] when the book has not been
     * analyzed yet. Unique work name per book with [ExistingWorkPolicy.KEEP]
     * guarantees analysis runs at most once per book.
     */
    private fun maybeScheduleLufsAnalysis(bookId: String) {
        playerServiceScope.launch(Dispatchers.IO) {
            try {
                if (booksDao.getBookById(bookId)?.lufsValue != null) return@launch
                val request =
                    OneTimeWorkRequestBuilder<LufsAnalysisWorker>()
                        .setInputData(workDataOf(LufsAnalysisWorker.KEY_BOOK_ID to bookId))
                        .build()
                workManager.enqueueUniqueWork(
                    "lufs_analysis_$bookId",
                    ExistingWorkPolicy.KEEP,
                    request,
                )
                LogUtils.i("AudioPlayerService", "Scheduled LUFS analysis for book=$bookId")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                LogUtils.w("AudioPlayerService", "Failed to schedule LUFS analysis for book=$bookId: ${e.message}")
            }
        }
    }

    /**
     * Crossfades from the current book to a new book using [CrossFadePlayer].
     *
     * 1. Creates the first MediaSource of the new book
     * 2. Prepares it on CrossFadePlayer's next player
     * 3. Starts crossfade
     * 4. After completion, loads remaining tracks onto the current player
     */
    private fun performBookSwitchCrossfade(
        filePaths: List<String>,
        playlistItems: List<PlaylistItem>,
        metadata: Map<String, String>?,
        initialTrackIndex: Int?,
        initialPosition: Long?,
        groupPath: String?,
        callback: ((Boolean, Exception?) -> Unit)?,
    ) {
        val settings =
            playerConfigurator?.audioProcessingSettings ?: run {
                playlistManager?.setPlaylist(
                    filePaths,
                    metadata,
                    initialTrackIndex,
                    initialPosition,
                    groupPath,
                    callback,
                    playlistItems,
                )
                return
            }
        val cfp =
            crossFadePlayer ?: run {
                playlistManager?.setPlaylist(
                    filePaths,
                    metadata,
                    initialTrackIndex,
                    initialPosition,
                    groupPath,
                    callback,
                    playlistItems,
                )
                return
            }
        val pm =
            playlistManager ?: run {
                callback?.invoke(false, IllegalStateException("PlaylistManager not initialized"))
                return
            }
        val durationMs = settings.crossfadeBetweenBooksMs.coerceAtLeast(0L)

        // Preserve the canonical chapter order supplied by the caller.
        val sessionState = PlaylistSessionStatePolicy.buildSnapshot(filePaths, initialTrackIndex)
        val playlistPaths = sessionState.filePaths
        val normalizedIndex = sessionState.normalizedTrackIndex

        playerServiceScope.launch {
            val firstSource = pm.createMediaSourceForItems(playlistItems, normalizedIndex, metadata)
            if (firstSource == null) {
                LogUtils.w("AudioPlayerService", "Failed to create first MediaSource for book crossfade, falling back")
                pm.setPlaylist(filePaths, metadata, initialTrackIndex, initialPosition, groupPath, callback, playlistItems)
                return@launch
            }

            // A newer explicit book selection must not be left as an unobserved preload.
            // Cancel the old transition and let PlaylistManager apply the latest selection.
            if (cfp.isTransitionRunning()) {
                cfp.pause()
                pm.setPlaylist(filePaths, metadata, initialTrackIndex, initialPosition, groupPath, callback, playlistItems)
                return@launch
            }

            // Stop crossfadeHandler monitoring during the transition
            crossfadeHandler?.stopMonitoring()

            cfp.crossFadeDurationMs = durationMs
            cfp.setNextMediaSource(firstSource)
            cfp.getNextPlayer().seekTo((initialPosition ?: 0L).coerceAtLeast(0L))
            cfp.startCrossFade {
                // After crossfade completes:
                // 1. Update PlaylistManager state with new book info
                // 2. Load remaining tracks onto the current player
                // 3. Seek to the requested track/position
                playerServiceScope.launch(Dispatchers.Main) {
                    try {
                        pm.currentFilePaths = playlistPaths
                        pm.currentPlaylistItems = playlistItems
                        pm.currentMetadata = metadata
                        pm.currentGroupPath = groupPath
                        pm.actualTrackIndex = normalizedIndex
                        pm.isBookCompleted = false
                        pm.lastCompletedTrackIndex = -1

                        // The selected source was preloaded. Insert preceding sources in reverse so it
                        // remains at its intended timeline index, then append following sources.
                        val currentPlayer = getActivePlayer()
                        for (index in PlaylistSessionStatePolicy.crossfadeRemainingSourceIndices(playlistPaths.size, normalizedIndex)) {
                            val source = pm.createMediaSourceForItems(playlistItems, index, metadata)
                            if (source != null) {
                                if (index < normalizedIndex) {
                                    currentPlayer.addMediaSource(0, source)
                                } else {
                                    currentPlayer.addMediaSource(source)
                                }
                            }
                        }

                        LogUtils.i(
                            "AudioPlayerService",
                            "Book switch crossfade complete: ${filePaths.size} tracks, targetIndex=$normalizedIndex",
                        )

                        // Resume crossfade monitoring for chapter transitions
                        crossfadeHandler?.startMonitoring()

                        callback?.invoke(true, null)
                    } catch (e: Exception) {
                        LogUtils.e("AudioPlayerService", "Failed to complete book switch crossfade", e)
                        // Fallback: load playlist normally
                        pm.setPlaylist(filePaths, metadata, initialTrackIndex, initialPosition, groupPath, callback, playlistItems)
                    }
                }
            }
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

    /** Applies lifecycle side effects after MediaSession changes the player directly. */
    internal fun onMediaSessionPlaybackStarted() {
        playbackLifecycleActions.onPlay()
    }

    /** Applies lifecycle side effects after MediaSession pauses the player directly. */
    internal fun onMediaSessionPlaybackPaused() {
        // MediaSession invokes Player.pause() directly, bypassing AudioServiceCommandRouter.
        // Cancel both sides of an active fade so playback cannot resume after a user pause.
        crossFadePlayer?.pause()
        playbackLifecycleActions.onPause()
    }

    internal fun savePositionToRepository() {
        periodicPositionSaver.save()
    }

    /** Persists the final position before a terminal lifecycle event can kill this process. */
    internal fun saveCurrentPositionSynchronously() {
        // Only the blocking write belongs on this path: the async saver may never run
        // before process death. Async saves stay in their regular call sites.
        if (!::crashSafePositionWriter.isInitialized) {
            LogUtils.w("AudioPlayerService", "Crash-safe position writer is not initialized")
            return
        }

        val bookId = currentGroupPath ?: return
        val player = getActivePlayer()
        if (player.mediaItemCount == 0) return

        crashSafePositionWriter.writePositionSync(
            bookId = bookId,
            trackIndex = player.currentMediaItemIndex,
            positionMs = player.currentPosition,
        )
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

    /** Sets initial MediaButtonPreferences for MediaSession via [MediaSessionLayoutHelper]. */
    internal fun setInitialMediaButtonPreferences() {
        mediaSessionLayoutHelper.setInitialLayout()
    }

    // ── Chapter progress notification subtitle ──────────────────────────────

    private fun startChapterNotificationUpdates() {
        stopChapterNotificationUpdates()
        chapterNotificationJob =
            playerServiceScope.launch {
                while (true) {
                    val player = getActivePlayer()
                    if (!player.isPlaying) break

                    val currentIndex = player.currentMediaItemIndex
                    val totalTracks = player.mediaItemCount
                    val currentPos = player.currentPosition
                    val duration = player.duration

                    if (duration > 0 && currentPos >= 0 && totalTracks > 0) {
                        val remaining = (duration - currentPos).coerceAtLeast(0L)
                        val timeStr = formatDuration(remaining)
                        val subtitle = "Глава ${currentIndex + 1} из $totalTracks • $timeStr осталось в главе"
                        notificationSubtitleOverride = subtitle
                        notificationProviderRef?.invalidateNotification()
                    }

                    delay(5000)
                }
            }
    }

    private fun stopChapterNotificationUpdates() {
        chapterNotificationJob?.cancel()
        chapterNotificationJob = null
    }

    internal fun onPlaybackIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            startChapterNotificationUpdates()
        } else {
            stopChapterNotificationUpdates()
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }

    // ──────────────────────────────────────────────────────────────────────────

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

    public suspend fun extractArtworkFromFile(filePath: String): String? = commandRouter.extractArtworkFromFile(filePath)

    public fun getPlaylistInfo(): Map<String, Any> = commandRouter.getPlaylistInfo()

    public fun unloadPlayerDueToInactivity(): Unit = commandRouter.unloadPlayerDueToInactivity()

    override fun onTaskRemoved(rootIntent: Intent?) {
        lifecycleManager?.onTaskRemoved() ?: super.onTaskRemoved(rootIntent)
    }

    /** Public wrapper for protected [setMediaNotificationProvider]. Called by [AudioPlayerServiceInitializer]. */
    @OptIn(UnstableApi::class)
    internal fun setNotificationProvider(provider: MediaNotification.Provider) {
        setMediaNotificationProvider(provider)
        if (provider is AudioPlayerNotificationProvider) {
            notificationProviderRef = provider
        }
    }

    private fun cleanupExistingComponents() {
        releaseHandler.cleanupExistingComponents()
    }

    override fun onDestroy() {
        instance = null
        // Persist the active player's position before releaseHandler can release a custom player.
        lifecycleManager?.onDestroy()
        releaseHandler.releaseRuntimeComponents(cancelServiceScopeChildren = true)
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
