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

/**
 * Time-based next-track preload trigger (inspired by spotube's percent-based
 * stream prefetch at ~80% of the current item).
 *
 * Pure policy: given the current playback position, the current item duration and
 * whether the preload already fired for this item, decides if the next track should
 * be preloaded now. The caller owns the per-item `alreadyTriggered` flag and resets
 * it on media item transitions.
 *
 * Note: there is no local/remote playlist distinction in the preload API
 * ([PlaylistManager.preloadNextTrack] handles bounds and already-loaded checks
 * itself), so this policy applies uniformly to all playlists.
 */
internal object PreloadTriggerPolicy {
    internal const val DEFAULT_THRESHOLD_FRACTION = 0.8

    /**
     * @param currentPositionMs Current playback position within the item
     * @param durationMs Current item duration; `<= 0` (including Media3 `C.TIME_UNSET`) means unknown
     * @param alreadyTriggered Whether preload already fired for the current item
     * @param thresholdFraction Fraction of the duration after which to preload
     */
    internal fun shouldPreload(
        currentPositionMs: Long,
        durationMs: Long,
        alreadyTriggered: Boolean,
        thresholdFraction: Double = DEFAULT_THRESHOLD_FRACTION,
    ): Boolean {
        if (alreadyTriggered) return false
        if (durationMs <= 0) return false
        return currentPositionMs >= durationMs * thresholdFraction
    }
}
