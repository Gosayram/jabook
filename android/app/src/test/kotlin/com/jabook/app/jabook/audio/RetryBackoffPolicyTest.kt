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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [RetryBackoffPolicy] — exponential backoff with jitter. */
class RetryBackoffPolicyTest {

    // --- calculateDelay: basic exponential growth ---

    @Test
    fun firstRetryDelayIsApproximatelyBaseMs() {
        val policy = RetryBackoffPolicy(baseMs = 1_000L, jitterMs = 0L, maxRetries = 3)

        val delay = policy.calculateDelay(0)!!

        assertEquals(1_000L, delay)
    }

    @Test
    fun secondRetryDelayDoublesBaseMs() {
        val policy = RetryBackoffPolicy(baseMs = 1_000L, jitterMs = 0L, maxRetries = 3)

        val delay = policy.calculateDelay(1)!!

        assertEquals(2_000L, delay)
    }

    @Test
    fun thirdRetryDelayQuadruplesBaseMs() {
        val policy = RetryBackoffPolicy(baseMs = 1_000L, jitterMs = 0L, maxRetries = 3)

        val delay = policy.calculateDelay(2)!!

        assertEquals(4_000L, delay)
    }

    // --- calculateDelay: returns null when retries exhausted ---

    @Test
    fun returnsNullWhenAttemptExceedsMaxRetries() {
        val policy = RetryBackoffPolicy(maxRetries = 3)

        assertNull(policy.calculateDelay(3))
        assertNull(policy.calculateDelay(4))
        assertNull(policy.calculateDelay(100))
    }

    @Test
    fun returnsNullForNegativeAttempt() {
        val policy = RetryBackoffPolicy(maxRetries = 3)

        assertNull(policy.calculateDelay(-1))
    }

    // --- calculateDelay: jitter bounds ---

    @Test
    fun delayIncludesJitterWithinExpectedRange() {
        val policy = RetryBackoffPolicy(baseMs = 1_000L, jitterMs = 500L, maxRetries = 3)

        repeat(100) {
            val delay = policy.calculateDelay(0)!!
            // attempt 0: base=1000, jitter=[0, 500] → [1000, 1500]
            assertTrue("Delay should be >= 1000, got $delay", delay >= 1_000L)
            assertTrue("Delay should be <= 1500, got $delay", delay <= 1_500L)
        }
    }

    @Test
    fun secondRetryWithJitterStaysWithinBounds() {
        val policy = RetryBackoffPolicy(baseMs = 1_000L, jitterMs = 500L, maxRetries = 3)

        repeat(100) {
            val delay = policy.calculateDelay(1)!!
            // attempt 1: base=2000, jitter=[0, 500] → [2000, 2500]
            assertTrue("Delay should be >= 2000, got $delay", delay >= 2_000L)
            assertTrue("Delay should be <= 2500, got $delay", delay <= 2_500L)
        }
    }

    // --- calculateDelay: maxDelayMs cap ---

    @Test
    fun delayIsCappedByMaxDelayMs() {
        val policy = RetryBackoffPolicy(
            baseMs = 10_000L,
            maxRetries = 5,
            jitterMs = 0L,
            maxDelayMs = 15_000L,
        )

        // attempt 0: 10000 (ok)
        assertEquals(10_000L, policy.calculateDelay(0))
        // attempt 1: 20000 → capped to 15000
        assertEquals(15_000L, policy.calculateDelay(1))
        // attempt 2: 40000 → capped to 15000
        assertEquals(15_000L, policy.calculateDelay(2))
    }

    // --- hasRetriesLeft ---

    @Test
    fun hasRetriesLeftReturnsTrueWithinRange() {
        val policy = RetryBackoffPolicy(maxRetries = 3)

        assertTrue(policy.hasRetriesLeft(0))
        assertTrue(policy.hasRetriesLeft(1))
        assertTrue(policy.hasRetriesLeft(2))
    }

    @Test
    fun hasRetriesLeftReturnsFalseAtAndBeyondMax() {
        val policy = RetryBackoffPolicy(maxRetries = 3)

        assertFalse(policy.hasRetriesLeft(3))
        assertFalse(policy.hasRetriesLeft(4))
        assertFalse(policy.hasRetriesLeft(100))
    }

    @Test
    fun hasRetriesLeftReturnsFalseForNegativeAttempt() {
        val policy = RetryBackoffPolicy(maxRetries = 3)

        assertFalse(policy.hasRetriesLeft(-1))
    }

    // --- zero jitter edge case ---

    @Test
    fun zeroJitterProducesDeterministicDelays() {
        val policy = RetryBackoffPolicy(baseMs = 1_000L, jitterMs = 0L, maxRetries = 3)

        assertEquals(1_000L, policy.calculateDelay(0))
        assertEquals(2_000L, policy.calculateDelay(1))
        assertEquals(4_000L, policy.calculateDelay(2))
    }

    // --- single retry policy ---

    @Test
    fun singleRetryPolicyOnlyAllowsAttempt0() {
        val policy = RetryBackoffPolicy(maxRetries = 1, jitterMs = 0L)

        assertTrue(policy.hasRetriesLeft(0))
        assertEquals(RetryBackoffPolicy.DEFAULT_BASE_MS, policy.calculateDelay(0))
        assertFalse(policy.hasRetriesLeft(1))
        assertNull(policy.calculateDelay(1))
    }

    // --- default policy produces sensible delays ---

    @Test
    fun defaultPolicyProduces3RetriesWithExponentialGrowth() {
        val policy = RetryBackoffPolicy()

        repeat(50) {
            val d0 = policy.calculateDelay(0)
            val d1 = policy.calculateDelay(1)
            val d2 = policy.calculateDelay(2)

            assertNotNull("Attempt 0 should succeed", d0)
            assertNotNull("Attempt 1 should succeed", d1)
            assertNotNull("Attempt 2 should succeed", d2)

            assertTrue("Attempt 0 delay >= 1000", d0!! >= 1_000L)
            assertTrue("Attempt 0 delay <= 1500", d0 <= 1_500L)
            assertTrue("Attempt 1 delay >= 2000", d1!! >= 2_000L)
            assertTrue("Attempt 1 delay <= 2500", d1 <= 2_500L)
            assertTrue("Attempt 2 delay >= 4000", d2!! >= 4_000L)
            assertTrue("Attempt 2 delay <= 4500", d2 <= 4_500L)
        }
    }
}