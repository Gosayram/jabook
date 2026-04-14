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

package com.jabook.app.jabook.compose.feature.player.controller

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionPublishPolicyTest {
    @Test
    fun `force publish always returns true`() {
        assertTrue(
            PositionPublishPolicy.shouldPublish(
                previousPositionMs = 1_000L,
                incomingPositionMs = 1_001L,
                force = true,
            ),
        )
    }

    @Test
    fun `small delta below epsilon does not publish`() {
        assertFalse(
            PositionPublishPolicy.shouldPublish(
                previousPositionMs = 10_000L,
                incomingPositionMs = 10_100L,
                force = false,
            ),
        )
    }

    @Test
    fun `delta equal or above epsilon publishes`() {
        assertTrue(
            PositionPublishPolicy.shouldPublish(
                previousPositionMs = 10_000L,
                incomingPositionMs = 10_120L,
                force = false,
            ),
        )
        assertTrue(
            PositionPublishPolicy.shouldPublish(
                previousPositionMs = 10_000L,
                incomingPositionMs = 10_400L,
                force = false,
            ),
        )
    }
}
