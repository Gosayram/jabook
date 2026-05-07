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

import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.crash.CrashDiagnostics
import com.jabook.app.jabook.util.LogUtils

/**
 * Manages playback context updates for crash diagnostics and book completion tracking.
 *
 * Extracted from AudioPlayerService as part of TASK-VERM-04 (service decomposition).
 * Consolidates crash context, actual track index updates, and book completion reset logic.
 *
 * @param getActivePlayer provider for active ExoPlayer instance
 * @param getCurrentMetadata provider for current track metadata map
 * @param getPlaylistManager provider for PlaylistManager (nullable)
 * @param isSleepTimerEndOfChapter check if sleep timer is set to end of chapter
 * @param isSleepTimerEndOfTrack check if sleep timer is set to end of track
 * @param isSleepTimerActive check if any sleep timer is active
 */
internal class PlaybackContextHelper(
    private val getActivePlayer: () -> ExoPlayer,
    private val getCurrentMetadata: () -> Map<String, String>?,
    private val getPlaylistManager: () -> PlaylistManager?,
    private val isSleepTimerEndOfChapter: () -> Boolean,
    private val isSleepTimerEndOfTrack: () -> Boolean,
    private val isSleepTimerActive: () -> Boolean,
) {
    /** Updates the actual track index from onMediaItemTransition events. */
    fun updateActualTrackIndex(index: Int) {
        getPlaylistManager()?.actualTrackIndex = index
        LogUtils.d(TAG, "Updated actualTrackIndex to $index")
        updateCrashPlaybackContext()
    }

    /** Updates crash diagnostics with current playback context. */
    fun updateCrashPlaybackContext() {
        val player = getActivePlayer()
        val metadata = getCurrentMetadata()
        val effectiveTitle =
            metadata?.get("title")
                ?: metadata?.get("bookTitle")
                ?: metadata?.get("album")
                ?: player.mediaMetadata.albumTitle?.toString()
                ?: player.mediaMetadata.title?.toString()
        val sleepMode =
            when {
                isSleepTimerEndOfChapter() -> "chapter_end"
                isSleepTimerEndOfTrack() -> "track_end"
                isSleepTimerActive() -> "fixed"
                else -> "none"
            }
        CrashDiagnostics.setPlaybackContext(
            bookTitle = effectiveTitle,
            playerState = player.playbackState.toString(),
            playbackSpeed = player.playbackParameters.speed,
            sleepMode = sleepMode,
        )
    }

    /** Resets book completion flag if book was completed. */
    fun resetBookCompletionIfNeeded(actionLabel: String) {
        if (getPlaylistManager()?.isBookCompleted != true) return
        LogUtils.i(TAG, "$actionLabel resetting completion flag")
        getPlaylistManager()?.isBookCompleted = false
        getPlaylistManager()?.lastCompletedTrackIndex = -1
    }

    private companion object {
        private const val TAG = "PlaybackContextHelper"
    }
}
