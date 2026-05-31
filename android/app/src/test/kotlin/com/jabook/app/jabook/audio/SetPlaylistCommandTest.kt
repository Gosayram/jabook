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

class SetPlaylistCommandTest {
    // --- Basic construction ---

    @Test
    fun `valid command constructs successfully`() {
        val cmd =
            SetPlaylistCommand(
                bookId = "book-1",
                filePaths = listOf("/a.m4b", "/b.m4b"),
                trackIndex = 0,
                positionMs = 45_000L,
                speed = 1.5f,
            )
        assertEquals("book-1", cmd.bookId)
        assertEquals(2, cmd.trackCount)
        assertEquals(0, cmd.trackIndex)
        assertEquals(45_000L, cmd.positionMs)
        assertEquals(1.5f, cmd.speed, 0.001f)
    }

    // --- Validation ---

    @Test(expected = IllegalArgumentException::class)
    fun `blank bookId throws`() {
        SetPlaylistCommand(bookId = " ", filePaths = listOf("/a.m4b"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty filePaths throws`() {
        SetPlaylistCommand(bookId = "book-1", filePaths = emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `trackIndex out of bounds throws`() {
        SetPlaylistCommand(bookId = "book-1", filePaths = listOf("/a.m4b"), trackIndex = 5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative positionMs throws`() {
        SetPlaylistCommand(bookId = "book-1", filePaths = listOf("/a.m4b"), positionMs = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `speed below min throws`() {
        SetPlaylistCommand(bookId = "book-1", filePaths = listOf("/a.m4b"), speed = 0.1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `speed above max throws`() {
        SetPlaylistCommand(bookId = "book-1", filePaths = listOf("/a.m4b"), speed = 5.0f)
    }

    // --- Helpers ---

    @Test
    fun `currentFilePath returns correct path`() {
        val cmd =
            SetPlaylistCommand(
                bookId = "book-1",
                filePaths = listOf("/a.m4b", "/b.m4b", "/c.m4b"),
                trackIndex = 1,
            )
        assertEquals("/b.m4b", cmd.currentFilePath)
    }

    @Test
    fun `isSingleTrack for single file`() {
        val cmd = SetPlaylistCommand(bookId = "book-1", filePaths = listOf("/a.m4b"))
        assertTrue(cmd.isSingleTrack)
    }

    // --- with helpers ---

    @Test
    fun `withTrackIndex clamps to valid range`() {
        val cmd = SetPlaylistCommand(bookId = "book-1", filePaths = listOf("/a.m4b", "/b.m4b"))
        val updated = cmd.withTrackIndex(99)
        assertEquals(1, updated.trackIndex)
    }

    @Test
    fun `withPosition clamps negative to zero`() {
        val cmd = SetPlaylistCommand(bookId = "book-1", filePaths = listOf("/a.m4b"))
        val updated = cmd.withPosition(-100)
        assertEquals(0L, updated.positionMs)
    }

    @Test
    fun `withSpeed clamps to valid range`() {
        val cmd = SetPlaylistCommand(bookId = "book-1", filePaths = listOf("/a.m4b"))
        val updated = cmd.withSpeed(10.0f)
        assertEquals(SetPlaylistCommand.MAX_SPEED, updated.speed, 0.001f)
    }

    // --- Defaults ---

    @Test
    fun `defaults are correct`() {
        assertEquals(1.0f, SetPlaylistCommand.DEFAULT_SPEED, 0.001f)
        assertEquals(0.5f, SetPlaylistCommand.MIN_SPEED, 0.001f)
        assertEquals(4.0f, SetPlaylistCommand.MAX_SPEED, 0.001f)
        assertEquals(3_000L, SetPlaylistCommand.DEFAULT_CROSSFADE_MS)
    }
}
