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

package com.jabook.app.jabook.compose.feature.player

import com.jabook.app.jabook.compose.domain.model.BookmarkItem
import com.jabook.app.jabook.compose.domain.model.Chapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class BookmarkMarkerPolicyTest {
    private fun chapter(
        index: Int,
        durationMs: Long,
    ) = Chapter(
        id = "ch-$index",
        bookId = "book-1",
        title = "Chapter $index",
        chapterIndex = index,
        fileIndex = index,
        duration = durationMs.milliseconds,
        fileUrl = null,
        position = 0L.milliseconds,
        isCompleted = false,
        isDownloaded = false,
    )

    private fun bookmark(
        id: String = "bm-1",
        chapterIndex: Int = 0,
        positionMs: Long = 0L,
    ) = BookmarkItem(
        id = id,
        bookId = "book-1",
        chapterIndex = chapterIndex,
        positionMs = positionMs,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
    )

    @Test
    fun `empty bookmarks returns empty fractions`() {
        val chapters = listOf(chapter(0, 10_000L))
        val result =
            BookmarkMarkerPolicy.calculateBookmarkMarkerFractions(
                bookmarks = emptyList(),
                chapters = chapters,
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `empty chapters returns empty fractions`() {
        val bookmarks = listOf(bookmark(chapterIndex = 0, positionMs = 5_000L))
        val result =
            BookmarkMarkerPolicy.calculateBookmarkMarkerFractions(
                bookmarks = bookmarks,
                chapters = emptyList(),
            )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single bookmark in single chapter returns correct fraction`() {
        val chapters = listOf(chapter(0, 10_000L))
        val bookmarks = listOf(bookmark(chapterIndex = 0, positionMs = 5_000L))

        val result =
            BookmarkMarkerPolicy.calculateBookmarkMarkerFractions(
                bookmarks = bookmarks,
                chapters = chapters,
            )

        assertEquals(1, result.size)
        assertEquals(0.5f, result[0], 0.001f)
    }

    @Test
    fun `bookmark in second chapter accounts for chapter offset`() {
        val chapters =
            listOf(
                chapter(0, 10_000L),
                chapter(1, 10_000L),
            )
        val bookmarks = listOf(bookmark(chapterIndex = 1, positionMs = 5_000L))

        val result =
            BookmarkMarkerPolicy.calculateBookmarkMarkerFractions(
                bookmarks = bookmarks,
                chapters = chapters,
            )

        assertEquals(1, result.size)
        assertEquals(0.75f, result[0], 0.001f)
    }

    @Test
    fun `multiple bookmarks are sorted and distinct`() {
        val chapters = listOf(chapter(0, 10_000L))
        val bookmarks =
            listOf(
                bookmark(id = "bm-2", chapterIndex = 0, positionMs = 8_000L),
                bookmark(id = "bm-1", chapterIndex = 0, positionMs = 2_000L),
                bookmark(id = "bm-3", chapterIndex = 0, positionMs = 5_000L),
            )

        val result =
            BookmarkMarkerPolicy.calculateBookmarkMarkerFractions(
                bookmarks = bookmarks,
                chapters = chapters,
            )

        assertEquals(3, result.size)
        assertEquals(0.2f, result[0], 0.001f)
        assertEquals(0.5f, result[1], 0.001f)
        assertEquals(0.8f, result[2], 0.001f)
    }

    @Test
    fun `bookmark at position zero is included`() {
        val chapters = listOf(chapter(0, 10_000L))
        val bookmarks = listOf(bookmark(chapterIndex = 0, positionMs = 0L))

        val result =
            BookmarkMarkerPolicy.calculateBookmarkMarkerFractions(
                bookmarks = bookmarks,
                chapters = chapters,
            )

        assertEquals(1, result.size)
        assertEquals(0f, result[0], 0.001f)
    }

    @Test
    fun `bookmark at end of book is included`() {
        val chapters = listOf(chapter(0, 10_000L))
        val bookmarks = listOf(bookmark(chapterIndex = 0, positionMs = 10_000L))

        val result =
            BookmarkMarkerPolicy.calculateBookmarkMarkerFractions(
                bookmarks = bookmarks,
                chapters = chapters,
            )

        assertEquals(1, result.size)
        assertEquals(1f, result[0], 0.001f)
    }

    @Test
    fun `chapter index out of bounds is clamped`() {
        val chapters = listOf(chapter(0, 10_000L))
        val bookmarks = listOf(bookmark(chapterIndex = 99, positionMs = 5_000L))

        val result =
            BookmarkMarkerPolicy.calculateBookmarkMarkerFractions(
                bookmarks = bookmarks,
                chapters = chapters,
            )

        assertEquals(1, result.size)
        assertEquals(0.5f, result[0], 0.001f)
    }

    @Test
    fun `zero duration chapters return empty fractions`() {
        val chapters = listOf(chapter(0, 0L))
        val bookmarks = listOf(bookmark(chapterIndex = 0, positionMs = 5_000L))

        val result =
            BookmarkMarkerPolicy.calculateBookmarkMarkerFractions(
                bookmarks = bookmarks,
                chapters = chapters,
            )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `no duplicates when list is empty`() {
        val result =
            BookmarkMarkerPolicy.isDuplicateBookmark(
                existing = emptyList(),
                chapterIndex = 0,
                positionMs = 5_000L,
            )
        assertTrue(!result)
    }

    @Test
    fun `detects duplicate within threshold`() {
        val existing = listOf(bookmark(chapterIndex = 0, positionMs = 5_000L))
        val result =
            BookmarkMarkerPolicy.isDuplicateBookmark(
                existing = existing,
                chapterIndex = 0,
                positionMs = 8_000L,
                thresholdMs = 5000L,
            )
        assertTrue(result)
    }

    @Test
    fun `no duplicate when different chapter`() {
        val existing = listOf(bookmark(chapterIndex = 0, positionMs = 5_000L))
        val result =
            BookmarkMarkerPolicy.isDuplicateBookmark(
                existing = existing,
                chapterIndex = 1,
                positionMs = 5_000L,
                thresholdMs = 5000L,
            )
        assertTrue(!result)
    }

    @Test
    fun `no duplicate when beyond threshold`() {
        val existing = listOf(bookmark(chapterIndex = 0, positionMs = 5_000L))
        val result =
            BookmarkMarkerPolicy.isDuplicateBookmark(
                existing = existing,
                chapterIndex = 0,
                positionMs = 15_000L,
                thresholdMs = 5000L,
            )
        assertTrue(!result)
    }

    @Test
    fun `exact same position is duplicate`() {
        val existing = listOf(bookmark(chapterIndex = 0, positionMs = 5_000L))
        val result =
            BookmarkMarkerPolicy.isDuplicateBookmark(
                existing = existing,
                chapterIndex = 0,
                positionMs = 5_000L,
                thresholdMs = 5000L,
            )
        assertTrue(result)
    }
}
