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
import kotlin.math.abs

/**
 * Guard policy for Player intents to keep behavior deterministic and idempotent.
 */
public object PlayerIntentGuardPolicy {
    public fun clampPlaybackSpeed(requestedSpeed: Float): Float = requestedSpeed.coerceIn(MIN_PLAYBACK_SPEED, MAX_PLAYBACK_SPEED)

    public fun clampSeekPosition(
        requestedPositionMs: Long,
        chapterDurationMs: Long?,
    ): Long {
        val lower = requestedPositionMs.coerceAtLeast(0L)
        val upper = chapterDurationMs?.coerceAtLeast(0L)
        return if (upper != null) lower.coerceAtMost(upper) else lower
    }

    public fun shouldStartFixedSleepTimer(
        currentState: SleepTimerState,
        requestedMinutes: Int,
    ): Boolean {
        val requestedSeconds = requestedMinutes.coerceAtLeast(1) * 60
        return when (currentState) {
            is SleepTimerState.Active -> abs(currentState.remainingSeconds - requestedSeconds) > SAME_TIMER_EPSILON_SECONDS
            else -> true
        }
    }

    public fun shouldStartEndOfChapter(currentState: SleepTimerState): Boolean = currentState !is SleepTimerState.EndOfChapter

    public fun shouldStartEndOfTrack(currentState: SleepTimerState): Boolean = currentState !is SleepTimerState.EndOfTrack

    private const val SAME_TIMER_EPSILON_SECONDS: Int = 2
    private const val MIN_PLAYBACK_SPEED: Float = 0.5f
    private const val MAX_PLAYBACK_SPEED: Float = 2.0f
}
