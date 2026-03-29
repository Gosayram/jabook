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

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistUriRoutingTest {
    @Test
    fun `buildPlaybackUri keeps already qualified uri schemes`() {
        assertEquals("http", buildPlaybackUri("http://example.com/book.mp3").scheme)
        assertEquals("https", buildPlaybackUri("https://example.com/book.mp3").scheme)
        assertEquals("content", buildPlaybackUri("content://media/external/audio/media/1").scheme)
        assertEquals("file", buildPlaybackUri("file:///storage/emulated/0/Music/book.mp3").scheme)
    }

    @Test
    fun `buildPlaybackUri converts plain path to file uri`() {
        val uri = buildPlaybackUri("/storage/emulated/0/Audiobooks/book.mp3")

        assertEquals("file", uri.scheme)
        assertEquals("/storage/emulated/0/Audiobooks/book.mp3", uri.path)
    }

    @Test
    fun `resolveMediaDataSourceRoute selects expected route`() {
        assertEquals(
            MediaDataSourceRoute.NETWORK_CACHED,
            resolveMediaDataSourceRoute(Uri.parse("https://example.com/book.mp3")),
        )
        assertEquals(
            MediaDataSourceRoute.LOCAL_FILE,
            resolveMediaDataSourceRoute(Uri.parse("file:///storage/emulated/0/book.mp3")),
        )
        assertEquals(
            MediaDataSourceRoute.LOCAL_CONTENT,
            resolveMediaDataSourceRoute(Uri.parse("content://media/external/audio/media/1")),
        )
        assertEquals(
            MediaDataSourceRoute.DEFAULT,
            resolveMediaDataSourceRoute(Uri.parse("ftp://example.com/book.mp3")),
        )
    }
}
