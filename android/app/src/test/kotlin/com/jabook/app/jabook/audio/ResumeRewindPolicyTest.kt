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
import org.junit.Test

class ResumeRewindPolicyTest {
    @Test
    fun `does not rewind when pause is not long enough`() {
        val rewindMs =
            ResumeRewindPolicy.resolveRewindMs(
                pauseDurationMs = 30L * 60L * 1000L,
                configuredSeconds = 10,
                mode = ResumeRewindMode.FIXED,
            )

        assertEquals(0L, rewindMs)
    }

    @Test
    fun `applies configured rewind after long pause`() {
        val rewindMs =
            ResumeRewindPolicy.resolveRewindMs(
                pauseDurationMs = 30L * 60L * 1000L + 1L,
                configuredSeconds = 30,
                mode = ResumeRewindMode.FIXED,
            )

        assertEquals(30_000L, rewindMs)
    }

    @Test
    fun `falls back to safe default when configured value is invalid`() {
        val rewindMs =
            ResumeRewindPolicy.resolveRewindMs(
                pauseDurationMs = 30L * 60L * 1000L + 1L,
                configuredSeconds = 99,
                mode = ResumeRewindMode.FIXED,
            )

        assertEquals(10_000L, rewindMs)
    }

    @Test
    fun `smart mode returns expected base ranges`() {
        assertEquals(
            0L,
            ResumeRewindPolicy.resolveRewindMs(
                pauseDurationMs = 4L * 60L * 1000L,
                configuredSeconds = 10,
                mode = ResumeRewindMode.SMART,
            ),
        )
        assertEquals(
            10_000L,
            ResumeRewindPolicy.resolveRewindMs(
                pauseDurationMs = 10L * 60L * 1000L,
                configuredSeconds = 10,
                mode = ResumeRewindMode.SMART,
            ),
        )
        assertEquals(
            20_000L,
            ResumeRewindPolicy.resolveRewindMs(
                pauseDurationMs = 60L * 60L * 1000L,
                configuredSeconds = 10,
                mode = ResumeRewindMode.SMART,
            ),
        )
        assertEquals(
            30_000L,
            ResumeRewindPolicy.resolveRewindMs(
                pauseDurationMs = 4L * 60L * 60L * 1000L,
                configuredSeconds = 10,
                mode = ResumeRewindMode.SMART,
            ),
        )
        assertEquals(
            45_000L,
            ResumeRewindPolicy.resolveRewindMs(
                pauseDurationMs = 12L * 60L * 60L * 1000L,
                configuredSeconds = 10,
                mode = ResumeRewindMode.SMART,
            ),
        )
        assertEquals(
            60_000L,
            ResumeRewindPolicy.resolveRewindMs(
                pauseDurationMs = 36L * 60L * 60L * 1000L,
                configuredSeconds = 10,
                mode = ResumeRewindMode.SMART,
            ),
        )
    }

    @Test
    fun `smart mode scales by aggressiveness with clamping`() {
        val moderate =
            ResumeRewindPolicy.resolveRewindMs(
                pauseDurationMs = 4L * 60L * 60L * 1000L, // base 30s
                configuredSeconds = 10,
                mode = ResumeRewindMode.SMART,
                aggressiveness = 1.5f,
            )
        val clampedLow =
            ResumeRewindPolicy.resolveRewindMs(
                pauseDurationMs = 4L * 60L * 60L * 1000L, // base 30s
                configuredSeconds = 10,
                mode = ResumeRewindMode.SMART,
                aggressiveness = 0.1f, // clamp to 0.5
            )
        val clampedHigh =
            ResumeRewindPolicy.resolveRewindMs(
                pauseDurationMs = 36L * 60L * 60L * 1000L, // base 60s
                configuredSeconds = 10,
                mode = ResumeRewindMode.SMART,
                aggressiveness = 10f, // clamp to 2.0
            )

        assertEquals(45_000L, moderate)
        assertEquals(15_000L, clampedLow)
        assertEquals(120_000L, clampedHigh)
    }
}
