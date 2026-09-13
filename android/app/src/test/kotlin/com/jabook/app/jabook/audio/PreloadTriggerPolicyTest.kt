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

class PreloadTriggerPolicyTest {
    @Test
    fun `returns false when already triggered`() {
        val result =
            PreloadTriggerPolicy.shouldPreload(
                currentPositionMs = 999_999L,
                durationMs = 1_000_000L,
                alreadyTriggered = true,
            )
        assertFalse(result)
    }

    @Test
    fun `returns false when duration is unknown or non positive`() {
        assertFalse(
            PreloadTriggerPolicy.shouldPreload(
                currentPositionMs = 500_000L,
                durationMs = 0L,
                alreadyTriggered = false,
            ),
        )
        // covers Media3 C.TIME_UNSET (negative) without importing Media3
        assertFalse(
            PreloadTriggerPolicy.shouldPreload(
                currentPositionMs = 500_000L,
                durationMs = Long.MIN_VALUE + 1,
                alreadyTriggered = false,
            ),
        )
    }

    @Test
    fun `returns false before threshold`() {
        val result =
            PreloadTriggerPolicy.shouldPreload(
                currentPositionMs = 479_999L,
                durationMs = 600_000L,
                alreadyTriggered = false,
            )
        assertFalse(result)
    }

    @Test
    fun `returns true at and after threshold`() {
        val atThreshold =
            PreloadTriggerPolicy.shouldPreload(
                currentPositionMs = 480_000L,
                durationMs = 600_000L,
                alreadyTriggered = false,
            )
        val afterThreshold =
            PreloadTriggerPolicy.shouldPreload(
                currentPositionMs = 590_000L,
                durationMs = 600_000L,
                alreadyTriggered = false,
            )
        assertTrue(atThreshold)
        assertTrue(afterThreshold)
    }

    @Test
    fun `honors custom threshold fraction`() {
        val result =
            PreloadTriggerPolicy.shouldPreload(
                currentPositionMs = 300_000L,
                durationMs = 600_000L,
                alreadyTriggered = false,
                thresholdFraction = 0.5,
            )
        assertTrue(result)
    }
}
