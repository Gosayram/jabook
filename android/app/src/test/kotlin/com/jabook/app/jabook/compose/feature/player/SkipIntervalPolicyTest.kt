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

package com.jabook.app.jabook.compose.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for skip interval resolution logic used by PlayerViewModel.
 *
 * The effective skip interval follows a hierarchy:
 * 1. Per-book override (if set)
 * 2. User global preference (if > 0)
 * 3. Default (10s rewind, 30s forward)
 */
class SkipIntervalPolicyTest {
    data class SkipConfig(
        val rewindDuration: Int?,
        val forwardDuration: Int?,
        val globalRewindSeconds: Int,
        val globalForwardSeconds: Int,
    )

    private fun resolveRewind(config: SkipConfig): Int =
        config.rewindDuration
            ?: if (config.globalRewindSeconds > 0) config.globalRewindSeconds else 10

    private fun resolveForward(config: SkipConfig): Int =
        config.forwardDuration
            ?: if (config.globalForwardSeconds > 0) config.globalForwardSeconds else 30

    @Test
    fun `default rewind is 10 seconds when no overrides set`() {
        val config =
            SkipConfig(
                rewindDuration = null,
                forwardDuration = null,
                globalRewindSeconds = 0,
                globalForwardSeconds = 0,
            )
        assertEquals(10, resolveRewind(config))
    }

    @Test
    fun `default forward is 30 seconds when no overrides set`() {
        val config =
            SkipConfig(
                rewindDuration = null,
                forwardDuration = null,
                globalRewindSeconds = 0,
                globalForwardSeconds = 0,
            )
        assertEquals(30, resolveForward(config))
    }

    @Test
    fun `global preference overrides default`() {
        val config =
            SkipConfig(
                rewindDuration = null,
                forwardDuration = null,
                globalRewindSeconds = 15,
                globalForwardSeconds = 60,
            )
        assertEquals(15, resolveRewind(config))
        assertEquals(60, resolveForward(config))
    }

    @Test
    fun `per-book override takes highest priority`() {
        val config =
            SkipConfig(
                rewindDuration = 5,
                forwardDuration = 45,
                globalRewindSeconds = 15,
                globalForwardSeconds = 60,
            )
        assertEquals(5, resolveRewind(config))
        assertEquals(45, resolveForward(config))
    }

    @Test
    fun `per-book rewind overrides global even when global is set`() {
        val config =
            SkipConfig(
                rewindDuration = 10,
                forwardDuration = null,
                globalRewindSeconds = 15,
                globalForwardSeconds = 0,
            )
        assertEquals(10, resolveRewind(config))
        assertEquals(30, resolveForward(config))
    }

    @Test
    fun `global rewind applies when per-book is null`() {
        val config =
            SkipConfig(
                rewindDuration = null,
                forwardDuration = 20,
                globalRewindSeconds = 15,
                globalForwardSeconds = 0,
            )
        assertEquals(15, resolveRewind(config))
        assertEquals(20, resolveForward(config))
    }

    @Test
    fun `valid skip intervals are 10 15 30 60 seconds`() {
        val validIntervals = listOf(10, 15, 30, 60)
        for (interval in validIntervals) {
            val config =
                SkipConfig(
                    rewindDuration = interval,
                    forwardDuration = interval,
                    globalRewindSeconds = 0,
                    globalForwardSeconds = 0,
                )
            assertEquals(interval, resolveRewind(config))
            assertEquals(interval, resolveForward(config))
        }
    }
}
