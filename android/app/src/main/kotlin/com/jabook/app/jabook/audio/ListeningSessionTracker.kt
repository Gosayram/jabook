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
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks active listening session boundaries and persists them to local DB.
 *
 * Stats credit gate: a finished session only counts (is persisted as finished)
 * once it passes [MinListenCreditPolicy]; sessions below the floor are discarded
 * so repeated short plays don't double-count. One credit per (book, chapter) item.
 */
internal class ListeningSessionTracker(
    private val repository: ListeningSessionRepository,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val getCurrentBookId: () -> String?,
    private val getCurrentPositionMs: () -> Long,
    private val getCurrentSpeed: () -> Float,
    private val getCurrentChapterIndex: () -> Int,
    private val getCurrentDurationMs: () -> Long = { 0L },
) {
    @Volatile
    private var activeSessionId: String? = null

    @Volatile
    private var activeBookId: String? = null

    @Volatile
    private var isStartingSession: Boolean = false

    @Volatile
    private var pendingStopReason: String? = null

    @Volatile
    private var activeSessionStartPositionMs: Long = 0L

    @Volatile
    private var activeSessionChapterIndex: Int = -1
    private val sessionGeneration: AtomicLong = AtomicLong(0L)

    // ponytail: in-memory one-credit-per-chapter set, scoped to service lifetime
    private val creditedChapterKeys: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

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
                        // Stale session never became active: zero listened time, never credit.
                        repository.discardSession(sessionId = sessionId)
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        LogUtils.e("ListeningSessionTracker", "Failed to discard stale session for book=$bookId", error)
                    }
                } else {
                    activeSessionId = sessionId
                    activeBookId = bookId
                    activeSessionStartPositionMs = positionStartMs
                    activeSessionChapterIndex = chapterIndex
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
        val bookId = activeBookId
        val startPositionMs = activeSessionStartPositionMs
        val chapterIndex = activeSessionChapterIndex
        // Capture player getters on the caller thread BEFORE launching: the save below
        // runs on IO after possible teardown, and Media3 getters on the wrong thread
        // are racy (IllegalStateException) — a throw there would leak the session row.
        val positionEndMs = getCurrentPositionMs()
        val speedFactor = getCurrentSpeed()
        val durationMs = getCurrentDurationMs()
        activeSessionId = null
        activeBookId = null
        isStartingSession = false
        activeSessionStartPositionMs = 0L
        activeSessionChapterIndex = -1
        pendingStopReason = null

        // Service teardown cancels its scope immediately after requesting the final
        // session update, so the close must outlive that cancellation.
        scope.launch(ioDispatcher + kotlinx.coroutines.NonCancellable) {
            try {
                if (shouldCreditSession(
                        bookId = bookId,
                        chapterIndex = chapterIndex,
                        startPositionMs = startPositionMs,
                        positionEndMs = positionEndMs,
                        durationMs = durationMs,
                    )
                ) {
                    repository.finishSession(
                        sessionId = sessionId,
                        positionEndMs = positionEndMs,
                        speedFactor = speedFactor,
                        chapterIndex = chapterIndex,
                    )
                } else {
                    // Below the min-listen floor (or already credited): drop the row
                    // so it doesn't inflate session counts or play time.
                    repository.discardSession(sessionId = sessionId)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                LogUtils.e("ListeningSessionTracker", "Failed to finish listening session reason=$reason", error)
            }
        }
    }

    /** Applies [MinListenCreditPolicy] with one-time-per-(book, chapter) dedupe. */
    private fun shouldCreditSession(
        bookId: String?,
        chapterIndex: Int,
        startPositionMs: Long,
        positionEndMs: Long,
        durationMs: Long,
    ): Boolean {
        val creditKey = "${bookId ?: "unknown"}#$chapterIndex"
        val credited =
            MinListenCreditPolicy.shouldCredit(
                listenedMs = (positionEndMs - startPositionMs).coerceAtLeast(0L),
                durationMs = durationMs,
                alreadyCredited = creditKey in creditedChapterKeys,
            )
        if (credited) creditedChapterKeys.add(creditKey)
        return credited
    }
}
