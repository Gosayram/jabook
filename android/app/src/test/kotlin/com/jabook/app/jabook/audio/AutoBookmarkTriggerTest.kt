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

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class AutoBookmarkTriggerTest {
    private lateinit var repository: com.jabook.app.jabook.compose.data.repository.BookmarkRepository
    private lateinit var trigger: AutoBookmarkTrigger

    @Before
    fun setUp() {
        repository = mock()
        trigger = AutoBookmarkTrigger(repository)
    }

    // --- shouldAutoBookmark ---

    @Test
    fun `shouldAutoBookmark returns true for all reasons except MANUAL`() {
        assertTrue(trigger.shouldAutoBookmark(AutoBookmarkTrigger.AutoBookmarkReason.CHAPTER_START))
        assertTrue(trigger.shouldAutoBookmark(AutoBookmarkTrigger.AutoBookmarkReason.SLEEP_TIMER_STOP))
        assertTrue(trigger.shouldAutoBookmark(AutoBookmarkTrigger.AutoBookmarkReason.PHONE_CALL_INTERRUPTED))
        assertTrue(trigger.shouldAutoBookmark(AutoBookmarkTrigger.AutoBookmarkReason.HEADPHONES_REMOVED))
        assertTrue(trigger.shouldAutoBookmark(AutoBookmarkTrigger.AutoBookmarkReason.LONG_PAUSE_RESUME))
        assertFalse(trigger.shouldAutoBookmark(AutoBookmarkTrigger.AutoBookmarkReason.MANUAL))
    }

    // --- clearCache ---

    @Test
    fun `clearCache does not throw`() {
        trigger.clearCache()
    }

    // --- deduplication window constant ---

    @Test
    fun `deduplication window is 30 seconds`() {
        assertEquals(30_000L, AutoBookmarkTrigger.DEDUPLICATE_WINDOW_MS)
    }

    // --- AutoBookmarkReason enum ---

    @Test
    fun `all auto bookmark reasons exist`() {
        val reasons = AutoBookmarkTrigger.AutoBookmarkReason.entries
        assertEquals(6, reasons.size)
    }

    // --- createAutoBookmark dedup window ---

    @Test
    fun `createAutoBookmark deduplicates within 30s window`() =
        runBlocking {
            assertTrue(trigger.createAutoBookmark("b", 1_000L, 0, AutoBookmarkTrigger.AutoBookmarkReason.SLEEP_TIMER_STOP))
            assertFalse(trigger.createAutoBookmark("b", 10_000L, 0, AutoBookmarkTrigger.AutoBookmarkReason.SLEEP_TIMER_STOP))
            assertTrue(trigger.createAutoBookmark("b", 40_000L, 0, AutoBookmarkTrigger.AutoBookmarkReason.SLEEP_TIMER_STOP))
        }

    // --- bounded dedup cache ---

    @Test
    fun `recent bookmark cache evicts oldest entries beyond cap`() =
        runBlocking {
            repeat(AutoBookmarkTrigger.MAX_RECENT_BOOKMARKS + 1) { i ->
                trigger.createAutoBookmark("book-$i", 0L, 0, AutoBookmarkTrigger.AutoBookmarkReason.CHAPTER_START)
            }

            // Oldest key evicted → bookmark is created again instead of deduplicated
            assertTrue(trigger.createAutoBookmark("book-0", 0L, 0, AutoBookmarkTrigger.AutoBookmarkReason.CHAPTER_START))

            // Newest key still deduplicated
            assertFalse(
                trigger.createAutoBookmark(
                    "book-${AutoBookmarkTrigger.MAX_RECENT_BOOKMARKS}",
                    0L,
                    0,
                    AutoBookmarkTrigger.AutoBookmarkReason.CHAPTER_START,
                ),
            )
        }
}
