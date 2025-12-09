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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.TaskStackBuilder
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.jabook.app.jabook.MainActivity
import com.jabook.app.jabook.audio.processors.AudioProcessingSettings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import okhttp3.Cache
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
class AudioPlayerService : MediaLibraryService() {
    @Inject
    lateinit var exoPlayer: ExoPlayer

    @Inject
    @javax.inject.Named("okhttp")
    lateinit var mediaCache: okhttp3.Cache

    // Media3 cache for streaming (different from network cache)
    @Inject
    lateinit var media3Cache: androidx.media3.datasource.cache.Cache

    @Inject
    lateinit var playerPersistenceManager: PlayerPersistenceManager

    @Inject
    lateinit var eventChannelHandler: com.jabook.app.jabook.audio.bridge.EventChannelHandler

    internal var bridgePlayerListener: com.jabook.app.jabook.audio.bridge.BridgePlayerListener? = null

    internal var mediaLibrarySession: MediaLibrarySession? = null

    // Keep mediaSession for backward compatibility during migration
    internal var mediaSession: MediaSession? = null
    internal var notificationManager: NotificationManager? = null
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

    // Store the custom provider to access it in onUpdateNotification
    internal var customMediaNotificationProvider: AudioPlayerNotificationProvider? = null

    // Helper for player state
    internal var playerStateHelper: PlayerStateHelper? = null
    internal var unloadManager: UnloadManager? = null
    internal var playbackPositionSaver: PlaybackPositionSaver? = null

    // Sleep timer manager
    internal var sleepTimerManager: SleepTimerManager? = null

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
    // private var lastPositionSaveTime: Long = 0 // Removed

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

    // MethodChannel for communication with Flutter (set from MainActivity)
    internal var methodChannel: io.flutter.plugin.common.MethodChannel? = null

    /**
     * Sets callback for getting duration from database.
     * This is called from Flutter via MethodChannel to enable database lookup.
     *
     * @param callback Callback that takes file path and returns duration in ms, or null
     */
    fun setGetDurationFromDbCallback(callback: ((String) -> Long?)?) {
        durationManager.setGetDurationFromDbCallback(callback)
    }

    /**
     * Sets MethodChannel for communication with Flutter.
     * This is called from MainActivity to enable position saving via MethodChannel.
     *
     * @param channel MethodChannel instance
     */
    fun setMethodChannel(channel: io.flutter.plugin.common.MethodChannel?) {
        android.util.Log.i(
            "AudioPlayerService",
            "setMethodChannel called: channel=${channel != null}, service instance=${instance != null}",
        )
        methodChannel = channel
        // methodChannel is updated, PlaybackPositionSaver will pick it up via provider lambda
        android.util.Log.i("AudioPlayerService", "MethodChannel updated")
    }

    /**
     * Gets duration for file path.
     * Checks cache first, then database via callback, then returns null.
     *
     * @param filePath Absolute path to the audio file
     * @return Duration in milliseconds, or null if not found
     */
    fun getDurationForFile(filePath: String): Long? = durationManager.getDurationForFile(filePath)

    /**
     * Gets cached duration for file path.
     *
     * @param filePath Absolute path to the audio file
     * @return Cached duration in milliseconds, or null if not cached
     */
    fun getCachedDuration(filePath: String): Long? = durationManager.getCachedDuration(filePath)

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
        durationManager.saveDurationToCache(filePath, durationMs)
    }

    // Audio processing settings
    // internal var audioProcessingSettings = AudioProcessingSettings() // Delegated to PlayerConfigurator

    // Custom ExoPlayer instance (wraps singleton ExoPlayer)
    // internal var customExoPlayer: ExoPlayer? = null // Delegated to PlayerConfigurator
    internal var customExoPlayer: ExoPlayer? = null

    internal val playerServiceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Limited dispatcher for MediaItem creation (max 16 parallel tasks)
    // Increased parallelism for faster loading on modern devices with fast storage
    // Modern devices can handle more concurrent I/O operations efficiently
    @OptIn(ExperimentalCoroutinesApi::class)
    internal val mediaItemDispatcher = Dispatchers.IO.limitedParallelism(16)

    companion object {
        const val ACTION_EXIT_APP = "com.jabook.app.jabook.audio.EXIT_APP"

        @Volatile
        private var instance: AudioPlayerService? = null

        fun getInstance(): AudioPlayerService? = instance

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
            // Capitalize first letter for display
            return if (flavor.isEmpty()) "" else flavor.substring(0, 1).uppercase() + flavor.substring(1)
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
    fun isFullyInitialized(): Boolean = isFullyInitializedFlag && (mediaLibrarySession != null || mediaSession != null)

    /**
     * Gets the MediaLibrarySession instance.
     * Used by AudioPlayerMethodHandler to check if service is fully ready.
     */
    fun getMediaSession(): MediaSession? = mediaLibrarySession ?: mediaSession

    /**
     * Returns the single top activity. It is used by the notification when the app task is
     * active and an activity is in the fore or background.
     *
     * Tapping the notification then typically should trigger a single top activity. This way, the
     * user navigates to the previous activity when pressing back.
     *
     * Based on Media3 DemoPlaybackService example.
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
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_player", true) // Signal to Flutter to open player screen
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
                Intent(this@AudioPlayerService, MainActivity::class.java),
            )
            // MainActivity will handle opening player screen via Flutter (open_player flag)
            // Flutter navigation is handled in MainActivity.onNewIntent() and onResume()
            getPendingIntent(0, immutableFlag or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }

    @OptIn(UnstableApi::class) // MediaSessionService.setListener
    override fun onCreate() {
        super.onCreate()
        instance = this

        // Set MediaSessionService.Listener for handling foreground service start exceptions
        // This is required for Android 12+ when system doesn't allow foreground service start
        setListener(MediaSessionServiceListener(this))

        // Set custom MediaNotification.Provider to handle "Minimal Notification" mode
        // We use the DefaultMediaNotificationProvider but intercept Bitmap loading
        // Set custom MediaNotification.Provider to handle "Minimal Notification" mode
        // We use the DefaultMediaNotificationProvider but intercept Bitmap loading
        val provider = AudioPlayerNotificationProvider(this)
        customMediaNotificationProvider = provider
        setMediaNotificationProvider(provider)

        // Initialize service components using extracted initializer
        // This handles all complex logic previously directly in onCreate
        AudioPlayerServiceInitializer(this).initialize()

        // Setup Bridge Listener for EventChannel updates
        // This connects the player events to the Flutter V2 EventChannel
        bridgePlayerListener =
            com.jabook.app.jabook.audio.bridge
                .BridgePlayerListener(eventChannelHandler) { getActivePlayer() }
        exoPlayer.addListener(bridgePlayerListener!!)
        android.util.Log.i("AudioPlayerService", "BridgePlayerListener attached to ExoPlayer")
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
            android.util.Log.e("AudioPlayerService", "PlayerConfigurator not initialized")
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
        playerConfigurator?.configureExoPlayer(settings) ?: run {
            android.util.Log.e("AudioPlayerService", "PlayerConfigurator not initialized")
        }
    }

    /**
     * Gets the active ExoPlayer instance (custom with processors or singleton).
     */
    internal fun getActivePlayer(): ExoPlayer = playerConfigurator?.getActivePlayer(exoPlayer) ?: exoPlayer

    /**
     * Updates the actual track index from onMediaItemTransition events.
     * This is the single source of truth for current track index.
     *
     * @param index The actual track index from ExoPlayer's onMediaItemTransition event
     */
    internal fun updateActualTrackIndex(index: Int) {
        playlistManager?.actualTrackIndex = index
        android.util.Log.d("AudioPlayerService", "Updated actualTrackIndex to $index")
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
    fun setPlaylist(
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
            android.util.Log.e("AudioPlayerService", "PlaylistManager not initialized")
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

    /**
     * Sets notification type (full or minimal).
     *
     * @param isMinimal true for minimal notification (Play/Pause only),
     * false for full notification (all controls)
     */
    fun setNotificationType(isMinimal: Boolean) {
        // TODO: This functionality may not be needed with MediaLibraryService
        // MediaLibraryService automatically manages notifications based on Player state
        // If we need custom notification types, we should configure MediaButtonPreferences instead
        notificationManager?.setNotificationType(isMinimal)
        // MediaLibraryService automatically updates notification when Player state changes
    }

    fun play() {
        playbackController?.play() ?: run {
            android.util.Log.e("AudioPlayerService", "PlaybackController not initialized")
            return
        }
        // Start periodic position saving when playback starts
        playbackPositionSaver?.startPeriodicPositionSaving()
    }

    fun pause() {
        playbackController?.pause() ?: run {
            android.util.Log.e("AudioPlayerService", "PlaybackController not initialized")
            return
        }
        // Save position immediately when pausing (critical event)
        playbackPositionSaver?.savePosition("pause")
        // Store media item for playback resumption
        storeCurrentMediaItem()
        // Stop periodic saving when paused (will resume when playing again)
        playbackPositionSaver?.stopPeriodicPositionSaving()
    }

    fun stop() {
        playbackController?.stop() ?: run {
            android.util.Log.e("AudioPlayerService", "PlaybackController not initialized")
            return
        }
        // Save position immediately when stopping (critical event)
        playbackPositionSaver?.savePosition("stop")
        // Store media item for playback resumption
        storeCurrentMediaItem()
        // Stop periodic saving when stopped
        playbackPositionSaver?.stopPeriodicPositionSaving()
    }

    /**
     * Stops playback and releases all resources.
     * Closes notification and stops service.
     *
     * This is a complete cleanup method that should be called when
     * playback is permanently stopped (e.g., from Stop button in notification).
     */
    fun stopAndCleanup() {
        lifecycleManager?.stopAndCleanup() ?: run {
            android.util.Log.e("AudioPlayerService", "ServiceLifecycleManager not initialized for stopAndCleanup")
            // Fallback manual cleanup if needed, or just log error
        }
    }

    internal fun saveCurrentPosition() {
        // Use PlaybackPositionSaver if available (preferred), otherwise fallback to PositionManager
        playbackPositionSaver?.savePosition("manual") ?: run {
            positionManager?.saveCurrentPosition() ?: run {
                android.util.Log.e("AudioPlayerService", "Neither PlaybackPositionSaver nor PositionManager initialized")
            }
        }
    }

    /**
     * Stores current media item detailed state for persistence.
     * Delegated to PlaybackPositionSaver.
     *
     * Based on Media3 DemoPlaybackService example.
     */
    @OptIn(UnstableApi::class) // Player.listen, BitmapLoader
    internal fun storeCurrentMediaItem() {
        playbackPositionSaver?.storeCurrentMediaItem()
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

    // Periodic position saving methods removed (delegated to PlaybackPositionSaver)

    override fun onTaskRemoved(rootIntent: Intent?) {
        lifecycleManager?.onTaskRemoved(rootIntent) ?: super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // Release bridge listener
        bridgePlayerListener?.release()
        bridgePlayerListener = null

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
