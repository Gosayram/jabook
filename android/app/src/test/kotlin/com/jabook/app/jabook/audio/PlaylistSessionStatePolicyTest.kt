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

class PlaylistSessionStatePolicyTest {
    @Test
    fun `buildSnapshot sorts paths and normalizes nullable index`() {
        val snapshot =
            PlaylistSessionStatePolicy.buildSnapshot(
                filePaths = listOf("/b/10.mp3", "/b/2.mp3", "/b/01.mp3"),
                initialTrackIndex = null,
            )

        assertEquals(listOf("/b/01.mp3", "/b/2.mp3", "/b/10.mp3"), snapshot.sortedFilePaths)
        assertEquals(0, snapshot.normalizedTrackIndex)
    }

    @Test
    fun `buildSnapshot clamps out-of-range initial index`() {
        val low =
            PlaylistSessionStatePolicy.buildSnapshot(
                filePaths = listOf("/b/1.mp3", "/b/2.mp3"),
                initialTrackIndex = -4,
            )
        val high =
            PlaylistSessionStatePolicy.buildSnapshot(
                filePaths = listOf("/b/1.mp3", "/b/2.mp3"),
                initialTrackIndex = 40,
            )

        assertEquals(0, low.normalizedTrackIndex)
        assertEquals(1, high.normalizedTrackIndex)
    }

    @Test
    fun `buildSnapshot returns zero index for empty playlist`() {
        val snapshot =
            PlaylistSessionStatePolicy.buildSnapshot(
                filePaths = emptyList(),
                initialTrackIndex = 5,
            )

        assertEquals(emptyList<String>(), snapshot.sortedFilePaths)
        assertEquals(0, snapshot.normalizedTrackIndex)
    }
}
