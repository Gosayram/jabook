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

package com.jabook.app.jabook.compose.feature.player.controller

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.jabook.app.jabook.audio.AudioPlayerService
import com.jabook.app.jabook.audio.MediaControllerConstants
import com.jabook.app.jabook.audio.MediaControllerExtensions
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controller to bridge Compose UI with AudioPlayerService via MediaController.
 *
 * Architecture Note:
 * This controller uses MediaController to communicate with AudioPlayerService,
 * replacing the getInstance() anti-pattern. MediaController provides proper Media3 integration
 * and ensures state synchronization through MediaSession.
 *
 * Uses:
 * - MediaController for all player operations (replaces getInstance())
 * - MediaController.Listener for UI state management (StateFlow)
 * - Service layer handles business logic (persistence, notifications, widgets)
 *
 * Inspired by Material-3-Music-Player and Rhythm implementations.
 */
@Singleton
public class AudioPlayerController
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val exoPlayer: ExoPlayer, // Keep for backward compatibility during migration
        private val userPreferencesRepository: com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository,
        private val loggerFactory: LoggerFactory,
    ) {
        private val logger = loggerFactory.get("AudioPlayerController")
        private val scope = CoroutineScope(Dispatchers.Main)
        private var mediaController: MediaController? = null
        private var mediaControllerFuture: ListenableFuture<MediaController>? = null
        private var mediaControllerRetryJob: Job? = null
        private var serviceInitRetryJob: Job? = null
        private var loadBookRetryJob: Job? = null
        private var exoFallbackListenerAttached = false
        private val pendingCommands = ArrayDeque<PendingControllerCommand>()
        private var pendingLoadRequest: PendingLoadRequest? = null
        private val maxPendingCommands = 64
        private var loadBookRetryAttempts: Int = 0
        private var nextLoadRequestId: Long = 0L

        // Playback State
        private val _isPlaying = MutableStateFlow(false)
        public val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

        private val _currentPosition = MutableStateFlow(0L)
        public val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

        private val _duration = MutableStateFlow(0L)
        public val duration: StateFlow<Long> = _duration.asStateFlow()

        private val _currentChapterIndex = MutableStateFlow(0)
        public val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()

        // Pitch Correction State
        private val _pitchCorrectionEnabled = MutableStateFlow(true)
        public val pitchCorrectionEnabled: StateFlow<Boolean> = _pitchCorrectionEnabled.asStateFlow()

        // Audio Stats for Nerds
        private val _playerStats =
            MutableStateFlow(
                com.jabook.app.jabook.compose.feature.player
                    .PlayerStats(),
            )
        public val playerStats: StateFlow<com.jabook.app.jabook.compose.feature.player.PlayerStats> =
            _playerStats
                .asStateFlow()

        // Current Book ID for isolation - ensures we don't mix data between books
        private val _currentBookId = MutableStateFlow<String?>(null)
        public val currentBookId: StateFlow<String?> = _currentBookId.asStateFlow()

        // Connection state for debugging - tracks MediaController connection status
        public enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, FAILED_USING_FALLBACK }

        private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
        public val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

        // Callback for chapter end handling (e.g., repeat logic)
        private var onChapterEndedCallback: (() -> Boolean)? = null

        private data class PendingLoadRequest(
            val requestId: Long,
            val filePaths: List<String>,
            val initialChapterIndex: Int,
            val initialPosition: Long,
            val autoPlay: Boolean,
            val metadata: Map<String, String>?,
            val bookId: String?,
            val retryAttempt: Int = 0,
        )

        private sealed interface PendingControllerCommand {
            fun execute(controller: MediaController)
        }

        private data object PlayCommand : PendingControllerCommand {
            override fun execute(controller: MediaController) {
                controller.play()
            }
        }

        private data object PauseCommand : PendingControllerCommand {
            override fun execute(controller: MediaController) {
                controller.pause()
            }
        }

        private data class SeekToCommand(
            private val positionMs: Long,
        ) : PendingControllerCommand {
            override fun execute(controller: MediaController) {
                controller.seekTo(positionMs)
            }
        }

        private data object SkipToNextCommand : PendingControllerCommand {
            override fun execute(controller: MediaController) {
                controller.seekToNext()
            }
        }

        private data object SkipToPreviousCommand : PendingControllerCommand {
            override fun execute(controller: MediaController) {
                controller.seekToPrevious()
            }
        }

        private data class SkipToChapterCommand(
            private val chapterIndex: Int,
        ) : PendingControllerCommand {
            override fun execute(controller: MediaController) {
                controller.seekTo(chapterIndex, 0L)
            }
        }

        private data class SetPlaybackSpeedCommand(
            private val speed: Float,
        ) : PendingControllerCommand {
            override fun execute(controller: MediaController) {
                controller.setPlaybackSpeed(speed)
            }
        }

        private data object InitializeVisualizerCommand : PendingControllerCommand {
            override fun execute(controller: MediaController) {
                MediaControllerExtensions.initializeVisualizer(controller)
            }
        }

        private data class SetVisualizerEnabledCommand(
            private val enabled: Boolean,
        ) : PendingControllerCommand {
            override fun execute(controller: MediaController) {
                MediaControllerExtensions.setVisualizerEnabled(controller, enabled)
            }
        }

        /**
         * Set callback to handle chapter end events.
         * Callback should return true if chapter should be repeated, false to continue to next.
         */
        public fun setOnChapterEndedCallback(callback: (() -> Boolean)?) {
            onChapterEndedCallback = callback
        }

        /**
         * MediaController listener for UI state management.
         * Note: This replaces direct ExoPlayer listener. MediaController provides proper
         * state synchronization through MediaSession. Service layer handles business logic.
         */
        private val mediaControllerListener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    // Update UI state from MediaController (single source of truth)
                    _isPlaying.value = isPlaying
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val controller = mediaController ?: return
                    // Update duration from MediaController (single source of truth)
                    _duration.value = controller.duration.coerceAtLeast(0)
                    if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                        // Initial position update from MediaController
                        publishCurrentPosition(controller.currentPosition, force = true)

                        // Handle chapter end for repeat logic (UI-level concern)
                        if (playbackState == Player.STATE_ENDED) {
                            val shouldRepeat = onChapterEndedCallback?.invoke() ?: false
                            if (shouldRepeat) {
                                // Repeat current chapter by seeking to start
                                val currentIndex = controller.currentMediaItemIndex
                                controller.seekTo(currentIndex, 0)
                                // Resume playback if it was playing
                                if (controller.playWhenReady) {
                                    controller.play()
                                }
                            }
                        }
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    val controller = mediaController ?: return
                    // Update position and chapter index from MediaController (single source of truth)
                    publishCurrentPosition(controller.currentPosition)
                    _currentChapterIndex.value = controller.currentMediaItemIndex
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    val controller = mediaController ?: return
                    // Update chapter index and duration from MediaController (single source of truth)
                    _currentChapterIndex.value = controller.currentMediaItemIndex
                    _duration.value = controller.duration.coerceAtLeast(0)
                    updateStats(controller)
                }

                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    val controller = mediaController ?: return
                    updateStats(controller)
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    val controller = mediaController ?: return
                    updateStats(controller)
                }
            }

        /**
         * ExoPlayer fallback listener for UI state updates when MediaController is not connected.
         *
         * This listener is attached to the injected ExoPlayer singleton when:
         * - Controller is first initialized (before MediaController connects)
         * - MediaController fails to connect after all retries
         *
         * It is detached when MediaController successfully connects to avoid
         * double-updating the same StateFlows from two different player sources.
         */
        private val exoPlayerFallbackListener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _duration.value = exoPlayer.duration.coerceAtLeast(0)
                    if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                        publishCurrentPosition(exoPlayer.currentPosition, force = true)
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    publishCurrentPosition(exoPlayer.currentPosition)
                    _currentChapterIndex.value = exoPlayer.currentMediaItemIndex
                }

                override fun onMediaItemTransition(
                    mediaItem: MediaItem?,
                    reason: Int,
                ) {
                    _currentChapterIndex.value = exoPlayer.currentMediaItemIndex
                    _duration.value = exoPlayer.duration.coerceAtLeast(0)
                    updateStats(exoPlayer)
                }

                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    updateStats(exoPlayer)
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    updateStats(exoPlayer)
                }
            }

        private fun attachExoPlayerFallbackListener() {
            if (exoFallbackListenerAttached) return
            exoPlayer.addListener(exoPlayerFallbackListener)
            exoFallbackListenerAttached = true
            logger.d { "ExoPlayer fallback listener attached" }
        }

        private fun detachExoPlayerFallbackListener() {
            if (!exoFallbackListenerAttached) return
            exoPlayer.removeListener(exoPlayerFallbackListener)
            exoFallbackListenerAttached = false
            logger.d { "ExoPlayer fallback listener detached" }
        }

        private fun updateStats(controller: Player = mediaController ?: exoPlayer) {
            // audioFormat and audioSessionId are only available in ExoPlayer, not in Player interface
            // Use exoPlayer as fallback for stats when using MediaController
            val exoPlayerForStats = if (controller is ExoPlayer) controller else exoPlayer

            val format = exoPlayerForStats.audioFormat
            val audioFormat =
                "${format?.sampleMimeType ?: "Unknown"} ${format?.bitrate?.let {
                    if (it > 0) "${it / 1000}kbps" else ""
                } ?: ""}"
                    .trim()
            val bufferMs = controller.bufferedPosition - controller.currentPosition

            _playerStats.value =
                com.jabook.app.jabook.compose.feature.player.PlayerStats(
                    audioFormat = audioFormat.ifEmpty { "Unknown" },
                    bitrate = format?.bitrate?.let { if (it > 0) "${it / 1000} kbps" else "Unknown" } ?: "Unknown",
                    bufferHealth = "${bufferMs / 1000}s",
                    audioSessionId =
                        if (exoPlayerForStats.audioSessionId !=
                            C.AUDIO_SESSION_ID_UNSET
                        ) {
                            exoPlayerForStats.audioSessionId.toString()
                        } else {
                            "None"
                        },
                    decoderName = "ExoPlayer Audio Decoder",
                    droppedFrames = 0, // Audio usually doesn't drop frames like video
                )
        }

        init {
            // Attach ExoPlayer fallback listener immediately so UI gets state updates
            // even when MediaController is not yet connected
            attachExoPlayerFallbackListener()

            // Initialize MediaController for proper Media3 integration
            initMediaController()

            // Observe playback speed and pitch correction
            scope.launch {
                kotlinx.coroutines.flow
                    .combine(
                        userPreferencesRepository.userData,
                        _pitchCorrectionEnabled,
                    ) { userData, pitchCorrection ->
                        Pair(userData.playbackSpeed, pitchCorrection)
                    }.collect { (speed, pitchCorrection) ->
                        val pitch = if (pitchCorrection) 1.0f else speed
                        // Skip if no change to avoid interruptions (although setPlaybackParameters checks internally)
                        val params = androidx.media3.common.PlaybackParameters(speed, pitch)
                        mediaController?.playbackParameters = params
                    }
            }
        }

        /**
         * Initializes MediaController for communication with AudioPlayerService.
         * Replaces getInstance() pattern with proper Media3 integration.
         *
         * Uses retry logic to handle cases when service is not yet ready.
         */
        private fun initMediaController(retryCount: Int = 0) {
            val maxRetries = 10
            val retryDelayMs = 1500L

            if (mediaController != null) {
                _connectionState.value = ConnectionState.CONNECTED
                return
            }
            if (retryCount == 0 &&
                _connectionState.value == ConnectionState.CONNECTING &&
                mediaControllerFuture != null
            ) {
                logger.d { "MediaController initialization already in progress, skipping duplicate init call" }
                return
            }

            logger.d { "initMediaController START (attempt ${retryCount + 1}/$maxRetries)" }
            _connectionState.value = ConnectionState.CONNECTING

            try {
                mediaControllerFuture?.let { future ->
                    MediaController.releaseFuture(future)
                }
                mediaControllerFuture = null

                val sessionToken =
                    SessionToken(
                        context,
                        ComponentName(context, AudioPlayerService::class.java),
                    )
                logger.d { "SessionToken created: ${sessionToken.packageName}/${sessionToken.serviceName}" }

                mediaControllerFuture =
                    MediaController
                        .Builder(context, sessionToken)
                        .setApplicationLooper(context.mainLooper)
                        .buildAsync()

                logger.d { "MediaController.Builder.buildAsync() called, waiting for result..." }

                mediaControllerFuture?.addListener(
                    {
                        try {
                            // Wait for controller with timeout
                            val controller =
                                mediaControllerFuture?.get(
                                    MediaControllerConstants.DEFAULT_TIMEOUT_SECONDS.toLong(),
                                    TimeUnit.SECONDS,
                                )
                            mediaController?.let { existing ->
                                if (existing !== controller) {
                                    existing.removeListener(mediaControllerListener)
                                    existing.release()
                                }
                            }
                            mediaController = controller
                            // MediaController connected: detach fallback listener to avoid double updates.
                            detachExoPlayerFallbackListener()
                            controller?.addListener(mediaControllerListener)

                            // Initialize state from MediaController
                            controller?.let { ctrl ->
                                _isPlaying.value = ctrl.isPlaying
                                publishCurrentPosition(ctrl.currentPosition, force = true)
                                _duration.value = ctrl.duration.coerceAtLeast(0)
                                _currentChapterIndex.value = ctrl.currentMediaItemIndex
                                updateStats(ctrl)
                                _connectionState.value = ConnectionState.CONNECTED
                                mediaControllerRetryJob?.cancel()
                                mediaControllerRetryJob = null
                                flushPendingOperations(ctrl)
                                logger.i {
                                    "MediaController CONNECTED! isPlaying=${ctrl.isPlaying}, mediaItemCount=${ctrl.mediaItemCount}"
                                }
                                logger.i { "MediaController initialized successfully" }
                            } ?: run {
                                logger.e { "MediaController is null after get()!" }
                                throw IllegalStateException("MediaController is null after get()")
                            }
                        } catch (e: java.util.concurrent.TimeoutException) {
                            logger.w {
                                "MediaController initialization timeout, retrying... (attempt $retryCount/$maxRetries)"
                            }
                            scheduleMediaControllerRetry(
                                nextRetryCount = retryCount + 1,
                                maxRetries = maxRetries,
                                retryDelayMs = retryDelayMs,
                                reason = "timeout",
                            )
                        } catch (e: Exception) {
                            logger.e(e) { "Exception in MediaController init: ${e.message}" }
                            logger.e(e) { "Error initializing MediaController" }
                            scheduleMediaControllerRetry(
                                nextRetryCount = retryCount + 1,
                                maxRetries = maxRetries,
                                retryDelayMs = retryDelayMs,
                                reason = "exception",
                            )
                        }
                    },
                    ContextCompat.getMainExecutor(context),
                )
            } catch (e: Exception) {
                logger.e(e) { "Failed to create SessionToken/MediaController: ${e.message}" }
                logger.e(e) { "Failed to create MediaController" }
                scheduleMediaControllerRetry(
                    nextRetryCount = retryCount + 1,
                    maxRetries = maxRetries,
                    retryDelayMs = retryDelayMs,
                    reason = "token_creation",
                )
            }
        }

        private fun scheduleMediaControllerRetry(
            nextRetryCount: Int,
            maxRetries: Int,
            retryDelayMs: Long,
            reason: String,
        ) {
            if (mediaController != null) {
                return
            }
            if (nextRetryCount > maxRetries) {
                logger.e { "FAILED after $maxRetries retries ($reason), retrying controller bootstrap cycle" }
                logger.e {
                    "MediaController initialization failed after $maxRetries retries ($reason), keeping queued commands and restarting retry cycle"
                }
                _connectionState.value = ConnectionState.FAILED_USING_FALLBACK
                // Re-attach ExoPlayer fallback listener so UI continues receiving state updates
                attachExoPlayerFallbackListener()
                mediaControllerRetryJob?.cancel()
                mediaControllerRetryJob =
                    scope.launch {
                        delay(retryDelayMs)
                        if (mediaController == null) {
                            initMediaController(retryCount = 0)
                        }
                    }
                return
            }
            mediaControllerRetryJob?.cancel()
            mediaControllerRetryJob =
                scope.launch {
                    delay(retryDelayMs)
                    initMediaController(nextRetryCount)
                }
        }

        private fun enqueueCommand(
            command: PendingControllerCommand,
            commandName: String,
        ) {
            coalescePendingCommands(command)
            if (command is InitializeVisualizerCommand &&
                pendingCommands.any { it is InitializeVisualizerCommand }
            ) {
                logger.d {
                    "Skip duplicate queued command '$commandName' while MediaController is not ready"
                }
                return
            }
            if (pendingCommands.size >= maxPendingCommands) {
                pendingCommands.removeFirstOrNull()
            }
            pendingCommands.addLast(command)
            logger.w {
                "Queued command '$commandName' while MediaController is not ready (queueSize=${pendingCommands.size})"
            }
        }

        private fun coalescePendingCommands(newCommand: PendingControllerCommand) {
            val incomingType = mapToDeferredCommandType(newCommand)
            pendingCommands.removeAll { existing ->
                val existingType = mapToDeferredCommandType(existing)
                DeferredCommandCoalescingPolicy.shouldRemoveExisting(
                    existing = existingType,
                    incoming = incomingType,
                )
            }
        }

        private fun mapToDeferredCommandType(command: PendingControllerCommand): DeferredCommandType =
            when (command) {
                PlayCommand,
                PauseCommand,
                -> DeferredCommandType.PLAYBACK_TOGGLE

                is SeekToCommand -> DeferredCommandType.SEEK

                SkipToNextCommand,
                SkipToPreviousCommand,
                is SkipToChapterCommand,
                -> DeferredCommandType.SKIP

                is SetPlaybackSpeedCommand -> DeferredCommandType.SPEED

                is SetVisualizerEnabledCommand -> DeferredCommandType.VISUALIZER_ENABLED

                InitializeVisualizerCommand -> DeferredCommandType.VISUALIZER_INITIALIZE
            }

        private fun flushPendingOperations(controller: MediaController) {
            val pendingLoad = pendingLoadRequest
            if (pendingLoad != null) {
                pendingLoadRequest = null
                loadBookInternal(pendingLoad, resetRetryState = false)
            }

            while (pendingCommands.isNotEmpty()) {
                val command = pendingCommands.removeFirst()
                try {
                    command.execute(controller)
                } catch (e: Exception) {
                    logger.e(e) { "Failed to execute queued MediaController command" }
                }
            }
        }

        private fun executeOrQueue(
            commandName: String,
            pendingCommand: PendingControllerCommand,
            action: (MediaController) -> Unit,
        ) {
            val controller = mediaController
            if (controller != null) {
                action(controller)
                return
            }
            enqueueCommand(pendingCommand, commandName)
            ensureControllerReady()
        }

        public fun setPitchCorrectionEnabled(enabled: Boolean) {
            _pitchCorrectionEnabled.value = enabled
        }

        /**
         * Load and play a book.
         */
        public fun loadBook(
            filePaths: List<String>,
            initialChapterIndex: Int = 0,
            initialPosition: Long = 0L,
            autoPlay: Boolean = false,
            metadata: Map<String, String>? = null,
            bookId: String? = null,
        ) {
            val request =
                PendingLoadRequest(
                    requestId = nextRequestId(),
                    filePaths = filePaths,
                    initialChapterIndex = initialChapterIndex,
                    initialPosition = initialPosition,
                    autoPlay = autoPlay,
                    metadata = metadata,
                    bookId = bookId,
                    retryAttempt = 0,
                )
            loadBookInternal(request, resetRetryState = true)
        }

        private fun loadBookInternal(
            request: PendingLoadRequest,
            resetRetryState: Boolean,
        ) {
            if (resetRetryState) {
                // New user-initiated load should invalidate any previously scheduled retry.
                loadBookRetryJob?.cancel()
                loadBookRetryJob = null
                pendingLoadRequest = null
                loadBookRetryAttempts = 0
            }

            startService()

            // CRITICAL: Check if we're switching to a different book
            val previousBookId = _currentBookId.value
            val isBookChanged = request.bookId != null && request.bookId != previousBookId

            if (isBookChanged) {
                logger.i { "Book changed: $previousBookId -> ${request.bookId}. Resetting state." }
                // Reset state to avoid showing old book's data
                publishCurrentPosition(request.initialPosition, force = true)
                _currentChapterIndex.value = request.initialChapterIndex
                _isPlaying.value = false
            }

            // Update current book ID
            _currentBookId.value = request.bookId

            // Use MediaController for all operations including setPlaylist
            val controller = mediaController
            if (controller == null) {
                pendingLoadRequest =
                    PendingLoadRequest(
                        requestId = request.requestId,
                        filePaths = request.filePaths,
                        initialChapterIndex = request.initialChapterIndex,
                        initialPosition = request.initialPosition,
                        autoPlay = request.autoPlay,
                        metadata = request.metadata,
                        bookId = request.bookId,
                        retryAttempt = request.retryAttempt,
                    )
                logger.w { "Queued loadBook request while MediaController is not ready" }
                loadBookRetryJob?.cancel()
                loadBookRetryJob =
                    scope.launch {
                        delay(1200L)
                        if (mediaController == null) {
                            ensureControllerReady()
                        }
                    }
                ensureControllerReady()
                return
            }

            // Check if we are already playing this book to avoid restarting
            // Use MediaController custom commands to get current state
            scope.launch {
                try {
                    val currentGroupPath = MediaControllerExtensions.getCurrentGroupPath(controller)
                    val currentPaths = MediaControllerExtensions.getCurrentFilePaths(controller)

                    val isSameBook = request.bookId != null && request.bookId == currentGroupPath
                    // Compare playlists by content, not by reference, to handle sorted paths
                    val isSamePlaylist =
                        currentPaths != null &&
                            currentPaths.size == request.filePaths.size &&
                            currentPaths.sorted() == request.filePaths.sorted()

                    if ((isSameBook || isSamePlaylist) && !isBookChanged) {
                        logger.i {
                            "Book already loaded (groupPath match: $isSameBook, paths match: $isSamePlaylist). Skipping setPlaylist."
                        }

                        // Handle seeking if needed (e.g. user clicked a specific chapter)
                        // Only seek if significantly different to allow resume logic to work
                        if (request.initialChapterIndex != controller.currentMediaItemIndex) {
                            controller.seekTo(request.initialChapterIndex, 0L)
                        }
                        if (request.initialPosition > 0L &&
                            Math.abs(controller.currentPosition - request.initialPosition) > 1000L
                        ) {
                            controller.seekTo(request.initialPosition)
                        }

                        if (request.autoPlay && !controller.isPlaying) {
                            controller.play()
                        }
                        pendingLoadRequest = null
                        loadBookRetryAttempts = 0
                        return@launch
                    }

                    // Use MediaController custom command for setPlaylist
                    val future =
                        MediaControllerExtensions.setPlaylist(
                            controller = controller,
                            filePaths = request.filePaths,
                            metadata = request.metadata,
                            initialTrackIndex = request.initialChapterIndex,
                            initialPosition = request.initialPosition,
                            groupPath = request.bookId,
                        )

                    // Wait for result
                    val result =
                        withContext(Dispatchers.IO) {
                            future.get(30, TimeUnit.SECONDS)
                        }
                    if (result.resultCode == SessionResult.RESULT_SUCCESS && request.autoPlay) {
                        controller.play()
                    } else if (result.resultCode != SessionResult.RESULT_SUCCESS) {
                        logger.e {
                            "setPlaylist failed with code: ${result.resultCode}"
                        }
                        scheduleLoadBookRetry(request, "result=${result.resultCode}")
                        return@launch
                    }
                    pendingLoadRequest = null
                    loadBookRetryAttempts = 0
                } catch (e: Exception) {
                    logger.e({ "Error in loadBook" }, e)
                    scheduleLoadBookRetry(request, "exception=${e::class.java.simpleName}")
                }
            }
        }

        private fun scheduleLoadBookRetry(
            request: PendingLoadRequest,
            reason: String,
        ) {
            val nextAttempt = request.retryAttempt + 1
            if (!LoadBookRetryPolicy.shouldRetry(nextAttempt)) {
                logger.e {
                    "loadBook retry limit reached for ${request.bookId ?: "unknown-book"} after ${request.retryAttempt} attempts ($reason)"
                }
                pendingLoadRequest = null
                loadBookRetryAttempts = request.retryAttempt
                return
            }

            val delayedRequest =
                request.copy(
                    retryAttempt = nextAttempt,
                )
            pendingLoadRequest = delayedRequest
            loadBookRetryAttempts = nextAttempt
            val delayMs = LoadBookRetryPolicy.retryDelayMs(nextAttempt)

            loadBookRetryJob?.cancel()
            loadBookRetryJob =
                scope.launch {
                    delay(delayMs)
                    if (pendingLoadRequest?.requestId != delayedRequest.requestId) {
                        return@launch
                    }
                    pendingLoadRequest = null
                    loadBookInternal(
                        request = delayedRequest,
                        resetRetryState = false,
                    )
                }
            logger.w {
                "Scheduling loadBook retry #$nextAttempt in ${delayMs}ms for ${request.bookId ?: "unknown-book"} ($reason)"
            }
        }

        private fun nextRequestId(): Long {
            nextLoadRequestId += 1L
            return nextLoadRequestId
        }

        public fun play() {
            executeOrQueue(
                commandName = "play",
                pendingCommand = PlayCommand,
            ) { controller ->
                controller.play()
            }
        }

        public fun pause() {
            executeOrQueue(
                commandName = "pause",
                pendingCommand = PauseCommand,
            ) { controller ->
                controller.pause()
            }
        }

        public fun seekTo(positionMs: Long) {
            executeOrQueue(
                commandName = "seekTo",
                pendingCommand = SeekToCommand(positionMs),
            ) { controller ->
                controller.seekTo(positionMs)
            }
        }

        public fun skipToNext() {
            executeOrQueue(
                commandName = "skipToNext",
                pendingCommand = SkipToNextCommand,
            ) { controller ->
                controller.seekToNext()
            }
        }

        public fun skipToPrevious() {
            executeOrQueue(
                commandName = "skipToPrevious",
                pendingCommand = SkipToPreviousCommand,
            ) { controller ->
                controller.seekToPrevious()
            }
        }

        public fun skipToChapter(index: Int) {
            executeOrQueue(
                commandName = "skipToChapter",
                pendingCommand = SkipToChapterCommand(index),
            ) { controller ->
                controller.seekTo(index, 0L)
            }
        }

        public fun setPlaybackSpeed(speed: Float) {
            executeOrQueue(
                commandName = "setPlaybackSpeed",
                pendingCommand = SetPlaybackSpeedCommand(speed),
            ) { controller ->
                controller.setPlaybackSpeed(speed)
            }
        }

        public fun initializeVisualizer() {
            executeOrQueue(
                commandName = "initializeVisualizer",
                pendingCommand = InitializeVisualizerCommand,
            ) { controller ->
                MediaControllerExtensions.initializeVisualizer(controller)
            }
        }

        public fun setVisualizerEnabled(enabled: Boolean) {
            executeOrQueue(
                commandName = "setVisualizerEnabled",
                pendingCommand = SetVisualizerEnabledCommand(enabled),
            ) { controller ->
                MediaControllerExtensions.setVisualizerEnabled(controller, enabled)
            }
        }

        private fun ensureControllerReady() {
            startService()
            if (mediaController == null && mediaControllerFuture == null) {
                initMediaController()
            }
        }

        private fun publishCurrentPosition(
            positionMs: Long,
            force: Boolean = false,
        ) {
            val sanitizedPositionMs = positionMs.coerceAtLeast(0L)
            if (
                !PositionPublishPolicy.shouldPublish(
                    previousPositionMs = _currentPosition.value,
                    incomingPositionMs = sanitizedPositionMs,
                    force = force,
                )
            ) {
                return
            }
            _currentPosition.value = sanitizedPositionMs
        }

        private fun startService() {
            try {
                // Check if MediaController is already connected (service is running)
                if (mediaController != null) {
                    logger.d { "MediaController connected, service already running" }
                    return
                }

                logger.i { "Starting AudioPlayerService..." }
                val intent = Intent(context, AudioPlayerService::class.java)
                // Use startService instead of startForegroundService.
                // Since the app is in the foreground, we are allowed to start the service.
                // MediaLibraryService will internally handle promoting it to foreground
                // when playback starts and notification is posted.
                context.startService(intent)

                // Retry MediaController initialization after service start
                serviceInitRetryJob?.cancel()
                serviceInitRetryJob =
                    scope.launch {
                        delay(1000L) // Wait for service to initialize
                        if (mediaController == null) {
                            logger.d { "Retrying MediaController initialization after service start" }
                            initMediaController()
                        }
                    }
            } catch (e: Exception) {
                logger.e(
                    e,
                    { "Failed to start service" },
                )
            }
        }

        /**
         * Releases MediaController resources.
         * Should be called when controller is no longer needed.
         */
        public fun release() {
            mediaControllerRetryJob?.cancel()
            mediaControllerRetryJob = null
            serviceInitRetryJob?.cancel()
            serviceInitRetryJob = null
            loadBookRetryJob?.cancel()
            loadBookRetryJob = null
            pendingLoadRequest = null
            loadBookRetryAttempts = 0
            pendingCommands.clear()

            detachExoPlayerFallbackListener()
            mediaController?.removeListener(mediaControllerListener)
            mediaController?.release()
            mediaController = null
            mediaControllerFuture?.let {
                MediaController.releaseFuture(it)
            }
            mediaControllerFuture = null
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }
