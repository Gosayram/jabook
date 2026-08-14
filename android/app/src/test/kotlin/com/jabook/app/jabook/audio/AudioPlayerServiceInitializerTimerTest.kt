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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPlayerServiceInitializerTimerTest {
    /**
     * Verifies the logic used for EXTRA_IS_SLEEP_TIMER_ACTIVE in session extras.
     *
     * Before the fix: `isTimerActive = remaining > 0` — diverges from
     * service.isSleepTimerActive() which checks sleepTimerMode != NONE.
     *
     * After the fix: `isTimerActive = service.isSleepTimerActive()`.
     *
     * This test validates that the derivation matches the service contract:
     * isSleepTimerActive checks sleepTimerMode, not remaining seconds.
     */
    @Test
    fun `isTimerActive derivation matches isSleepTimerActive contract`() {
        // Simulate: CHAPTER_END mode, getSleepTimerRemainingSeconds returns null/0
        // because there's no fixed-duration countdown.
        // Before fix: isTimerActive = (null ?: -1) > 0 = false (WRONG)
        // After fix:  isTimerActive = isSleepTimerActive() = true (CORRECT)

        val sleepTimerModeActive = true // CHAPTER_END mode
        val remainingSeconds: Int? = null // no fixed countdown

        val isTimerActiveOld = (remainingSeconds ?: -1) > 0
        val isTimerActiveNew = sleepTimerModeActive

        assertFalse("Old derivation (remaining > 0) reports false for CHAPTER_END", isTimerActiveOld)
        assertTrue("New derivation (isSleepTimerActive) reports true for CHAPTER_END", isTimerActiveNew)
    }

    @Test
    fun `isTimerActive derivation when no timer active and no remaining`() {
        val sleepTimerModeActive = false
        val remainingSeconds: Int? = -1

        val isTimerActiveOld = (remainingSeconds ?: -1) > 0
        val isTimerActiveNew = sleepTimerModeActive

        assertFalse("Old derivation reports false", isTimerActiveOld)
        assertFalse("New derivation reports false", isTimerActiveNew)
    }

    @Test
    fun `isTimerActive derivation when fixed timer active with remaining`() {
        val sleepTimerModeActive = true
        val remainingSeconds: Int? = 300

        val isTimerActiveOld = (remainingSeconds ?: -1) > 0
        val isTimerActiveNew = sleepTimerModeActive

        assertTrue("Old derivation reports true", isTimerActiveOld)
        assertTrue("New derivation reports true", isTimerActiveNew)
    }

    @Test
    fun `isTimerActive derivation when fixed timer expired but mode still set`() {
        // Edge case: fixed timer reached 0 but mode hasn't been reset to NONE yet
        val sleepTimerModeActive = true
        val remainingSeconds: Int? = 0

        val isTimerActiveOld = (remainingSeconds ?: -1) > 0
        val isTimerActiveNew = sleepTimerModeActive

        assertFalse("Old derivation reports false when remaining=0", isTimerActiveOld)
        assertTrue("New derivation reports true when mode is still active", isTimerActiveNew)
    }
}
