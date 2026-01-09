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
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.jabook.app.jabook.audio.AudioPlayerService
import com.jabook.app.jabook.audio.MediaControllerConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
class AudioPlayerController
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val exoPlayer: ExoPlayer, // Keep for backward compatibility during migration
        private val userPreferencesRepository: com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository,
    ) {
        private val scope = CoroutineScope(Dispatchers.Main)
        private var mediaController: MediaController? = null
        private var mediaControllerFuture: ListenableFuture<MediaController>? = null

        // Playback State
        private val _isPlaying = MutableStateFlow(false)
        val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

        private val _currentPosition = MutableStateFlow(0L)
        val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

        private val _duration = MutableStateFlow(0L)
        val duration: StateFlow<Long> = _duration.asStateFlow()

        private val _currentChapterIndex = MutableStateFlow(0)
        val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()

        // Pitch Correction State
        private val _pitchCorrectionEnabled = MutableStateFlow(true)
        val pitchCorrectionEnabled: StateFlow<Boolean> = _pitchCorrectionEnabled.asStateFlow()

        // Audio Stats for Nerds
        private val _playerStats =
            MutableStateFlow(
                com.jabook.app.jabook.compose.feature.player
                    .PlayerStats(),
            )
        val playerStats: StateFlow<com.jabook.app.jabook.compose.feature.player.PlayerStats> = _playerStats.asStateFlow()

        // Current Book ID for isolation - ensures we don't mix data between books
        private val _currentBookId = MutableStateFlow<String?>(null)
        val currentBookId: StateFlow<String?> = _currentBookId.asStateFlow()

        // Callback for chapter end handling (e.g., repeat logic)
        private var onChapterEndedCallback: (() -> Boolean)? = null

        /**
         * Set callback to handle chapter end events.
         * Callback should return true if chapter should be repeated, false to continue to next.
         */
        fun setOnChapterEndedCallback(callback: (() -> Boolean)?) {
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
                        _currentPosition.value = controller.currentPosition

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
                    _currentPosition.value = controller.currentPosition
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
                        // Fallback to exoPlayer during migration
                        if (mediaController == null) {
                            exoPlayer.playbackParameters = params
                        }
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
            val maxRetries = 3
            val retryDelayMs = 500L

            try {
                val sessionToken =
                    SessionToken(
                        context,
                        ComponentName(context, AudioPlayerService::class.java),
                    )

                mediaControllerFuture =
                    MediaController
                        .Builder(context, sessionToken)
                        .setApplicationLooper(context.mainLooper)
                        .buildAsync()

                mediaControllerFuture?.addListener(
                    {
                        try {
                            // Wait for controller with timeout
                            val controller =
                                mediaControllerFuture?.get(
                                    MediaControllerConstants.DEFAULT_TIMEOUT_SECONDS,
                                    TimeUnit.SECONDS,
                                )
                            mediaController = controller
                            controller?.addListener(mediaControllerListener)

                            // Initialize state from MediaController
                            controller?.let { ctrl ->
                                _isPlaying.value = ctrl.isPlaying
                                _currentPosition.value = ctrl.currentPosition
                                _duration.value = ctrl.duration.coerceAtLeast(0)
                                _currentChapterIndex.value = ctrl.currentMediaItemIndex
                                updateStats(ctrl)
                                android.util.Log.i("AudioPlayerController", "MediaController initialized successfully")
                            } ?: run {
                                throw IllegalStateException("MediaController is null after get()")
                            }
                        } catch (e: java.util.concurrent.TimeoutException) {
                            android.util.Log.w(
                                "AudioPlayerController",
                                "MediaController initialization timeout, retrying... (attempt $retryCount/$maxRetries)",
                            )
                            if (retryCount < maxRetries) {
                                // Retry after delay
                                scope.launch {
                                    kotlinx.coroutines.delay(retryDelayMs)
                                    initMediaController(retryCount + 1)
                                }
                            } else {
                                android.util.Log.e(
                                    "AudioPlayerController",
                                    "MediaController initialization failed after $maxRetries retries, using fallback",
                                )
                                initializeFromExoPlayer()
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("AudioPlayerController", "Error initializing MediaController", e)
                            // Fallback: initialize from exoPlayer
                            initializeFromExoPlayer()
                        }
                    },
                    ContextCompat.getMainExecutor(context),
                )
            } catch (e: Exception) {
                android.util.Log.e("AudioPlayerController", "Failed to create MediaController", e)
                if (retryCount < maxRetries) {
                    // Retry after delay
                    scope.launch {
                        kotlinx.coroutines.delay(retryDelayMs)
                        initMediaController(retryCount + 1)
                    }
                } else {
                    android.util.Log.e("AudioPlayerController", "MediaController creation failed after $maxRetries retries, using fallback")
                    // Fallback: initialize from exoPlayer
                    initializeFromExoPlayer()
                }
            }
        }

        /**
         * Fallback initialization from ExoPlayer during migration period.
         */
        private fun initializeFromExoPlayer() {
            android.util.Log.w("AudioPlayerController", "Using ExoPlayer fallback during MediaController initialization")
            // Attach listener to singleton ExoPlayer as fallback
            exoPlayer.addListener(mediaControllerListener)
            // Initialize state
            _isPlaying.value = exoPlayer.isPlaying
            _currentPosition.value = exoPlayer.currentPosition
            _duration.value = exoPlayer.duration.coerceAtLeast(0)
            _currentChapterIndex.value = exoPlayer.currentMediaItemIndex
            updateStats(exoPlayer)
        }

        fun setPitchCorrectionEnabled(enabled: Boolean) {
            _pitchCorrectionEnabled.value = enabled
        }

        /**
         * Load and play a book.
         */
        fun loadBook(
            filePaths: List<String>,
            initialChapterIndex: Int = 0,
            initialPosition: Long = 0,
            autoPlay: Boolean = false,
            metadata: Map<String, String>? = null,
            bookId: String? = null,
        ) {
            startService()

            // CRITICAL: Check if we're switching to a different book
            val previousBookId = _currentBookId.value
            val isBookChanged = bookId != null && bookId != previousBookId

            if (isBookChanged) {
                android.util.Log.i("AudioPlayerController", "Book changed: $previousBookId -> $bookId. Resetting state.")
                // Reset state to avoid showing old book's data
                _currentPosition.value = initialPosition
                _currentChapterIndex.value = initialChapterIndex
                _isPlaying.value = false
            }

            // Update current book ID
            _currentBookId.value = bookId

            // Use MediaController for state checks, but setPlaylist still requires service
            // (setPlaylist has complex logic with PlaylistManager that can't be done via MediaController)
            val controller = mediaController

            // setPlaylist requires direct service access (not available via MediaController)
            // Use helper method with retry logic for safer access
            // Try to get service synchronously first (fast path)
            @Suppress("DEPRECATION")
            var service = AudioPlayerService.getInstance()
            if (service == null || !service.isFullyInitialized()) {
                // Service not ready, retry asynchronously
                android.util.Log.d("AudioPlayerController", "Service not ready, retrying asynchronously...")
                scope.launch {
                    val retryService = waitForServiceReady()
                    if (retryService != null) {
                        // Retry loadBook with ready service
                        loadBook(filePaths, initialChapterIndex, initialPosition, autoPlay, metadata, bookId)
                    } else {
                        android.util.Log.e("AudioPlayerController", "Service instance not found after retries")
                    }
                }
                return
            }

            // Check if we are already playing this book to avoid restarting
            // CRITICAL: Use bookId (groupPath) as primary check since file paths may differ after sorting
            val currentGroupPath = service.currentGroupPath
            val currentPaths = service.currentFilePaths
            val isSameBook = bookId != null && bookId == currentGroupPath
            // Compare playlists by content, not by reference, to handle sorted paths
            val isSamePlaylist =
                currentPaths != null &&
                    currentPaths.size == filePaths.size &&
                    currentPaths.sorted() == filePaths.sorted()

            if ((isSameBook || isSamePlaylist) && !isBookChanged) {
                android.util.Log.i(
                    "AudioPlayerController",
                    "Book already loaded (groupPath match: $isSameBook, paths match: $isSamePlaylist). Skipping setPlaylist.",
                )

                // Use MediaController for seeking if available, otherwise use service
                if (controller != null) {
                    // Handle seeking if needed (e.g. user clicked a specific chapter)
                    // Only seek if significantly different to allow resume logic to work
                    if (initialChapterIndex != controller.currentMediaItemIndex) {
                        controller.seekTo(initialChapterIndex, 0)
                    }
                    if (initialPosition > 0 && Math.abs(controller.currentPosition - initialPosition) > 1000) {
                        controller.seekTo(initialPosition)
                    }

                    if (autoPlay && !controller.isPlaying) {
                        controller.play()
                    }
                } else {
                    // Fallback to service methods
                    if (initialChapterIndex != service.actualTrackIndex) {
                        service.seekToTrack(initialChapterIndex)
                    }
                    if (initialPosition > 0) {
                        val currentPos = service.getCurrentPosition()
                        if (Math.abs(currentPos - initialPosition) > 1000) {
                            service.seekTo(initialPosition)
                        }
                    }
                    if (autoPlay && !service.isPlaying) {
                        service.play()
                    }
                }
                return
            }

            // setPlaylist requires service-specific logic (PlaylistManager, etc.)
            // This cannot be done via MediaController, so we use service directly
            service.setPlaylist(
                filePaths = filePaths,
                metadata = metadata,
                initialTrackIndex = initialChapterIndex,
                initialPosition = initialPosition,
                groupPath = bookId,
                callback = { success, _ ->
                    if (success && autoPlay) {
                        // Use MediaController if available for play command
                        if (controller != null) {
                            controller.play()
                        } else {
                            service.play()
                        }
                    }
                },
            )
        }

        fun play() {
            startService()
            mediaController?.play() ?: run {
                android.util.Log.w("AudioPlayerController", "MediaController not available for play(), service may not be ready")
            }
        }

        fun pause() {
            mediaController?.pause() ?: run {
                android.util.Log.w("AudioPlayerController", "MediaController not available for pause(), service may not be ready")
            }
        }

        fun seekTo(positionMs: Long) {
            mediaController?.seekTo(positionMs) ?: run {
                android.util.Log.w("AudioPlayerController", "MediaController not available for seekTo(), service may not be ready")
            }
        }

        fun skipToNext() {
            mediaController?.seekToNext() ?: run {
                android.util.Log.w("AudioPlayerController", "MediaController not available for skipToNext(), service may not be ready")
            }
        }

        fun skipToPrevious() {
            mediaController?.seekToPrevious() ?: run {
                android.util.Log.w("AudioPlayerController", "MediaController not available for skipToPrevious(), service may not be ready")
            }
        }

        fun skipToChapter(index: Int) {
            mediaController?.seekTo(index, 0) ?: run {
                android.util.Log.w("AudioPlayerController", "MediaController not available for skipToChapter(), service may not be ready")
            }
        }

        fun setPlaybackSpeed(speed: Float) {
            mediaController?.setPlaybackSpeed(speed) ?: run {
                android.util.Log.w(
                    "AudioPlayerController",
                    "MediaController not available for setPlaybackSpeed(), service may not be ready",
                )
            }
        }

        private fun startService() {
            try {
                // Check if MediaController is already connected (service is running)
                if (mediaController != null) {
                    android.util.Log.d("AudioPlayerController", "MediaController connected, service already running")
                    return
                }

                android.util.Log.i("AudioPlayerController", "Starting AudioPlayerService...")
                val intent = Intent(context, AudioPlayerService::class.java)
                // Use startService instead of startForegroundService.
                // Since the app is in the foreground, we are allowed to start the service.
                // MediaLibraryService will internally handle promoting it to foreground
                // when playback starts and notification is posted.
                context.startService(intent)

                // Retry MediaController initialization after service start
                scope.launch {
                    kotlinx.coroutines.delay(500) // Wait for service to initialize
                    if (mediaController == null) {
                        android.util.Log.d("AudioPlayerController", "Retrying MediaController initialization after service start")
                        initMediaController()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AudioPlayerController", "Failed to start service", e)
            }
        }

        /**
         * Helper method to safely get service instance with retry logic.
         * This is a temporary solution until we can fully migrate to MediaController.
         */
        private suspend fun waitForServiceReady(
            maxRetries: Int = 5,
            delayMs: Long = 200,
        ): AudioPlayerService? {
            repeat(maxRetries) { attempt ->
                @Suppress("DEPRECATION")
                val service = AudioPlayerService.getInstance()
                if (service != null && service.isFullyInitialized()) {
                    return service
                }
                if (attempt < maxRetries - 1) {
                    kotlinx.coroutines.delay(delayMs * (attempt + 1)) // Exponential backoff
                }
            }
            return null
        }

        /**
         * Releases MediaController resources.
         * Should be called when controller is no longer needed.
         */
        fun release() {
            mediaController?.removeListener(mediaControllerListener)
            mediaController?.release()
            mediaController = null
            mediaControllerFuture?.let {
                MediaController.releaseFuture(it)
            }
            mediaControllerFuture = null
        }
    }
