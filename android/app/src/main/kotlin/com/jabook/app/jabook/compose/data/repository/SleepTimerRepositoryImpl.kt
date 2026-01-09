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
            // Poll service for timer state with adaptive polling interval
            // Poll more frequently when timer is active, less when idle
            scope.launch {
                var lastState: SleepTimerState = SleepTimerState.Idle
                while (isActive) {
                    val newState = updateTimerState()
                    // Adaptive polling: faster when active, slower when idle
                    val delayMs =
                        when {
                            newState is SleepTimerState.Active -> 1000L // 1 second when active
                            newState is SleepTimerState.EndOfChapter -> 1000L // 1 second for end of chapter
                            // Immediate check when transitioning to idle
                            lastState !is SleepTimerState.Idle && newState is SleepTimerState.Idle -> 1000L
                            else -> 5000L // 5 seconds when idle
                        }
                    lastState = newState
                    delay(delayMs)
                }
            }
        }

        /**
         * Updates timer state from service.
         * Returns the new state for adaptive polling.
         */
        private fun updateTimerState(): SleepTimerState {
            // Sleep timer methods require direct service access (not available via MediaController)
            // Use safer access with initialization check
            @Suppress("DEPRECATION")
            val service = AudioPlayerService.getInstance()
            if (service == null || !service.isFullyInitialized()) {
                if (_timerState.value !is SleepTimerState.Idle) {
                    _timerState.value = SleepTimerState.Idle
                }
                return SleepTimerState.Idle
            }

            // Sync state with service
            val newState =
                if (service.isSleepTimerEndOfChapter()) {
                    SleepTimerState.EndOfChapter
                } else {
                    val remaining = service.getSleepTimerRemainingSeconds()
                    if (remaining != null && remaining > 0) {
                        SleepTimerState.Active(remaining)
                    } else {
                        SleepTimerState.Idle
                    }
                }

            // Only update if state actually changed to avoid unnecessary recompositions
            if (_timerState.value != newState) {
                _timerState.value = newState
            }

            return newState
        }

        override fun startTimer(durationMinutes: Int) {
            // Sleep timer methods require direct service access (not available via MediaController)
            // Use safer access with retry logic
            scope.launch {
                val service = waitForServiceReady()
                service?.setSleepTimerMinutes(durationMinutes)
                // State will be updated by polling, but eagerly update for responsiveness
                _timerState.value = SleepTimerState.Active(durationMinutes * 60)
            }
        }

        override fun startTimerEndOfChapter() {
            // Sleep timer methods require direct service access (not available via MediaController)
            scope.launch {
                val service = waitForServiceReady()
                service?.setSleepTimerEndOfChapter()
                _timerState.value = SleepTimerState.EndOfChapter
            }
        }

        override fun cancelTimer() {
            // Sleep timer methods require direct service access (not available via MediaController)
            scope.launch {
                val service = waitForServiceReady()
                service?.cancelSleepTimer()
                _timerState.value = SleepTimerState.Idle
            }
        }

        /**
         * Helper method to safely get service instance with retry logic.
         * This is a temporary solution until we can fully migrate to MediaController.
         */
        private suspend fun waitForServiceReady(
            maxRetries: Int = 3,
            delayMs: Long = 200,
        ): AudioPlayerService? {
            repeat(maxRetries) { attempt ->
                @Suppress("DEPRECATION")
                val service = AudioPlayerService.getInstance()
                if (service != null && service.isFullyInitialized()) {
                    return service
                }
                if (attempt < maxRetries - 1) {
                    delay(delayMs * (attempt + 1)) // Exponential backoff
                }
            }
            return null
        }
    }
