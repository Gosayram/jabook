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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaylistAsyncLoadPriorityPolicyTest {
    @Test
    fun `buildPlan splits indices into critical windows and others`() {
        val plan =
            PlaylistAsyncLoadPriorityPolicy.buildPlan(
                remainingIndices = listOf(0, 1, 2, 4, 5, 6, 8, 9),
                firstTrackIndex = 3,
            )

        assertThat(plan.criticalPrevious).containsExactly(2, 1).inOrder()
        assertThat(plan.criticalNext).containsExactly(4, 5).inOrder()
        assertThat(plan.otherIndices).containsExactly(0, 6, 8, 9).inOrder()
        assertThat(plan.orderedIndices).containsExactly(2, 1, 4, 5, 0, 6, 8, 9).inOrder()
    }

    @Test
    fun `buildPlan handles boundaries near start of playlist`() {
        val plan =
            PlaylistAsyncLoadPriorityPolicy.buildPlan(
                remainingIndices = listOf(1, 2, 3, 4),
                firstTrackIndex = 0,
            )

        assertThat(plan.criticalPrevious).isEmpty()
        assertThat(plan.criticalNext).containsExactly(1, 2).inOrder()
        assertThat(plan.otherIndices).containsExactly(3, 4).inOrder()
    }

    @Test
    fun `priorityLabelFor maps index to expected label`() {
        val plan =
            PlaylistAsyncLoadPriorityPolicy.buildPlan(
                remainingIndices = listOf(1, 2, 4, 5, 8),
                firstTrackIndex = 3,
            )

        assertThat(PlaylistAsyncLoadPriorityPolicy.priorityLabelFor(2, plan)).isEqualTo("critical_previous")
        assertThat(PlaylistAsyncLoadPriorityPolicy.priorityLabelFor(4, plan)).isEqualTo("critical_next")
        assertThat(PlaylistAsyncLoadPriorityPolicy.priorityLabelFor(8, plan)).isEqualTo("other")
    }
}
