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
 * This is an **in-memory** calculation only — it does NOT write to the
 * [androidx.media3.datasource.cache.SimpleCache] disk cache provided in
 * [MediaModule] (`MediaModule.kt:103`, 200 MB LRU). The playlist preload
 * path (`PlaylistManager.preloadNextTrack` → `ExoPlayer.addMediaSource`) and
 * memory trimming (`PlaylistManager.optimizeMemoryUsage` →
 * `PlaylistMemoryOptimizer.applyPlan`) operate on the ExoPlayer timeline
 * in RAM, not on [SimpleCache] entries. There is no contention between this
 * optimizer and [androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor]:
 * disk eviction is LRU-managed and the actively playing track (including any
 * A-B repeat range in [com.jabook.app.jabook.compose.feature.player.PlayerABRepeatHandler])
 * is MRU due to continuous reads, so preloading a single neighbor
 * (`PlaybackEventProcessor.kt:248`) cannot evict it. With 200 MB holding
 * ~6-20 chapters (10-30 MB each), window ±1-10 stays well under the limit.
 * No cache pinning is required — see issue #61 (resolved as speculative).
 *
 * Usage:
 * ```
 * val window = optimizer.calculateBufferWindow()
 * playlistManager.optimizeMemoryUsage(currentIndex, keepWindow = window)
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
