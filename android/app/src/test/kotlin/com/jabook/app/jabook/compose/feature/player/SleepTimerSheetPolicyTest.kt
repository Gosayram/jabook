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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class SleepTimerSheetPolicyTest {
    @Test
    public fun `handleSleepTimerPresetSelection invokes start with minutes and dismiss`() {
        var startedWith: Int? = null
        var dismissed = false

        handleSleepTimerPresetSelection(
            minutes = 15,
            onStartTimer = { minutes -> startedWith = minutes },
            onDismiss = { dismissed = true },
        )

        assertEquals(15, startedWith)
        assertTrue(dismissed)
    }

    @Test
    public fun `default sleep timer preset durations stay stable`() {
        assertEquals(listOf(5, 10, 15, 30, 45, 60), DEFAULT_SLEEP_TIMER_PRESET_DURATIONS)
    }
}
