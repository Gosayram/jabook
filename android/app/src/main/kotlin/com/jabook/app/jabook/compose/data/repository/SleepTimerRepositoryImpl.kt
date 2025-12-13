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

import android.content.Context
import com.jabook.app.jabook.audio.AudioPlayerService
import com.jabook.app.jabook.compose.domain.model.SleepTimerState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
 * Implementation of SleepTimerRepository using coroutines.
 *
 * Manages countdown timer and auto-pauses playback when timer expires.
 */
@Singleton
class SleepTimerRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : SleepTimerRepository {
        private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        private val _timerState = MutableStateFlow<SleepTimerState>(SleepTimerState.Idle)
        override val timerState: StateFlow<SleepTimerState> = _timerState.asStateFlow()

        private var timerJob: Job? = null

        override fun startTimer(durationMinutes: Int) {
            // Cancel existing timer if any
            timerJob?.cancel()

            var remainingSeconds = durationMinutes * 60
            _timerState.value = SleepTimerState.Active(remainingSeconds)

            timerJob =
                scope.launch {
                    while (remainingSeconds > 0 && isActive) {
                        delay(1000) // 1 second
                        remainingSeconds--

                        if (remainingSeconds > 0) {
                            _timerState.value = SleepTimerState.Active(remainingSeconds)
                        }
                    }

                    if (remainingSeconds == 0 && isActive) {
                        // Timer finished - pause playback
                        _timerState.value = SleepTimerState.Idle
                        pausePlayback()
                    }
                }
        }

        override fun cancelTimer() {
            timerJob?.cancel()
            timerJob = null
            _timerState.value = SleepTimerState.Idle
        }

        private fun pausePlayback() {
            // Pause via AudioPlayerService
            AudioPlayerService.getInstance()?.pause()
        }
    }
