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
import android.content.Intent
import androidx.media3.common.Player
import com.jabook.app.jabook.util.LogUtils

/**
 * Single coordinator for book completion detection and handling.
 *
 * Consolidates all completion-flow paths that were previously scattered across
 * [PlayerListener] (STATE_ENDED, positionDiscontinuity, positionCheck loop)
 * into one idempotent entry point.
 *
 * ## Completion sources
 * - **STATE_ENDED**: ExoPlayer reports playback ended for last track.
 * - **DISCONTINUITY**: Position discontinuity from last track to invalid index.
 * - **POSITION_CHECK**: Periodic position monitor detects end-of-file (incorrect duration).
 * - **POSITION_STOPPED**: Position stopped advancing on last track.
 * - **SMART_COMPLETION**: Near-end detection within configurable threshold.
 *
 * All sources converge into [notifyCompletion], which deduplicates via
 * [getIsBookCompleted] flag and normalizes the track index via
 * [BookCompletionIndexPolicy].
 */
internal class BookCompletionCoordinator(
    private val context: Context,
    private val getIsBookCompleted: () -> Boolean,
    private val setIsBookCompleted: (Boolean) -> Unit,
    private val getActualPlaylistSize: () -> Int,
    private val getLastCompletedTrackIndex: () -> Int,
    private val setLastCompletedTrackIndex: (Int) -> Unit,
    private val getCurrentBookId: () -> String?,
    private val markBookCompleted: (String) -> Unit,
    private val saveCurrentPosition: () -> Unit,
) {
    /**
     * Origin of the completion event, used for logging and diagnostics.
     * Order does not imply priority — all sources are treated equally.
     */
    enum class Source(
        val label: String,
    ) {
        STATE_ENDED("STATE_ENDED"),
        DISCONTINUITY("DISCONTINUITY"),
        POSITION_CHECK("POSITION_CHECK"),
        POSITION_STOPPED("POSITION_STOPPED"),
        SMART_COMPLETION("SMART_COMPLETION"),
        PLAYBACK_STOPPED_NEAR_END("PLAYBACK_STOPPED_NEAR_END"),
    }

    /**
     * Attempts to mark the book as completed.
     *
     * This method is idempotent: once the book is marked completed, subsequent
     * calls are silently ignored (logged at VERBOSE level).
     *
     * @param player Active ExoPlayer instance (used for pause/seek operations).
     * @param currentIndex The track index reported by the completion source.
     * @param source Origin of the completion event for traceability.
     * @return `true` if the book was successfully marked as completed,
     *         `false` if it was already completed or not the last track.
     */
    fun notifyCompletion(
        player: Player,
        currentIndex: Int,
        source: Source,
    ): Boolean {
        if (getIsBookCompleted()) {
            LogUtils.v(
                TAG,
                "Skipping duplicate completion from ${source.label}: book already completed",
            )
            return false
        }

        val totalTracks = getActualPlaylistSize()
        if (totalTracks <= 0) {
            LogUtils.w(TAG, "Cannot complete: totalTracks=$totalTracks")
            return false
        }

        // Normalize unstable/out-of-bounds indexes
        val savedIndex = getLastCompletedTrackIndex()
        val actualIndex =
            BookCompletionIndexPolicy.resolveCompletionIndex(
                currentIndex = currentIndex,
                totalTracks = totalTracks,
                savedCompletedIndex = savedIndex,
                currentPositionMs = player.currentPosition,
                durationMs = player.duration,
            )
        if (actualIndex != currentIndex) {
            LogUtils.d(
                TAG,
                "${source.label}: normalized index $currentIndex -> $actualIndex " +
                    "(saved=$savedIndex, total=$totalTracks)",
            )
        }

        // Only the last track triggers book completion
        if (actualIndex < totalTracks - 1) {
            LogUtils.d(
                TAG,
                "${source.label}: track $actualIndex is not last (total=$totalTracks), skipping",
            )
            return false
        }

        executeCompletion(player, actualIndex, totalTracks, source)
        return true
    }

    /**
     * Performs the actual completion actions: pauses playback, persists state,
     * broadcasts the event.
     */
    private fun executeCompletion(
        player: Player,
        lastIndex: Int,
        totalTracks: Int,
        source: Source,
    ) {
        val lastPosition = player.currentPosition

        LogUtils.i(
            TAG,
            "Book completed from ${source.label}: last track $lastIndex of ${totalTracks - 1}, " +
                "position=${lastPosition}ms",
        )

        // 1. Set completion flag to prevent further auto-playback
        setIsBookCompleted(true)

        // 2. Mark book as completed for activity sorting
        getCurrentBookId()?.let { bookId ->
            markBookCompleted(bookId)
        }

        // 3. Save last track index so getState() returns correct index
        setLastCompletedTrackIndex(lastIndex)
        LogUtils.d(TAG, "${source.label}: saved last completed track index: $lastIndex")

        // 4. Pause playback without stopping the player (stop() resets index to 0)
        try {
            player.pause()
            player.playWhenReady = false

            // Seek to end of last track to preserve index in UI
            if (lastIndex >= 0 && lastIndex < totalTracks) {
                val seekPosition = if (lastPosition > 0) lastPosition else Long.MAX_VALUE
                player.seekTo(lastIndex, seekPosition)
                LogUtils.d(
                    TAG,
                    "${source.label}: seeked to track $lastIndex at $seekPosition to preserve index",
                )
            }
        } catch (e: Exception) {
            LogUtils.e(TAG, "Error during completion pause/seek", e)
            player.playWhenReady = false
        }

        // 5. Persist final position
        saveCurrentPosition()

        // 6. Broadcast completion to UI
        val intent =
            Intent(ACTION_BOOK_COMPLETED).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_LAST_TRACK_INDEX, lastIndex)
            }
        context.sendBroadcast(intent)

        LogUtils.i(TAG, "${source.label}: completion flow finished successfully")
    }

    companion object {
        private const val TAG = "BookCompletion"

        const val ACTION_BOOK_COMPLETED: String = "com.jabook.app.jabook.BOOK_COMPLETED"
        const val EXTRA_LAST_TRACK_INDEX: String = "last_track_index"
    }
}
