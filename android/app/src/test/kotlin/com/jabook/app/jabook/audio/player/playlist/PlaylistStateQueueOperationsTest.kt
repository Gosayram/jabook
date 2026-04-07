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

package com.jabook.app.jabook.audio.player.playlist

import com.jabook.app.jabook.audio.core.model.Chapter
import com.jabook.app.jabook.audio.core.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistStateQueueOperationsTest {
    private fun chapter(index: Int): Chapter =
        Chapter(
            id = "ch-$index",
            title = "Chapter $index",
            fileIndex = index,
            duration = 1_000L,
        )

    private fun playlist(currentIndex: Int = 0): Playlist =
        Playlist(
            bookId = "book",
            bookTitle = "Book",
            chapters = listOf(chapter(0), chapter(1), chapter(2)),
            currentIndex = currentIndex,
        )

    @Test
    fun `playAt updates playlist and actual track index atomically`() {
        val state = PlaylistState()
        state.updatePlaylist(playlist(currentIndex = 0))

        val success = state.playAt(2)

        assertTrue(success)
        assertEquals(2, state.getCurrentTrackIndex())
        assertEquals(2, state.getCurrentPlaylist()?.currentIndex)
    }

    @Test
    fun `moveChapter keeps state indices in sync`() {
        val state = PlaylistState()
        state.updatePlaylist(playlist(currentIndex = 1))

        val success = state.moveChapter(fromIndex = 1, toIndex = 2)

        assertTrue(success)
        assertEquals(2, state.getCurrentTrackIndex())
        assertEquals(2, state.getCurrentPlaylist()?.currentIndex)
    }

    @Test
    fun `invalid queue operation returns false and keeps state unchanged`() {
        val state = PlaylistState()
        state.updatePlaylist(playlist(currentIndex = 1))

        val success = state.removeChapterAt(10)

        assertFalse(success)
        assertEquals(1, state.getCurrentTrackIndex())
        assertEquals(1, state.getCurrentPlaylist()?.currentIndex)
    }
}
