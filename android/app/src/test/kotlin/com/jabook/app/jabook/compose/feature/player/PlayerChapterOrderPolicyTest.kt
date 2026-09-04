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

import com.jabook.app.jabook.compose.domain.model.Chapter
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration

@RunWith(RobolectricTestRunner::class)
class PlayerChapterOrderPolicyTest {
    @Test
    fun `chapters preserve their stored order for playback`() {
        val chapters =
            listOf(
                chapter(id = "10", path = "/book/10.mp3", index = 2),
                chapter(id = "2", path = "/book/2.mp3", index = 1),
                chapter(id = "1", path = "/book/01.mp3", index = 0),
                chapter(id = "missing", path = null, index = 3),
            )

        assertEquals(listOf("1", "2", "10"), sortChaptersForPlayback(chapters).map(Chapter::id))
    }

    @Test
    fun `blank fileUrl chapters are excluded`() {
        val chapters =
            listOf(
                chapter(id = "1", path = "/book/1.mp3", index = 0),
                chapter(id = "2", path = "  ", index = 1),
                chapter(id = "3", path = "/book/3.mp3", index = 2),
            )

        val result = sortChaptersForPlayback(chapters)
        assertEquals(2, result.size)
        assertEquals(listOf("1", "3"), result.map(Chapter::id))
    }

    @Test
    fun `null fileUrl chapters are excluded`() {
        val chapters =
            listOf(
                chapter(id = "1", path = null, index = 0),
                chapter(id = "2", path = "/book/2.mp3", index = 1),
            )

        val result = sortChaptersForPlayback(chapters)
        assertEquals(1, result.size)
        assertEquals("2", result[0].id)
    }

    @Test
    fun `numeric filename ordering sorts by chapterIndex`() {
        val chapters =
            listOf(
                chapter(id = "c3", path = "/book/03.mp3", index = 2),
                chapter(id = "c1", path = "/book/01.mp3", index = 0),
                chapter(id = "c2", path = "/book/02.mp3", index = 1),
            )

        val result = sortChaptersForPlayback(chapters)
        assertEquals(listOf("c1", "c2", "c3"), result.map(Chapter::id))
    }

    @Test
    fun `duplicate file paths retain original order`() {
        val chapters =
            listOf(
                chapter(id = "a", path = "/book/same.mp3", index = 0),
                chapter(id = "b", path = "/book/same.mp3", index = 1),
                chapter(id = "c", path = "/book/other.mp3", index = 2),
            )

        val result = sortChaptersForPlayback(chapters)
        assertEquals(3, result.size)
        // sortedBy is stable — duplicates keep original insertion order
        assertEquals("a", result[0].id)
        assertEquals("b", result[1].id)
        assertEquals("c", result[2].id)
    }

    @Test
    fun `empty list returns empty`() {
        assertEquals(emptyList<Chapter>(), sortChaptersForPlayback(emptyList()))
    }

    @Test
    fun `chapters with null fileUrl are excluded`() {
        val chapters =
            listOf(
                chapter(id = "1", path = "/book/1.mp3", index = 0),
                chapter(id = "missing", path = null, index = 1),
                chapter(id = "3", path = "/book/3.mp3", index = 2),
            )

        assertEquals(listOf("1", "3"), sortChaptersForPlayback(chapters).map(Chapter::id))
    }

    @Test
    fun `chapters with blank fileUrl are excluded`() {
        val chapters =
            listOf(
                chapter(id = "1", path = "/book/1.mp3", index = 0),
                chapter(id = "empty", path = "", index = 1),
                chapter(id = "whitespace", path = "   ", index = 2),
                chapter(id = "4", path = "/book/4.mp3", index = 3),
            )

        assertEquals(listOf("1", "4"), sortChaptersForPlayback(chapters).map(Chapter::id))
    }

    @Test
    fun `all chapters filtered returns empty`() {
        val chapters =
            listOf(
                chapter(id = "1", path = null, index = 0),
                chapter(id = "2", path = "", index = 1),
            )

        assertEquals(emptyList<Chapter>(), sortChaptersForPlayback(chapters))
    }

    @Test
    fun `duplicate chapterIndex keeps both sorted by index`() {
        val chapters =
            listOf(
                chapter(id = "b", path = "/b.mp3", index = 0),
                chapter(id = "a", path = "/a.mp3", index = 0),
                chapter(id = "c", path = "/c.mp3", index = 1),
            )

        val result = sortChaptersForPlayback(chapters)
        assertEquals(3, result.size)
        assertEquals(0, result[0].chapterIndex)
        assertEquals(0, result[1].chapterIndex)
        assertEquals(1, result[2].chapterIndex)
    }

    @Test
    fun `single chapter returns as-is`() {
        val chapters = listOf(chapter(id = "only", path = "/only.mp3", index = 0))

        assertEquals(listOf("only"), sortChaptersForPlayback(chapters).map(Chapter::id))
    }

    private fun chapter(
        id: String,
        path: String?,
        index: Int,
    ): Chapter =
        Chapter(
            id = id,
            bookId = "book",
            title = id,
            chapterIndex = index,
            fileIndex = index,
            duration = Duration.ZERO,
            fileUrl = path,
            position = Duration.ZERO,
            isCompleted = false,
            isDownloaded = true,
        )
}
