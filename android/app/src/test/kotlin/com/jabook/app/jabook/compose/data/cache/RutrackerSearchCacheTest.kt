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

package com.jabook.app.jabook.compose.data.cache

import com.jabook.app.jabook.compose.data.remote.model.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RutrackerSearchCacheTest {
    private val cache = RutrackerSearchCache()

    @Test
    fun `clear removes every previously cached result`() {
        cache.put("query one", null, listOf(result("1")))
        cache.put("query two", "33", listOf(result("2")))

        cache.clear()

        assertNull(cache.get("query one"))
        assertNull(cache.get("query two", "33"))
        assertEquals(0, cache.getStatistics().entriesCount)
    }

    @Test
    fun `put snapshots mutable caller list`() {
        val suppliedResults = mutableListOf(result("1"))
        cache.put("query", null, suppliedResults)
        suppliedResults.clear()

        assertEquals(listOf(result("1")), cache.get("query"))
    }

    @Test
    fun `get returns snapshot that cannot corrupt cached entry`() {
        cache.put("query", null, mutableListOf(result("1")))

        val receivedResults = cache.get("query") as MutableList<SearchResult>
        receivedResults.clear()

        assertEquals(listOf(result("1")), cache.get("query"))
    }

    private fun result(topicId: String): SearchResult =
        SearchResult(
            topicId = topicId,
            title = "Title $topicId",
            author = "Author",
            category = "Audiobooks",
            size = "1 GB",
            seeders = 1,
            leechers = 0,
            magnetUrl = null,
            torrentUrl = "https://example.com/$topicId.torrent",
        )
}
