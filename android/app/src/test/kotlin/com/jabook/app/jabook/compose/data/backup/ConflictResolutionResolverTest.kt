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

package com.jabook.app.jabook.compose.data.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictResolutionResolverTest {
    @Test
    fun `shouldUseIncoming returns true when local does not exist`() {
        val result =
            ConflictResolutionResolver.shouldUseIncoming(
                policy = ConflictResolutionPolicy.KEEP_LOCAL,
                localExists = false,
                localTimestamp = 100L,
                incomingTimestamp = 10L,
            )

        assertTrue(result)
    }

    @Test
    fun `keep local rejects incoming when local exists`() {
        val result =
            ConflictResolutionResolver.shouldUseIncoming(
                policy = ConflictResolutionPolicy.KEEP_LOCAL,
                localExists = true,
                localTimestamp = 200L,
                incomingTimestamp = 300L,
            )

        assertFalse(result)
    }

    @Test
    fun `keep remote always accepts incoming when local exists`() {
        val result =
            ConflictResolutionResolver.shouldUseIncoming(
                policy = ConflictResolutionPolicy.KEEP_REMOTE,
                localExists = true,
                localTimestamp = 500L,
                incomingTimestamp = 100L,
            )

        assertTrue(result)
    }

    @Test
    fun `keep newer accepts incoming when incoming is newer`() {
        val result =
            ConflictResolutionResolver.shouldUseIncoming(
                policy = ConflictResolutionPolicy.KEEP_NEWER,
                localExists = true,
                localTimestamp = 100L,
                incomingTimestamp = 101L,
            )

        assertTrue(result)
    }

    @Test
    fun `keep newer rejects incoming when local is newer`() {
        val result =
            ConflictResolutionResolver.shouldUseIncoming(
                policy = ConflictResolutionPolicy.KEEP_NEWER,
                localExists = true,
                localTimestamp = 102L,
                incomingTimestamp = 101L,
            )

        assertFalse(result)
    }
}
