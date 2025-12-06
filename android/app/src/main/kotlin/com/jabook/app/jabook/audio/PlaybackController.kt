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

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.audio.ErrorHandler
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
) {
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
                resetInactivityTimer()
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
            resetInactivityTimer()

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
        resetInactivityTimer()
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
        resetInactivityTimer()
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
        resetInactivityTimer()
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
        resetInactivityTimer()
    }

    /**
     * Skips to previous track.
     */
    fun previous() {
        getActivePlayer().seekToPreviousMediaItem()
        android.util.Log.d("AudioPlayerService", "Skipping to previous track")
        // Reset inactivity timer (user action)
        resetInactivityTimer()
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
            resetInactivityTimer()

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
            resetInactivityTimer()

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
        resetInactivityTimer()
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
            resetInactivityTimer()
        }
    }
}
