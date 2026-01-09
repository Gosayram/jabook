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

import android.content.Context
import com.jabook.app.jabook.audio.AudioPlayerService
import com.jabook.app.jabook.compose.domain.model.SleepTimerState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SleepTimerRepository using AudioPlayerService as source of truth.
 *
 * Polls the service to keep UI state in sync.
 */
@Singleton
class SleepTimerRepositoryImpl
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : SleepTimerRepository {
        private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        private val _timerState = MutableStateFlow<SleepTimerState>(SleepTimerState.Idle)
        override val timerState: StateFlow<SleepTimerState> = _timerState.asStateFlow()

        init {
            // Poll service for timer state
            scope.launch {
                while (isActive) {
                    updateTimerState()
                    delay(1000)
                }
            }
        }

        private fun updateTimerState() {
            // Sleep timer methods require direct service access (not available via MediaController)
            @Suppress("DEPRECATION")
            val service = AudioPlayerService.getInstance()
            if (service == null) {
                if (_timerState.value !is SleepTimerState.Idle) {
                    _timerState.value = SleepTimerState.Idle
                }
                return
            }

            // Sync state with service
            if (service.isSleepTimerEndOfChapter()) {
                if (_timerState.value !is SleepTimerState.EndOfChapter) {
                    _timerState.value = SleepTimerState.EndOfChapter
                }
            } else {
                val remaining = service.getSleepTimerRemainingSeconds()
                if (remaining != null && remaining > 0) {
                    _timerState.value = SleepTimerState.Active(remaining)
                } else {
                    if (_timerState.value !is SleepTimerState.Idle) {
                        _timerState.value = SleepTimerState.Idle
                    }
                }
            }
        }

        override fun startTimer(durationMinutes: Int) {
            // Sleep timer methods require direct service access (not available via MediaController)
            @Suppress("DEPRECATION")
            AudioPlayerService.getInstance()?.setSleepTimerMinutes(durationMinutes)
            // State will be updated by polling
            // But we can eagerly update to feel responsive
            _timerState.value = SleepTimerState.Active(durationMinutes * 60)
        }

        override fun startTimerEndOfChapter() {
            // Sleep timer methods require direct service access (not available via MediaController)
            @Suppress("DEPRECATION")
            AudioPlayerService.getInstance()?.setSleepTimerEndOfChapter()
            _timerState.value = SleepTimerState.EndOfChapter
        }

        override fun cancelTimer() {
            // Sleep timer methods require direct service access (not available via MediaController)
            @Suppress("DEPRECATION")
            AudioPlayerService.getInstance()?.cancelSleepTimer()
            _timerState.value = SleepTimerState.Idle
        }
    }
