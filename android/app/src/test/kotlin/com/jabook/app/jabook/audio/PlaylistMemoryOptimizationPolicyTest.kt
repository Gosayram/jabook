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
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistMemoryOptimizationPolicyTest {
    @Test
    fun `buildPlan returns null when playlist already within keep window`() {
        val plan =
            PlaylistMemoryOptimizationPolicy.buildPlan(
                totalTracks = 8,
                currentTrackIndex = 3,
                keepWindow = 5,
                trackExistsAt = { true },
            )

        assertNull(plan)
    }

    @Test
    fun `buildPlan returns descending removal indices outside keep window`() {
        val plan =
            PlaylistMemoryOptimizationPolicy.buildPlan(
                totalTracks = 20,
                currentTrackIndex = 10,
                keepWindow = 2,
                trackExistsAt = { true },
            )

        requireNotNull(plan)
        assertEquals(8, plan.keepStartIndex)
        assertEquals(12, plan.keepEndIndex)
        assertEquals((0..7).toList().plus(13..19).sortedDescending(), plan.removalIndicesDescending)
    }

    @Test
    fun `buildPlan skips indices that are missing in player`() {
        val missing = setOf(0, 6, 18, 19)
        val plan =
            PlaylistMemoryOptimizationPolicy.buildPlan(
                totalTracks = 20,
                currentTrackIndex = 10,
                keepWindow = 2,
                trackExistsAt = { index -> index !in missing },
            )

        requireNotNull(plan)
        assertEquals(
            listOf(17, 16, 15, 14, 13, 7, 5, 4, 3, 2, 1),
            plan.removalIndicesDescending,
        )
    }
}
