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

package com.jabook.app.jabook.audio.processors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [EqualizerPresetResolver] policy logic.
 *
 * TASK-VERM-11: Per-book EQ override
 */
class EqualizerPresetResolverTest {
    private val resolver =
        EqualizerPresetResolver(
            getGlobalPreset = { EqualizerPreset.VOICE_CLARITY },
            getBookOverride = { bookId -> overrides[bookId] },
        )

    private val overrides = mutableMapOf<String, String?>()

    @Test
    fun `returns global preset when no override set`() {
        overrides.clear()
        assertEquals(EqualizerPreset.VOICE_CLARITY, resolver.resolve("unknown-book"))
    }

    @Test
    fun `returns book override when set`() {
        overrides["book-1"] = "NIGHT"
        assertEquals(EqualizerPreset.NIGHT, resolver.resolve("book-1"))
    }

    @Test
    fun `returns FLAT override when explicitly set`() {
        overrides["book-2"] = "FLAT"
        assertEquals(EqualizerPreset.FLAT, resolver.resolve("book-2"))
    }

    @Test
    fun `falls back to global for unknown override name`() {
        overrides["book-3"] = "NONEXISTENT_PRESET"
        assertEquals(EqualizerPreset.VOICE_CLARITY, resolver.resolve("book-3"))
    }

    @Test
    fun `hasOverride returns true when override exists`() {
        overrides["book-1"] = "NIGHT"
        assertTrue(resolver.hasOverride("book-1"))
    }

    @Test
    fun `hasOverride returns false when no override`() {
        overrides.clear()
        assertFalse(resolver.hasOverride("unknown-book"))
    }

    @Test
    fun `USE_GLOBAL sentinel is defined`() {
        assertEquals("USE_GLOBAL", EqualizerPresetResolver.USE_GLOBAL)
    }

    @Test
    fun `resolves different presets for different books`() {
        overrides["book-a"] = "NIGHT"
        overrides["book-b"] = "FLAT"
        assertEquals(EqualizerPreset.NIGHT, resolver.resolve("book-a"))
        assertEquals(EqualizerPreset.FLAT, resolver.resolve("book-b"))
    }

    @Test
    fun `global preset used for null override`() {
        overrides["book-x"] = null
        assertEquals(EqualizerPreset.VOICE_CLARITY, resolver.resolve("book-x"))
    }
}
