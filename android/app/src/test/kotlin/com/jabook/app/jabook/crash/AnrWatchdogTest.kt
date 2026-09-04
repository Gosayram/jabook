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

package com.jabook.app.jabook.crash

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests — [MainThreadPoster] and the stack-trace provider are
 * injected, so no Android Looper or Robolectric is needed.
 * `android.util.Log` calls inside LogUtils return default values via
 * `isReturnDefaultValues = true`.
 */
public class AnrWatchdogTest {
    /** Poster that optionally "consumes" the token, simulating a responsive or blocked main thread. */
    private class StubPoster(
        internal var consume: Boolean,
    ) : MainThreadPoster {
        var postCount: Int = 0

        override fun post(token: Runnable): Boolean {
            postCount++
            if (consume) token.run()
            return true
        }
    }

    @Test
    fun `start stop lifecycle works without throwing`() {
        val watchdog =
            AnrWatchdog(
                timeoutMs = 10L,
                gracePeriodMs = 0L,
                poster = StubPoster(consume = true),
                mainThreadStackTrace = { "at stub" },
            )
        assertFalse(watchdog.isRunning())
        watchdog.start()
        assertTrue(watchdog.isRunning())
        // Background loop must survive with a responsive main thread.
        Thread.sleep(50L)
        watchdog.stop()
        assertFalse(watchdog.isRunning())
        // Idempotent stop.
        watchdog.stop()
    }

    @Test
    fun `manual cycle detects stall and reports once per episode`() {
        val poster = StubPoster(consume = false)
        val watchdog =
            AnrWatchdog(
                timeoutMs = 10L,
                gracePeriodMs = 0L,
                poster = poster,
                mainThreadStackTrace = { "at stub" },
            )

        assertTrue(watchdog.checkOnce()) // stall detected and logged
        assertFalse(watchdog.checkOnce()) // same episode — not re-logged
    }

    @Test
    fun `manual cycle resets episode once token is consumed`() {
        val poster = StubPoster(consume = false)
        val watchdog =
            AnrWatchdog(
                timeoutMs = 10L,
                gracePeriodMs = 0L,
                poster = poster,
                mainThreadStackTrace = { "at stub" },
            )
        assertTrue(watchdog.checkOnce())

        // Main thread recovers...
        poster.consume = true
        assertFalse(watchdog.checkOnce())

        // ...so a new stall is a fresh episode and is reported again.
        poster.consume = false
        assertTrue(watchdog.checkOnce())
    }

    @Test
    fun `failed post is not treated as a stall`() {
        val watchdog =
            AnrWatchdog(
                timeoutMs = 10L,
                gracePeriodMs = 0L,
                poster = { false },
                mainThreadStackTrace = { "at stub" },
            )
        assertFalse(watchdog.checkOnce())
    }
}
