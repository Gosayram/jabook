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
            )

        assertEquals(0L, rewindMs)
    }

    @Test
    fun `applies configured rewind after long pause`() {
        val rewindMs =
            ResumeRewindPolicy.resolveRewindMs(
                pauseDurationMs = 30L * 60L * 1000L + 1L,
                configuredSeconds = 30,
            )

        assertEquals(30_000L, rewindMs)
    }

    @Test
    fun `falls back to safe default when configured value is invalid`() {
        val rewindMs =
            ResumeRewindPolicy.resolveRewindMs(
                pauseDurationMs = 30L * 60L * 1000L + 1L,
                configuredSeconds = 99,
            )

        assertEquals(10_000L, rewindMs)
    }
}
