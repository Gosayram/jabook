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
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaylistManagerEdgeCaseTest {
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

        playlistManager =
            PlaylistManager(
                context = context,
                mediaCache = mock(),
                getActivePlayer = { exoPlayer },
                playerServiceScope = testScope,
                mediaItemDispatcher = testDispatcher,
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
    fun `preparePlaybackOptimized handles empty playlist safely`() =
        testScope.runTest {
            playlistManager.preparePlaybackOptimized(filePaths = emptyList(), metadata = null)
            advanceUntilIdle()

            verify(exoPlayer).clearMediaItems()
            verify(exoPlayer, never()).setMediaItems(any(), any<Int>(), any<Long>())
            verify(exoPlayer, never()).prepare()
        }

    @Test
    fun `preparePlaybackOptimized clamps initial index for single item playlist`() =
        testScope.runTest {
            val singleFile = listOf("/storage/book/single.mp3")

            playlistManager.preparePlaybackOptimized(
                filePaths = singleFile,
                metadata = null,
                initialTrackIndex = 42,
                initialPosition = 1234L,
            )
            advanceUntilIdle()

            verify(exoPlayer).setMediaItems(any(), eq(0), eq(1234L))
            verify(exoPlayer).prepare()
        }

    @Test
    fun `preparePlaybackOptimized uses async strategy for large playlist`() =
        testScope.runTest {
            val largePlaylist = (0 until 60).map { index -> "/storage/book/$index.mp3" }
            whenever(exoPlayer.currentMediaItemIndex).thenReturn(0)
            whenever(exoPlayer.currentPosition).thenReturn(0L)

            playlistManager.preparePlaybackOptimized(
                filePaths = largePlaylist,
                metadata = null,
                initialTrackIndex = null,
                initialPosition = null,
            )
            advanceUntilIdle()

            verify(exoPlayer).clearMediaItems()
            verify(exoPlayer).addMediaSource(eq(0), any())
            verify(exoPlayer).prepare()
            verify(exoPlayer, never()).setMediaItems(any(), any<Int>(), any<Long>())
        }

    @Test
    fun `setPlaylist short-circuits duplicate call when loading already in progress`() =
        testScope.runTest {
            playlistManager.isPlaylistLoading = true
            var callbackSuccess: Boolean? = null
            var callbackError: Exception? = null

            playlistManager.setPlaylist(
                filePaths = listOf("/storage/book/ch1.mp3"),
                callback = { success, error ->
                    callbackSuccess = success
                    callbackError = error
                },
            )
            advanceUntilIdle()

            assertTrue(callbackSuccess == true)
            assertNull(callbackError)
            verify(exoPlayer, never()).clearMediaItems()
            verify(exoPlayer, never()).setMediaItems(any(), any<Int>(), any<Long>())
        }
}
