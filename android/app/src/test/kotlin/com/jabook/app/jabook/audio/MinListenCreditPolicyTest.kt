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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinListenCreditPolicyTest {
    @Test
    fun `floor is half of duration when below cap`() {
        assertEquals(150_000L, MinListenCreditPolicy.floorMs(300_000L))
    }

    @Test
    fun `floor is capped at 240 seconds`() {
        assertEquals(240_000L, MinListenCreditPolicy.floorMs(600_000L))
        assertEquals(240_000L, MinListenCreditPolicy.floorMs(Long.MAX_VALUE))
    }

    @Test
    fun `floor is 240 seconds when duration is unknown or non positive`() {
        assertEquals(240_000L, MinListenCreditPolicy.floorMs(0L))
        assertEquals(240_000L, MinListenCreditPolicy.floorMs(-1L))
    }

    @Test
    fun `credits when listened meets or exceeds floor`() {
        assertTrue(
            MinListenCreditPolicy.shouldCredit(
                listenedMs = 240_000L,
                durationMs = 600_000L,
                alreadyCredited = false,
            ),
        )
        assertTrue(
            MinListenCreditPolicy.shouldCredit(
                listenedMs = 300_000L,
                durationMs = 600_000L,
                alreadyCredited = false,
            ),
        )
    }

    @Test
    fun `does not credit below floor`() {
        assertFalse(
            MinListenCreditPolicy.shouldCredit(
                listenedMs = 239_999L,
                durationMs = 600_000L,
                alreadyCredited = false,
            ),
        )
    }

    @Test
    fun `does not credit when already credited`() {
        assertFalse(
            MinListenCreditPolicy.shouldCredit(
                listenedMs = 600_000L,
                durationMs = 600_000L,
                alreadyCredited = true,
            ),
        )
    }
}
