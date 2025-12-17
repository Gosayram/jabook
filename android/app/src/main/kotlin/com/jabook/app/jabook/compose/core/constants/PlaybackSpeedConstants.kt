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

package com.jabook.app.jabook.compose.core.constants

/**
 * Playback speed constants shared between Settings and Player.
 */
object PlaybackSpeedConstants {
    const val MIN_SPEED = 0.5f
    const val MAX_SPEED = 2.0f
    const val SPEED_STEP = 0.05f

    /**
     * Calculate number of steps for a slider.
     * Formula: (MAX - MIN) / STEP - 1
     */
    val SLIDER_STEPS = ((MAX_SPEED - MIN_SPEED) / SPEED_STEP - 1).toInt()

    /**
     * Generate list of all available speeds.
     * Used in Player speed selector.
     */
    fun generateSpeedsList(): List<Float> {
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
    fun formatSpeed(speed: Float): String = String.format("%.2fx", speed)
}
