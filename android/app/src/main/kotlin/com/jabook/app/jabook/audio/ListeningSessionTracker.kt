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

import com.jabook.app.jabook.audio.data.repository.ListeningSessionRepository
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks active listening session boundaries and persists them to local DB.
 */
internal class ListeningSessionTracker(
    private val repository: ListeningSessionRepository,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val getCurrentBookId: () -> String?,
    private val getCurrentPositionMs: () -> Long,
    private val getCurrentSpeed: () -> Float,
    private val getCurrentChapterIndex: () -> Int,
) {
    @Volatile
    private var activeSessionId: String? = null

    @Volatile
    private var activeBookId: String? = null

    @Volatile
    private var isStartingSession: Boolean = false

    @Volatile
    private var pendingStopReason: String? = null
    private val sessionGeneration: AtomicLong = AtomicLong(0L)

    public fun onPlaybackStarted() {
        val bookId = getCurrentBookId()?.takeIf { it.isNotBlank() } ?: return
        pendingStopReason = null
        if ((activeSessionId != null || isStartingSession) && activeBookId == bookId) {
            return
        }

        if (activeBookId != null && activeBookId != bookId) {
            finishActiveSession(reason = "book_switched")
        }

        val generation = sessionGeneration.incrementAndGet()
        val positionStartMs = getCurrentPositionMs()
        val speedFactor = getCurrentSpeed()
        val chapterIndex = getCurrentChapterIndex()
        isStartingSession = true
        activeBookId = bookId

        scope.launch(ioDispatcher) {
            try {
                val sessionId =
                    repository.startSession(
                        bookId = bookId,
                        positionStartMs = positionStartMs,
                        speedFactor = speedFactor,
                        chapterIndex = chapterIndex,
                    )
                if (generation != sessionGeneration.get() || activeBookId != bookId) {
                    try {
                        repository.finishSession(
                            sessionId = sessionId,
                            positionEndMs = positionStartMs,
                            speedFactor = speedFactor,
                            chapterIndex = chapterIndex,
                        )
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        LogUtils.e("ListeningSessionTracker", "Failed to discard stale session for book=$bookId", error)
                    }
                } else {
                    activeSessionId = sessionId
                    activeBookId = bookId
                    isStartingSession = false
                    pendingStopReason?.let(::finishActiveSession)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation == sessionGeneration.get() && activeBookId == bookId) {
                    activeSessionId = null
                    activeBookId = null
                    isStartingSession = false
                    pendingStopReason = null
                }
                LogUtils.e("ListeningSessionTracker", "Failed to start listening session for book=$bookId", error)
            }
        }
    }

    public fun onPlaybackStopped(reason: String) {
        if (isStartingSession) {
            pendingStopReason = reason
            return
        }
        finishActiveSession(reason)
    }

    public fun finishActiveSession(reason: String) {
        val sessionId = activeSessionId ?: return
        activeSessionId = null
        activeBookId = null
        isStartingSession = false
        pendingStopReason = null

        // Service teardown cancels its scope immediately after requesting the final
        // session update, so the close must outlive that cancellation.
        scope.launch(ioDispatcher + kotlinx.coroutines.NonCancellable) {
            try {
                repository.finishSession(
                    sessionId = sessionId,
                    positionEndMs = getCurrentPositionMs(),
                    speedFactor = getCurrentSpeed(),
                    chapterIndex = getCurrentChapterIndex(),
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                LogUtils.e("ListeningSessionTracker", "Failed to finish listening session reason=$reason", error)
            }
        }
    }
}
