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

package com.jabook.app.jabook.compose.data.indexing

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the rate-limit backoff math (exponential in the real retry
 * attempt, ±30% jitter, 30s cap, verbatim Retry-After), the indexing
 * Retry-After cap, and the per-forum-per-run rate-limit budget.
 */
class ForumIndexerBackoffTest {
    @Test
    fun `backoff grows by attempt not page number`() {
        // attempt = real retry counter (1-based): 1s * 2^attempt
        assertEquals(2000L, ForumIndexer.calculateAdaptiveBackoffMs(1, null, random = 0.5))
        assertEquals(4000L, ForumIndexer.calculateAdaptiveBackoffMs(2, null, random = 0.5))
        assertEquals(8000L, ForumIndexer.calculateAdaptiveBackoffMs(3, null, random = 0.5))
        assertEquals(16000L, ForumIndexer.calculateAdaptiveBackoffMs(4, null, random = 0.5))
    }

    @Test
    fun `backoff caps at 30 seconds`() {
        assertEquals(30_000L, ForumIndexer.calculateAdaptiveBackoffMs(20, null, random = 0.5))
    }

    @Test
    fun `retry-after is honored without jitter and capped at 30s`() {
        assertEquals(12_000L, ForumIndexer.calculateAdaptiveBackoffMs(1, 12_000L, random = 0.0))
        assertEquals(12_000L, ForumIndexer.calculateAdaptiveBackoffMs(1, 12_000L, random = 1.0))
        // Server asks 45s — still capped at MAX_BACKOFF_MS
        assertEquals(30_000L, ForumIndexer.calculateAdaptiveBackoffMs(1, 45_000L, random = 0.5))
    }

    @Test
    fun `jitter stays within plus minus 30 percent`() {
        // base for attempt 1 is 2000ms → ±600ms
        val min = 1400L
        val max = 2600L
        val low = ForumIndexer.calculateAdaptiveBackoffMs(1, null, random = 0.0)
        val high = ForumIndexer.calculateAdaptiveBackoffMs(1, null, random = 1.0)
        assertEquals(min, low)
        assertEquals(max, high)
    }

    @Test
    fun `indexing retry-after is capped at 8 seconds`() {
        assertEquals(8_000L, ForumIndexer.INDEXING_RETRY_AFTER_CAP_MS)
        // Server asks 45s / 30s / 12s — all coerced to 8s
        assertEquals(8_000L, ForumIndexer.indexingRetryAfterMs(45_000L))
        assertEquals(8_000L, ForumIndexer.indexingRetryAfterMs(30_000L))
        assertEquals(8_000L, ForumIndexer.indexingRetryAfterMs(12_000L))
        // Modest asks pass through unchanged; null (no header) stays null
        assertEquals(3_000L, ForumIndexer.indexingRetryAfterMs(3_000L))
        assertEquals(null, ForumIndexer.indexingRetryAfterMs(null))
    }

    @Test
    fun `rate limit budget is cumulative per forum per run`() {
        assertEquals(3, ForumIndexer.MAX_RATE_LIMIT_RETRIES_PER_FORUM_RUN)
        // 3 rate-limit responses are tolerated (backoff + retry each)...
        assertEquals(false, ForumIndexer.isRateLimitBudgetExhausted(1))
        assertEquals(false, ForumIndexer.isRateLimitBudgetExhausted(2))
        assertEquals(false, ForumIndexer.isRateLimitBudgetExhausted(3))
        // ...the 4th stops the forum (budget never resets on success)
        assertEquals(true, ForumIndexer.isRateLimitBudgetExhausted(4))
        assertEquals(true, ForumIndexer.isRateLimitBudgetExhausted(50))
    }

    @Test
    fun `page fetch retry ceiling bounds a hung page`() {
        assertEquals(1, ForumIndexer.PAGE_FETCH_MAX_RETRIES)
        assertEquals(20_000L, ForumIndexer.PAGE_FETCH_MAX_ELAPSED_MS)
    }
}
