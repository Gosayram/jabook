// Copyright 2025 Jabook Contributors
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
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for managing the sleep timer.
 *
 * Sleep timer automatically pauses audio playback after a specified duration.
 */
interface SleepTimerRepository {
    /**
     * Current timer state.
     */
    val timerState: StateFlow<SleepTimerState>

    /**
     * Start sleep timer.
     *
     * @param durationMinutes Duration in minutes before auto-pause
     */
    fun startTimer(durationMinutes: Int)

    /**
     * Cancel active timer.
     */
    fun cancelTimer()
}
