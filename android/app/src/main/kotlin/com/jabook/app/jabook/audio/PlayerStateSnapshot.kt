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

/**
 * P-26: Typed snapshot of player state, replacing `Map<String, Any?>`.
 *
 * Provides compile-time safety for player state access.
 * Used by MediaSession callbacks and UI state updates.
 *
 * @property isPlaying Whether playback is active
 * @property currentPositionMs Current playback position in milliseconds
 * @property durationMs Total duration of current track in milliseconds
 * @property currentTrackIndex Index of the current track in the playlist
 * @property playbackSpeed Current playback speed multiplier
 * @property bufferedPositionMs Buffered position in milliseconds
 * @property sleepTimerRemainingSeconds Remaining sleep timer seconds, or null if inactive
 * @property bookId Current book identifier
 * @property chapterIndex Current chapter index
 */
public data class PlayerStateSnapshot(
    val isPlaying: Boolean,
    val currentPositionMs: Long,
    val durationMs: Long,
    val currentTrackIndex: Int,
    val playbackSpeed: Float,
    val bufferedPositionMs: Long,
    val sleepTimerRemainingSeconds: Int? = null,
    val bookId: String? = null,
    val chapterIndex: Int = 0,
) {
    /** Whether playback has finished (position at end). */
    val isAtEnd: Boolean
        get() = durationMs > 0 && currentPositionMs >= durationMs

    /** Progress fraction 0.0–1.0. */
    val progress: Float
        get() = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /** Whether the sleep timer is active. */
    val isSleepTimerActive: Boolean
        get() = sleepTimerRemainingSeconds != null && sleepTimerRemainingSeconds > 0

    public companion object {
        /** Default/empty snapshot for initialization. */
        public val EMPTY: PlayerStateSnapshot =
            PlayerStateSnapshot(
                isPlaying = false,
                currentPositionMs = 0L,
                durationMs = 0L,
                currentTrackIndex = 0,
                playbackSpeed = 1.0f,
                bufferedPositionMs = 0L,
            )
    }
}
