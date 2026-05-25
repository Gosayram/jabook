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

import com.jabook.app.jabook.util.LogUtils
import com.jabook.app.jabook.utils.loggingCoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Throttles seek operations using trailing-edge debounce.
 *
 * P-82: Inspired by RetroMusicPlayer's ThrottledSeekHandler.
 *
 * When the user drags the seek bar rapidly, this handler aggregates
 * events and only executes the **final** position after a quiet period,
 * preventing:
 * - UI jitter from too many position updates
 * - Excessive ExoPlayer seek operations
 * - MediaSession notification spam
 *
 * **Trailing-edge guarantee:** the last position is always executed.
 * If the user stops dragging, the final position fires after [throttleMs].
 * If the user releases the bar, call [seekToImmediate] for instant execution.
 *
 * Usage:
 * ```
 * // During drag:
 * throttledSeekHandler.notifySeek(position) { pos ->
 *     player.seekTo(pos)
 * }
 * // On finger up:
 * throttledSeekHandler.seekToImmediate(position) { pos ->
 *     player.seekTo(pos)
 * }
 * ```
 */
@Singleton
public class ThrottledSeekHandler
    @Inject
    constructor() {
        public companion object {
            private const val TAG = "ThrottledSeekHandler"

            /** Default throttle delay in milliseconds (100ms trailing edge). */
            public const val DEFAULT_THROTTLE_MS: Long = 100L
        }

        private val scope =
            CoroutineScope(
                SupervisorJob() + Dispatchers.Main + loggingCoroutineExceptionHandler("ThrottledSeekHandler"),
            )
        private var pendingSeekJob: Job? = null
        private var pendingPositionMs: Long? = null

        /** Throttle delay in milliseconds. Can be configured. */
        public var throttleMs: Long = DEFAULT_THROTTLE_MS

        /**
         * Notifies a seek event during rapid dragging.
         *
         * Uses trailing-edge debounce: records the position and waits for [throttleMs]
         * of silence before executing. Each new call resets the timer but the latest
         * position is always preserved and eventually executed.
         *
         * @param positionMs The seek target position in milliseconds
         * @param onSeekComplete Callback executed when throttle delay expires with final position
         */
        public fun notifySeek(
            positionMs: Long,
            onSeekComplete: (Long) -> Unit,
        ) {
            pendingPositionMs = positionMs

            if (pendingSeekJob?.isActive == true) return

            pendingSeekJob =
                scope.launch {
                    LogUtils.v(TAG, "Seek trailing-edge wait started: ${positionMs}ms, throttle=${throttleMs}ms")
                    delay(throttleMs)

                    pendingPositionMs?.let { finalPosition ->
                        LogUtils.d(TAG, "Seek executed (trailing edge): ${finalPosition}ms")
                        pendingPositionMs = null
                        onSeekComplete(finalPosition)
                    }
                    pendingSeekJob = null
                }
        }

        /**
         * Immediately executes a seek without throttle delay.
         *
         * P-82: Use when the user releases the seek bar (finger up / ACTION_UP).
         * Cancels any pending throttled seek and executes immediately with
         * the given position. This prevents the last seek from being lost.
         *
         * @param positionMs The final seek position in milliseconds
         * @param onSeekComplete Callback executed immediately with the position
         */
        public fun seekToImmediate(
            positionMs: Long,
            onSeekComplete: (Long) -> Unit,
        ) {
            pendingSeekJob?.cancel()
            pendingSeekJob = null
            pendingPositionMs = null

            LogUtils.d(TAG, "Seek immediate (finger up): ${positionMs}ms")
            onSeekComplete(positionMs)
        }

        /**
         * Immediately executes any pending seek without waiting.
         * Use when playback stops or state needs to be finalized.
         *
         * @param onSeekComplete Callback with the pending position, or no-op if none pending
         */
        public fun flush(onSeekComplete: ((Long) -> Unit)? = null) {
            val pending = pendingPositionMs
            pendingSeekJob?.cancel()
            pendingSeekJob = null
            pendingPositionMs = null

            if (pending != null && onSeekComplete != null) {
                LogUtils.d(TAG, "Flush executed: ${pending}ms")
                onSeekComplete(pending)
            }
        }

        /**
         * Cancels any pending seek operation without executing it.
         */
        public fun cancel() {
            pendingSeekJob?.cancel()
            pendingSeekJob = null
            pendingPositionMs = null
            LogUtils.v(TAG, "Pending seek cancelled")
        }

        /**
         * Checks if there's a pending seek operation.
         */
        public fun hasPendingSeek(): Boolean = pendingPositionMs != null || pendingSeekJob?.isActive == true

        /**
         * Returns the last seek position that was requested, or 0 if none.
         */
        public fun getLastSeekPosition(): Long = pendingPositionMs ?: 0L

        /**
         * Releases resources. Call when service is destroyed.
         */
        public fun release() {
            cancel()
            LogUtils.d(TAG, "ThrottledSeekHandler released")
        }
    }
