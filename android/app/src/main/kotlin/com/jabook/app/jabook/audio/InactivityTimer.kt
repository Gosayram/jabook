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

import android.content.Context
import android.content.Intent
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Manages inactivity timer for audio player.
 *
 * This timer automatically unloads the player after a period of inactivity
 * (configurable, default: 60 minutes) to save system resources (CPU, RAM, battery).
 *
 * Timer starts when playback is paused/stopped and no user interaction occurs.
 * Timer resets on any user action (play, seek, speed change, etc.).
 *
 * Inspired by best practices for Android Media3 lifecycle management.
 */
class InactivityTimer(
    private val context: Context,
    private val player: ExoPlayer,
    private val onTimerExpired: () -> Unit,
) {
    private var timer: SuspendableCountDownTimer? = null

    /**
     * Current inactivity timeout in seconds.
     * Can be updated via setInactivityTimeoutMinutes().
     */
    private var inactivityTimeoutSeconds: Long = DEFAULT_INACTIVITY_TIMEOUT_SECONDS

    companion object {
        /**
         * Default inactivity timeout: 60 minutes (3600 seconds).
         * Can be configured via AudioSettingsManager (Dart) and passed through MethodChannel.
         */
        const val DEFAULT_INACTIVITY_TIMEOUT_SECONDS = 3600L // 60 minutes

        /**
         * Minimum inactivity timeout: 10 minutes (600 seconds).
         */
        const val MIN_INACTIVITY_TIMEOUT_SECONDS = 600L // 10 minutes

        /**
         * Maximum inactivity timeout: 180 minutes (10800 seconds = 3 hours).
         */
        const val MAX_INACTIVITY_TIMEOUT_SECONDS = 10800L // 180 minutes = 3 hours

        const val ACTION_INACTIVITY_TIMER_EXPIRED = "com.jabook.app.jabook.audio.INACTIVITY_TIMER_EXPIRED"
    }

    /**
     * Sets the inactivity timeout in minutes.
     *
     * @param minutes Timeout in minutes (10-180)
     */
    fun setInactivityTimeoutMinutes(minutes: Int) {
        val seconds = (minutes * 60).toLong()
        if (seconds < MIN_INACTIVITY_TIMEOUT_SECONDS || seconds > MAX_INACTIVITY_TIMEOUT_SECONDS) {
            android.util.Log.w(
                "InactivityTimer",
                "Invalid timeout: $minutes minutes (must be between ${MIN_INACTIVITY_TIMEOUT_SECONDS / 60} and ${MAX_INACTIVITY_TIMEOUT_SECONDS / 60} minutes), using default",
            )
            inactivityTimeoutSeconds = DEFAULT_INACTIVITY_TIMEOUT_SECONDS
            return
        }

        val oldTimeout = inactivityTimeoutSeconds
        inactivityTimeoutSeconds = seconds
        android.util.Log.d(
            "InactivityTimer",
            "Inactivity timeout updated: ${oldTimeout / 60} -> ${inactivityTimeoutSeconds / 60} minutes",
        )

        // If timer is running, restart it with new timeout
        if (timer != null) {
            android.util.Log.d("InactivityTimer", "Restarting timer with new timeout")
            stopTimer()
            checkAndStartTimer()
        }
    }

    private val playerListener =
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    // Playback started - reset timer
                    android.util.Log.d(
                        "InactivityTimer",
                        "Playback started (isPlaying=true), resetting inactivity timer",
                    )
                    resetTimer()
                } else {
                    // Playback paused/stopped - start timer if conditions are met
                    android.util.Log.d(
                        "InactivityTimer",
                        "Playback paused/stopped (isPlaying=false), checking if should start timer",
                    )
                    checkAndStartTimer()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                // Check if we should start/stop timer based on playback state
                when (playbackState) {
                    Player.STATE_READY -> {
                        if (!player.playWhenReady) {
                            // Player is ready but paused - start timer
                            checkAndStartTimer()
                        } else {
                            // Player is ready and playing - reset timer
                            resetTimer()
                        }
                    }
                    Player.STATE_ENDED -> {
                        // Playback ended - start timer
                        checkAndStartTimer()
                    }
                    Player.STATE_IDLE, Player.STATE_BUFFERING -> {
                        // Don't start timer in these states
                        resetTimer()
                    }
                }
            }

            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int,
            ) {
                // Track changed - reset timer (user action)
                android.util.Log.d(
                    "InactivityTimer",
                    "Media item transition detected (user action), resetting inactivity timer",
                )
                resetTimer()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                // Position changed (seek) - reset timer (user action)
                if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                    reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
                ) {
                    android.util.Log.d(
                        "InactivityTimer",
                        "Position discontinuity (seek) detected (user action), resetting inactivity timer",
                    )
                    resetTimer()
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                // Playback speed changed - reset timer (user action)
                android.util.Log.d(
                    "InactivityTimer",
                    "Playback parameters changed (speed=${playbackParameters.speed}, user action), resetting inactivity timer",
                )
                resetTimer()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                // Repeat mode changed - reset timer (user action)
                android.util.Log.d("InactivityTimer", "Repeat mode changed (user action), resetting inactivity timer")
                resetTimer()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                // Shuffle mode changed - reset timer (user action)
                android.util.Log.d("InactivityTimer", "Shuffle mode changed (user action), resetting inactivity timer")
                resetTimer()
            }
        }

    init {
        player.addListener(playerListener)
        android.util.Log.d("InactivityTimer", "InactivityTimer initialized")
    }

    /**
     * Checks if conditions are met to start the inactivity timer.
     * Timer starts only if:
     * - Player is paused/stopped (not playing)
     * - There are media items loaded
     */
    private fun checkAndStartTimer() {
        if (player.isPlaying) {
            // Player is playing - don't start timer
            android.util.Log.d("InactivityTimer", "Player is playing, not starting inactivity timer")
            return
        }

        if (player.mediaItemCount == 0) {
            // No media items loaded - don't start timer
            android.util.Log.d("InactivityTimer", "No media items loaded, not starting inactivity timer")
            return
        }

        val playbackState = player.playbackState
        val shouldStart =
            when (playbackState) {
                Player.STATE_READY -> !player.playWhenReady // Paused
                Player.STATE_ENDED -> true // Ended
                else -> false // Other states
            }

        if (shouldStart && timer == null) {
            android.util.Log.d(
                "InactivityTimer",
                "Conditions met for starting timer: playbackState=$playbackState, playWhenReady=${player.playWhenReady}",
            )
            startTimer()
        } else if (!shouldStart) {
            android.util.Log.d(
                "InactivityTimer",
                "Conditions not met for starting timer: playbackState=$playbackState, playWhenReady=${player.playWhenReady}",
            )
        }
    }

    /**
     * Starts the inactivity timer.
     */
    private fun startTimer() {
        stopTimer()

        android.util.Log.d(
            "InactivityTimer",
            "Starting inactivity timer: ${inactivityTimeoutSeconds}s (${inactivityTimeoutSeconds / 60} minutes)",
        )

        timer =
            SuspendableCountDownTimer(
                totalMillis = inactivityTimeoutSeconds * 1000L,
                intervalMillis = 60000L, // Check every minute (not too frequent)
                onTickSeconds = { seconds ->
                    // Log remaining time every 10 minutes for debugging
                    if (seconds % 600 == 0L) {
                        android.util.Log.d(
                            "InactivityTimer",
                            "Inactivity timer running: ${seconds / 60} minutes remaining",
                        )
                    }
                },
                onFinished = {
                    android.util.Log.i(
                        "InactivityTimer",
                        "Inactivity timer expired after ${inactivityTimeoutSeconds}s (${inactivityTimeoutSeconds / 60} minutes), unloading player",
                    )
                    android.util.Log.d("InactivityTimer", "Releasing resources: MediaSession, ExoPlayer, Notification")
                    broadcastTimerExpired()
                    onTimerExpired()
                    stopTimer()
                },
            ).also { it.start() }
    }

    /**
     * Resets (stops and restarts) the inactivity timer.
     * Called when user performs any action (play, seek, etc.).
     */
    fun resetTimer() {
        if (timer != null) {
            android.util.Log.d("InactivityTimer", "Resetting inactivity timer (user action detected)")
            stopTimer()
        }
        // Timer will be restarted automatically if conditions are met (when playback is paused)
    }

    /**
     * Stops and cancels the inactivity timer.
     */
    fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    /**
     * Broadcasts timer expiration event.
     */
    private fun broadcastTimerExpired() {
        val intent = Intent(ACTION_INACTIVITY_TIMER_EXPIRED)
        context.sendBroadcast(intent)
        android.util.Log.d("InactivityTimer", "Broadcasted inactivity timer expiration")
    }

    /**
     * Releases timer resources.
     */
    fun release() {
        stopTimer()
        player.removeListener(playerListener)
        android.util.Log.d("InactivityTimer", "InactivityTimer released")
    }
}
