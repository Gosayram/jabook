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
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkClusterizerTest {
    // --- Empty list ---

    @Test
    fun `empty bookmarks returns empty clusters`() {
        val clusters = BookmarkClusterizer.clusterize(emptyList())
        assertTrue(clusters.isEmpty())
    }

    // --- Single bookmark ---

    @Test
    fun `single bookmark creates one cluster`() {
        val bookmark = bookmark(0, 10_000L)
        val clusters = BookmarkClusterizer.clusterize(listOf(bookmark))

        assertEquals(1, clusters.size)
        assertEquals(0, clusters[0].chapterIndex)
        assertEquals("Глава 1", clusters[0].chapterTitle)
        assertEquals(1, clusters[0].size)
    }

    // --- Multiple bookmarks same chapter ---

    @Test
    fun `bookmarks in same chapter grouped together`() {
        val bookmarks =
            listOf(
                bookmark(0, 30_000L),
                bookmark(0, 10_000L),
                bookmark(0, 20_000L),
            )
        val clusters = BookmarkClusterizer.clusterize(bookmarks)

        assertEquals(1, clusters.size)
        assertEquals(3, clusters[0].size)
        assertEquals(10_000L, clusters[0].firstPositionMs)
        assertEquals(30_000L, clusters[0].lastPositionMs)
    }

    // --- Multiple chapters ---

    @Test
    fun `bookmarks in different chapters create separate clusters`() {
        val bookmarks =
            listOf(
                bookmark(0, 10_000L),
                bookmark(2, 50_000L),
                bookmark(1, 30_000L),
            )
        val clusters = BookmarkClusterizer.clusterize(bookmarks)

        assertEquals(3, clusters.size)
        assertEquals(0, clusters[0].chapterIndex)
        assertEquals(1, clusters[1].chapterIndex)
        assertEquals(2, clusters[2].chapterIndex)
    }

    // --- Bookmarks sorted within clusters ---

    @Test
    fun `bookmarks sorted by position within cluster`() {
        val bookmarks =
            listOf(
                bookmark(0, 30_000L),
                bookmark(0, 10_000L),
                bookmark(0, 20_000L),
            )
        val clusters = BookmarkClusterizer.clusterize(bookmarks)

        assertEquals(10_000L, clusters[0].bookmarks[0].positionMs)
        assertEquals(20_000L, clusters[0].bookmarks[1].positionMs)
        assertEquals(30_000L, clusters[0].bookmarks[2].positionMs)
    }

    // --- filterAutoClusters ---

    @Test
    fun `filterAutoClusters keeps clusters with auto bookmarks`() {
        val bookmarks =
            listOf(
                bookmark(0, 10_000L, noteText = "Автозакладка"),
                bookmark(0, 20_000L, noteText = "Manual"),
            )
        val clusters = BookmarkClusterizer.clusterize(bookmarks)
        val filtered = BookmarkClusterizer.filterAutoClusters(clusters)

        assertEquals(1, filtered.size)
    }

    private fun bookmark(
        chapter: Int,
        positionMs: Long,
        noteText: String? = null,
    ) = com.jabook.app.jabook.compose.domain.model.BookmarkItem(
        id = "id-$chapter-$positionMs",
        bookId = "book-1",
        chapterIndex = chapter,
        positionMs = positionMs,
        noteText = noteText,
        noteAudioPath = null,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
    )
}
