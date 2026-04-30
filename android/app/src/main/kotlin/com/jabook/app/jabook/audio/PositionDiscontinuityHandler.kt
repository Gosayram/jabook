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

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.util.LogUtils
import java.io.File

/**
 * Handles position discontinuity events from ExoPlayer.
 *
 * Extracted from PlayerListener as part of TASK-VERM-03 (PlayerListener decomposition).
 * Responsible for:
 * - Track availability checking during auto-transitions
 * - Book completion detection from position discontinuities
 * - Manual track switch detection after book completion
 * - Invalid transition prevention (last track wraparound)
 */
internal class PositionDiscontinuityHandler(
    private val getActivePlayer: () -> ExoPlayer,
    private val getIsBookCompleted: () -> Boolean,
    private val setIsBookCompleted: (Boolean) -> Unit,
    private val setLastCompletedTrackIndex: ((Int) -> Unit)?,
    private val getActualPlaylistSize: (() -> Int)?,
    private val saveCurrentPosition: () -> Unit,
    private val bookCompletionTracker: BookCompletionTracker,
    private val playerErrorHandler: PlayerErrorHandler,
) {
    /**
     * Handles position discontinuity events (track changes, seeks, auto-transitions).
     *
     * @return `true` if the discontinuity was fully handled and further processing should be skipped.
     */
    fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ): Boolean {
        val player = getActivePlayer()
        val previousIndex = oldPosition.mediaItemIndex
        val currentIndex = newPosition.mediaItemIndex
        val totalTracks = getActualPlaylistSize?.invoke() ?: player.mediaItemCount

        // Check track availability for automatic transitions
        if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
            if (!TrackAvailabilityChecker.isTrackAvailable(player, currentIndex)) {
                handleUnavailableTrack(player, currentIndex, previousIndex)
                return true
            }
        }

        val isManualSeek =
            reason == Player.DISCONTINUITY_REASON_SEEK ||
                reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT
        val isAutoTransition =
            reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION || reason == 4

        // Handle book completion state during discontinuities
        if (getIsBookCompleted()) {
            if (isManualSeek && currentIndex != previousIndex) {
                LogUtils.i(
                    "AudioPlayerService",
                    "Manual track switch after completion: $previousIndex -> $currentIndex, resetting flag",
                )
                setIsBookCompleted(false)
                setLastCompletedTrackIndex?.invoke(-1)
            } else if (isAutoTransition) {
                LogUtils.w("AudioPlayerService", "Auto transition after completion, ignoring")
                return true
            }
        }

        if (currentIndex == previousIndex) return false

        LogUtils.d(
            "AudioPlayerService",
            "Position discontinuity: $previousIndex -> $currentIndex, reason=$reason, total=$totalTracks",
        )

        // Detect end-of-book: last track wraps to index 0 via auto-transition
        if (isEndOfBookWraparound(previousIndex, currentIndex, totalTracks, reason)) {
            handleEndOfBookWraparound(player, previousIndex, oldPosition.positionMs, totalTracks)
            return true
        }

        // Stop position check on track change
        bookCompletionTracker.stopPositionCheck()
        playerErrorHandler.resetCounts()

        // Check if current track file is accessible
        checkCurrentTrackAccessibility(player, currentIndex)
        return false
    }

    private fun handleUnavailableTrack(
        player: ExoPlayer,
        currentIndex: Int,
        previousIndex: Int,
    ) {
        LogUtils.w(
            "AudioPlayerService",
            "Track $currentIndex unavailable, searching for next available",
        )

        val direction =
            when {
                currentIndex > previousIndex ||
                    (currentIndex == 0 && previousIndex == player.mediaItemCount - 1)
                -> TrackAvailabilityChecker.Direction.FORWARD
                else -> TrackAvailabilityChecker.Direction.BACKWARD
            }

        val nextAvailableIndex =
            TrackAvailabilityChecker.findAvailableTrackIndex(
                player = player,
                currentIndex = currentIndex,
                direction = direction,
            )

        if (nextAvailableIndex != null && nextAvailableIndex != currentIndex) {
            player.seekTo(nextAvailableIndex, 0L)
            LogUtils.d("AudioPlayerService", "Switched to available track: $nextAvailableIndex")
        } else {
            LogUtils.w("AudioPlayerService", "No available tracks found, pausing playback")
            player.playWhenReady = false
        }
    }

    private fun isEndOfBookWraparound(
        previousIndex: Int,
        currentIndex: Int,
        totalTracks: Int,
        reason: Int,
    ): Boolean =
        previousIndex >= 0 &&
            previousIndex >= totalTracks - 1 &&
            (currentIndex == 0 || currentIndex < 0 || currentIndex >= totalTracks) &&
            (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION || reason == 4)

    private fun handleEndOfBookWraparound(
        player: ExoPlayer,
        previousIndex: Int,
        lastPositionMs: Long,
        totalTracks: Int,
    ) {
        LogUtils.i(
            "AudioPlayerService",
            "Detected end of book: transition from last track $previousIndex to invalid index (${player.currentMediaItemIndex}, total=$totalTracks)",
        )
        bookCompletionTracker.handleBookCompletion(player, previousIndex, "discontinuity_wraparound")

        if (previousIndex >= 0 && previousIndex < totalTracks) {
            try {
                player.seekTo(previousIndex, lastPositionMs.coerceAtLeast(0L))
                player.pause()
                player.playWhenReady = false
                LogUtils.d(
                    "AudioPlayerService",
                    "Prevented invalid transition, seeked back to track $previousIndex",
                )
            } catch (e: Exception) {
                LogUtils.e("AudioPlayerService", "Error preventing invalid transition", e)
            }
        }
    }

    private fun checkCurrentTrackAccessibility(
        player: ExoPlayer,
        currentIndex: Int,
    ) {
        val currentItem = player.currentMediaItem ?: return
        val uri = currentItem.localConfiguration?.uri ?: return

        if (uri.scheme == "file") {
            val file = File(uri.path ?: "")
            if (!file.exists() || !file.canRead()) {
                LogUtils.w(
                    "AudioPlayerService",
                    "Current track file not accessible: ${uri.path}, trying to skip",
                )
                val previousIndex = if (currentIndex > 0) currentIndex - 1 else 0
                playerErrorHandler.skipToNextAvailableTrack(currentIndex, previousIndex)
            }
        }
    }
}
