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
 * attempt, ±30% jitter, 30s cap, verbatim Retry-After) and the per-page
 * rate-limit retry cap.
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
    fun `rate limit retries are capped per page`() {
        assertEquals(3, ForumIndexer.MAX_RATE_LIMIT_RETRIES_PER_PAGE)
    }
}
