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

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.audio.ErrorHandler
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages playback control operations (play, pause, stop, seek, etc.).
 */
internal class PlaybackController(
    private val getActivePlayer: () -> ExoPlayer,
    private val playerServiceScope: CoroutineScope,
    private val resetInactivityTimer: () -> Unit,
    private val getAutoRewindEnabled: () -> Boolean,
) {
    /**
     * Starts or resumes playback.
     *
     * Simplified implementation matching lissen-android approach.
     */
    private var lastPauseTime: Long = 0L

    /**
     * Starts or resumes playback.
     *
     * Simplified implementation matching lissen-android approach.
     */
    public fun play() {
        LogUtils.i("AudioPlayerService", "play() called")

        val player = getActivePlayer()
        if (player.mediaItemCount == 0) {
            LogUtils.w("AudioPlayerService", "Cannot play: no media items loaded")
            // Service might have been unloaded - state will be restored when playlist is set
            return
        }

        // Match lissen-android: simple approach - just set playWhenReady=true in coroutine
        // ExoPlayer will handle AudioFocus automatically
        playerServiceScope.launch(Dispatchers.Main) {
            try {
                // Call prepare() if player is in IDLE or ENDED state (following RiMusic pattern)
                // This ensures player restarts properly after book completion or errors
                if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                    LogUtils.d("AudioPlayerService", "play() - player is IDLE/ENDED, calling prepare()")
                    player.prepare()
                }

                // Smart Rewind Logic (Audiobook tailored)
                // Rewind based on how long we were paused
                val autoRewindEnabled =
                    try {
                        getAutoRewindEnabled()
                    } catch (e: Exception) {
                        false
                    }

                if (autoRewindEnabled) {
                    val currentTime = System.currentTimeMillis()
                    val pauseDurationMs = if (lastPauseTime > 0) currentTime - lastPauseTime else Long.MAX_VALUE

                    // Only rewind if we're not at the very beginning
                    // And if we're definitely resuming (not starting fresh with 0 duration)
                    if (player.currentPosition > 5000) {
                        val rewindMs =
                            when {
                                pauseDurationMs < 60_000 -> 0L // < 1 min: No rewind
                                pauseDurationMs < 600_000 -> 10_000L // 1-10 mins: 10s rewind
                                else -> 30_000L // > 10 mins (or app restart): 30s rewind
                            }

                        if (rewindMs > 0) {
                            val newPos = (player.currentPosition - rewindMs).coerceAtLeast(0L)
                            player.seekTo(newPos)
                            LogUtils.d(
                                "AudioPlayerService",
                                "Smart Rewind: Rewinding ${rewindMs / 1000}s (pause: ${pauseDurationMs / 1000}s)",
                            )
                        }
                    }
                }

                // Match lissen-android: simply set playWhenReady=true
                // ExoPlayer manages AudioFocus automatically when handleAudioFocus=true
                player.playWhenReady = true
                LogUtils.d(
                    "AudioPlayerService",
                    "play() - set playWhenReady=true, letting ExoPlayer handle AudioFocus",
                )

                // Reset inactivity timer (user action)
                resetInactivityTimer()
            } catch (e: Exception) {
                LogUtils.e("AudioPlayerService", "Failed to start playback", e)
                e.printStackTrace()
                ErrorHandler.handleGeneralError("AudioPlayerService", e, "Play method execution")
            }
        }
    }

    /**
     * Pauses playback.
     */
    public fun pause() {
        playerServiceScope.launch {
            try {
                val player = getActivePlayer()

                // Update lastPauseTime for Smart Rewind
                lastPauseTime = System.currentTimeMillis()

                // Small rewind on pause improves context retention for audiobooks.
                if (player.playbackState != Player.STATE_ENDED) {
                    val rewindPosition = (player.currentPosition - 2_000L).coerceAtLeast(0L)
                    player.seekTo(rewindPosition)
                }

                player.playWhenReady = false
                // Note: We don't abandon AudioFocus on pause - we keep it for quick resume
                // AudioFocus will be abandoned when service is stopped

                // Reset inactivity timer (user action - pause is also an interaction)
                resetInactivityTimer()
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
    public fun stop() {
        val player = getActivePlayer()
        try {
            LogUtils.d("AudioPlayerService", "stop() called, current playbackState: ${player.playbackState}")
            player.stop()
            // ExoPlayer manages AudioFocus automatically, no need to abandon manually
        } catch (e: Exception) {
            ErrorHandler.handleGeneralError("AudioPlayerService", e, "Stop method execution")
        }
    }

    /**
     * Seeks to specific position.
     *
     * @param positionMs Position in milliseconds
     */
    public fun seekTo(positionMs: Long) {
        val player = getActivePlayer()

        try {
            if (positionMs < 0) {
                LogUtils.w("AudioPlayerService", "Seek position cannot be negative: $positionMs")
                return
            }

            if (player.mediaItemCount == 0) {
                LogUtils.w("AudioPlayerService", "Cannot seek: no media items loaded")
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
            resetInactivityTimer()

            if (playWhenReadyBeforeSeek) {
                playerServiceScope.launch {
                    delay(100L)
                    if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) {
                        if (!player.playWhenReady) {
                            player.playWhenReady = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LogUtils.e("AudioPlayerService", "Failed to seek to position: $positionMs", e)
        }
    }

    /**
     * Sets playback speed.
     *
     * @param speed Playback speed (0.5x to 2.0x)
     */
    public fun setSpeed(speed: Float) {
        getActivePlayer().setPlaybackSpeed(speed)
        // Reset inactivity timer (user action)
        resetInactivityTimer()
    }

    /**
     * Gets current playback speed.
     */
    public fun getSpeed(): Float = getActivePlayer().playbackParameters.speed

    /**
     * Sets repeat mode.
     *
     * @param repeatMode Repeat mode:
     *   - REPEAT_MODE_OFF: No repeat
     *   - REPEAT_MODE_ONE: Repeat current track
     *   - REPEAT_MODE_ALL: Repeat all tracks
     */
    public fun setRepeatMode(repeatMode: Int) {
        getActivePlayer().repeatMode = repeatMode
        LogUtils.d("AudioPlayerService", "Repeat mode set to: $repeatMode")
        // Reset inactivity timer (user action)
        resetInactivityTimer()
    }

    /**
     * Gets current repeat mode.
     *
     * @return Current repeat mode
     */
    public fun getRepeatMode(): Int = getActivePlayer().repeatMode

    /**
     * Sets shuffle mode.
     *
     * @param shuffleModeEnabled true to enable shuffle, false to disable
     */
    public fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        getActivePlayer().shuffleModeEnabled = shuffleModeEnabled
        LogUtils.d("AudioPlayerService", "Shuffle mode set to: $shuffleModeEnabled")
        // Reset inactivity timer (user action)
        resetInactivityTimer()
    }

    /**
     * Gets current shuffle mode.
     *
     * @return true if shuffle is enabled, false otherwise
     */
    public fun getShuffleModeEnabled(): Boolean = getActivePlayer().shuffleModeEnabled

    /**
     * Skips to next track.
     *
     * Inspired by lissen-android: checks track availability before switching.
     */
    public fun next() {
        val player = getActivePlayer()
        val currentIndex = player.currentMediaItemIndex

        // Find next available track
        val nextAvailableIndex =
            TrackAvailabilityChecker.findAvailableTrackIndex(
                player = player,
                currentIndex = currentIndex,
                direction = TrackAvailabilityChecker.Direction.FORWARD,
            )

        if (nextAvailableIndex != null && nextAvailableIndex != currentIndex) {
            player.seekTo(nextAvailableIndex, 0L)
            // Following RiMusic pattern: prepare() + playWhenReady after seek
            if (player.playbackState != Player.STATE_READY && player.playbackState != Player.STATE_BUFFERING) {
                player.prepare()
            }
            player.playWhenReady = true
            LogUtils.d("AudioPlayerService", "Skipping to next available track: $nextAvailableIndex")
        } else if (nextAvailableIndex == null) {
            LogUtils.w("AudioPlayerService", "No available tracks found, stopping playback")
            player.playWhenReady = false
        } else {
            // Already on available track, use default behavior
            player.seekToNextMediaItem()
            // Following RiMusic pattern: prepare() + playWhenReady after seek
            if (player.playbackState != Player.STATE_READY && player.playbackState != Player.STATE_BUFFERING) {
                player.prepare()
            }
            player.playWhenReady = true
            LogUtils.d("AudioPlayerService", "Skipping to next track (default)")
        }

        // Reset inactivity timer (user action)
        resetInactivityTimer()
    }

    /**
     * Skips to previous track.
     *
     * Inspired by lissen-android: checks track availability before switching.
     */
    public fun previous() {
        val player = getActivePlayer()
        val currentIndex = player.currentMediaItemIndex

        // Find previous available track
        val prevAvailableIndex =
            TrackAvailabilityChecker.findAvailableTrackIndex(
                player = player,
                currentIndex = currentIndex,
                direction = TrackAvailabilityChecker.Direction.BACKWARD,
            )

        if (prevAvailableIndex != null && prevAvailableIndex != currentIndex) {
            player.seekTo(prevAvailableIndex, 0L)
            // Following RiMusic pattern: prepare() + playWhenReady after seek
            if (player.playbackState != Player.STATE_READY && player.playbackState != Player.STATE_BUFFERING) {
                player.prepare()
            }
            player.playWhenReady = true
            LogUtils.d("AudioPlayerService", "Skipping to previous available track: $prevAvailableIndex")
        } else if (prevAvailableIndex == null) {
            LogUtils.w("AudioPlayerService", "No available tracks found, stopping playback")
            player.playWhenReady = false
        } else {
            // Already on available track, use default behavior
            player.seekToPreviousMediaItem()
            // Following RiMusic pattern: prepare() + playWhenReady after seek
            if (player.playbackState != Player.STATE_READY && player.playbackState != Player.STATE_BUFFERING) {
                player.prepare()
            }
            player.playWhenReady = true
            LogUtils.d("AudioPlayerService", "Skipping to previous track (default)")
        }

        // Reset inactivity timer (user action)
        resetInactivityTimer()
    }

    /**
     * Seeks to specific track by index.
     *
     * @param index Track index in playlist
     */
    public fun seekToTrack(index: Int) {
        val player = getActivePlayer()
        if (index >= 0 && index < player.mediaItemCount) {
            val playWhenReadyBeforeSeek = player.playWhenReady
            player.seekTo(index, 0L)

            // Reset inactivity timer (user action)
            resetInactivityTimer()

            if (playWhenReadyBeforeSeek) {
                playerServiceScope.launch {
                    delay(100L)
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
     * Seeks to specific track and position.
     *
     * @param trackIndex Track index in playlist
     * @param positionMs Position in milliseconds within the track
     */
    public fun seekToTrackAndPosition(
        trackIndex: Int,
        positionMs: Long,
    ) {
        val player = getActivePlayer()

        if (trackIndex < 0 || trackIndex >= player.mediaItemCount) {
            LogUtils.w(
                "AudioPlayerService",
                "Invalid track index: $trackIndex (mediaItemCount: ${player.mediaItemCount})",
            )
            return
        }

        if (positionMs < 0) {
            LogUtils.w("AudioPlayerService", "Seek position cannot be negative: $positionMs")
            return
        }

        try {
            val playWhenReadyBeforeSeek = player.playWhenReady
            player.seekTo(trackIndex, positionMs)

            // Reset inactivity timer (user action)
            resetInactivityTimer()

            if (playWhenReadyBeforeSeek) {
                playerServiceScope.launch {
                    delay(100L)
                    if (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING) {
                        if (!player.playWhenReady) {
                            player.playWhenReady = true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LogUtils.e(
                "AudioPlayerService",
                "Failed to seek to track and position: trackIndex=$trackIndex, positionMs=$positionMs",
                e,
            )
        }
    }

    /**
     * Rewinds playback by specified seconds.
     *
     * @param seconds Number of seconds to rewind (default: 15)
     */
    public fun rewind(seconds: Int = 15) {
        val player = getActivePlayer()
        val currentPosition = player.currentPosition
        val newPosition = (currentPosition - seconds * 1000L).coerceAtLeast(0L)
        player.seekTo(newPosition)
        LogUtils.d("AudioPlayerService", "Rewind: ${seconds}s (from ${currentPosition}ms to ${newPosition}ms)")
        // Reset inactivity timer (user action)
        resetInactivityTimer()
    }

    /**
     * Forwards playback by specified seconds.
     *
     * @param seconds Number of seconds to forward (default: 30)
     */
    public fun forward(seconds: Int = 30) {
        val player = getActivePlayer()
        val currentPosition = player.currentPosition
        val duration = player.duration
        if (duration != C.TIME_UNSET) {
            val newPosition = (currentPosition + seconds * 1000L).coerceAtMost(duration)
            player.seekTo(newPosition)
            LogUtils.d(
                "AudioPlayerService",
                "Forward: ${seconds}s (from ${currentPosition}ms to ${newPosition}ms)",
            )
            // Reset inactivity timer (user action)
            resetInactivityTimer()
        }
    }

    /**
     * Applies initial position after playlist is loaded.
     * This is called in background to avoid blocking setPlaylist callback.
     *
     * @param trackIndex Track index to seek to
     * @param positionMs Position in milliseconds to seek to
     * @param expectedTrackCount Total expected tracks (for validation)
     */
    public suspend fun applyInitialPosition(
        trackIndex: Int,
        positionMs: Long,
        expectedTrackCount: Int?,
    ) {
        LogUtils.d(
            "AudioPlayerService",
            "Waiting for player to be ready and track loaded before applying initial position: track=$trackIndex, position=${positionMs}ms",
        )

        // Check if target track is already the current track (loaded first)
        val initialPlayer = getActivePlayer()
        val currentIndex = initialPlayer.currentMediaItemIndex
        val isTargetTrackAlreadyCurrent = currentIndex == trackIndex

        if (isTargetTrackAlreadyCurrent) {
            LogUtils.d(
                "AudioPlayerService",
                "Target track $trackIndex is already current track, applying position immediately for smooth resume",
            )
        }

        // Wait for player to be ready AND the target track to be loaded
        var attempts = 0
        val maxAttempts = 50 // 5 seconds max wait (reduced since we don't need all tracks)
        var playerReady = false
        var trackLoaded = false

        while (attempts < maxAttempts) {
            val checkPlayer = getActivePlayer()
            val state = checkPlayer.playbackState
            val mediaItemCount = checkPlayer.mediaItemCount
            val currentMediaItemIndex = checkPlayer.currentMediaItemIndex

            if (attempts % 10 == 0) { // Log every second
                LogUtils.v(
                    "AudioPlayerService",
                    "Waiting for player ready and track loaded: attempt=$attempts, state=$state, mediaItemCount=$mediaItemCount, currentIndex=$currentMediaItemIndex, needTrack=$trackIndex",
                )
            }

            // Check if player is ready
            val isPlayerReady = state == Player.STATE_READY || state == Player.STATE_BUFFERING
            // Check if target track is loaded
            val isTrackLoaded = mediaItemCount > trackIndex

            // OPTIMIZATION: If target track is already current, we can apply position immediately
            // without waiting for all tracks. This provides instant, smooth resume.
            // Only wait for all tracks if target track is NOT the first loaded track.
            val shouldWaitForAllTracks = !isTargetTrackAlreadyCurrent && expectedTrackCount != null
            val allTracksLoaded = !shouldWaitForAllTracks || mediaItemCount >= (expectedTrackCount ?: 0)

            if (isPlayerReady && isTrackLoaded && allTracksLoaded) {
                playerReady = true
                trackLoaded = true
                LogUtils.d(
                    "AudioPlayerService",
                    "Player is ready, track $trackIndex is loaded (current=$currentMediaItemIndex, mediaItemCount=$mediaItemCount), applying initial position immediately",
                )
                break
            }

            // Log progress every second
            if (attempts % 10 == 0) {
                LogUtils.v(
                    "AudioPlayerService",
                    "Waiting: ready=$isPlayerReady, trackLoaded=$isTrackLoaded, allTracksLoaded=$allTracksLoaded, currentIndex=$currentMediaItemIndex",
                )
            }

            delay(100L)
            attempts++
        }

        if (!playerReady || !trackLoaded) {
            LogUtils.w(
                "AudioPlayerService",
                "Player not ready or track not loaded after $maxAttempts attempts: playerReady=$playerReady, trackLoaded=$trackLoaded, will try to apply position anyway",
            )
        }

        if (expectedTrackCount != null) {
            val finalPlayer = getActivePlayer()
            val finalCount = finalPlayer.mediaItemCount
            if (finalCount != expectedTrackCount) {
                LogUtils.w(
                    "AudioPlayerService",
                    "Track count mismatch: expected=$expectedTrackCount, actual=$finalCount (possible duplicates detected)",
                )
            }
        }

        // Seek to saved position
        val player = getActivePlayer()
        val currentMediaItemIndex = player.currentMediaItemIndex
        var currentMediaItemCount = player.mediaItemCount

        // OPTIMIZATION: If target track is already current, apply position immediately
        // This provides instant, smooth resume without waiting for all tracks
        if (currentMediaItemIndex == trackIndex) {
            LogUtils.d(
                "AudioPlayerService",
                "Target track $trackIndex is already current, applying position immediately for smooth resume",
            )
            // Small delay to ensure player is stable
            delay(100L)
        } else {
            // If target track is not current, we need to wait a bit more and potentially
            // wait for all tracks to prevent playlist resets during seekTo
            LogUtils.d(
                "AudioPlayerService",
                "Target track $trackIndex is not current (current=$currentMediaItemIndex), waiting for stability",
            )
            delay(200L) // Small delay to let parallel loading stabilize

            // Only wait for all tracks if target is not the first loaded track
            if (expectedTrackCount != null && currentMediaItemCount < expectedTrackCount) {
                LogUtils.d(
                    "AudioPlayerService",
                    "Waiting for all tracks to load: current=$currentMediaItemCount, expected=$expectedTrackCount",
                )
                var waitAttempts = 0
                val maxWaitAttempts = 20 // 2 seconds (reduced for faster resume)
                while (waitAttempts < maxWaitAttempts && currentMediaItemCount < expectedTrackCount) {
                    delay(100L)
                    val checkPlayer = getActivePlayer()
                    val newCount = checkPlayer.mediaItemCount
                    if (newCount >= expectedTrackCount) {
                        LogUtils.d(
                            "AudioPlayerService",
                            "All tracks loaded: $newCount/$expectedTrackCount",
                        )
                        break
                    }
                    currentMediaItemCount = newCount
                    waitAttempts++
                }
            }
        }

        LogUtils.d(
            "AudioPlayerService",
            "Attempting to apply initial position: track=$trackIndex, position=${positionMs}ms, currentIndex=$currentMediaItemIndex, mediaItemCount=$currentMediaItemCount",
        )

        if (currentMediaItemCount > trackIndex) {
            LogUtils.i(
                "AudioPlayerService",
                "Applying initial position: track=$trackIndex, position=${positionMs}ms (all tracks loaded: $currentMediaItemCount)",
            )
            seekToTrackAndPosition(trackIndex, positionMs)
        } else {
            LogUtils.w(
                "AudioPlayerService",
                "Cannot apply initial position: track index $trackIndex not yet loaded (mediaItemCount=$currentMediaItemCount), waiting for track to load...",
            )
            // Wait for the track to be loaded (with additional timeout)
            var retryAttempts = 0
            val maxRetryAttempts = 50 // 5 more seconds
            while (retryAttempts < maxRetryAttempts) {
                delay(200L)
                val retryPlayer = getActivePlayer()
                if (retryPlayer.mediaItemCount > trackIndex) {
                    LogUtils.i(
                        "AudioPlayerService",
                        "Track $trackIndex loaded after waiting, applying initial position: position=${positionMs}ms",
                    )
                    seekToTrackAndPosition(trackIndex, positionMs)
                    return
                }
                retryAttempts++
                if (retryAttempts % 10 == 0) {
                    LogUtils.v(
                        "AudioPlayerService",
                        "Waiting for track load: $retryAttempts",
                    )
                }
            }
            LogUtils.e(
                "AudioPlayerService",
                "Failed to apply initial position: track $trackIndex never loaded",
            )
        }
    }
}
