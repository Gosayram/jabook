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

import com.jabook.app.jabook.compose.core.logger.Logger
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.repository.SleepTimerRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * P-92: Sleep timer operations extracted from PlayerViewModel.
 *
 * Handles start/cancel sleep timer with guard checks.
 *
 * @param sleepTimerRepository Repository for sleep timer state
 * @param sleepTimerState Current sleep timer state
 * @param loggerFactory Logger factory
 */
internal class PlayerSleepTimerHandler(
    private val sleepTimerRepository: SleepTimerRepository,
    private val sleepTimerState: StateFlow<com.jabook.app.jabook.compose.domain.model.SleepTimerState>,
    loggerFactory: LoggerFactory,
) {
    private val logger: Logger = loggerFactory.get("PlayerSleepTimerHandler")

    fun startSleepTimer(minutes: Int) {
        if (!PlayerIntentGuardPolicy.shouldStartFixedSleepTimer(sleepTimerState.value, minutes)) {
            logger.d { "Sleep timer already active with same target, skipping restart" }
            return
        }
        sleepTimerRepository.startTimer(minutes)
    }

    fun startSleepTimerEndOfChapter() {
        if (!PlayerIntentGuardPolicy.shouldStartEndOfChapter(sleepTimerState.value)) {
            logger.d { "Sleep timer is already in end-of-chapter mode, skipping restart" }
            return
        }
        sleepTimerRepository.startTimerEndOfChapter()
    }

    fun startSleepTimerEndOfTrack() {
        if (!PlayerIntentGuardPolicy.shouldStartEndOfTrack(sleepTimerState.value)) {
            logger.d { "Sleep timer is already in end-of-track mode, skipping restart" }
            return
        }
        sleepTimerRepository.startTimerEndOfTrack()
    }

    fun cancelSleepTimer() {
        sleepTimerRepository.cancelTimer()
    }
}
