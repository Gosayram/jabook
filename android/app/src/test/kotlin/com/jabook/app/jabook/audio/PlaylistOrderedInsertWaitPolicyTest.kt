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

class PlaylistOrderedInsertWaitPolicyTest {
    @Test
    fun `decide returns stable retry settings`() {
        val decision = PlaylistOrderedInsertWaitPolicy.decide()

        assertThat(decision.maxAttempts).isEqualTo(200)
        assertThat(decision.delayMs).isEqualTo(100L)
    }

    @Test
    fun `areAllPreviousIndicesAdded ignores first track index and validates others`() {
        val ready =
            PlaylistOrderedInsertWaitPolicy.areAllPreviousIndicesAdded(
                index = 5,
                firstTrackIndex = 2,
                addedIndices = setOf(0, 1, 3, 4),
            )
        val missing =
            PlaylistOrderedInsertWaitPolicy.areAllPreviousIndicesAdded(
                index = 5,
                firstTrackIndex = 2,
                addedIndices = setOf(0, 1, 4),
            )

        assertThat(ready).isTrue()
        assertThat(missing).isFalse()
    }

    @Test
    fun `shouldContinueWaiting obeys max attempts boundary`() {
        assertThat(
            PlaylistOrderedInsertWaitPolicy.shouldContinueWaiting(
                waitAttempts = 199,
                maxAttempts = 200,
            ),
        ).isTrue()
        assertThat(
            PlaylistOrderedInsertWaitPolicy.shouldContinueWaiting(
                waitAttempts = 200,
                maxAttempts = 200,
            ),
        ).isFalse()
    }
}
