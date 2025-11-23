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

package com.jabook.app.jabook.audio

import android.os.CountDownTimer

/**
 * Suspendable countdown timer that supports pause and resume operations.
 * 
 * Inspired by lissen-android implementation for better timer control.
 * This allows the timer to pause when playback pauses and resume when playback resumes.
 */
class SuspendableCountDownTimer(
    totalMillis: Long,
    private val intervalMillis: Long,
    private val onTickSeconds: (Long) -> Unit,
    private val onFinished: () -> Unit,
) : CountDownTimer(totalMillis, intervalMillis) {
    private var remainingMillis: Long = totalMillis

    override fun onTick(millisUntilFinished: Long) {
        remainingMillis = millisUntilFinished
        onTickSeconds(millisUntilFinished / 1000)
    }

    override fun onFinish() {
        remainingMillis = 0L
        onFinished()
    }

    /**
     * Pauses the timer and returns remaining milliseconds.
     * 
     * @return Remaining milliseconds
     */
    fun pause(): Long {
        cancel()
        return remainingMillis
    }

    /**
     * Resumes the timer with remaining milliseconds.
     * 
     * @return New SuspendableCountDownTimer instance
     */
    fun resume(): SuspendableCountDownTimer {
        val timer = SuspendableCountDownTimer(remainingMillis, intervalMillis, onTickSeconds, onFinished)
        timer.start()
        return timer
    }
}

