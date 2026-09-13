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

import com.jabook.app.jabook.compose.data.remote.model.SearchResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ForumIndexer.isPageOlderThan] — the pure cutoff predicate of
 * the date-driven depth window. (The full crawl loop has no test seam, so the
 * boundary logic is pinned here.)
 */
class ForumIndexerCutoffPredicateTest {
    private fun topic(
        id: String,
        registeredAtEpochSec: Long?,
    ): SearchResult =
        SearchResult(
            topicId = id,
            title = "Title $id",
            author = "Author",
            category = "cat",
            size = "1 MB",
            seeders = 1,
            leechers = 0,
            magnetUrl = null,
            torrentUrl = "https://x/dl.php?t=$id",
            registeredAtEpochSec = registeredAtEpochSec,
        )

    @Test
    fun `mixed dates within and beyond cutoff is not older`() {
        val cutoffMs = 1_000_000L
        val topics =
            listOf(
                topic("a", registeredAtEpochSec = 1_500L), // 1_500_000ms > cutoff
                topic("b", registeredAtEpochSec = 100L), // 100_000ms < cutoff
            )
        assertFalse(ForumIndexer.isPageOlderThan(cutoffMs, topics))
    }

    @Test
    fun `all topics older than cutoff is older`() {
        val cutoffMs = 1_000_000L
        val topics =
            listOf(
                topic("a", registeredAtEpochSec = 100L),
                topic("b", registeredAtEpochSec = 200L),
            )
        assertTrue(ForumIndexer.isPageOlderThan(cutoffMs, topics))
    }

    @Test
    fun `unknown dates count as fresh so page is never older`() {
        val cutoffMs = 1_000_000L
        val topics =
            listOf(
                topic("a", registeredAtEpochSec = null),
                topic("b", registeredAtEpochSec = 100L),
            )
        assertFalse(ForumIndexer.isPageOlderThan(cutoffMs, topics))
    }

    @Test
    fun `empty page is not older`() {
        assertFalse(ForumIndexer.isPageOlderThan(1_000_000L, emptyList()))
    }
}
