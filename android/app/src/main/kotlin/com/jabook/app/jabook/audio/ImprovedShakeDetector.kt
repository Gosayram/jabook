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

import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.jabook.app.jabook.util.LogUtils

/**
 * Improved shake detector with pattern-based recognition and false-positive protection.
 *
 * P-13: The previous single-threshold approach (1.6 g-force with 2000ms debounce) triggers
 * false positives when walking with the phone in a pocket. This implementation requires
 * a minimum number of shakes within a time window (back-and-forth pattern), similar to
 * how Audible and Google Play Books implement shake-to-extend.
 *
 * Usage:
 * ```
 * val detector = ImprovedShakeDetector { onShake() }
 * sensorManager.registerListener(detector.asListener(), accelerometer, ...)
 * ```
 *
 * @param threshold G-force threshold per shake event (default 2.5, up from 1.6)
 * @param minShakeCount Minimum shakes within [windowMs] to trigger (default 2)
 * @param windowMs Time window in ms to accumulate shakes (default 1000)
 * @param debounceMs Minimum interval between consecutive triggers (default 3000)
 * @param clockMs Injectable clock for testing
 * @param onShakeDetected Callback invoked when a valid shake pattern is detected
 */
internal class ImprovedShakeDetector(
    private val threshold: Float = DEFAULT_THRESHOLD,
    private val minShakeCount: Int = DEFAULT_MIN_SHAKE_COUNT,
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val onShakeDetected: () -> Unit,
) {
    private val shakeTimestamps = ArrayDeque<Long>(INITIAL_CAPACITY)
    private var lastTriggerMs: Long = Long.MIN_VALUE / 2

    /**
     * Processes a raw accelerometer reading and returns true if a shake was detected.
     *
     * Call this from [SensorEventListener.onSensorChanged].
     *
     * @param ax X-axis acceleration (m/s^2)
     * @param ay Y-axis acceleration (m/s^2)
     * @param az Z-axis acceleration (m/s^2)
     * @return true if a valid shake pattern was completed this call
     */
    fun processAccelerometer(
        ax: Float,
        ay: Float,
        az: Float,
    ): Boolean {
        val gX = ax / SensorManager.GRAVITY_EARTH
        val gY = ay / SensorManager.GRAVITY_EARTH
        val gZ = az / SensorManager.GRAVITY_EARTH
        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)

        if (gForce < threshold) return false

        val now = clockMs()
        shakeTimestamps.addLast(now)

        pruneOldTimestamps(now)

        if (shakeTimestamps.size < minShakeCount) return false

        if (now - lastTriggerMs < debounceMs) {
            return false
        }

        lastTriggerMs = now
        shakeTimestamps.clear()
        LogUtils.d(TAG, "Shake detected: gForce=$gForce, triggering onShakeDetected")
        onShakeDetected()
        return true
    }

    /**
     * Creates a [SensorEventListener] that feeds accelerometer data into this detector.
     * Register with [SensorManager.registerListener].
     */
    fun asListener(): SensorEventListener =
        object : SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                if (event == null) return
                processAccelerometer(event.values[0], event.values[1], event.values[2])
            }

            override fun onAccuracyChanged(
                sensor: android.hardware.Sensor?,
                accuracy: Int,
            ) = Unit
        }

    /**
     * Resets internal state. Call when detection is paused or restarted.
     */
    fun reset() {
        shakeTimestamps.clear()
        lastTriggerMs = 0L
    }

    private fun pruneOldTimestamps(now: Long) {
        val cutoff = now - windowMs
        while (shakeTimestamps.isNotEmpty() && shakeTimestamps.first() < cutoff) {
            shakeTimestamps.removeFirst()
        }
    }

    internal companion object {
        private const val TAG = "ImprovedShakeDetector"

        /** G-force threshold per shake event. */
        internal const val DEFAULT_THRESHOLD = 2.5f

        /** Minimum shakes in window to trigger. */
        internal const val DEFAULT_MIN_SHAKE_COUNT = 2

        /** Window in ms to accumulate shakes. */
        internal const val DEFAULT_WINDOW_MS = 1_000L

        /** Minimum interval between consecutive triggers. */
        internal const val DEFAULT_DEBOUNCE_MS = 3_000L

        private const val INITIAL_CAPACITY = 4
    }
}

private fun sqrt(value: Float): Float = kotlin.math.sqrt(value)
