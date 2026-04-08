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

package com.jabook.app.jabook.compose.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class NavigationClickGuardTest {
    @Test
    fun `run suppresses rapid repeated clicks within interval`() {
        var now = 1_000L
        val guard = NavigationClickGuard(minIntervalMs = 350L, nowMsProvider = { now })
        var invocations = 0

        guard.run { invocations++ } // accepted
        now += 100L
        guard.run { invocations++ } // suppressed
        now += 100L
        guard.run { invocations++ } // suppressed

        assertEquals(1, invocations)
    }

    @Test
    fun `run accepts click after debounce interval elapsed`() {
        var now = 2_000L
        val guard = NavigationClickGuard(minIntervalMs = 350L, nowMsProvider = { now })
        var invocations = 0

        guard.run { invocations++ } // accepted
        now += 351L
        guard.run { invocations++ } // accepted

        assertEquals(2, invocations)
    }

    @Test
    fun `run is independent per guard instance`() {
        var now = 3_000L
        val first = NavigationClickGuard(minIntervalMs = 350L, nowMsProvider = { now })
        val second = NavigationClickGuard(minIntervalMs = 350L, nowMsProvider = { now })
        var firstInvocations = 0
        var secondInvocations = 0

        first.run { firstInvocations++ } // accepted
        second.run { secondInvocations++ } // accepted
        now += 100L
        first.run { firstInvocations++ } // suppressed
        second.run { secondInvocations++ } // suppressed

        assertEquals(1, firstInvocations)
        assertEquals(1, secondInvocations)
    }

    @Test
    fun `run accepts click exactly at interval boundary`() {
        var now = 4_000L
        val guard = NavigationClickGuard(minIntervalMs = 350L, nowMsProvider = { now })
        var invocations = 0

        guard.run { invocations++ } // accepted
        now += 350L // exactly at boundary
        guard.run { invocations++ } // accepted (>= interval)

        assertEquals(2, invocations)
    }

    @Test
    fun `run handles burst of many rapid clicks accepting only first`() {
        var now = 7_000L
        val guard = NavigationClickGuard(minIntervalMs = 500L, nowMsProvider = { now })
        var invocations = 0

        repeat(20) {
            guard.run { invocations++ }
            now += 25L // well within interval
        }

        assertEquals(1, invocations)
    }

    @Test
    fun `run is thread-safe under concurrent access`() {
        val guard = NavigationClickGuard(minIntervalMs = 100L, nowMsProvider = { System.currentTimeMillis() })
        val invocations = AtomicInteger(0)
        val threadCount = 10
        val latch = CountDownLatch(threadCount)
        val threads =
            (0 until threadCount).map {
                Thread {
                    guard.run { invocations.incrementAndGet() }
                    latch.countDown()
                }
            }

        threads.forEach { it.start() }
        latch.await()

        // Only one invocation should succeed (all threads start nearly simultaneously)
        assertEquals(1, invocations.get())
    }
}
