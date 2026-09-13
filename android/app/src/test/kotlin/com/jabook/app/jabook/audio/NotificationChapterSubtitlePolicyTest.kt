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
import org.junit.Test

class NotificationChapterSubtitlePolicyTest {
    @Test
    fun `resolveSubtitle returns Chapter X of Y at beginning`() {
        val subtitle =
            NotificationChapterSubtitlePolicy.resolveSubtitle(
                path = "/storage/emulated/0/Books/01_intro.mp3",
                index = 0,
                metadata = mapOf("trackTitle" to "Chapter One"),
                totalChapters = 12,
            )

        assertEquals("Chapter 1 of 12", subtitle)
    }

    @Test
    fun `resolveSubtitle returns Chapter X of Y at end`() {
        val subtitle =
            NotificationChapterSubtitlePolicy.resolveSubtitle(
                path = "/storage/emulated/0/Books/12_outro.mp3",
                index = 11,
                metadata = emptyMap(),
                totalChapters = 12,
            )

        assertEquals("Chapter 12 of 12", subtitle)
    }

    @Test
    fun `resolveSubtitle works with single track`() {
        val subtitle =
            NotificationChapterSubtitlePolicy.resolveSubtitle(
                path = "/storage/emulated/0/Books/only_one.mp3",
                index = 0,
                metadata = null,
                totalChapters = 1,
            )

        assertEquals("Chapter 1 of 1", subtitle)
    }

    @Test
    fun `resolveSubtitle ignores trackTitle metadata`() {
        val subtitle =
            NotificationChapterSubtitlePolicy.resolveSubtitle(
                path = "https://cdn.example.com/",
                index = 4,
                metadata = mapOf("trackTitle" to "Some Title"),
                totalChapters = 10,
            )

        assertEquals("Chapter 5 of 10", subtitle)
    }
}
