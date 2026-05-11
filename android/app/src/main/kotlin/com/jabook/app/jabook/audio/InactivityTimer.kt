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

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.util.LogUtils

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
public class InactivityTimer(
    private val context: Context,
    private val player: ExoPlayer,
    private val onTimerExpired: () -> Unit,
) {
    private var timer: SuspendableCountDownTimer? = null
    private val unloadOrchestrator = InactivityUnloadOrchestrator(context = context, onTimerExpired = onTimerExpired)

    /**
     * Current inactivity timeout in seconds.
     * Can be updated via setInactivityTimeoutMinutes().
     */
    private var inactivityTimeoutSeconds: Long = DEFAULT_INACTIVITY_TIMEOUT_SECONDS

    public companion object {
        /**
         * Default inactivity timeout: 60 minutes (3600 seconds).
         * Service will automatically stop after 60 minutes of inactivity.
         * Can be configured via AudioSettingsManager (Dart) and passed through MethodChannel.
         */
        public const val DEFAULT_INACTIVITY_TIMEOUT_SECONDS: Long = 3600L // 60 minutes

        /**
         * Minimum inactivity timeout: 10 minutes (600 seconds).
         */
        public const val MIN_INACTIVITY_TIMEOUT_SECONDS: Long = 600L // 10 minutes

        /**
         * Maximum inactivity timeout: 180 minutes (10800 seconds = 3 hours).
         */
        public const val MAX_INACTIVITY_TIMEOUT_SECONDS: Long = 10800L // 180 minutes = 3 hours
        public const val ACTION_INACTIVITY_TIMER_EXPIRED: String = "com.jabook.app.jabook.audio.INACTIVITY_TIMER_EXPIRED"
    }

    /**
     * Sets the inactivity timeout in minutes.
     *
     * @param minutes Timeout in minutes (10-180)
     */
    public fun setInactivityTimeoutMinutes(minutes: Int) {
        val seconds = (minutes * 60).toLong()
        if (seconds < MIN_INACTIVITY_TIMEOUT_SECONDS || seconds > MAX_INACTIVITY_TIMEOUT_SECONDS) {
            LogUtils.w(
                "InactivityTimer",
                "Invalid timeout: $minutes minutes (must be between ${MIN_INACTIVITY_TIMEOUT_SECONDS / 60} and ${MAX_INACTIVITY_TIMEOUT_SECONDS / 60} minutes), using default",
            )
            inactivityTimeoutSeconds = DEFAULT_INACTIVITY_TIMEOUT_SECONDS
            return
        }

        val oldTimeout = inactivityTimeoutSeconds
        inactivityTimeoutSeconds = seconds
        LogUtils.d(
            "InactivityTimer",
            "Inactivity timeout updated: ${oldTimeout / 60} -> ${inactivityTimeoutSeconds / 60} minutes",
        )

        // If timer is running, restart it with new timeout
        if (timer != null) {
            LogUtils.d("InactivityTimer", "Restarting timer with new timeout")
            stopTimer()
            checkAndStartTimer()
        }
    }

    private val eventObserver =
        InactivityPlaybackEventObserver(
            player = player,
            checkAndStartTimer = { checkAndStartTimer() },
            resetTimer = { source -> resetIfApplicable(source) },
        )

    private val playerListener: Player.Listener = eventObserver.listener
    private val listenerBinding = InactivityPlayerListenerBinding(player = player, listener = playerListener)

    init {
        listenerBinding.attach()
        LogUtils.d("InactivityTimer", "InactivityTimer initialized")
    }

    /**
     * Checks if conditions are met to start the inactivity timer.
     * Timer starts only if:
     * - Player is paused/stopped (not playing)
     * - There are media items loaded
     */
    private fun checkAndStartTimer() {
        val playbackState = player.playbackState
        val shouldStart =
            InactivityStartConditionPolicy.shouldStart(
                isPlaying = player.isPlaying,
                mediaItemCount = player.mediaItemCount,
                playbackState = playbackState,
                playWhenReady = player.playWhenReady,
            )

        if (shouldStart && timer == null) {
            LogUtils.d(
                "InactivityTimer",
                "Conditions met for starting timer: playbackState=$playbackState, playWhenReady=${player.playWhenReady}",
            )
            startTimer()
        } else if (!shouldStart) {
            LogUtils.d(
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

        LogUtils.d(
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
                        LogUtils.d(
                            "InactivityTimer",
                            "Inactivity timer running: ${seconds / 60} minutes remaining",
                        )
                    }
                },
                onFinished = {
                    unloadOrchestrator.handleTimeout(inactivityTimeoutSeconds = inactivityTimeoutSeconds)
                    stopTimer()
                },
            ).also { it.start() }
    }

    /**
     * Resets (stops and restarts) the inactivity timer.
     * Called when user performs any action (play, seek, etc.).
     */
    public fun resetTimer() {
        if (timer != null) {
            LogUtils.d("InactivityTimer", "Resetting inactivity timer (user action detected)")
            stopTimer()
        }
        // Timer will be restarted automatically if conditions are met (when playback is paused)
    }

    /**
     * Resets timer only when source is allowed by [InactivityResetPolicy].
     */
    internal fun resetIfApplicable(source: InactivityCommandSource) {
        if (!InactivityResetPolicy.shouldReset(source)) {
            LogUtils.d("InactivityTimer", "Skipping inactivity reset for source=$source")
            return
        }
        resetTimer()
    }

    /**
     * Stops and cancels the inactivity timer.
     */
    public fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    /**
     * Releases timer resources.
     */
    public fun release() {
        stopTimer()
        listenerBinding.detach()
        LogUtils.d("InactivityTimer", "InactivityTimer released")
    }
}
