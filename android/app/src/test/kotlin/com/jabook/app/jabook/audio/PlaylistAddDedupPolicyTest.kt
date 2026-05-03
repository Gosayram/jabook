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

class PlaylistAddDedupPolicyTest {
    @Test
    fun `decide skips when index already marked as added`() {
        val decision =
            PlaylistAddDedupPolicy.decide(
                index = 4,
                expectedPath = "/a/4.mp3",
                addedIndices = setOf(4),
                currentPlayerItemCount = 0,
                existingPathAtIndex = null,
            )

        assertThat(decision.shouldSkipAdd).isTrue()
        assertThat(decision.shouldMarkAdded).isFalse()
        assertThat(decision.reason).isEqualTo(PlaylistAddDedupReason.ALREADY_MARKED_ADDED)
    }

    @Test
    fun `decide skips and marks when player already has expected item at index`() {
        val decision =
            PlaylistAddDedupPolicy.decide(
                index = 2,
                expectedPath = "/a/2.mp3",
                addedIndices = emptySet(),
                currentPlayerItemCount = 5,
                existingPathAtIndex = "/a/2.mp3",
            )

        assertThat(decision.shouldSkipAdd).isTrue()
        assertThat(decision.shouldMarkAdded).isTrue()
        assertThat(decision.reason).isEqualTo(PlaylistAddDedupReason.PLAYER_ALREADY_HAS_EXPECTED_ITEM)
    }

    @Test
    fun `decide proceeds when no duplicate conditions are met`() {
        val decision =
            PlaylistAddDedupPolicy.decide(
                index = 2,
                expectedPath = "/a/2.mp3",
                addedIndices = emptySet(),
                currentPlayerItemCount = 2,
                existingPathAtIndex = null,
            )

        assertThat(decision.shouldSkipAdd).isFalse()
        assertThat(decision.shouldMarkAdded).isFalse()
        assertThat(decision.reason).isEqualTo(PlaylistAddDedupReason.PROCEED)
    }
}
