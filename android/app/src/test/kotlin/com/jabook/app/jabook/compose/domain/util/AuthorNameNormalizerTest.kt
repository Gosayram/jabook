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

package com.jabook.app.jabook.compose.domain.util

import com.jabook.app.jabook.compose.domain.util.AuthorNameNormalizer.NormalizationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorNameNormalizerTest {
    //region AUTO mode — Cyrillic names

    @Test
    fun `auto inverts Last-First Cyrillic`() {
        val result = AuthorNameNormalizer.normalize("Иванов, Иван")
        assertEquals("Иван Иванов", result.normalized)
        assertTrue(result.wasInverted)
    }

    @Test
    fun `auto inverts Last-First-Patronymic Cyrillic`() {
        val result = AuthorNameNormalizer.normalize("Иванов, Иван Иванович")
        assertEquals("Иван Иванович Иванов", result.normalized)
        assertTrue(result.wasInverted)
    }

    @Test
    fun `auto inverts single-letter initial Cyrillic`() {
        val result = AuthorNameNormalizer.normalize("Петров, А.")
        assertEquals("А. Петров", result.normalized)
        assertTrue(result.wasInverted)
    }

    @Test
    fun `auto keeps plain name without comma`() {
        val result = AuthorNameNormalizer.normalize("Александр Пушкин")
        assertEquals("Александр Пушкин", result.normalized)
        assertFalse(result.wasInverted)
    }

    //endregion

    //region AUTO mode — Latin names

    @Test
    fun `auto inverts Last-First Latin`() {
        val result = AuthorNameNormalizer.normalize("Smith, John")
        assertEquals("John Smith", result.normalized)
        assertTrue(result.wasInverted)
    }

    @Test
    fun `auto inverts Last-First-Middle Latin`() {
        val result = AuthorNameNormalizer.normalize("Doe, John Michael")
        assertEquals("John Michael Doe", result.normalized)
        assertTrue(result.wasInverted)
    }

    @Test
    fun `auto keeps plain Latin name`() {
        val result = AuthorNameNormalizer.normalize("John Smith")
        assertEquals("John Smith", result.normalized)
        assertFalse(result.wasInverted)
    }

    //endregion

    //region AUTO mode — edge cases and particles

    @Test
    fun `auto does not invert particle van`() {
        val result = AuthorNameNormalizer.normalize("van, Gogh")
        assertEquals("van, Gogh", result.normalized)
        assertFalse(result.wasInverted)
    }

    @Test
    fun `auto does not invert particle de`() {
        val result = AuthorNameNormalizer.normalize("de, la Cruz")
        assertEquals("de, la Cruz", result.normalized)
        assertFalse(result.wasInverted)
    }

    @Test
    fun `auto does not invert when first part ends with period (initials)`() {
        val result = AuthorNameNormalizer.normalize("J.R.R., Tolkien")
        assertEquals("J.R.R., Tolkien", result.normalized)
        assertFalse(result.wasInverted)
    }

    @Test
    fun `auto does not invert roman numeral suffix`() {
        val result = AuthorNameNormalizer.normalize("Smith, III")
        assertEquals("Smith, III", result.normalized)
        assertFalse(result.wasInverted)
    }

    @Test
    fun `auto does not invert jr suffix`() {
        val result = AuthorNameNormalizer.normalize("Smith, Jr")
        assertEquals("Smith, Jr", result.normalized)
        assertFalse(result.wasInverted)
    }

    //endregion

    //region ALWAYS_INVERT mode

    @Test
    fun `always invert swaps any comma-separated name`() {
        val result = AuthorNameNormalizer.normalize("Smith, John", NormalizationMode.ALWAYS_INVERT)
        assertEquals("John Smith", result.normalized)
        assertTrue(result.wasInverted)
    }

    @Test
    fun `always invert swaps particle names`() {
        val result = AuthorNameNormalizer.normalize("van, Gogh", NormalizationMode.ALWAYS_INVERT)
        assertEquals("Gogh van", result.normalized)
        assertTrue(result.wasInverted)
    }

    @Test
    fun `always invert keeps no-comma name unchanged`() {
        val result = AuthorNameNormalizer.normalize("John Smith", NormalizationMode.ALWAYS_INVERT)
        assertEquals("John Smith", result.normalized)
        assertFalse(result.wasInverted)
    }

    //endregion

    //region AS_IS mode

    @Test
    fun `as is keeps comma format unchanged`() {
        val result = AuthorNameNormalizer.normalize("Иванов, Иван", NormalizationMode.AS_IS)
        assertEquals("Иванов, Иван", result.normalized)
        assertFalse(result.wasInverted)
    }

    @Test
    fun `as is keeps plain name unchanged`() {
        val result = AuthorNameNormalizer.normalize("John Smith", NormalizationMode.AS_IS)
        assertEquals("John Smith", result.normalized)
        assertFalse(result.wasInverted)
    }

    //endregion

    //region Blank / empty / whitespace

    @Test
    fun `blank name returns trimmed blank`() {
        val result = AuthorNameNormalizer.normalize("   ")
        assertEquals("", result.normalized)
        assertFalse(result.wasInverted)
    }

    @Test
    fun `empty name returns empty`() {
        val result = AuthorNameNormalizer.normalize("")
        assertEquals("", result.normalized)
        assertFalse(result.wasInverted)
    }

    @Test
    fun `trailing whitespace is trimmed`() {
        val result = AuthorNameNormalizer.normalize("  Smith, John  ")
        assertEquals("John Smith", result.normalized)
        assertTrue(result.wasInverted)
    }

    //endregion

    //region Multiple commas

    @Test
    fun `multiple commas are not inverted in auto`() {
        val result = AuthorNameNormalizer.normalize("van der Berg, Jan, Peter")
        assertEquals("van der Berg, Jan, Peter", result.normalized)
        assertFalse(result.wasInverted)
    }

    //endregion

    //region normalizeAll

    @Test
    fun `normalizeAll processes list in order`() {
        val results =
            AuthorNameNormalizer.normalizeAll(
                listOf("Иванов, Иван", "John Smith", "Петров, А."),
            )
        assertEquals(3, results.size)
        assertEquals("Иван Иванов", results[0].normalized)
        assertEquals("John Smith", results[1].normalized)
        assertEquals("А. Петров", results[2].normalized)
    }

    //endregion

    //region normalizeToString

    @Test
    fun `normalizeToString returns display string`() {
        assertEquals("Иван Иванов", AuthorNameNormalizer.normalizeToString("Иванов, Иван"))
    }

    //endregion

    //region Result metadata

    @Test
    fun `result preserves original`() {
        val result = AuthorNameNormalizer.normalize("Иванов, Иван")
        assertEquals("Иванов, Иван", result.original)
    }

    //endregion
}
