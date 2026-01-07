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

package com.jabook.app.jabook.compose.feature.player.gestures

import android.content.Context
import android.media.AudioManager
import android.view.Window
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Handles swipe gesture calculations for the player screen.
 *
 * Provides utilities for:
 * - Brightness adjustment via vertical swipe on left side
 * - Volume adjustment via vertical swipe on right side
 * - Seek adjustment via horizontal swipe
 *
 * Based on NextPlayer gesture implementation patterns.
 */
class SwipeGestureHandler(
    private val context: Context,
) {
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /**
     * Maximum volume for the media stream.
     */
    val maxVolume: Int = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    /**
     * Current volume for the media stream.
     */
    val currentVolume: Int
        get() = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    /**
     * Adjusts screen brightness based on vertical drag delta.
     *
     * @param delta Normalized delta (-1.0 to 1.0), negative = darker
     * @param window Window to adjust brightness on
     * @param currentBrightness Current brightness (0.0 to 1.0)
     * @return New brightness value
     */
    fun adjustBrightness(
        delta: Float,
        window: Window,
        currentBrightness: Float,
    ): Float {
        // Invert: swipe up increases brightness
        val newBrightness = (currentBrightness - delta).coerceIn(0.01f, 1.0f)

        val layoutParams = window.attributes
        layoutParams.screenBrightness = newBrightness
        window.attributes = layoutParams

        return newBrightness
    }

    /**
     * Gets the current screen brightness.
     *
     * @param window Window to get brightness from
     * @return Current brightness (0.0 to 1.0), or system default if automatic
     */
    fun getCurrentBrightness(window: Window): Float {
        val brightness = window.attributes.screenBrightness
        return if (brightness < 0) {
            // Automatic brightness, try to get system setting
            try {
                android.provider.Settings.System.getInt(
                    context.contentResolver,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS,
                ) / 255f
            } catch (e: Exception) {
                0.5f // Default to 50%
            }
        } else {
            brightness
        }
    }

    /**
     * Adjusts volume based on vertical drag delta.
     *
     * @param delta Normalized delta (-1.0 to 1.0), negative = quieter
     * @return New volume level (0 to maxVolume)
     */
    fun adjustVolume(delta: Float): Int {
        // Invert: swipe up increases volume
        val volumeChange = (-delta * maxVolume * 0.5f).roundToInt()
        val newVolume = (currentVolume + volumeChange).coerceIn(0, maxVolume)

        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            newVolume,
            0, // No flags - we show our own indicator
        )

        return newVolume
    }

    /**
     * Calculates seek delta based on horizontal drag.
     *
     * @param horizontalDrag Horizontal drag in pixels
     * @param screenWidth Width of the screen for normalization
     * @param sensitivity Multiplier for seek speed (default 1.0)
     * @param maxSeekMs Maximum seek in milliseconds (default 2 minutes)
     * @return Seek delta in milliseconds (positive = forward)
     */
    fun calculateSeekDelta(
        horizontalDrag: Float,
        screenWidth: Float,
        sensitivity: Float = 1.0f,
        maxSeekMs: Long = 120_000L,
    ): Long {
        // Normalize drag to -1.0 to 1.0
        val normalizedDrag = (horizontalDrag / screenWidth).coerceIn(-1f, 1f)

        // Apply non-linear curve for fine control at small drags
        val curved = normalizedDrag * abs(normalizedDrag)

        // Calculate seek in milliseconds
        return (curved * maxSeekMs * sensitivity).toLong()
    }

    /**
     * Formats a seek delta for display.
     *
     * @param deltaMs Seek delta in milliseconds
     * @return Formatted string like "+10s" or "-5s"
     */
    fun formatSeekDelta(deltaMs: Long): String {
        val totalSeconds = deltaMs / 1000
        val absSeconds = abs(totalSeconds)
        val sign = if (totalSeconds >= 0) "+" else "-"
        
        val m = absSeconds / 60
        val s = absSeconds % 60
        
        return if (m > 0) {
            "$sign$m:${s.toString().padStart(2, '0')}"
        } else {
            "$sign${absSeconds}s"
        }
    }

    companion object {
        /**
         * Minimum drag distance to trigger a gesture (in dp).
         */
        const val MIN_DRAG_THRESHOLD_DP = 10f

        /**
         * Zone width ratio for left/right side detection.
         * Value of 0.4 means left 40% of screen = brightness, right 40% = volume.
         */
        const val SIDE_ZONE_RATIO = 0.4f
    }
}

/**
 * Enum representing the type of gesture being performed.
 */
enum class GestureType {
    NONE,
    BRIGHTNESS,
    VOLUME,
    SEEK,
}

/**
 * State holder for active gesture.
 */
data class GestureState(
    val type: GestureType = GestureType.NONE,
    val value: Float = 0f, // 0.0 to 1.0 for brightness/volume, or seek delta in seconds
    val isActive: Boolean = false,
)
