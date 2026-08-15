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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SuspendableCountDownTimerTest {
    private lateinit var ticks: MutableList<Long>
    private lateinit var finishes: MutableList<Unit>

    @Before
    fun setUp() {
        ticks = mutableListOf()
        finishes = mutableListOf()
    }

    private fun createTimer(
        totalMillis: Long,
        intervalMillis: Long = 500L,
    ): SuspendableCountDownTimer =
        SuspendableCountDownTimer(
            totalMillis = totalMillis,
            intervalMillis = intervalMillis,
            onTickSeconds = { ticks.add(it) },
            onFinished = { finishes.add(Unit) },
        )

    // --- getRemainingMillis returns total initially ---

    @Test
    fun `getRemainingMillis returns total before start`() {
        val timer = createTimer(10_000L)
        assertEquals(10_000L, timer.getRemainingMillis())
    }

    // --- pause returns remaining ---

    @Test
    fun `pause returns remaining millis`() {
        val timer = createTimer(10_000L)
        val remaining = timer.pause()
        assertEquals(10_000L, remaining)
    }

    // --- cancel clears state ---

    @Test
    fun `cancel does not throw on unstarted timer`() {
        val timer = createTimer(10_000L)
        timer.cancel()
    }

    // --- resume creates new timer ---

    @Test
    fun `resume returns new timer instance`() {
        val timer = createTimer(10_000L)
        val resumed = timer.resume()
        resumed.cancel()
    }

    // --- start is idempotent ---

    @Test
    fun `double start does not create duplicate jobs`() {
        val timer = createTimer(10_000L, intervalMillis = 10_000L)
        timer.start()
        timer.start()
        timer.cancel()
    }

    // --- onFinished callback ---

    @Test
    fun `timer finishes with zero duration`() {
        val timer = createTimer(0L)
        timer.start()

        waitForFinishes(1)
        assertEquals(1, finishes.size)
    }

    // --- restart on the reused scope ---

    @Test
    fun `timer restarts on the reused scope after pause`() {
        val timer = createTimer(0L)
        timer.start()
        waitForFinishes(1)
        timer.pause()
        timer.start()
        waitForFinishes(2)
        assertEquals(2, finishes.size)
        timer.cancel()
    }

    private fun waitForFinishes(expected: Int, timeoutMs: Long = 10_000L) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (finishes.size < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
        }
        assertTrue(
            "Expected $expected finishes but got ${finishes.size}",
            finishes.size >= expected,
        )
    }
}
