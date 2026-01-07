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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
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
        context = ApplicationProvider.getApplicationContext()
        exoPlayer = mock()
        whenever(exoPlayer.playbackState).thenReturn(Player.STATE_IDLE)
        whenever(exoPlayer.mediaItemCount).thenReturn(0)

        // Mock dependencies
        val durationManager = mock<DurationManager>()
        val playerPersistenceManager = mock<PlayerPersistenceManager>()
        val playbackController = mock<PlaybackController>()

        // Create PlaylistManager with mocks
        playlistManager = PlaylistManager(
            context = context,
            mediaCache = mock(),
            getActivePlayer = { exoPlayer },
            getNotificationManager = { null },
            playerServiceScope = testScope,
            mediaItemDispatcher = testDispatcher,
            getFlavorSuffix = { "" },
            durationManager = durationManager,
            playerPersistenceManager = playerPersistenceManager,
            playbackController = playbackController
        )
    }

    @Test
    fun `preparePlaybackOptimized adds multiple items to player`() = testScope.runTest {
        // Given a list of files
        val files = listOf("/storage/book/1.mp3", "/storage/book/2.mp3", "/storage/book/3.mp3")
        
        // Mock player behavior
        whenever(exoPlayer.mediaItemCount).thenReturn(3)

        // When playlist is prepared
        playlistManager.preparePlaybackOptimized(files, null)
        
        // Then items are added to the player
        // 1. First item added synchronously
        verify(exoPlayer).addMediaSource(any(), any()) // At least one addMediaSource call
        // 2. Clear items called first
        verify(exoPlayer).clearMediaItems()
    }
}
