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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerResumeHintPolicyTest {
    @Test
    fun shouldShowHint_whenStoppedBySleepTimerAndPausedAndNotShownYet() {
        val shouldShow =
            SleepTimerResumeHintPolicy.shouldShowHint(
                wasLastStopBySleepTimer = true,
                isPlaying = false,
                hasAlreadyShownInSession = false,
            )

        assertTrue(shouldShow)
    }

    @Test
    fun shouldNotShowHint_whenAlreadyPlaying() {
        val shouldShow =
            SleepTimerResumeHintPolicy.shouldShowHint(
                wasLastStopBySleepTimer = true,
                isPlaying = true,
                hasAlreadyShownInSession = false,
            )

        assertFalse(shouldShow)
    }

    @Test
    fun shouldNotShowHint_whenAlreadyShownInSession() {
        val shouldShow =
            SleepTimerResumeHintPolicy.shouldShowHint(
                wasLastStopBySleepTimer = true,
                isPlaying = false,
                hasAlreadyShownInSession = true,
            )

        assertFalse(shouldShow)
    }

    @Test
    fun shouldNotShowHint_whenNoSleepTimerStopFlag() {
        val shouldShow =
            SleepTimerResumeHintPolicy.shouldShowHint(
                wasLastStopBySleepTimer = false,
                isPlaying = false,
                hasAlreadyShownInSession = false,
            )

        assertFalse(shouldShow)
    }
}
