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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncQueueItemTest {
    // --- Basic construction ---

    @Test
    fun `item with defaults is pending`() {
        val item =
            SyncQueueItem(
                id = "1",
                type = SyncType.POSITION_UPDATE,
                payload = "{}",
                createdAt = System.currentTimeMillis(),
            )
        assertTrue(item.isPending)
        assertFalse(item.isExhausted)
        assertEquals(0, item.retryCount)
        assertNull(item.lastError)
    }

    // --- withRetry ---

    @Test
    fun `withRetry increments count and sets error`() {
        val item =
            SyncQueueItem(
                id = "1",
                type = SyncType.BOOKMARK_CREATE,
                payload = "{}",
                createdAt = System.currentTimeMillis(),
            )
        val retried = item.withRetry("timeout")
        assertEquals(1, retried.retryCount)
        assertEquals("timeout", retried.lastError)
        assertTrue(retried.isPending)
    }

    // --- Exhausted after max retries ---

    @Test
    fun `exhausted after max retries`() {
        var item =
            SyncQueueItem(
                id = "1",
                type = SyncType.BOOK_COMPLETED,
                payload = "{}",
                createdAt = System.currentTimeMillis(),
            )
        repeat(SyncQueueItem.MAX_RETRIES) {
            item = item.withRetry("error $it")
        }
        assertTrue(item.isExhausted)
        assertFalse(item.isPending)
    }

    // --- SyncType enum ---

    @Test
    fun `SyncType has all expected values`() {
        val types = SyncType.entries
        assertTrue(types.contains(SyncType.POSITION_UPDATE))
        assertTrue(types.contains(SyncType.BOOKMARK_CREATE))
        assertTrue(types.contains(SyncType.BOOKMARK_DELETE))
        assertTrue(types.contains(SyncType.BOOK_COMPLETED))
        assertTrue(types.contains(SyncType.SPEED_CHANGED))
        assertTrue(types.contains(SyncType.SLEEP_TIMER_CHANGED))
    }
}
