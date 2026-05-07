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

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaylistTrackTitlePolicyTest {
    @Test
    fun `deriveFileName resolves name from local file path`() {
        val name = PlaylistTrackTitlePolicy.deriveFileName("/books/01-chapter.mp3", index = 0)
        assertThat(name).isEqualTo("01-chapter")
    }

    @Test
    fun `deriveFileName resolves name from url without query and extension`() {
        val name =
            PlaylistTrackTitlePolicy.deriveFileName(
                "https://cdn.example.com/audio/chapter-1.m4a?token=abc#frag",
                index = 0,
            )
        assertThat(name).isEqualTo("chapter-1")
    }

    @Test
    fun `resolveBaseTitle prioritizes provided title and falls back to derived or track label`() {
        assertThat(
            PlaylistTrackTitlePolicy.resolveBaseTitle(
                providedTitle = "Custom",
                derivedFileName = "derived",
                index = 2,
            ),
        ).isEqualTo("Custom")
        assertThat(
            PlaylistTrackTitlePolicy.resolveBaseTitle(
                providedTitle = null,
                derivedFileName = "derived",
                index = 2,
            ),
        ).isEqualTo("derived")
        assertThat(
            PlaylistTrackTitlePolicy.resolveBaseTitle(
                providedTitle = null,
                derivedFileName = "",
                index = 2,
            ),
        ).isEqualTo("Track 3")
    }
}
