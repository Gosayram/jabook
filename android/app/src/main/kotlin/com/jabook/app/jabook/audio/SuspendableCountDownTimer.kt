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

package com.jabook.app.jabook.audio

import android.os.SystemClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Precise countdown timer based on [SystemClock.elapsedRealtime].
 *
 * P-15: Unlike [android.os.CountDownTimer] which accumulates error over time
 * (~100-500ms per hour), this timer uses wall-clock absolute time to stay accurate.
 * Each tick recalculates remaining time from the absolute deadline, preventing drift.
 *
 * Supports pause/resume: pause saves remaining time, resume recalculates deadline.
 *
 * @param totalMillis Total countdown duration
 * @param intervalMillis Tick interval in milliseconds
 * @param onTickSeconds Called every tick with remaining seconds
 * @param onFinished Called when countdown reaches zero
 * @param dispatcher Coroutine dispatcher for the timer (default: Dispatchers.Default)
 */
public class SuspendableCountDownTimer(
    totalMillis: Long,
    private val intervalMillis: Long = 500L,
    private val onTickSeconds: (Long) -> Unit,
    private val onFinished: () -> Unit,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private var remainingMillis: Long = totalMillis
    private val timerScope = CoroutineScope(SupervisorJob() + dispatcher)
    private var job: Job? = null
    private var isRunning: Boolean = false

    /**
     * Starts the countdown timer.
     * If already running, this is a no-op.
     */
    public fun start() {
        if (isRunning) return
        isRunning = true
        val deadlineMs = SystemClock.elapsedRealtime() + remainingMillis
        job =
            timerScope.launch {
                try {
                    while (true) {
                        ensureActive()
                        val now = SystemClock.elapsedRealtime()
                        val remaining = deadlineMs - now
                        if (remaining <= 0L) {
                            remainingMillis = 0L
                            onTickSeconds(0L)
                            onFinished()
                            break
                        }
                        remainingMillis = remaining
                        onTickSeconds(remaining / 1000L)
                        val nextTick =
                            minOf(
                                intervalMillis,
                                remaining.coerceAtMost(intervalMillis),
                            )
                        delay(nextTick)
                    }
                } finally {
                    isRunning = false
                }
            }
    }

    /**
     * Pauses the timer and returns remaining milliseconds.
     *
     * @return Remaining milliseconds
     */
    public fun pause(): Long {
        job?.cancel()
        job = null
        isRunning = false
        return remainingMillis.coerceAtLeast(0L)
    }

    /**
     * Returns current remaining milliseconds.
     */
    public fun getRemainingMillis(): Long = remainingMillis.coerceAtLeast(0L)

    /**
     * Resumes the timer with remaining milliseconds.
     * Creates a new instance with the current remaining time.
     *
     * @return New [SuspendableCountDownTimer] instance with remaining time
     */
    public fun resume(): SuspendableCountDownTimer {
        val timer =
            SuspendableCountDownTimer(
                totalMillis = remainingMillis.coerceAtLeast(0L),
                intervalMillis = intervalMillis,
                onTickSeconds = onTickSeconds,
                onFinished = onFinished,
                dispatcher = dispatcher,
            )
        timer.start()
        return timer
    }

    /**
     * Cancels the timer without triggering [onFinished].
     */
    public fun cancel() {
        job?.cancel()
        job = null
        isRunning = false
    }
}
