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
class ContextualResumeManagerTest {
    private lateinit var manager: ContextualResumeManager

    @Before
    fun setUp() {
        manager = ContextualResumeManager()
    }

    // --- Short pause ---

    @Test
    fun `short pause has no rewind`() {
        val ctx = manager.buildResumeContext(2 * 60 * 1000L) // 2 minutes
        assertEquals(0L, ctx.rewindMs)
        assertFalse(ctx.shouldShowRecap)
    }

    // --- Medium pause ---

    @Test
    fun `medium pause has rewind`() {
        val ctx = manager.buildResumeContext(30 * 60 * 1000L) // 30 minutes
        assertTrue(ctx.rewindMs > 0)
        assertFalse(ctx.shouldShowRecap)
    }

    // --- Long pause ---

    @Test
    fun `long pause has larger rewind`() {
        val ctx = manager.buildResumeContext(12 * 60 * 60 * 1000L) // 12 hours
        assertTrue(ctx.rewindMs >= ContextualResumeManager.MEDIUM_REWIND_MS)
        assertFalse(ctx.shouldShowRecap)
    }

    // --- Very long pause ---

    @Test
    fun `very long pause shows recap`() {
        val ctx = manager.buildResumeContext(48 * 60 * 60 * 1000L) // 48 hours
        assertEquals(ContextualResumeManager.VERY_LONG_REWIND_MS, ctx.rewindMs)
        assertTrue(ctx.shouldShowRecap)
        assertEquals(ContextualResumeManager.RECAP_DURATION_MS, ctx.recapDurationMs)
    }

    // --- isLongPause ---

    @Test
    fun `isLongPause returns false for short pause`() {
        assertFalse(manager.isLongPause(2 * 60 * 1000L))
    }

    @Test
    fun `isLongPause returns true for long pause`() {
        assertTrue(manager.isLongPause(10 * 60 * 1000L))
    }

    // --- Constants ---

    @Test
    fun `short rewind is 10 seconds`() {
        assertEquals(10_000L, ContextualResumeManager.SHORT_REWIND_MS)
    }

    @Test
    fun `very long rewind is 5 minutes`() {
        assertEquals(300_000L, ContextualResumeManager.VERY_LONG_REWIND_MS)
    }

    @Test
    fun `recap duration is 2 minutes`() {
        assertEquals(120_000L, ContextualResumeManager.RECAP_DURATION_MS)
    }
}
