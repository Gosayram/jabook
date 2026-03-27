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

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SetPlaylistCommandArgsParserTest {
    @Test
    fun `parse returns null when file paths are missing`() {
        val result = SetPlaylistCommandArgsParser.parse(Bundle())

        assertNull(result)
    }

    @Test
    fun `parse returns null when file paths become empty after sanitization`() {
        val args =
            Bundle().apply {
                putStringArray(AudioPlayerLibrarySessionCallback.ARG_FILE_PATHS, arrayOf("", "   "))
            }

        val result = SetPlaylistCommandArgsParser.parse(args)

        assertNull(result)
    }

    @Test
    fun `parse returns null for out of bounds initial track index`() {
        val args =
            Bundle().apply {
                putStringArray(AudioPlayerLibrarySessionCallback.ARG_FILE_PATHS, arrayOf("/book/ch1.mp3", "/book/ch2.mp3"))
                putInt(AudioPlayerLibrarySessionCallback.ARG_INITIAL_TRACK_INDEX, 5)
            }

        val result = SetPlaylistCommandArgsParser.parse(args)

        assertNull(result)
    }

    @Test
    fun `parse sanitizes metadata and clamps negative initial position`() {
        val metadata =
            Bundle().apply {
                putString("title", "  ")
                putString("artist", "Jules Verne")
            }
        val args =
            Bundle().apply {
                putStringArray(
                    AudioPlayerLibrarySessionCallback.ARG_FILE_PATHS,
                    arrayOf("  /book/ch1.mp3  ", "/book/ch2.mp3"),
                )
                putBundle(AudioPlayerLibrarySessionCallback.ARG_METADATA, metadata)
                putInt(AudioPlayerLibrarySessionCallback.ARG_INITIAL_TRACK_INDEX, 1)
                putLong(AudioPlayerLibrarySessionCallback.ARG_INITIAL_POSITION, -500L)
                putString(AudioPlayerLibrarySessionCallback.ARG_GROUP_PATH, "book-42")
            }

        val result = SetPlaylistCommandArgsParser.parse(args)

        assertNotNull(result)
        assertEquals(listOf("/book/ch1.mp3", "/book/ch2.mp3"), result?.filePaths)
        assertEquals(mapOf("artist" to "Jules Verne"), result?.metadata)
        assertEquals(1, result?.initialTrackIndex)
        assertEquals(0L, result?.initialPositionMs)
        assertEquals("book-42", result?.groupPath)
    }
}
