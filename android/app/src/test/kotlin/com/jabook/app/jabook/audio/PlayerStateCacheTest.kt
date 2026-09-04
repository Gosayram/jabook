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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerStateCacheTest {
    private lateinit var cache: PlayerStateCache

    @Before
    fun setUp() {
        cache = PlayerStateCache()
    }

    // --- Initial state ---

    @Test
    fun `initially empty`() {
        assertNull(cache.read())
        assertFalse(cache.hasCachedState())
    }

    // --- Update and read ---

    @Test
    fun `update stores state`() {
        val state = createState()
        cache.update(state)
        assertEquals(state, cache.read())
        assertTrue(cache.hasCachedState())
    }

    // --- Clear ---

    @Test
    fun `clear removes state`() {
        cache.update(createState())
        cache.clear()
        assertNull(cache.read())
        assertFalse(cache.hasCachedState())
    }

    // --- Overwrite ---

    @Test
    fun `update overwrites previous state`() {
        cache.update(createState(currentIndex = 0))
        cache.update(createState(currentIndex = 5))
        assertEquals(5, cache.read()?.currentIndex)
    }

    // --- SavedPlaybackState ---

    @Test
    fun `SavedPlaybackState has correct fields`() {
        val state = createState(currentIndex = 3, currentPosition = 45_000L, isPlaying = true)
        assertEquals(3, state.currentIndex)
        assertEquals(45_000L, state.currentPosition)
        assertTrue(state.isPlaying)
    }

    private fun createState(
        currentIndex: Int = 0,
        currentPosition: Long = 0L,
        isPlaying: Boolean = false,
    ) = SavedPlaybackState(
        currentIndex = currentIndex,
        currentPosition = currentPosition,
        isPlaying = isPlaying,
    )
}
