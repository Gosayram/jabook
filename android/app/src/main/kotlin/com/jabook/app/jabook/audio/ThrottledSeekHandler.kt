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

import android.util.Log
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
 * Throttles seek operations to prevent excessive updates.
 *
 * Inspired by RetroMusicPlayer's ThrottledSeekHandler.
 *
 * When the user drags the seek bar rapidly, this handler aggregates
 * events and only executes the final action after a delay, preventing:
 * - UI jitter from too many position updates
 * - Excessive save operations
 * - MediaSession notification spam
 *
 * Usage:
 * ```
 * throttledSeekHandler.notifySeek(position) { pos ->
 *     player.seekTo(pos)
 *     savePosition(pos)
 * }
 * ```
 */
@Singleton
public class ThrottledSeekHandler
    @Inject
    constructor() {
        public companion object {
            private const val TAG = "ThrottledSeekHandler"

            /** Default throttle delay in milliseconds */
            public const val DEFAULT_THROTTLE_MS: Long = 0L
        }

        private val scope =
            CoroutineScope(
                SupervisorJob() + Dispatchers.Main + loggingCoroutineExceptionHandler("ThrottledSeekHandler"),
            )
        private var pendingSeekJob: Job? = null
        private var lastSeekPosition: Long = 0L

        /** Throttle delay in milliseconds. Can be configured. */
        public var throttleMs: Long = DEFAULT_THROTTLE_MS

        /**
         * Notifies a seek event. The action will be executed after [throttleMs]
         * if no new seek events arrive.
         *
         * @param positionMs The seek target position in milliseconds
         * @param onSeekComplete Callback executed when throttle delay expires with final position
         */
        public fun notifySeek(
            positionMs: Long,
            onSeekComplete: (Long) -> Unit,
        ) {
            lastSeekPosition = positionMs

            // Cancel previous pending seek
            pendingSeekJob?.cancel()

            pendingSeekJob =
                scope.launch {
                    Log.v(TAG, "Seek queued: ${positionMs}ms, waiting ${throttleMs}ms")
                    delay(throttleMs)

                    Log.d(TAG, "Seek executed: ${lastSeekPosition}ms")
                    onSeekComplete(lastSeekPosition)
                    pendingSeekJob = null
                }
        }

        /**
         * Immediately executes any pending seek without waiting.
         * Use when playback stops or state needs to be finalized.
         *
         * @param onSeekComplete Callback with the pending position, or current if none pending
         */
        public fun flush(onSeekComplete: ((Long) -> Unit)? = null) {
            pendingSeekJob?.cancel()
            pendingSeekJob = null

            if (lastSeekPosition > 0 && onSeekComplete != null) {
                Log.d(TAG, "Flush executed: ${lastSeekPosition}ms")
                onSeekComplete(lastSeekPosition)
            }
        }

        /**
         * Cancels any pending seek operation.
         */
        public fun cancel() {
            pendingSeekJob?.cancel()
            pendingSeekJob = null
            Log.v(TAG, "Pending seek cancelled")
        }

        /**
         * Checks if there's a pending seek operation.
         */
        public fun hasPendingSeek(): Boolean = pendingSeekJob?.isActive == true

        /**
         * Returns the last seek position that was requested.
         */
        public fun getLastSeekPosition(): Long = lastSeekPosition

        /**
         * Releases resources. Call when service is destroyed.
         */
        public fun release() {
            cancel()
            lastSeekPosition = 0L
            Log.d(TAG, "ThrottledSeekHandler released")
        }
    }
