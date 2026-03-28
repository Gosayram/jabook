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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerPersistenceTest {
    @Test
    fun `toPersistedState writes sentinel when paused remaining is null`() {
        val persisted =
            SleepTimerPersistence.toPersistedState(
                SleepTimerRuntimeState(
                    endTimeMillis = 1000L,
                    mode = SleepTimerMode.FIXED_DURATION,
                    fixedDurationPaused = false,
                    fixedDurationPausedRemainingMillis = null,
                ),
            )

        assertEquals(SleepTimerPersistence.NO_REMAINING_MILLIS, persisted.pausedRemainingMillis)
    }

    @Test
    fun `computeRestorePlan returns end of chapter when persisted end of chapter is true`() {
        val plan =
            SleepTimerPersistence.computeRestorePlan(
                persistedState =
                    SleepTimerPersistedState(
                        endTimeMillis = 0L,
                        endOfChapter = true,
                        mode = null,
                        paused = false,
                        pausedRemainingMillis = SleepTimerPersistence.NO_REMAINING_MILLIS,
                    ),
                nowMillis = 10_000L,
            )

        assertTrue(plan is SleepTimerRestorePlan.EndOfChapter)
    }

    @Test
    fun `computeRestorePlan prefers paused remaining over stale end time`() {
        val plan =
            SleepTimerPersistence.computeRestorePlan(
                persistedState =
                    SleepTimerPersistedState(
                        endTimeMillis = 5_000L,
                        endOfChapter = false,
                        mode = SleepTimerMode.FIXED_DURATION,
                        paused = true,
                        pausedRemainingMillis = 120_000L,
                    ),
                nowMillis = 20_000L,
            )

        assertTrue(plan is SleepTimerRestorePlan.FixedDuration)
        val fixed = plan as SleepTimerRestorePlan.FixedDuration
        assertEquals(120_000L, fixed.remainingMillis)
        assertTrue(fixed.paused)
    }

    @Test
    fun `computeRestorePlan uses end time for running timer`() {
        val plan =
            SleepTimerPersistence.computeRestorePlan(
                persistedState =
                    SleepTimerPersistedState(
                        endTimeMillis = 40_000L,
                        endOfChapter = false,
                        mode = SleepTimerMode.FIXED_DURATION,
                        paused = false,
                        pausedRemainingMillis = SleepTimerPersistence.NO_REMAINING_MILLIS,
                    ),
                nowMillis = 10_000L,
            )

        assertTrue(plan is SleepTimerRestorePlan.FixedDuration)
        val fixed = plan as SleepTimerRestorePlan.FixedDuration
        assertEquals(30_000L, fixed.remainingMillis)
        assertTrue(!fixed.paused)
    }

    @Test
    fun `computeRestorePlan returns none for expired fixed timer`() {
        val plan =
            SleepTimerPersistence.computeRestorePlan(
                persistedState =
                    SleepTimerPersistedState(
                        endTimeMillis = 5_000L,
                        endOfChapter = false,
                        mode = SleepTimerMode.FIXED_DURATION,
                        paused = false,
                        pausedRemainingMillis = SleepTimerPersistence.NO_REMAINING_MILLIS,
                    ),
                nowMillis = 10_000L,
            )

        assertTrue(plan is SleepTimerRestorePlan.None)
    }

    @Test
    fun `computeRestorePlan returns end of track when mode is track end`() {
        val plan =
            SleepTimerPersistence.computeRestorePlan(
                persistedState =
                    SleepTimerPersistedState(
                        endTimeMillis = 0L,
                        endOfChapter = false,
                        mode = SleepTimerMode.TRACK_END,
                        paused = false,
                        pausedRemainingMillis = SleepTimerPersistence.NO_REMAINING_MILLIS,
                    ),
                nowMillis = 10_000L,
            )

        assertTrue(plan is SleepTimerRestorePlan.EndOfTrack)
    }
}
