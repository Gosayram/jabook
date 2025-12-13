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

package com.jabook.app.jabook.compose.feature.player.controller

import android.content.Context
import android.content.Intent
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

        private val playerListener =
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    if (isPlaying) {
                        startPositionUpdater()
                    } else {
                        stopPositionUpdater()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _duration.value = exoPlayer.duration.coerceAtLeast(0)
                    if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                        // Initial position update
                        _currentPosition.value = exoPlayer.currentPosition
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    _currentPosition.value = exoPlayer.currentPosition
                    _currentChapterIndex.value = exoPlayer.currentMediaItemIndex
                }

                override fun onMediaItemTransition(
                    mediaItem: androidx.media3.common.MediaItem?,
                    reason: Int,
                ) {
                    _currentChapterIndex.value = exoPlayer.currentMediaItemIndex
                    _duration.value = exoPlayer.duration.coerceAtLeast(0)
                }
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

            // Observe playback speed
            scope.launch {
                userPreferencesRepository.userData.collect { userData ->
                    exoPlayer.setPlaybackSpeed(userData.playbackSpeed)
                }
            }
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
        ) {
            startService()

            // Wait slightly for service to be ready if needed, or rely on getInstance()
            // Since we started service, it might take a moment to set its instance.
            // However, usually we are in the same process.

            val service = AudioPlayerService.getInstance()
            if (service != null) {
                service.setPlaylist(
                    filePaths = filePaths,
                    initialTrackIndex = initialChapterIndex,
                    initialPosition = initialPosition,
                    callback = { success, _ ->
                        if (success) play()
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

        private fun startService() {
            try {
                val intent = Intent(context, AudioPlayerService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("AudioPlayerController", "Failed to start service", e)
            }
        }
    }
