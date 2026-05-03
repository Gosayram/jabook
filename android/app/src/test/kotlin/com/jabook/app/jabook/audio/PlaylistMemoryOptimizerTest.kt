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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistMemoryOptimizerTest {
    @Test
    fun `applyPlan removes all indices when no errors`() {
        val plan =
            PlaylistMemoryOptimizationPlan(
                keepStartIndex = 3,
                keepEndIndex = 7,
                removalIndicesDescending = listOf(9, 8, 2, 1, 0),
            )
        val removed = mutableListOf<Int>()

        val report =
            PlaylistMemoryOptimizer.applyPlan(
                plan = plan,
                removeByIndex = { removed += it },
            )

        assertEquals(plan.removalIndicesDescending, removed)
        assertEquals(5, report.attemptedRemovals)
        assertEquals(5, report.successfulRemovals)
    }

    @Test
    fun `applyPlan reports partial success and calls failure callback`() {
        val plan =
            PlaylistMemoryOptimizationPlan(
                keepStartIndex = 3,
                keepEndIndex = 7,
                removalIndicesDescending = listOf(9, 8, 2),
            )
        val removed = mutableListOf<Int>()
        val failed = mutableListOf<Int>()

        val report =
            PlaylistMemoryOptimizer.applyPlan(
                plan = plan,
                removeByIndex = { index ->
                    if (index == 8) throw IllegalStateException("can't remove")
                    removed += index
                },
                onRemovalFailed = { index, _ -> failed += index },
            )

        assertEquals(listOf(9, 2), removed)
        assertEquals(listOf(8), failed)
        assertEquals(3, report.attemptedRemovals)
        assertEquals(2, report.successfulRemovals)
        assertTrue(8 in plan.removalIndicesDescending)
    }
}
