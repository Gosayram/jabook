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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EventDeduplicatorTest {
    private lateinit var deduplicator: EventDeduplicator<String>

    @Before
    fun setUp() {
        deduplicator =
            EventDeduplicator(
                windowMs = 500L,
                keyExtractor = { it },
            )
    }

    // --- First event is not a duplicate ---

    @Test
    fun `first event is not duplicate`() {
        assertFalse(deduplicator.isDuplicate("click"))
    }

    // --- Same event within window is duplicate ---

    @Test
    fun `same event within window is duplicate`() {
        deduplicator.isDuplicate("click")
        assertTrue(deduplicator.isDuplicate("click"))
    }

    // --- Different events are not duplicates ---

    @Test
    fun `different events are not duplicates`() {
        assertFalse(deduplicator.isDuplicate("click"))
        assertFalse(deduplicator.isDuplicate("scroll"))
    }

    // --- clear resets state ---

    @Test
    fun `clear resets state`() {
        deduplicator.isDuplicate("click")
        deduplicator.clear()
        assertFalse(deduplicator.isDuplicate("click"))
    }

    // --- size tracks entries ---

    @Test
    fun `size tracks entries`() {
        assertEquals(0, deduplicator.size())
        deduplicator.isDuplicate("a")
        deduplicator.isDuplicate("b")
        assertEquals(2, deduplicator.size())
    }

    // --- default constants ---

    @Test
    fun `default window is 100ms`() {
        assertEquals(100L, EventDeduplicator.DEFAULT_WINDOW_MS)
    }

    @Test
    fun `default max entries is 50`() {
        assertEquals(50, EventDeduplicator.DEFAULT_MAX_ENTRIES)
    }
}
