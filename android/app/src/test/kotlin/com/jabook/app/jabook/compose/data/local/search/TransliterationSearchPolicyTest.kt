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

package com.jabook.app.jabook.compose.data.local.search

import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransliterationSearchPolicyTest {
    @Test
    fun `buildVariants includes cyrillic fallback for latin query`() {
        val variants = TransliterationSearchPolicy.buildVariants("tolstoy")

        assertTrue(variants.contains("tolstoy"))
        assertTrue(variants.contains("толстой"))
    }

    @Test
    fun `buildVariants includes latin fallback for cyrillic query`() {
        val variants = TransliterationSearchPolicy.buildVariants("достоевский")

        assertTrue(variants.contains("достоевский"))
        assertTrue(variants.contains("dostoevskiy"))
    }

    @Test
    fun `buildVariants returns empty list for blank query`() {
        val variants = TransliterationSearchPolicy.buildVariants("   ")
        assertEquals(emptyList<String>(), variants)
    }

    @Test
    fun `buildFtsMatchQuery builds prefix query for transliterated variants`() {
        val query = TransliterationSearchPolicy.buildFtsMatchQuery("tolstoy")
        assertTrue(query.contains("\"tolstoy*\""))
        assertTrue(query.contains("\"толстой*\""))
        assertTrue(query.contains("OR"))
    }

    @Test
    fun `buildFtsMatchQuery returns empty for blank input`() {
        assertEquals("", TransliterationSearchPolicy.buildFtsMatchQuery("  "))
    }

    @Test
    fun `property - buildVariants returns unique normalized variants`() {
        runBlocking {
            checkAll(Arb.string(minSize = 0, maxSize = 64)) { raw ->
                val variants = TransliterationSearchPolicy.buildVariants(raw)
                assertEquals(variants.distinct().size, variants.size)
                assertTrue(variants.all { it == it.trim().lowercase() })
                if (raw.trim().isBlank()) {
                    assertTrue(variants.isEmpty())
                } else {
                    assertTrue(variants.isNotEmpty())
                }
            }
        }
    }

    @Test
    fun `property - fts query is empty only when variants are empty`() {
        runBlocking {
            checkAll(Arb.string(minSize = 0, maxSize = 64)) { raw ->
                val variants = TransliterationSearchPolicy.buildVariants(raw)
                val fts = TransliterationSearchPolicy.buildFtsMatchQuery(variants)
                if (variants.isEmpty()) {
                    assertEquals("", fts)
                } else {
                    assertTrue(fts.startsWith("("))
                    assertTrue(fts.endsWith(")"))
                }
            }
        }
    }
}
