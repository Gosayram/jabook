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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DailyGoalTrackerTest {
    private lateinit var tracker: DailyGoalTracker

    @Before
    fun setUp() {
        tracker = DailyGoalTracker()
    }

    // --- Default goal ---

    @Test
    fun `default goal is 30 minutes`() {
        assertEquals(30, tracker.getGoalMinutes())
    }

    // --- Set goal ---

    @Test
    fun `set goal updates goal`() {
        tracker.setGoal(60)
        assertEquals(60, tracker.getGoalMinutes())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `set goal with zero throws`() {
        tracker.setGoal(0)
    }

    // --- Progress not met ---

    @Test
    fun `progress below goal is not met`() {
        val data = tracker.reportProgress(15)
        assertFalse(data.isGoalMet)
        assertEquals(15, data.todayMinutes)
        assertEquals(30, data.goalMinutes)
    }

    // --- Progress met ---

    @Test
    fun `progress at or above goal is met`() {
        val data = tracker.reportProgress(30)
        assertTrue(data.isGoalMet)
        assertEquals(1.0f, data.progress, 0.01f)
    }

    // --- Progress fraction ---

    @Test
    fun `progress fraction is correct`() {
        val data = tracker.reportProgress(15)
        assertEquals(0.5f, data.progress, 0.01f)
    }

    // --- Streak increments when goal met ---

    @Test
    fun `streak increments when goal met`() {
        tracker.reportProgress(30)
        val data = tracker.reportProgress(35)
        assertEquals(1, data.consecutiveDays)
    }

    // --- Streak resets ---

    @Test
    fun `reset streak clears counter`() {
        tracker.reportProgress(30)
        tracker.resetStreak()
        val data = tracker.reportProgress(0)
        assertEquals(0, data.consecutiveDays)
    }

    // --- StreakData with zero goal ---

    @Test
    fun `progress with zero goal returns zero`() {
        tracker.setGoal(1)
        val data = tracker.reportProgress(0)
        assertEquals(0.0f, data.progress, 0.01f)
    }
}
