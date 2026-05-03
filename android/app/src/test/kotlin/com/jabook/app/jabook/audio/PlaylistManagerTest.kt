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

import android.content.Context
import android.net.Uri
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.jabook.app.jabook.compose.core.di.AppDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaylistManagerTest {
    private lateinit var context: Context
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var playlistManager: PlaylistManager
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        exoPlayer = mock()
        whenever(exoPlayer.playbackState).thenReturn(Player.STATE_IDLE)
        whenever(exoPlayer.mediaItemCount).thenReturn(0)
        whenever(exoPlayer.playWhenReady).thenReturn(false)
        whenever(exoPlayer.duration).thenReturn(0L)
        whenever(exoPlayer.currentPosition).thenReturn(0L)

        val testAppDispatchers =
            object : AppDispatchers {
                override val io = testDispatcher
                override val default = testDispatcher
                override val main = testDispatcher
                override val unconfined = testDispatcher
            }

        playlistManager =
            PlaylistManager(
                context = context,
                mediaCache = mock(),
                getActivePlayer = { exoPlayer },
                playerServiceScope = testScope,
                mediaItemDispatcher = testDispatcher,
                dispatchers = testAppDispatchers,
                getFlavorSuffix = { "" },
                durationManager = mock(),
                playerPersistenceManager = mock(),
                playbackController = mock(),
                getCurrentTrackIndex = { 0 },
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `buildPlaybackUri keeps absolute uri schemes and wraps local path as file uri`() {
        assertEquals("https://example.com/audio.mp3", buildPlaybackUri("https://example.com/audio.mp3").toString())
        assertEquals("http://example.com/audio.mp3", buildPlaybackUri("http://example.com/audio.mp3").toString())
        assertEquals("content://media/external/audio/1", buildPlaybackUri("content://media/external/audio/1").toString())
        assertEquals("file:///tmp/book.mp3", buildPlaybackUri("file:///tmp/book.mp3").toString())

        val local = buildPlaybackUri("/storage/emulated/0/Books/ch1.mp3")
        assertEquals("file", local.scheme)
        assertTrue(local.path?.endsWith("/storage/emulated/0/Books/ch1.mp3") == true)
    }

    @Test
    fun `resolveMediaDataSourceRoute maps schemes to expected routes`() {
        assertEquals(MediaDataSourceRoute.NETWORK_CACHED, resolveMediaDataSourceRoute(Uri.parse("https://a/b.mp3")))
        assertEquals(MediaDataSourceRoute.NETWORK_CACHED, resolveMediaDataSourceRoute(Uri.parse("http://a/b.mp3")))
        assertEquals(MediaDataSourceRoute.LOCAL_CONTENT, resolveMediaDataSourceRoute(Uri.parse("content://a/b")))
        assertEquals(MediaDataSourceRoute.LOCAL_FILE, resolveMediaDataSourceRoute(Uri.parse("file:///a/b.mp3")))
        assertEquals(MediaDataSourceRoute.DEFAULT, resolveMediaDataSourceRoute(Uri.parse("ftp://a/b.mp3")))
    }

    @Test
    fun `sortFilesByNumericPrefix orders numbered files naturally and keeps lexical fallback`() {
        val input =
            listOf(
                "/books/10_chapter.mp3",
                "/books/2_chapter.mp3",
                "/books/01_intro.mp3",
                "/books/appendix.mp3",
                "/books/A-preface.mp3",
            )

        val sorted = sortFilesByNumericPrefix(input)

        assertEquals(
            listOf(
                "/books/01_intro.mp3",
                "/books/2_chapter.mp3",
                "/books/10_chapter.mp3",
                "/books/A-preface.mp3",
                "/books/appendix.mp3",
            ),
            sorted,
        )
    }

    @Test
    fun `optimizeMemoryUsage is no-op for small playlist window`() {
        whenever(exoPlayer.mediaItemCount).thenReturn(8)

        playlistManager.optimizeMemoryUsage(currentTrackIndex = 3, keepWindow = 5)

        verify(exoPlayer, never()).removeMediaItem(any())
    }

    @Test
    fun `optimizeMemoryUsage removes distant tracks in descending index order`() =
        testScope.runTest {
            whenever(exoPlayer.mediaItemCount).thenReturn(20)
            whenever(exoPlayer.getMediaItemAt(any())).thenReturn(mock())
            playlistManager.setPlaylist(
                filePaths = (0 until 20).map { "/storage/book/$it.mp3" },
                metadata = null,
            )
            advanceUntilIdle()

            playlistManager.optimizeMemoryUsage(currentTrackIndex = 10, keepWindow = 2)

            val removedCaptor = argumentCaptor<Int>()
            // Expect removing everything outside [8..12].
            val expectedDescending = (0..7).toList().plus(13..19).sortedDescending()
            verify(exoPlayer, times(expectedDescending.size)).removeMediaItem(removedCaptor.capture())
            val allRemoved = removedCaptor.allValues
            assertEquals(expectedDescending, allRemoved)
        }
}
