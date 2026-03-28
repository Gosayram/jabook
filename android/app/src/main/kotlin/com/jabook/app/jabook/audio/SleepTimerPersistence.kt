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

internal data class SleepTimerPersistedState(
    val endTimeMillis: Long,
    val endOfChapter: Boolean,
    val mode: SleepTimerMode?,
    val paused: Boolean,
    val pausedRemainingMillis: Long,
)

internal data class SleepTimerRuntimeState(
    val endTimeMillis: Long,
    val mode: SleepTimerMode,
    val fixedDurationPaused: Boolean,
    val fixedDurationPausedRemainingMillis: Long?,
)

internal sealed class SleepTimerRestorePlan {
    internal data object None : SleepTimerRestorePlan()

    internal data object EndOfChapter : SleepTimerRestorePlan()

    internal data object EndOfTrack : SleepTimerRestorePlan()

    internal data class FixedDuration(
        val remainingMillis: Long,
        val paused: Boolean,
    ) : SleepTimerRestorePlan()
}

internal object SleepTimerPersistence {
    internal const val PREFS_NAME: String = "jabook_timer_prefs"
    internal const val KEY_END_TIME: String = "sleepTimerEndTime"
    internal const val KEY_END_OF_CHAPTER: String = "sleepTimerEndOfChapter"
    internal const val KEY_MODE: String = "sleepTimerMode"
    internal const val KEY_PAUSED: String = "sleepTimerPaused"
    internal const val KEY_PAUSED_REMAINING_MILLIS: String = "sleepTimerPausedRemainingMillis"
    internal const val NO_REMAINING_MILLIS: Long = -1L

    internal fun toPersistedState(runtimeState: SleepTimerRuntimeState): SleepTimerPersistedState =
        SleepTimerPersistedState(
            endTimeMillis = runtimeState.endTimeMillis,
            endOfChapter = runtimeState.mode == SleepTimerMode.CHAPTER_END,
            mode = runtimeState.mode,
            paused = runtimeState.fixedDurationPaused,
            pausedRemainingMillis = runtimeState.fixedDurationPausedRemainingMillis ?: NO_REMAINING_MILLIS,
        )

    internal fun computeRestorePlan(
        persistedState: SleepTimerPersistedState,
        nowMillis: Long,
    ): SleepTimerRestorePlan {
        val persistedMode =
            persistedState.mode ?: if (persistedState.endOfChapter) SleepTimerMode.CHAPTER_END else SleepTimerMode.NONE
        when (persistedMode) {
            SleepTimerMode.CHAPTER_END -> return SleepTimerRestorePlan.EndOfChapter
            SleepTimerMode.TRACK_END -> return SleepTimerRestorePlan.EndOfTrack
            SleepTimerMode.NONE,
            SleepTimerMode.FIXED_DURATION,
            -> {
                // Continue with fixed/none path below.
            }
        }

        if (persistedState.endTimeMillis <= 0L) {
            return SleepTimerRestorePlan.None
        }

        val remainingMillis =
            when {
                persistedState.paused && persistedState.pausedRemainingMillis > 0L -> persistedState.pausedRemainingMillis
                else -> persistedState.endTimeMillis - nowMillis
            }

        return if (remainingMillis > 0L) {
            SleepTimerRestorePlan.FixedDuration(
                remainingMillis = remainingMillis,
                paused = persistedState.paused,
            )
        } else {
            SleepTimerRestorePlan.None
        }
    }
}
