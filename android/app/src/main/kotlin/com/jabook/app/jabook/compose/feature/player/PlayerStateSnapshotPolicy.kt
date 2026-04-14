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

package com.jabook.app.jabook.compose.feature.player

import com.jabook.app.jabook.compose.domain.model.SleepTimerState

/**
 * Serializable snapshot of player screen state for process-death restore.
 */
public data class PlayerStateSnapshot(
    val bookId: String,
    val positionMs: Long,
    val chapterIndex: Int,
    val playbackSpeed: Float,
    val sleepTimerMode: String,
)

public object PlayerStateSnapshotPolicy {
    public fun capture(
        bookId: String,
        state: PlayerState.Active,
        sleepTimerState: SleepTimerState,
    ): PlayerStateSnapshot =
        PlayerStateSnapshot(
            bookId = bookId,
            positionMs = state.currentPosition.coerceAtLeast(0L),
            chapterIndex = state.currentChapterIndex.coerceAtLeast(0),
            playbackSpeed = state.playbackSpeed.coerceAtLeast(0f),
            sleepTimerMode = sleepTimerModeOf(sleepTimerState),
        )

    public fun sleepTimerModeOf(state: SleepTimerState): String =
        when (state) {
            SleepTimerState.Idle -> MODE_IDLE
            is SleepTimerState.Active -> MODE_ACTIVE
            SleepTimerState.EndOfChapter -> MODE_END_OF_CHAPTER
            is SleepTimerState.EndOfTrack -> MODE_END_OF_TRACK
        }

    public fun normalizeForPersistence(snapshot: PlayerStateSnapshot): PlayerStateSnapshot =
        snapshot.copy(
            // Persist coarse-grained position to reduce DataStore write frequency.
            positionMs = (snapshot.positionMs / POSITION_PERSISTENCE_STEP_MS) * POSITION_PERSISTENCE_STEP_MS,
        )

    public fun shouldPersistSnapshot(
        previous: PlayerStateSnapshot?,
        current: PlayerStateSnapshot,
    ): Boolean {
        if (previous == null) return true
        if (previous.bookId != current.bookId) return true
        if (previous.chapterIndex != current.chapterIndex) return true
        if (previous.sleepTimerMode != current.sleepTimerMode) return true
        if (previous.playbackSpeed != current.playbackSpeed) return true
        return kotlin.math.abs(previous.positionMs - current.positionMs) >= POSITION_PERSISTENCE_STEP_MS
    }

    public const val MODE_IDLE: String = "idle"
    public const val MODE_ACTIVE: String = "active"
    public const val MODE_END_OF_CHAPTER: String = "end_of_chapter"
    public const val MODE_END_OF_TRACK: String = "end_of_track"
    public const val POSITION_PERSISTENCE_STEP_MS: Long = 5_000L
}
