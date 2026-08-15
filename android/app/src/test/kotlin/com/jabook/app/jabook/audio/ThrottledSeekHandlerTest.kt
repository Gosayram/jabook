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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for ThrottledSeekHandler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ThrottledSeekHandlerTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var handler: ThrottledSeekHandler

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        handler = ThrottledSeekHandler()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ============ Configuration Tests ============

    @Test
    fun `default throttle is 500ms`() =
        runTest(testDispatcher.scheduler) {
            assertEquals(ThrottledSeekHandler.DEFAULT_THROTTLE_MS, handler.throttleMs)
        }

    @Test
    fun `throttle can be configured`() =
        runTest(testDispatcher.scheduler) {
            handler.throttleMs = 1000L
            assertEquals(1000L, handler.throttleMs)
        }

    // ============ notifySeek Tests ============

    @Test
    fun `notifySeek sets pending state`() =
        runTest(testDispatcher.scheduler) {
            handler.notifySeek(30000L) { }

            assertTrue(handler.hasPendingSeek())
        }

    @Test
    fun `notifySeek updates last seek position`() =
        runTest(testDispatcher.scheduler) {
            handler.notifySeek(45000L) { }

            assertEquals(45000L, handler.getLastSeekPosition())
        }

    @Test
    fun `multiple seeks update last position`() =
        runTest(testDispatcher.scheduler) {
            handler.notifySeek(10000L) { }
            handler.notifySeek(20000L) { }
            handler.notifySeek(30000L) { }

            assertEquals(30000L, handler.getLastSeekPosition())
        }

    // ============ Cancel Tests ============

    @Test
    fun `cancel stops pending seek`() =
        runTest(testDispatcher.scheduler) {
            handler.notifySeek(30000L) { }
            assertTrue(handler.hasPendingSeek())

            handler.cancel()

            assertFalse(handler.hasPendingSeek())
        }

    @Test
    fun `hasPendingSeek returns false when no pending seek`() =
        runTest(testDispatcher.scheduler) {
            assertFalse(handler.hasPendingSeek())
        }

    // ============ Flush Tests ============

    @Test
    fun `flush cancels pending seek`() =
        runTest(testDispatcher.scheduler) {
            handler.notifySeek(30000L) { }

            handler.flush()

            assertFalse(handler.hasPendingSeek())
        }

    @Test
    fun `flush invokes callback with last position`() =
        runTest(testDispatcher.scheduler) {
            handler.notifySeek(50000L) { }
            var flushedPosition = 0L

            handler.flush { pos -> flushedPosition = pos }

            assertEquals(50000L, flushedPosition)
        }

    // ============ Release Tests ============

    @Test
    fun `release cleans up state`() =
        runTest(testDispatcher.scheduler) {
            handler.notifySeek(30000L) { }

            handler.release()

            assertFalse(handler.hasPendingSeek())
            assertEquals(0L, handler.getLastSeekPosition())
        }

    // ============ Edge Cases ============

    @Test
    fun `getLastSeekPosition returns 0 initially`() =
        runTest(testDispatcher.scheduler) {
            assertEquals(0L, handler.getLastSeekPosition())
        }

    @Test
    fun `flush with null callback does not crash`() =
        runTest(testDispatcher.scheduler) {
            handler.notifySeek(30000L) { }
            handler.flush(null)

            assertFalse(handler.hasPendingSeek())
        }

    @Test
    fun `cancel when no pending seek does not crash`() =
        runTest(testDispatcher.scheduler) {
            handler.cancel()
            assertFalse(handler.hasPendingSeek())
        }

    // ============ P-82: Trailing-Edge & seekToImmediate ============

    @Test
    fun `seekToImmediate executes callback immediately`() =
        runTest(testDispatcher.scheduler) {
            var seekTarget = 0L
            handler.seekToImmediate(42_000L) { pos -> seekTarget = pos }

            assertEquals(42_000L, seekTarget)
            assertFalse(handler.hasPendingSeek())
        }

    @Test
    fun `seekToImmediate clears pending throttled seek`() =
        runTest(testDispatcher.scheduler) {
            handler.notifySeek(30_000L) { }
            assertTrue(handler.hasPendingSeek())

            var seekTarget = 0L
            handler.seekToImmediate(45_000L) { pos -> seekTarget = pos }

            assertEquals(45_000L, seekTarget)
            assertFalse(handler.hasPendingSeek())
            assertEquals(0L, handler.getLastSeekPosition())
        }

    @Test
    fun `rapid notifySeek only starts one job`() =
        runTest(testDispatcher.scheduler) {
            handler.throttleMs = 500L
            handler.notifySeek(10_000L) { }
            handler.notifySeek(20_000L) { }
            handler.notifySeek(30_000L) { }

            assertEquals(30_000L, handler.getLastSeekPosition())
        }

    @Test
    fun `flush after seekToImmediate is no-op`() =
        runTest(testDispatcher.scheduler) {
            var flushCount = 0
            handler.seekToImmediate(30_000L) { }

            handler.flush { flushCount++ }

            assertEquals(0, flushCount)
        }

    @Test
    fun `cancel clears pendingPosition`() =
        runTest(testDispatcher.scheduler) {
            handler.notifySeek(50_000L) { }
            handler.cancel()

            assertEquals(0L, handler.getLastSeekPosition())
            assertFalse(handler.hasPendingSeek())
        }
}
