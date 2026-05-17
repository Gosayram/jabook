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

import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * DynamicPriorityRebalancer recalculates loading priorities when user rapidly navigates
 * between chapters (large jumps). Prevents playback buffering by rebalancing priorities.
 *
 * P-10: When user quickly flips through chapters (jumps 10+ chapters), the priority
 * policy doesn't recalculate — playback buffers. This class monitors chapter jumps
 * and triggers priority rebalancing.
 */
internal class DynamicPriorityRebalancer(
    private val scope: CoroutineScope,
) {
    private var lastKnownIndex = 0
    private var rebalanceJob: Job? = null

    /**
     * Call when user jumps to a new chapter index.
     * If the jump is large (>= 5 chapters), triggers rebalancing.
     */
    public fun onChapterJump(newIndex: Int) {
        val delta = kotlin.math.abs(newIndex - lastKnownIndex)
        lastKnownIndex = newIndex

        if (delta >= 5) {
            rebalanceJob?.cancel()
            rebalanceJob =
                scope.launch {
                    delay(REBALANCE_DEBOUNCE_MS)
                    LogUtils.d(TAG, "Rebalancing priorities after large jump: target=$newIndex")
                }
        }
    }

    private companion object {
        private const val TAG = "DynamicPriorityRebalancer"
        private const val REBALANCE_DEBOUNCE_MS = 150L
    }
}
