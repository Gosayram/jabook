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

package com.jabook.app.jabook.audio.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistQueueOperationsTest {
    private fun chapter(index: Int): Chapter =
        Chapter(
            id = "ch-$index",
            title = "Chapter $index",
            fileIndex = index,
            duration = 1_000L,
        )

    @Test
    fun `addChapter before current shifts current index`() {
        val playlist =
            Playlist(
                bookId = "book",
                bookTitle = "Book",
                chapters = listOf(chapter(0), chapter(1), chapter(2)),
                currentIndex = 1,
            )

        val updated = playlist.addChapter(chapter(99), index = 1)

        assertEquals(4, updated.chapters.size)
        assertEquals("ch-99", updated.chapters[1].id)
        assertEquals(2, updated.currentIndex)
    }

    @Test
    fun `removeChapterAt current index keeps selection valid`() {
        val playlist =
            Playlist(
                bookId = "book",
                bookTitle = "Book",
                chapters = listOf(chapter(0), chapter(1), chapter(2)),
                currentIndex = 2,
            )

        val updated = playlist.removeChapterAt(2)

        assertEquals(2, updated.chapters.size)
        assertEquals(1, updated.currentIndex)
    }

    @Test
    fun `moveChapter updates current index when moving current track`() {
        val playlist =
            Playlist(
                bookId = "book",
                bookTitle = "Book",
                chapters = listOf(chapter(0), chapter(1), chapter(2), chapter(3)),
                currentIndex = 1,
            )

        val updated = playlist.moveChapter(fromIndex = 1, toIndex = 3)

        assertEquals("ch-1", updated.chapters[3].id)
        assertEquals(3, updated.currentIndex)
    }

    @Test
    fun `replaceChapters clamps index and supports explicit playAt`() {
        val playlist =
            Playlist(
                bookId = "book",
                bookTitle = "Book",
                chapters = listOf(chapter(0), chapter(1), chapter(2)),
                currentIndex = 2,
            )

        val implicit = playlist.replaceChapters(listOf(chapter(10)))
        val explicit = playlist.replaceChapters(listOf(chapter(10), chapter(11)), playAtIndex = 1)

        assertEquals(0, implicit.currentIndex)
        assertEquals(1, explicit.currentIndex)
    }
}
