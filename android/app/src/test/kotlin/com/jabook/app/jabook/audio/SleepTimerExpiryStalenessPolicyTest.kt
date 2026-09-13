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

class SleepTimerExpiryStalenessPolicyTest {
    @Test
    fun `accepts expiry for the active fixed-duration timer`() {
        assertTrue(
            SleepTimerExpiryStalenessPolicy.shouldApply(
                activeGeneration = 4L,
                callbackGeneration = 4L,
                activeMode = SleepTimerMode.FIXED_DURATION,
            ),
        )
    }

    @Test
    fun `rejects expiry after timer is cancelled or replaced`() {
        assertFalse(
            SleepTimerExpiryStalenessPolicy.shouldApply(
                activeGeneration = 5L,
                callbackGeneration = 4L,
                activeMode = SleepTimerMode.FIXED_DURATION,
            ),
        )
    }

    @Test
    fun `rejects fixed timer expiry after timer switches to chapter mode`() {
        assertFalse(
            SleepTimerExpiryStalenessPolicy.shouldApply(
                activeGeneration = 4L,
                callbackGeneration = 4L,
                activeMode = SleepTimerMode.CHAPTER_END,
            ),
        )
    }
}
