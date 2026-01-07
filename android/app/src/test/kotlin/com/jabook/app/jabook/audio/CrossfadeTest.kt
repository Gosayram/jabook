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
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class CrossfadeTest {
    private lateinit var context: Context
    private lateinit var crossFadePlayer: CrossFadePlayer
    private lateinit var playerA: ExoPlayer
    private lateinit var playerB: ExoPlayer

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        playerA = mock()
        playerB = mock()

        // Mock factory to return our mocks
        var callCount = 0
        val factory = { _: Context ->
            callCount++
            if (callCount == 1) playerA else playerB
        }

        crossFadePlayer = CrossFadePlayer(context, factory)
        // Set short duration for testing
        crossFadePlayer.crossFadeDurationMs = 100L
    }

    @Test
    fun `Swap players after crossfade`() {
        // Given
        val activeBefore = crossFadePlayer.getActivePlayer()

        // When
        crossFadePlayer.startCrossFade()

        // Advance time to complete animation
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Then
        val activeAfter = crossFadePlayer.getActivePlayer()
        assertNotEquals("Active player should swap after crossfade", activeBefore, activeAfter)
    }

    @Test
    fun `Prepare next track sets item on idle player`() {
        // Given
        val mediaItem = MediaItem.fromUri("file://test.mp3")

        // When
        crossFadePlayer.setNextTrack(mediaItem)

        // Then (assuming playerA is active, playerB is next)
        verify(playerB).setMediaItem(mediaItem)
        verify(playerB).prepare()
    }

    @Test
    fun `onPlayerChanged callback fired after crossfade`() {
        var callbackPlayer: ExoPlayer? = null
        crossFadePlayer.onPlayerChanged = { callbackPlayer = it }

        crossFadePlayer.startCrossFade()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertNotNull(callbackPlayer)
        assertNotEquals(playerA, callbackPlayer)
    }

    @Test
    fun `Prepare next MediaSource sets source on idle player`() {
        val mediaSource = mock<MediaSource>()
        crossFadePlayer.setNextMediaSource(mediaSource)
        verify(playerB).setMediaSource(mediaSource)
        verify(playerB).prepare()
    }
}
