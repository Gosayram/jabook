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

package com.jabook.app.jabook.compose.data.network

import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.domain.model.RutrackerSearchResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchStaleWhileRevalidatePolicyTest {
    @Test
    fun `shouldPersistResults returns true for default scope`() {
        assertTrue(SearchStaleWhileRevalidatePolicy.shouldPersistResults(null))
        assertTrue(SearchStaleWhileRevalidatePolicy.shouldPersistResults(""))
    }

    @Test
    fun `shouldPersistResults returns true for audiobooks forums`() {
        assertTrue(
            SearchStaleWhileRevalidatePolicy.shouldPersistResults(
                RutrackerApi.AUDIOBOOKS_FORUM_IDS,
            ),
        )
    }

    @Test
    fun `shouldPersistResults returns false for narrow custom scope`() {
        assertFalse(SearchStaleWhileRevalidatePolicy.shouldPersistResults("33,37"))
    }

    @Test
    fun `meaningful refresh true when stale empty and refreshed has items`() {
        assertTrue(
            SearchStaleWhileRevalidatePolicy.isMeaningfulRefresh(
                stale = emptyList(),
                refreshed = listOf(result(topicId = "1")),
            ),
        )
    }

    @Test
    fun `meaningful refresh false when stale has items and refreshed empty`() {
        assertFalse(
            SearchStaleWhileRevalidatePolicy.isMeaningfulRefresh(
                stale = listOf(result(topicId = "1")),
                refreshed = emptyList(),
            ),
        )
    }

    @Test
    fun `meaningful refresh false when topic ids order and content unchanged`() {
        val stale = listOf(result(topicId = "1"), result(topicId = "2"))
        val refreshed = listOf(result(topicId = "1"), result(topicId = "2"))

        assertFalse(SearchStaleWhileRevalidatePolicy.isMeaningfulRefresh(stale, refreshed))
    }

    @Test
    fun `meaningful refresh true when topic ids changed`() {
        val stale = listOf(result(topicId = "1"), result(topicId = "2"))
        val refreshed = listOf(result(topicId = "1"), result(topicId = "3"))

        assertTrue(SearchStaleWhileRevalidatePolicy.isMeaningfulRefresh(stale, refreshed))
    }

    private fun result(topicId: String): RutrackerSearchResult =
        RutrackerSearchResult(
            topicId = topicId,
            title = "Title $topicId",
            author = "Author",
            category = "Audiobooks",
            size = "1 GB",
            seeders = 10,
            leechers = 1,
            magnetUrl = null,
            torrentUrl = "https://example.com/$topicId",
            coverUrl = null,
            uploader = null,
        )
}
