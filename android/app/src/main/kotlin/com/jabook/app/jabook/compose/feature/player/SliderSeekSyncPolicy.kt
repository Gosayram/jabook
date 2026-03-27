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

internal data class SliderSeekSyncResult(
    val sliderPosition: Float,
    val awaitingSeekSync: Boolean,
)

internal object SliderSeekSyncPolicy {
    private const val DEFAULT_CONVERGENCE_THRESHOLD = 0.02f

    fun resolveFromPlayerProgress(
        playerProgress: Float,
        currentSliderPosition: Float,
        isDragging: Boolean,
        awaitingSeekSync: Boolean,
        convergenceThreshold: Float = DEFAULT_CONVERGENCE_THRESHOLD,
    ): SliderSeekSyncResult {
        if (!playerProgress.isFinite() || isDragging) {
            return SliderSeekSyncResult(
                sliderPosition = currentSliderPosition,
                awaitingSeekSync = awaitingSeekSync,
            )
        }

        val clampedProgress = playerProgress.coerceIn(0f, 1f)
        if (!awaitingSeekSync) {
            return SliderSeekSyncResult(
                sliderPosition = clampedProgress,
                awaitingSeekSync = false,
            )
        }

        return if (kotlin.math.abs(clampedProgress - currentSliderPosition) <= convergenceThreshold) {
            SliderSeekSyncResult(
                sliderPosition = clampedProgress,
                awaitingSeekSync = false,
            )
        } else {
            SliderSeekSyncResult(
                sliderPosition = currentSliderPosition,
                awaitingSeekSync = true,
            )
        }
    }
}

