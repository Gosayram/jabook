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
 * Calculates adaptive position-save intervals based on playback speed.
 *
 * P-17: Fixed 30-second intervals lose up to 90 seconds of content at 3x speed.
 * This policy adjusts the interval so that the maximum content loss never exceeds
 * [maxContentLossMs], regardless of speed.
 *
 * Formula: `saveIntervalMs = maxContentLossMs / speed`
 * - At 1x: 30 000ms (30 seconds)
 * - At 2x: 15 000ms (15 seconds)
 * - At 3x: 10 000ms (10 seconds)
 * - At 4x:  7 500ms (7.5 seconds)
 *
 * Combined with edge-based intervals from [PeriodicPositionSaver]:
 * - Near start/end (first/last 10%): always use [edgeIntervalMs]
 * - In the middle: use speed-adapted interval
 *
 * @param maxContentLossMs Maximum content time (ms) that can be lost on crash (default 30s)
 * @param minIntervalMs Hard minimum interval to avoid excessive disk writes (default 5s)
 * @param edgeIntervalMs Interval near start/end of track (default 5s)
 */
internal class AdaptivePositionSavePolicy(
    private val maxContentLossMs: Long = DEFAULT_MAX_CONTENT_LOSS_MS,
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    private val edgeIntervalMs: Long = DEFAULT_EDGE_INTERVAL_MS,
) {
    /**
     * Calculates the save interval based on playback speed and position progress.
     *
     * @param speed Current playback speed (1.0 = normal, 2.0 = double, etc.)
     * @param progress Position as fraction of duration (0.0 to 1.0)
     * @return Save interval in milliseconds
     */
    fun calculateIntervalMs(
        speed: Float,
        progress: Float,
    ): Long {
        if (speed <= 0f) return edgeIntervalMs

        if (progress <= EDGE_THRESHOLD || progress >= (1f - EDGE_THRESHOLD)) {
            return edgeIntervalMs
        }

        val adaptedInterval = (maxContentLossMs / speed).toLong()
        return adaptedInterval.coerceAtLeast(minIntervalMs)
    }

    /**
     * Calculates interval for the middle section (no edge adjustment).
     *
     * Useful when the caller handles edge logic separately.
     */
    fun calculateMiddleIntervalMs(speed: Float): Long {
        if (speed <= 0f) return maxContentLossMs
        return (maxContentLossMs / speed).toLong().coerceAtLeast(minIntervalMs)
    }

    companion object {
        internal const val DEFAULT_MAX_CONTENT_LOSS_MS = 30_000L
        internal const val DEFAULT_MIN_INTERVAL_MS = 5_000L
        internal const val DEFAULT_EDGE_INTERVAL_MS = 5_000L

        /** Position fraction threshold for "near edge" classification. */
        internal const val EDGE_THRESHOLD = 0.1f
    }
}
