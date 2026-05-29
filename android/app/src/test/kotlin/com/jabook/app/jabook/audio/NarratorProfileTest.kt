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
import org.junit.Test

class NarratorProfileTest {
    // --- fromName ---

    @Test
    fun `fromName creates profile with correct name`() {
        val profile = NarratorProfile.fromName("John Doe")
        assertEquals("John Doe", profile.name)
        assertEquals("John Doe".hashCode().toString(), profile.id)
    }

    // --- isProlific ---

    @Test
    fun `isProlific returns true for 5+ books`() {
        val profile = NarratorProfile(id = "1", name = "Narrator", booksCount = 5)
        assertTrue(profile.isProlific())
    }

    @Test
    fun `isProlific returns false for fewer than 5 books`() {
        val profile = NarratorProfile(id = "1", name = "Narrator", booksCount = 3)
        assertFalse(profile.isProlific())
    }

    // --- toLabel ---

    @Test
    fun `toLabel with books count`() {
        val profile = NarratorProfile(id = "1", name = "John", booksCount = 10)
        assertEquals("John (10 книг)", profile.toLabel())
    }

    @Test
    fun `toLabel without books`() {
        val profile = NarratorProfile(id = "1", name = "John", booksCount = 0)
        assertEquals("John", profile.toLabel())
    }

    // --- aggregate ---

    @Test
    fun `aggregate groups by name and counts`() {
        val names = listOf("John", "Jane", "John", "John", "Jane")
        val profiles = NarratorProfile.aggregate(names)
        assertEquals(2, profiles.size)
        assertEquals("John", profiles[0].name)
        assertEquals(3, profiles[0].booksCount)
        assertEquals("Jane", profiles[1].name)
        assertEquals(2, profiles[1].booksCount)
    }

    @Test
    fun `aggregate returns empty for empty input`() {
        val profiles = NarratorProfile.aggregate(emptyList())
        assertTrue(profiles.isEmpty())
    }
}
