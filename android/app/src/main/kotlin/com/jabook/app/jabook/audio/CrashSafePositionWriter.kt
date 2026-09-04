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

import com.jabook.app.jabook.audio.core.result.Result
import com.jabook.app.jabook.audio.data.repository.PlaybackPositionRepository
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Synchronous position writer for crash-critical moments.
 *
 * P-66: When the app is being killed (swipe away, onTaskRemoved, OOM kill), async
 * coroutines may not complete. This writer blocks the calling thread to guarantee
 * the position is persisted to Room before the process dies.
 *
 * Use ONLY in lifecycle-critical paths:
 * - [AudioPlayerService.onTaskRemoved]
 * - Before process death signals
 *
 * For normal periodic saving, use [PeriodicPositionSaver] instead.
 *
 * @param positionRepository Room-backed repository for playback positions
 */
internal class CrashSafePositionWriter
    @Inject
    constructor(
        private val positionRepository: PlaybackPositionRepository,
    ) {
        /**
         * Writes position synchronously to Room, blocking the caller.
         *
         * This switches to [Dispatchers.IO] internally and waits for the database
         * write to complete. Safe to call from the main thread in onTaskRemoved
         * (system gives ~5 seconds before ANR).
         *
         * @param bookId The book identifier
         * @param trackIndex Current chapter/track index (0-based)
         * @param positionMs Current playback position in milliseconds
         * @return true if save succeeded, false otherwise
         */
        fun writePositionSync(
            bookId: String,
            trackIndex: Int,
            positionMs: Long,
        ): Boolean {
            if (bookId.isBlank()) {
                LogUtils.w(TAG, "writePositionSync: blank bookId, skipping")
                return false
            }
            if (trackIndex < 0) {
                LogUtils.w(TAG, "writePositionSync: negative trackIndex=$trackIndex, skipping")
                return false
            }
            if (positionMs < 0) {
                LogUtils.w(TAG, "writePositionSync: negative positionMs=$positionMs, skipping")
                return false
            }

            return try {
                // Bounded blocking write: crash-safety needs synchronous completion in
                // onDestroy/onTaskRemoved, but an unbounded main-thread block is an ANR
                // under DB contention. Timeout loses the position; ANR kills the process.
                // withTimeoutOrNull distinguishes timeout (null) from a real DB error.
                val result =
                    runBlocking(Dispatchers.IO) {
                        withTimeoutOrNull(2_000) {
                            positionRepository.savePosition(
                                bookId = bookId,
                                trackIndex = trackIndex,
                                position = positionMs,
                            )
                        }
                    }
                when {
                    result == null -> {
                        // Timeout — position is stale but process stays alive; no misleading ERROR log.
                        LogUtils.w(TAG, "Crash-safe position write TIMED OUT for book=$bookId")
                        false
                    }
                    result is Result.Success -> {
                        LogUtils.d(TAG, "Crash-safe position written: book=$bookId, track=$trackIndex, pos=${positionMs}ms")
                        true
                    }
                    result is Result.Error -> {
                        LogUtils.e(TAG, "Crash-safe position write FAILED for book=$bookId", result.exception)
                        false
                    }
                    else -> {
                        // Should not happen for a synchronous operation
                        LogUtils.w(TAG, "Crash-safe position write returned Loading state")
                        false
                    }
                }
            } catch (e: Exception) {
                LogUtils.e(TAG, "Crash-safe position write FAILED for book=$bookId", e)
                false
            }
        }

        companion object {
            private const val TAG = "CrashSafePosition"
        }
    }
