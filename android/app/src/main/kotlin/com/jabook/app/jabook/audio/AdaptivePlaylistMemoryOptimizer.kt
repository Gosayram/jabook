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

import android.app.ActivityManager
import com.jabook.app.jabook.util.LogUtils

/**
 * Dynamically adjusts playlist buffer window based on available device memory.
 *
 * P-29: Fixed ±5 tracks may be too many for low-RAM devices (2 GB) or too
 * few for high-RAM devices. This optimizer queries available memory and
 * adjusts the buffer window accordingly.
 *
 * Usage:
 * ```
 * val window = optimizer.calculateBufferWindow()
 * playlistMemoryOptimizer.setBufferWindow(currentIndex - window, currentIndex + window)
 * ```
 *
 * @param activityManager System activity manager for memory queries
 */
internal class AdaptivePlaylistMemoryOptimizer(
    private val activityManager: ActivityManager,
) {
    /**
     * Calculates the optimal buffer window (number of tracks to keep loaded
     * around the current playback position).
     *
     * @return Number of tracks to keep before/after current position
     */
    fun calculateBufferWindow(): Int {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val availableMb = memInfo.availMem / (1024 * 1024)

        val window =
            when {
                memInfo.lowMemory -> 1
                availableMb < 256 -> 2
                availableMb < 512 -> 5
                availableMb < 1024 -> 8
                else -> 10
            }

        LogUtils.d(TAG, "Buffer window: ±$window tracks (availMem=${availableMb}MB, lowMemory=${memInfo.lowMemory})")
        return window
    }

    /**
     * Checks if the device is in a low-memory condition.
     */
    fun isLowMemory(): Boolean {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.lowMemory
    }

    companion object {
        private const val TAG = "AdaptiveMemOpt"
    }
}
