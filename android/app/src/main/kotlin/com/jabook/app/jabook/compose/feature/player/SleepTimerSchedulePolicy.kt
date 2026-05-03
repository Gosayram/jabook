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

import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.max

internal object SleepTimerSchedulePolicy {
    /**
     * Returns minutes until the next occurrence of [targetHour]:[targetMinute].
     * If selected time is already passed for today, schedules timer for tomorrow.
     */
    fun minutesUntil(
        targetHour: Int,
        targetMinute: Int,
        now: LocalDateTime,
    ): Int {
        val todayTarget =
            now
                .withHour(targetHour.coerceIn(0, 23))
                .withMinute(targetMinute.coerceIn(0, 59))
                .withSecond(0)
                .withNano(0)
        val target = if (todayTarget.isAfter(now)) todayTarget else todayTarget.plusDays(1)
        val minutes = Duration.between(now, target).toMinutes().toInt()
        return max(1, minutes)
    }
}
