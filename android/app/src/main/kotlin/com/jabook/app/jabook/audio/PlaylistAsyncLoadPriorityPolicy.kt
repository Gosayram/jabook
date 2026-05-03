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

internal data class PlaylistAsyncLoadPriorityPlan(
    val criticalPrevious: List<Int>,
    val criticalNext: List<Int>,
    val otherIndices: List<Int>,
) {
    val orderedIndices: List<Int> = criticalPrevious + criticalNext + otherIndices
}

internal object PlaylistAsyncLoadPriorityPolicy {
    private const val PRIORITY_WINDOW = 2

    internal fun buildPlan(
        remainingIndices: List<Int>,
        firstTrackIndex: Int,
    ): PlaylistAsyncLoadPriorityPlan {
        val criticalPrevious = mutableListOf<Int>()
        val criticalNext = mutableListOf<Int>()
        val otherIndices = mutableListOf<Int>()

        for (index in remainingIndices) {
            when {
                index >= firstTrackIndex - PRIORITY_WINDOW && index < firstTrackIndex -> {
                    criticalPrevious.add(index)
                }
                index > firstTrackIndex && index <= firstTrackIndex + PRIORITY_WINDOW -> {
                    criticalNext.add(index)
                }
                else -> {
                    otherIndices.add(index)
                }
            }
        }

        criticalPrevious.sortDescending()
        criticalNext.sort()
        otherIndices.sort()
        return PlaylistAsyncLoadPriorityPlan(
            criticalPrevious = criticalPrevious,
            criticalNext = criticalNext,
            otherIndices = otherIndices,
        )
    }

    internal fun priorityLabelFor(
        index: Int,
        plan: PlaylistAsyncLoadPriorityPlan,
    ): String =
        when {
            index in plan.criticalPrevious -> "critical_previous"
            index in plan.criticalNext -> "critical_next"
            else -> "other"
        }
}
