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

class PlaylistMetadataFieldPolicyTest {
    @Test
    fun `resolve uses primary keys when available`() {
        val fields =
            PlaylistMetadataFieldPolicy.resolve(
                mapOf(
                    "title" to "T",
                    "artist" to "A",
                    "album" to "B",
                ),
            )

        assertThat(fields.title).isEqualTo("T")
        assertThat(fields.artist).isEqualTo("A")
        assertThat(fields.album).isEqualTo("B")
    }

    @Test
    fun `resolve falls back to secondary keys`() {
        val fields =
            PlaylistMetadataFieldPolicy.resolve(
                mapOf(
                    "trackTitle" to "TT",
                    "author" to "AU",
                    "bookTitle" to "BT",
                ),
            )

        assertThat(fields.title).isEqualTo("TT")
        assertThat(fields.artist).isEqualTo("AU")
        assertThat(fields.album).isEqualTo("BT")
    }

    @Test
    fun `resolve falls back to secondary keys when primary values are blank`() {
        val fields =
            PlaylistMetadataFieldPolicy.resolve(
                mapOf(
                    "title" to "   ",
                    "trackTitle" to "TT",
                    "artist" to "",
                    "author" to "AU",
                    "album" to "  ",
                    "bookTitle" to "BT",
                ),
            )

        assertThat(fields.title).isEqualTo("TT")
        assertThat(fields.artist).isEqualTo("AU")
        assertThat(fields.album).isEqualTo("BT")
    }

    @Test
    fun `resolve trims resolved fields`() {
        val fields =
            PlaylistMetadataFieldPolicy.resolve(
                mapOf(
                    "title" to "  T  ",
                    "artist" to "  A",
                    "album" to "B  ",
                ),
            )

        assertThat(fields.title).isEqualTo("T")
        assertThat(fields.artist).isEqualTo("A")
        assertThat(fields.album).isEqualTo("B")
    }

    @Test
    fun `resolve handles null metadata`() {
        val fields = PlaylistMetadataFieldPolicy.resolve(null)

        assertThat(fields.title).isNull()
        assertThat(fields.artist).isNull()
        assertThat(fields.album).isNull()
    }
}
