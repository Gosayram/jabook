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

import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * P-85: Smooth speed ramp controller for the hold-to-boost gesture.
 *
 * Sits on top of [HoldToBoostPolicy] (pure state machine) and adds
 * animated speed transitions so the user doesn't hear a jarring jump.
 *
 * - Press: smoothly ramps from current speed to boost speed over [rampUpMs]
 * - Release: smoothly ramps back to the saved speed over [rampDownMs]
 *
 * @param player ExoPlayer instance to control
 * @param policy Hold-to-boost state machine
 * @param rampUpMs Duration for speed ramp-up (press)
 * @param rampDownMs Duration for speed ramp-down (release)
 * @param rampSteps Number of interpolation steps
 */
internal class HoldToBoostController(
    private val player: ExoPlayer,
    private val policy: HoldToBoostPolicy = HoldToBoostPolicy(),
    private val rampUpMs: Long = DEFAULT_RAMP_UP_MS,
    private val rampDownMs: Long = DEFAULT_RAMP_DOWN_MS,
    private val rampSteps: Int = DEFAULT_RAMP_STEPS,
) {
    private var rampJob: Job? = null

    /**
     * Called on press-down (pointer/button down).
     * Starts smooth ramp-up to boost speed.
     *
     * @param scope Coroutine scope for the ramp animation
     */
    fun onHoldStart(scope: CoroutineScope) {
        val currentSpeed = player.playbackParameters.speed
        val targetSpeed = policy.onPress(currentSpeed)

        rampJob?.cancel()
        rampJob =
            scope.launch {
                animateSpeed(from = currentSpeed, to = targetSpeed, durationMs = rampUpMs)
            }
    }

    /**
     * Called on release (pointer/button up).
     * Starts smooth ramp-down to the pre-boost speed.
     *
     * @param scope Coroutine scope for the ramp animation
     */
    fun onHoldEnd(scope: CoroutineScope) {
        val restoreSpeed = policy.onRelease() ?: return

        val currentSpeed = player.playbackParameters.speed
        rampJob?.cancel()
        rampJob =
            scope.launch {
                animateSpeed(from = currentSpeed, to = restoreSpeed, durationMs = rampDownMs)
            }
    }

    /**
     * Called on cancellation (focus loss, gesture cancelled).
     * Immediately restores to pre-boost speed without animation.
     */
    fun onHoldCancel() {
        rampJob?.cancel()
        rampJob = null
        val restoreSpeed = policy.onCancel()
        if (restoreSpeed != null) {
            player.playbackParameters = PlaybackParameters(restoreSpeed)
        }
    }

    private suspend fun animateSpeed(
        from: Float,
        to: Float,
        durationMs: Long,
    ) {
        val stepDelay = durationMs / rampSteps.coerceAtLeast(1)
        repeat(rampSteps) { i ->
            val fraction = (i + 1).toFloat() / rampSteps
            val speed = from + (to - from) * fraction
            player.playbackParameters = PlaybackParameters(speed)
            LogUtils.v(TAG, "Speed ramp: ${String.format("%.2f", speed)}x (${i + 1}/$rampSteps)")
            delay(stepDelay)
        }
    }

    companion object {
        private const val TAG = "HoldToBoostCtrl"
        internal const val DEFAULT_RAMP_UP_MS = 200L
        internal const val DEFAULT_RAMP_DOWN_MS = 150L
        internal const val DEFAULT_RAMP_STEPS = 10
    }
}
