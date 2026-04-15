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

class SleepTimerSchedulePolicyTest {
    @Test
    fun `minutesUntil returns same-day delta when target is in future`() {
        val now = LocalDateTime.of(2026, 4, 16, 22, 0, 0)
        val minutes = SleepTimerSchedulePolicy.minutesUntil(targetHour = 23, targetMinute = 30, now = now)
        assertEquals(90, minutes)
    }

    @Test
    fun `minutesUntil wraps to next day when target already passed`() {
        val now = LocalDateTime.of(2026, 4, 16, 23, 45, 0)
        val minutes = SleepTimerSchedulePolicy.minutesUntil(targetHour = 23, targetMinute = 30, now = now)
        assertEquals(23 * 60 + 45, minutes)
    }

    @Test
    fun `minutesUntil returns at least one minute`() {
        val now = LocalDateTime.of(2026, 4, 16, 23, 30, 0)
        val minutes = SleepTimerSchedulePolicy.minutesUntil(targetHour = 23, targetMinute = 30, now = now)
        assertEquals(24 * 60, minutes)
    }
}
