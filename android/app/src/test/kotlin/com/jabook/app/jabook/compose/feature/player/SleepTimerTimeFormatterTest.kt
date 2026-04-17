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

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class SleepTimerTimeFormatterTest {
    @Test
    fun `formatSleepTimerStopAt formats same-day end time`() {
        val now = LocalDateTime.of(2026, 4, 17, 22, 15, 0)
        assertEquals("22:45", formatSleepTimerStopAt(remainingSeconds = 30 * 60, now = now))
    }

    @Test
    fun `formatSleepTimerStopAt wraps across midnight`() {
        val now = LocalDateTime.of(2026, 4, 17, 23, 50, 0)
        assertEquals("00:10", formatSleepTimerStopAt(remainingSeconds = 20 * 60, now = now))
    }

    @Test
    fun `formatSleepTimerStopAt clamps negative seconds to now`() {
        val now = LocalDateTime.of(2026, 4, 17, 14, 30, 0)
        assertEquals("14:30", formatSleepTimerStopAt(remainingSeconds = -100, now = now))
    }
}