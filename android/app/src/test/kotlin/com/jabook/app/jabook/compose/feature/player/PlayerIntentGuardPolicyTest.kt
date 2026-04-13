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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerIntentGuardPolicyTest {
    @Test
    fun `clampSeekPosition clamps below zero and above chapter duration`() {
        assertEquals(0L, PlayerIntentGuardPolicy.clampSeekPosition(-10L, 1000L))
        assertEquals(1000L, PlayerIntentGuardPolicy.clampSeekPosition(5000L, 1000L))
        assertEquals(700L, PlayerIntentGuardPolicy.clampSeekPosition(700L, 1000L))
    }

    @Test
    fun `clampSeekPosition keeps non-negative value when duration is unknown`() {
        assertEquals(500L, PlayerIntentGuardPolicy.clampSeekPosition(500L, null))
    }

    @Test
    fun `fixed sleep timer start is idempotent for same target`() {
        assertFalse(
            PlayerIntentGuardPolicy.shouldStartFixedSleepTimer(
                currentState = SleepTimerState.Active(remainingSeconds = 180),
                requestedMinutes = 3,
            ),
        )
        assertTrue(
            PlayerIntentGuardPolicy.shouldStartFixedSleepTimer(
                currentState = SleepTimerState.Active(remainingSeconds = 60),
                requestedMinutes = 3,
            ),
        )
    }

    @Test
    fun `end-of modes are idempotent only for same mode`() {
        assertFalse(PlayerIntentGuardPolicy.shouldStartEndOfChapter(SleepTimerState.EndOfChapter))
        assertFalse(PlayerIntentGuardPolicy.shouldStartEndOfTrack(SleepTimerState.EndOfTrack()))

        assertTrue(PlayerIntentGuardPolicy.shouldStartEndOfChapter(SleepTimerState.Idle))
        assertTrue(PlayerIntentGuardPolicy.shouldStartEndOfTrack(SleepTimerState.Idle))
    }
}
