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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

/**
 * Verifies Gapless Playback prerequisites: multi-item playlist support.
 *
 * Note: True gapless playback is handled by ExoPlayer's internal engine and DefaultRenderersFactory.
 * This test verifies that we are correctly populating the playlist, which is the
 * application-side requirement for gapless playback.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GaplessPlaybackTest {
    private lateinit var context: Context
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var playlistManager: PlaylistManager
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        // Set test dispatcher as Main dispatcher for coroutines
        Dispatchers.setMain(testDispatcher)

        context = ApplicationProvider.getApplicationContext()
        exoPlayer = mock()
        whenever(exoPlayer.playbackState).thenReturn(Player.STATE_IDLE)
        whenever(exoPlayer.mediaItemCount).thenReturn(0)
        whenever(exoPlayer.playWhenReady).thenReturn(false)

        // Mock dependencies
        val durationManager = mock<DurationManager>()
        val playerPersistenceManager = mock<PlayerPersistenceManager>()
        val playbackController = mock<PlaybackController>()

        // Create PlaylistManager with mocks
        playlistManager =
            PlaylistManager(
                context = context,
                mediaCache = mock(),
                getActivePlayer = { exoPlayer },
                playerServiceScope = testScope,
                mediaItemDispatcher = testDispatcher,
                getFlavorSuffix = { "" },
                durationManager = durationManager,
                playerPersistenceManager = playerPersistenceManager,
                playbackController = playbackController,
                getCurrentTrackIndex = { 0 },
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `preparePlaybackOptimized adds multiple items to player`() =
        testScope.runTest {
            // Given a list of files (small playlist <50, will use synchronous loading)
            val files = listOf("/storage/book/1.mp3", "/storage/book/2.mp3", "/storage/book/3.mp3")

            // Mock player behavior for synchronous loading
            // For small playlists, setMediaItems is used instead of addMediaSource
            whenever(exoPlayer.mediaItemCount).thenReturn(0) // Start with 0, will be set by setMediaItems

            // When playlist is prepared
            playlistManager.preparePlaybackOptimized(files, null)

            // Advance all coroutines to ensure completion
            advanceUntilIdle()

            // Then items are added to the player
            // For small playlists (<50), synchronous loading uses setMediaItems
            // 1. Clear items called first
            verify(exoPlayer).clearMediaItems()
            // 2. setMediaItems is called with all items at once (synchronous loading)
            verify(exoPlayer).setMediaItems(any(), any(), any())
            // 3. prepare is called
            verify(exoPlayer).prepare()
        }
}
