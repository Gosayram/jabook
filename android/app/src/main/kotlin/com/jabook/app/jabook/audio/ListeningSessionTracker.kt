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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    private var activeSessionId: String? = null
    private var activeBookId: String? = null
    private var isStartingSession: Boolean = false

    public fun onPlaybackStarted() {
        val bookId = getCurrentBookId()?.takeIf { it.isNotBlank() } ?: return
        if ((activeSessionId != null || isStartingSession) && activeBookId == bookId) {
            return
        }

        if (activeBookId != null && activeBookId != bookId) {
            finishActiveSession(reason = "book_switched")
        }

        isStartingSession = true
        activeBookId = bookId

        scope.launch(ioDispatcher) {
            runCatching {
                repository.startSession(
                    bookId = bookId,
                    positionStartMs = getCurrentPositionMs(),
                    speedFactor = getCurrentSpeed(),
                    chapterIndex = getCurrentChapterIndex(),
                )
            }.onSuccess { sessionId ->
                activeSessionId = sessionId
                activeBookId = bookId
                isStartingSession = false
            }.onFailure { error ->
                activeSessionId = null
                activeBookId = null
                isStartingSession = false
                LogUtils.e("ListeningSessionTracker", "Failed to start listening session for book=$bookId", error)
            }
        }
    }

    public fun onPlaybackStopped(reason: String) {
        finishActiveSession(reason)
    }

    public fun finishActiveSession(reason: String) {
        val sessionId = activeSessionId ?: return
        activeSessionId = null
        activeBookId = null
        isStartingSession = false

        scope.launch(ioDispatcher) {
            runCatching {
                repository.finishSession(
                    sessionId = sessionId,
                    positionEndMs = getCurrentPositionMs(),
                    speedFactor = getCurrentSpeed(),
                    chapterIndex = getCurrentChapterIndex(),
                )
            }.onFailure { error ->
                LogUtils.e("ListeningSessionTracker", "Failed to finish listening session reason=$reason", error)
            }
        }
    }
}
