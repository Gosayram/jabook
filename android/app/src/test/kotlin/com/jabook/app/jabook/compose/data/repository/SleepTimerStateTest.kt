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

package com.jabook.app.jabook.compose.data.repository

import com.jabook.app.jabook.compose.domain.model.SleepTimerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for SleepTimerState.
 *
 * Tests the sealed interface hierarchy and formatting functionality.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerStateTest {
    @Test
    fun `Idle state should be singleton`() {
        val idle1 = SleepTimerState.Idle
        val idle2 = SleepTimerState.Idle
        assertEquals(idle1, idle2, "Idle should be singleton")
    }

    @Test
    fun `EndOfChapter state should be singleton`() {
        val eoc1 = SleepTimerState.EndOfChapter
        val eoc2 = SleepTimerState.EndOfChapter
        assertEquals(eoc1, eoc2, "EndOfChapter should be singleton")
    }

    @Test
    fun `EndOfTrack state should be singleton`() {
        val eot1 = SleepTimerState.EndOfTrack
        val eot2 = SleepTimerState.EndOfTrack
        assertEquals(eot1, eot2, "EndOfTrack should be singleton")
    }

    @Test
    fun `Active state should format time correctly for minutes and seconds`() {
        val state = SleepTimerState.Active(remainingSeconds = 125) // 2:05
        assertEquals("02:05", state.formattedTime)
    }

    @Test
    fun `Active state should format time correctly for exact minutes`() {
        val state = SleepTimerState.Active(remainingSeconds = 300) // 5:00
        assertEquals("05:00", state.formattedTime)
    }

    @Test
    fun `Active state should format time correctly for less than a minute`() {
        val state = SleepTimerState.Active(remainingSeconds = 45) // 0:45
        assertEquals("00:45", state.formattedTime)
    }

    @Test
    fun `Active state should format time correctly for zero`() {
        val state = SleepTimerState.Active(remainingSeconds = 0)
        assertEquals("00:00", state.formattedTime)
    }

    @Test
    fun `Active state should format time correctly for over an hour`() {
        val state = SleepTimerState.Active(remainingSeconds = 3665) // 61:05
        assertEquals("61:05", state.formattedTime)
    }

    @Test
    fun `States should be distinguishable via when expression`() =
        runTest {
            val states =
                listOf(
                    SleepTimerState.Idle,
                    SleepTimerState.Active(60),
                    SleepTimerState.EndOfChapter,
                    SleepTimerState.EndOfTrack,
                )

            var idleCount = 0
            var activeCount = 0
            var eocCount = 0
            var eotCount = 0

            states.forEach { state ->
                when (state) {
                    is SleepTimerState.Idle -> idleCount++
                    is SleepTimerState.Active -> activeCount++
                    is SleepTimerState.EndOfChapter -> eocCount++
                    is SleepTimerState.EndOfTrack -> eotCount++
                }
            }

            assertEquals(1, idleCount)
            assertEquals(1, activeCount)
            assertEquals(1, eocCount)
            assertEquals(1, eotCount)
        }

    @Test
    fun `Active state equality should be based on remaining seconds`() {
        val state1 = SleepTimerState.Active(100)
        val state2 = SleepTimerState.Active(100)
        val state3 = SleepTimerState.Active(200)

        assertEquals(state1, state2)
        assertTrue(state1 != state3)
    }
}
