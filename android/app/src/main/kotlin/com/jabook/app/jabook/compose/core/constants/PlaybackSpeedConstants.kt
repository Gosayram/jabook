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

package com.jabook.app.jabook.compose.core.constants

/**
 * Playback speed constants shared between Settings and Player.
 */
public object PlaybackSpeedConstants {
    public const val MIN_SPEED: Float = 0.5f
    public const val MAX_SPEED: Float = 2.0f
    public const val SPEED_STEP: Float = 0.05f

    /**
     * Calculate number of steps for a slider.
     * Formula: (MAX - MIN) / STEP - 1
     */
    public val SLIDER_STEPS: Int = ((MAX_SPEED - MIN_SPEED) / SPEED_STEP - 1).toInt()

    /**
     * Generate list of all available speeds.
     * Used in Player speed selector.
     */
    public fun generateSpeedsList(): List<Float> {
        val speeds = mutableListOf<Float>()
        var current = MIN_SPEED
        while (current <= MAX_SPEED + 0.001f) { // +0.001f to handle floating point precision
            speeds.add(current)
            current += SPEED_STEP
        }
        return speeds
    }

    /**
     * Format speed for display.
     */
    public fun formatSpeed(speed: Float): String = String.format("%.2fx", speed)
}
