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
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CompletableDeferred

/**
 * Coordinates track transition events between PlaylistManager and PlayerListener.
 *
 * Extracted from PlayerListener as part of TASK-VERM-03 (PlayerListener decomposition).
 * Responsible for:
 * - Managing pending track switch deferreds for async playlist operations
 * - Deduplicating rapid track transition events
 * - Updating actual track index from transition events
 */
internal class TrackTransitionCoordinator(
    private val isPlaylistLoading: (() -> Boolean)? = null,
    private val updateActualTrackIndex: ((Int) -> Unit)? = null,
) {
    // Deduplication window for rapid transition events
    private val transitionDedupWindowMs = 300L

    @Volatile
    private var pendingTrackSwitchDeferred: CompletableDeferred<Int>? = null
    private var lastHandledTransitionIndex: Int = -1
    private var lastHandledTransitionAtElapsedMs: Long = 0L

    /**
     * Sets a deferred to be completed when track switch occurs.
     * Used by PlaylistManager to wait for onMediaItemTransition event.
     */
    fun setPendingTrackSwitchDeferred(deferred: CompletableDeferred<Int>) {
        this.pendingTrackSwitchDeferred = deferred
        LogUtils.d("AudioPlayerService", "Set pendingTrackSwitchDeferred: waiting for track switch")
    }

    /** Clears the pending deferred (cancels waiting for track switch). */
    fun clearPendingTrackSwitchDeferred() {
        pendingTrackSwitchDeferred?.cancel()
        pendingTrackSwitchDeferred = null
        LogUtils.d("AudioPlayerService", "Cleared pendingTrackSwitchDeferred")
    }

    /**
     * Handles a track transition event from any source (onEvents, onMediaItemTransition, etc.).
     * Deduplicates rapid events and completes pending deferreds.
     */
    fun handleTrackTransitionEvent(
        currentIndex: Int,
        source: String,
    ) {
        if (currentIndex < 0) return
        if (shouldSkipDuplicateTransition(currentIndex, source)) return
        updateTrackIndexFromTransition(currentIndex, source)
        completePendingTrackSwitchDeferred(currentIndex, source)
    }

    private fun shouldSkipDuplicateTransition(
        currentIndex: Int,
        source: String,
    ): Boolean {
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val isDuplicate =
            currentIndex == lastHandledTransitionIndex &&
                nowElapsedMs - lastHandledTransitionAtElapsedMs <= transitionDedupWindowMs
        if (isDuplicate) {
            LogUtils.v(
                "AudioPlayerService",
                "Skipping duplicate transition from $source for index $currentIndex " +
                    "(window=${transitionDedupWindowMs}ms)",
            )
            return true
        }
        lastHandledTransitionIndex = currentIndex
        lastHandledTransitionAtElapsedMs = nowElapsedMs
        return false
    }

    private fun updateTrackIndexFromTransition(
        currentIndex: Int,
        source: String,
    ) {
        val isLoading = isPlaylistLoading?.invoke() ?: false
        if (!isLoading || currentIndex != 0) {
            updateActualTrackIndex?.invoke(currentIndex)
            LogUtils.v(
                "AudioPlayerService",
                "Updated actualTrackIndex to $currentIndex from $source (isLoading=$isLoading)",
            )
        } else {
            LogUtils.v(
                "AudioPlayerService",
                "Skipped updating actualTrackIndex to 0 during loading (source=$source)",
            )
        }
    }

    private fun completePendingTrackSwitchDeferred(
        currentIndex: Int,
        source: String,
    ) {
        val deferred = pendingTrackSwitchDeferred ?: return
        try {
            if (deferred.isActive) {
                deferred.complete(currentIndex)
                LogUtils.d(
                    "AudioPlayerService",
                    "Completed pendingTrackSwitchDeferred with index $currentIndex from $source",
                )
            } else {
                LogUtils.v(
                    "AudioPlayerService",
                    "Pending deferred already completed/cancelled before $source (index=$currentIndex)",
                )
            }
        } catch (e: Exception) {
            LogUtils.w(
                "AudioPlayerService",
                "Failed to complete pendingTrackSwitchDeferred from $source: ${e.message}",
            )
        } finally {
            pendingTrackSwitchDeferred = null
        }
    }
}
