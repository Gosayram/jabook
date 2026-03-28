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
    fun `resolveSubtitle prefers metadata trackTitle when present`() {
        val subtitle =
            NotificationChapterSubtitlePolicy.resolveSubtitle(
                path = "/storage/emulated/0/Books/01_intro.mp3",
                index = 0,
                metadata = mapOf("trackTitle" to "Chapter One"),
            )

        assertEquals("Chapter One", subtitle)
    }

    @Test
    fun `resolveSubtitle falls back to local filename without extension`() {
        val subtitle =
            NotificationChapterSubtitlePolicy.resolveSubtitle(
                path = "/storage/emulated/0/Books/02_middle.m4b",
                index = 1,
                metadata = emptyMap(),
            )

        assertEquals("02_middle", subtitle)
    }

    @Test
    fun `resolveSubtitle uses url path segment for remote sources`() {
        val subtitle =
            NotificationChapterSubtitlePolicy.resolveSubtitle(
                path = "https://cdn.example.com/audio/chapter_03.mp3",
                index = 2,
                metadata = null,
            )

        assertEquals("chapter_03", subtitle)
    }

    @Test
    fun `resolveSubtitle falls back to generic track title when path has no name`() {
        val subtitle =
            NotificationChapterSubtitlePolicy.resolveSubtitle(
                path = "https://cdn.example.com/",
                index = 4,
                metadata = null,
            )

        assertEquals("Track 5", subtitle)
    }
}
