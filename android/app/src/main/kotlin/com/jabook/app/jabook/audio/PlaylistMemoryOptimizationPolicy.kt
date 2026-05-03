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

internal data class PlaylistMemoryOptimizationPlan(
    val keepStartIndex: Int,
    val keepEndIndex: Int,
    val removalIndicesDescending: List<Int>,
)

/**
 * Computes memory optimization strategy for playlist windows.
 */
internal object PlaylistMemoryOptimizationPolicy {
    internal fun buildPlan(
        totalTracks: Int,
        currentTrackIndex: Int,
        keepWindow: Int,
        trackExistsAt: (Int) -> Boolean,
    ): PlaylistMemoryOptimizationPlan? {
        if (totalTracks <= keepWindow * 2 + 1) {
            return null
        }

        val keepStart = (currentTrackIndex - keepWindow).coerceAtLeast(0)
        val keepEnd = (currentTrackIndex + keepWindow).coerceAtMost(totalTracks - 1)
        val toRemove = mutableListOf<Int>()

        for (index in 0 until totalTracks) {
            if (index in keepStart..keepEnd) {
                continue
            }
            if (trackExistsAt(index)) {
                toRemove.add(index)
            }
        }

        if (toRemove.isEmpty()) {
            return null
        }

        toRemove.sortDescending()
        return PlaylistMemoryOptimizationPlan(
            keepStartIndex = keepStart,
            keepEndIndex = keepEnd,
            removalIndicesDescending = toRemove,
        )
    }
}
