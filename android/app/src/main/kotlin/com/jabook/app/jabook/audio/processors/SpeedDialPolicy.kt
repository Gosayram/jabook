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

package com.jabook.app.jabook.audio.processors

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Defines the discrete speed steps available in the speed dial UI control.
 *
 * The speed dial provides fine-grained playback speed control with:
 * - Range: [MIN_SPEED] (0.5x) to [MAX_SPEED] (3.5x)
 * - Step size: [STEP] (0.05x)
 * - Total steps: 61 (0.50, 0.55, 0.60, …, 3.45, 3.50)
 *
 * Usage:
 * ```
 * val next = SpeedDialPolicy.stepUp(currentSpeed)
 * val prev = SpeedDialPolicy.stepDown(currentSpeed)
 * val snap = SpeedDialPolicy.snapToStep(1.37f) // → 1.35f
 * ```
 *
 * Design decision: using 0.05x steps gives the user fine control for
 * audiobook listening, where small speed adjustments matter for comprehension.
 * The 0.5x–3.5x range covers the practical limits for spoken content.
 */
public object SpeedDialPolicy {
    /** Minimum playback speed. */
    public const val MIN_SPEED: Float = 0.5f

    /** Maximum playback speed. */
    public const val MAX_SPEED: Float = 3.5f

    /** Step size between adjacent speed values. */
    public const val STEP: Float = 0.05f

    /** Total number of discrete speed steps in the range. */
    public const val TOTAL_STEPS: Int = 61 // (3.5 - 0.5) / 0.05 + 1

    /**
     * Default playback speed when no per-book or global override is set.
     */
    public const val DEFAULT_SPEED: Float = 1.0f

    /**
     * Quantizes an arbitrary speed value to the nearest valid step.
     *
     * Handles floating-point imprecision by rounding to the nearest step boundary.
     *
     * @param speed arbitrary speed value
     * @return nearest valid step value, clamped to [MIN_SPEED]..[MAX_SPEED]
     */
    public fun snapToStep(speed: Float): Float {
        if (!speed.isFinite()) return MIN_SPEED
        val clamped = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        val stepIndex = ((clamped - MIN_SPEED) / STEP).roundToInt()
        return MIN_SPEED + stepIndex * STEP
    }

    /**
     * Returns the next higher speed step, or [MAX_SPEED] if already at maximum.
     *
     * @param currentSpeed current playback speed (will be snapped to nearest step)
     * @return next speed step up, or [MAX_SPEED] if at maximum
     */
    public fun stepUp(currentSpeed: Float): Float {
        val snapped = snapToStep(currentSpeed)
        return if (snapped >= MAX_SPEED) MAX_SPEED else snapped + STEP
    }

    /**
     * Returns the next lower speed step, or [MIN_SPEED] if already at minimum.
     *
     * @param currentSpeed current playback speed (will be snapped to nearest step)
     * @return next speed step down, or [MIN_SPEED] if at minimum
     */
    public fun stepDown(currentSpeed: Float): Float {
        val snapped = snapToStep(currentSpeed)
        return if (snapped <= MIN_SPEED) MIN_SPEED else snapped - STEP
    }

    /**
     * Returns the zero-based step index for the given speed.
     *
     * Index 0 corresponds to [MIN_SPEED], index [TOTAL_STEPS]-1 to [MAX_SPEED].
     *
     * @param speed playback speed (will be snapped to nearest step)
     * @return step index in 0..[TOTAL_STEPS]-1
     */
    public fun stepIndex(speed: Float): Int {
        if (!speed.isFinite()) return 0
        val snapped = snapToStep(speed)
        return ((snapped - MIN_SPEED) / STEP).roundToInt()
    }

    /**
     * Returns the speed value for a given step index.
     *
     * @param index step index in 0..[TOTAL_STEPS]-1 (clamped if out of range)
     * @return speed value at the given step
     */
    public fun speedForIndex(index: Int): Float {
        val clampedIndex = index.coerceIn(0, TOTAL_STEPS - 1)
        return MIN_SPEED + clampedIndex * STEP
    }

    /**
     * Cached list of all valid speed steps.
     */
    private val cachedSteps: List<Float> by lazy {
        (0 until TOTAL_STEPS).map { speedForIndex(it) }
    }

    /**
     * Returns all valid speed steps as a list, from [MIN_SPEED] to [MAX_SPEED].
     * Useful for UI components that need the full list of available speeds.
     */
    public fun allSteps(): List<Float> = cachedSteps

    /**
     * Formats a speed value for display in the UI.
     *
     * @param speed playback speed
     * @return formatted string (e.g., "1x", "1.5x", "0.5x")
     */
    public fun formatSpeed(speed: Float): String {
        val snapped = snapToStep(speed)
        val formatted =
            if (snapped == snapped.toInt().toFloat()) {
                snapped.toInt().toString()
            } else {
                String.format(Locale.US, "%.2f", snapped).removeSuffix("0").removeSuffix(".")
            }
        return "${formatted}x"
    }
}