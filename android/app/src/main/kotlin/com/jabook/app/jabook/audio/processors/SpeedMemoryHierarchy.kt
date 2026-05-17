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

import kotlin.math.abs

/**
 * Resolves effective playback speed using a hierarchical preference fallback.
 *
 * Priority:
 * 1) per-book speed
 * 2) per-narrator speed
 * 3) per-author speed
 * 4) global last-used speed
 */
public object SpeedMemoryHierarchy {
    public const val MIN_TRUSTED_LISTENING_MS: Long = 5 * 60 * 1000L

    public fun resolveSpeed(
        perBookSpeed: Float?,
        perAuthorSpeed: Float?,
        globalSpeed: Float,
        perNarratorSpeed: Float? = null,
    ): Float =
        perBookSpeed
            ?: perNarratorSpeed
            ?: perAuthorSpeed
            ?: globalSpeed

    public fun hasMeaningfulSpeedDelta(
        previousSpeed: Float?,
        newSpeed: Float,
        epsilon: Float = 0.01f,
    ): Boolean {
        if (previousSpeed == null) return true
        return abs(previousSpeed - newSpeed) > epsilon
    }

    public fun shouldRecordBookSpeed(
        listenedMs: Long,
        previousSpeed: Float?,
        newSpeed: Float,
    ): Boolean =
        listenedMs >= MIN_TRUSTED_LISTENING_MS &&
            hasMeaningfulSpeedDelta(
                previousSpeed = previousSpeed,
                newSpeed = newSpeed,
            )
}
