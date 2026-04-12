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
 * Determines which chapter marker the user intended to tap on the seekbar,
 * given a hit-tolerance radius.
 *
 * On touch screens it is hard to tap exactly on a thin marker line. This
 * policy accepts the user's tap position (as a fraction of total duration)
 * and the list of chapter start fractions, then returns the nearest chapter
 * index if it falls within the configured tolerance.
 */
public object ChapterMarkerHitTolerancePolicy {
    /** Default tolerance as a fraction of total duration (±1% of the bar). */
    public const val DEFAULT_TOLERANCE_FRACTION: Float = 0.01f

    /**
     * Finds the chapter index whose marker is closest to [tapFraction],
     * but only if the distance is within [toleranceFraction].
     *
     * @param tapFraction        user's tap position as fraction of total book duration [0..1]
     * @param chapterFractions   chapter start positions as fractions [0..1], sorted ascending
     * @param toleranceFraction  maximum allowed distance between tap and marker
     * @return chapter index if a marker is within tolerance, or null
     */
    public fun resolveChapter(
        tapFraction: Float,
        chapterFractions: List<Float>,
        toleranceFraction: Float = DEFAULT_TOLERANCE_FRACTION,
    ): Int? {
        if (chapterFractions.isEmpty()) return null
        if (tapFraction < 0f || tapFraction > 1f) return null
        if (toleranceFraction < 0f) return null

        var bestIndex = -1
        var bestDistance = Float.MAX_VALUE

        for ((index, fraction) in chapterFractions.withIndex()) {
            val distance = kotlin.math.abs(tapFraction - fraction)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = index
            }
        }

        return if (bestDistance <= toleranceFraction) bestIndex else null
    }

    /**
     * Checks whether a tap at [tapFraction] is within tolerance of any chapter marker.
     *
     * @return true if the tap is near a chapter marker
     */
    public fun isNearMarker(
        tapFraction: Float,
        chapterFractions: List<Float>,
        toleranceFraction: Float = DEFAULT_TOLERANCE_FRACTION,
    ): Boolean = resolveChapter(tapFraction, chapterFractions, toleranceFraction) != null
}
