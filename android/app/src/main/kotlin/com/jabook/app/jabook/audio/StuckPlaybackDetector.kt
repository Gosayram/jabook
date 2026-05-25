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

import android.os.SystemClock
import androidx.media3.common.Player
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Detects and recovers from stuck playback where ExoPlayer reports STATE_BUFFERING
 * indefinitely or position stops advancing while reported as playing.
 *
 * P-68: ExoPlayer sometimes freezes in STATE_BUFFERING without throwing an exception,
 * especially during file system issues or corrupted media. This watchdog detects:
 * - STATE_BUFFERING exceeding [STUCK_THRESHOLD_MS] (15s default)
 * - Position not advancing while isPlaying=true (renderer stuck)
 *
 * Recovery strategy:
 * 1. First attempt: seek forward 1 second
 * 2. If still stuck after [RECOVERY_RECHECK_MS]: notify via [onUnrecoverable]
 *
 * @param player ExoPlayer instance to monitor
 * @param scope Coroutine scope for the watchdog loop
 * @param onUnrecoverable Callback when recovery fails (UI should show error)
 * @param nowMsProvider Injectable clock for testing (default: SystemClock.elapsedRealtime)
 */
internal class StuckPlaybackDetector(
    private val player: Player,
    private val scope: CoroutineScope,
    private val onUnrecoverable: () -> Unit = {},
    private val nowMsProvider: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private var watchJob: Job? = null
    private var bufferingStartMs: Long? = null
    private var lastPositionMs: Long = -1L
    private var lastPositionCheckMs: Long = 0L
    private var recoveryAttempted = false

    /**
     * Starts the watchdog loop. Call once after player is initialized.
     * Checks player state every [CHECK_INTERVAL_MS].
     */
    fun startWatching() {
        stopWatching()
        watchJob =
            scope.launch {
                while (isActive) {
                    checkPlaybackState()
                    delay(CHECK_INTERVAL_MS)
                }
            }
        LogUtils.d(TAG, "StuckPlaybackDetector started")
    }

    /**
     * Stops the watchdog loop. Call when player is released or service destroyed.
     */
    fun stopWatching() {
        watchJob?.cancel()
        watchJob = null
        bufferingStartMs = null
        lastPositionMs = -1L
        recoveryAttempted = false
    }

    /**
     * Resets internal state after a successful recovery or manual intervention.
     */
    fun reset() {
        bufferingStartMs = null
        lastPositionMs = -1L
        recoveryAttempted = false
    }

    private fun checkPlaybackState() {
        val state: Int
        val isPlaying: Boolean
        val positionMs: Long

        try {
            state = player.playbackState
            isPlaying = player.isPlaying
            positionMs = player.currentPosition
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error reading player state", e)
            return
        }

        val now = nowMsProvider()

        checkBufferingStuck(state, now)
        checkPositionStuck(isPlaying, positionMs, now)
    }

    /** Exposes [checkPlaybackState] for direct unit testing without coroutine loop. */
    internal fun checkPlaybackStateForTest() = checkPlaybackState()

    private fun checkBufferingStuck(
        state: Int,
        now: Long,
    ) {
        when {
            state == Player.STATE_BUFFERING && bufferingStartMs == null -> {
                bufferingStartMs = now
            }
            state == Player.STATE_BUFFERING && bufferingStartMs != null -> {
                val bufferingDurationMs = now - bufferingStartMs!!
                if (bufferingDurationMs > STUCK_THRESHOLD_MS) {
                    LogUtils.w(TAG, "Stuck playback: buffering for ${bufferingDurationMs}ms")
                    attemptRecovery()
                }
            }
            state == Player.STATE_READY -> {
                bufferingStartMs = null
                recoveryAttempted = false
            }
        }
    }

    private fun checkPositionStuck(
        isPlaying: Boolean,
        positionMs: Long,
        now: Long,
    ) {
        if (!isPlaying) {
            lastPositionMs = -1L
            return
        }

        if (lastPositionMs == positionMs && lastPositionMs != -1L) {
            val stuckDurationMs = now - lastPositionCheckMs
            if (stuckDurationMs > POSITION_STUCK_THRESHOLD_MS) {
                LogUtils.w(TAG, "Position stuck at ${positionMs}ms for ${stuckDurationMs}ms while playing")
                attemptRecovery()
            }
        } else {
            lastPositionMs = positionMs
            lastPositionCheckMs = now
            recoveryAttempted = false
        }
    }

    private fun attemptRecovery() {
        if (recoveryAttempted) {
            LogUtils.e(TAG, "Recovery already attempted, notifying unrecoverable")
            onUnrecoverable()
            return
        }

        recoveryAttempted = true
        val currentPos: Long
        try {
            currentPos = player.currentPosition
        } catch (e: Exception) {
            LogUtils.e(TAG, "Cannot read position for recovery", e)
            onUnrecoverable()
            return
        }

        LogUtils.w(TAG, "Attempting recovery: seek forward 1s from ${currentPos}ms")
        try {
            player.seekTo(currentPos + RECOVERY_SEEK_OFFSET_MS)
        } catch (e: Exception) {
            LogUtils.e(TAG, "Recovery seek failed", e)
            onUnrecoverable()
        }
    }

    internal companion object {
        private const val TAG = "StuckPlaybackDetector"

        /** How often to check player state. */
        internal const val CHECK_INTERVAL_MS = 5_000L

        /** Buffering duration before declaring stuck. */
        internal const val STUCK_THRESHOLD_MS = 15_000L

        /** Position must be stuck this long while isPlaying=true. */
        internal const val POSITION_STUCK_THRESHOLD_MS = 10_000L

        /** Seek offset for recovery attempt. */
        internal const val RECOVERY_SEEK_OFFSET_MS = 1_000L
    }
}
