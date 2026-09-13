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

import com.jabook.app.jabook.compose.data.repository.IndexingPageCursorsJson
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the pure phase math of the fresh-first indexing run: the
 * backfill page budget and the persisted page-cursor JSON codec. (The full
 * crawl loop has no test seam — boundary logic is pinned separately in
 * [ForumIndexerCutoffPredicateTest].)
 */
class ForumIndexerPhaseMathTest {
    @Test
    fun `backfill budget is capped per run when depth window is set`() {
        assertEquals(10, ForumIndexer.backfillPageBudget(1))
        assertEquals(10, ForumIndexer.backfillPageBudget(7))
        assertEquals(10, ForumIndexer.backfillPageBudget(30))
    }

    @Test
    fun `explicit All keeps the full-crawl backfill budget`() {
        assertEquals(ForumIndexer.MAX_PAGES_PER_FORUM, ForumIndexer.backfillPageBudget(0))
    }

    @Test
    fun `cursor json round trips`() {
        val cursors = mapOf("574" to 12, "1036" to 0)
        assertEquals(cursors, IndexingPageCursorsJson.decode(IndexingPageCursorsJson.encode(cursors)))
    }

    @Test
    fun `empty cursor map encodes and decodes`() {
        assertEquals(emptyMap<String, Int>(), IndexingPageCursorsJson.decode(IndexingPageCursorsJson.encode(emptyMap())))
    }

    @Test
    fun `legacy garbage decodes to empty map`() {
        assertEquals(emptyMap<String, Int>(), IndexingPageCursorsJson.decode("not-json{{{"))
        assertEquals(emptyMap<String, Int>(), IndexingPageCursorsJson.decode("\"a string\""))
        assertEquals(emptyMap<String, Int>(), IndexingPageCursorsJson.decode("{\"574\": \"not-an-int\"}"))
    }

    @Test
    fun `blank cursor payload decodes to empty map`() {
        assertEquals(emptyMap<String, Int>(), IndexingPageCursorsJson.decode(null))
        assertEquals(emptyMap<String, Int>(), IndexingPageCursorsJson.decode(""))
        assertEquals(emptyMap<String, Int>(), IndexingPageCursorsJson.decode("   "))
    }
}
