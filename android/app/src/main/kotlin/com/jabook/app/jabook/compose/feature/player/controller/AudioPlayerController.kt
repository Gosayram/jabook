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

import android.content.Context
import android.content.Intent
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.audio.AudioPlayerService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controller to bridge Compose UI with AudioPlayerService and ExoPlayer.
 *
 * Architecture Note:
 * This controller maintains its own Player.Listener for UI state management (StateFlow).
 * The AudioPlayerService also has a PlayerListener for business logic (saving position, notifications, widgets).
 * Both listen to the same ExoPlayer singleton, which serves as the single source of truth.
 * This separation of concerns allows:
 * - UI layer to reactively observe state changes via StateFlow
 * - Service layer to handle persistence, notifications, and other business logic
 *
 * Uses:
 * - Hilt-injected ExoPlayer singleton for reactive state observation
 * - AudioPlayerService.getInstance() for commands (preserving existing logic)
 */
@Singleton
class AudioPlayerController
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val exoPlayer: ExoPlayer,
        private val userPreferencesRepository: com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository,
    ) {
        private val scope = CoroutineScope(Dispatchers.Main)
        private var positionUpdaterJob: Job? = null

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
         * Player listener for UI state management.
         * Note: This is separate from PlayerListener in AudioPlayerService, which handles
         * business logic (persistence, notifications, widgets). Both listen to the same ExoPlayer
         * singleton, ensuring state consistency. ExoPlayer is the single source of truth.
         */
        private val playerListener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    // Update UI state from ExoPlayer (single source of truth)
                    _isPlaying.value = isPlaying
                    if (isPlaying) {
                        startPositionUpdater()
                    } else {
                        stopPositionUpdater()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    // Update duration from ExoPlayer (single source of truth)
                    _duration.value = exoPlayer.duration.coerceAtLeast(0)
                    if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                        // Initial position update from ExoPlayer
                        _currentPosition.value = exoPlayer.currentPosition

                        // Handle chapter end for repeat logic (UI-level concern)
                        if (playbackState == Player.STATE_ENDED) {
                            val shouldRepeat = onChapterEndedCallback?.invoke() ?: false
                            if (shouldRepeat) {
                                // Repeat current chapter by seeking to start
                                val currentIndex = exoPlayer.currentMediaItemIndex
                                exoPlayer.seekTo(currentIndex, 0)
                                // Resume playback if it was playing
                                if (exoPlayer.playWhenReady) {
                                    exoPlayer.play()
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
                    // Update position and chapter index from ExoPlayer (single source of truth)
                    _currentPosition.value = exoPlayer.currentPosition
                    _currentChapterIndex.value = exoPlayer.currentMediaItemIndex
                }

                override fun onMediaItemTransition(
                    mediaItem: androidx.media3.common.MediaItem?,
                    reason: Int,
                ) {
                    // Update chapter index and duration from ExoPlayer (single source of truth)
                    _currentChapterIndex.value = exoPlayer.currentMediaItemIndex
                    _duration.value = exoPlayer.duration.coerceAtLeast(0)
                    updateStats()
                }

                override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                    updateStats()
                }

                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    updateStats()
                }
            }

        private fun updateStats() {
            val format = exoPlayer.audioFormat
            val audioFormat =
                "${format?.sampleMimeType ?: "Unknown"} ${format?.bitrate?.let {
                    if (it > 0) "${it / 1000}kbps" else ""
                } ?: ""}"
                    .trim()
            val bufferMs = exoPlayer.bufferedPosition - exoPlayer.currentPosition

            _playerStats.value =
                com.jabook.app.jabook.compose.feature.player.PlayerStats(
                    audioFormat = audioFormat.ifEmpty { "Unknown" },
                    bitrate = format?.bitrate?.let { if (it > 0) "${it / 1000} kbps" else "Unknown" } ?: "Unknown",
                    bufferHealth = "${bufferMs / 1000}s",
                    audioSessionId =
                        if (exoPlayer.audioSessionId !=
                            C.AUDIO_SESSION_ID_UNSET
                        ) {
                            exoPlayer.audioSessionId.toString()
                        } else {
                            "None"
                        },
                    decoderName = "ExoPlayer Audio Decoder",
                    droppedFrames = 0, // Audio usually doesn't drop frames like video
                )
        }

        init {
            // Attach listener to singleton ExoPlayer
            exoPlayer.addListener(playerListener)
            // Initialize state
            _isPlaying.value = exoPlayer.isPlaying
            _currentPosition.value = exoPlayer.currentPosition
            _duration.value = exoPlayer.duration.coerceAtLeast(0)
            _currentChapterIndex.value = exoPlayer.currentMediaItemIndex

            if (exoPlayer.isPlaying) {
                startPositionUpdater()
            }

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
                        exoPlayer.playbackParameters = params
                    }
            }
        }

        fun setPitchCorrectionEnabled(enabled: Boolean) {
            _pitchCorrectionEnabled.value = enabled
        }

        private fun startPositionUpdater() {
            positionUpdaterJob?.cancel()
            positionUpdaterJob =
                scope.launch {
                    while (isActive) {
                        _currentPosition.value = exoPlayer.currentPosition
                        delay(200) // Update every 200ms
                    }
                }
        }

        private fun stopPositionUpdater() {
            positionUpdaterJob?.cancel()
            positionUpdaterJob = null
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

            val service = AudioPlayerService.getInstance()
            if (service != null) {
                // Check if we are already playing this book to avoid restarting
                // CRITICAL: Use bookId (groupPath) as primary check since file paths may differ after sorting
                val currentGroupPath = service.currentGroupPath
                val currentPaths = service.currentFilePaths
                val isSameBook = bookId != null && bookId == currentGroupPath
                val isSamePlaylist =
                    currentPaths != null &&
                        currentPaths.size == filePaths.size &&
                        currentPaths == filePaths

                if ((isSameBook || isSamePlaylist) && !isBookChanged) {
                    android.util.Log.i(
                        "AudioPlayerController",
                        "Book already loaded (groupPath match: $isSameBook, paths match: $isSamePlaylist). Skipping setPlaylist.",
                    )

                    // Handle seeking if needed (e.g. user clicked a specific chapter)
                    // Only seek if significantly different to allow resume logic to work
                    if (initialChapterIndex != exoPlayer.currentMediaItemIndex) {
                        service.seekToTrack(initialChapterIndex)
                    }
                    if (initialPosition > 0 && Math.abs(exoPlayer.currentPosition - initialPosition) > 1000) {
                        service.seekTo(initialPosition)
                    }

                    if (autoPlay && !exoPlayer.isPlaying) {
                        play()
                    }
                    return
                }

                service.setPlaylist(
                    filePaths = filePaths,
                    metadata = metadata,
                    initialTrackIndex = initialChapterIndex,
                    initialPosition = initialPosition,
                    groupPath = bookId,
                    callback = { success, _ ->
                        if (success && autoPlay) {
                            play()
                        }
                    },
                )
            } else {
                // Should potentially queue command or retry
                android.util.Log.e("AudioPlayerController", "Service instance not found after startService")
            }
        }

        fun play() {
            startService()
            AudioPlayerService.getInstance()?.play()
        }

        fun pause() {
            AudioPlayerService.getInstance()?.pause()
        }

        fun seekTo(positionMs: Long) {
            AudioPlayerService.getInstance()?.seekTo(positionMs)
        }

        fun skipToNext() {
            AudioPlayerService.getInstance()?.next()
        }

        fun skipToPrevious() {
            AudioPlayerService.getInstance()?.previous()
        }

        fun skipToChapter(index: Int) {
            AudioPlayerService.getInstance()?.seekToTrack(index)
        }

        fun setPlaybackSpeed(speed: Float) {
            AudioPlayerService.getInstance()?.setSpeed(speed)
        }

        private fun startService() {
            try {
                // CRITICAL FIX: Only start service if it's not already running
                // Calling startService() when service is already running can trigger onCreate() again
                if (AudioPlayerService.getInstance() != null) {
                    android.util.Log.d("AudioPlayerController", "Service already running, skipping startService()")
                    return
                }

                android.util.Log.i("AudioPlayerController", "Starting AudioPlayerService...")
                val intent = Intent(context, AudioPlayerService::class.java)
                // Use startService instead of startForegroundService.
                // Since the app is in the foreground, we are allowed to start the service.
                // MediaLibraryService will internally handle promoting it to foreground
                // when playback starts and notification is posted.
                context.startService(intent)
            } catch (e: Exception) {
                android.util.Log.e("AudioPlayerController", "Failed to start service", e)
            }
        }
    }
