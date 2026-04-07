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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistQueueMutationCoalescingPolicyTest {
    @Test
    fun `returns false when no previous operation key`() {
        val result =
            PlaylistQueueMutationCoalescingPolicy.shouldDropDuplicate(
                previousOperationKey = null,
                previousMutationAtMs = 1_000L,
                operationKey = "move:1:2",
                nowMs = 1_050L,
            )

        assertFalse(result)
    }

    @Test
    fun `drops identical operation inside coalescing window`() {
        val result =
            PlaylistQueueMutationCoalescingPolicy.shouldDropDuplicate(
                previousOperationKey = "move:1:2",
                previousMutationAtMs = 1_000L,
                operationKey = "move:1:2",
                nowMs = 1_080L,
            )

        assertTrue(result)
    }

    @Test
    fun `keeps operation when outside coalescing window`() {
        val result =
            PlaylistQueueMutationCoalescingPolicy.shouldDropDuplicate(
                previousOperationKey = "move:1:2",
                previousMutationAtMs = 1_000L,
                operationKey = "move:1:2",
                nowMs = 1_400L,
            )

        assertFalse(result)
    }

    @Test
    fun `keeps operation when key changes`() {
        val result =
            PlaylistQueueMutationCoalescingPolicy.shouldDropDuplicate(
                previousOperationKey = "move:1:2",
                previousMutationAtMs = 1_000L,
                operationKey = "move:2:3",
                nowMs = 1_050L,
            )

        assertFalse(result)
    }
}
