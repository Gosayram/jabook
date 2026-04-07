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

package com.jabook.app.jabook.compose.feature.player

import kotlin.math.abs
import kotlin.math.max

internal object PlayerSliderStateMachinePolicy {
    private const val DEFAULT_TICK_MS = 250L
    private const val MIN_PROGRESS_STEP = 0.001f

    private fun sanitizeProgress(
        value: Float,
        fallback: Float = 0f,
    ): Float =
        if (value.isFinite()) {
            value.coerceIn(0f, 1f)
        } else {
            fallback.coerceIn(0f, 1f)
        }

    fun displayedProgress(
        liveProgress: Float,
        dragProgress: Float?,
        pendingSeekProgress: Float?,
    ): Float = sanitizeProgress(dragProgress ?: pendingSeekProgress ?: liveProgress)

    fun coalesceLiveProgress(
        previousProgress: Float,
        incomingProgress: Float,
        totalDurationMs: Long,
        tickMs: Long = DEFAULT_TICK_MS,
        minProgressStep: Float = MIN_PROGRESS_STEP,
    ): Float {
        val previous = sanitizeProgress(previousProgress)
        val incoming = sanitizeProgress(incomingProgress, fallback = previous)
        if (incoming == 0f || incoming == 1f) {
            return incoming
        }

        val dynamicStep =
            if (totalDurationMs > 0L && tickMs > 0L) {
                max(minProgressStep, tickMs.toFloat() / totalDurationMs.toFloat())
            } else {
                minProgressStep
            }

        return if (abs(incoming - previous) >= dynamicStep) incoming else previous
    }
}
