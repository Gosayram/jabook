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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CrossfadeTest {
    private lateinit var context: Context
    private lateinit var crossFadePlayer: CrossFadePlayer
    private lateinit var playerA: ExoPlayer
    private lateinit var playerB: ExoPlayer
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        playerA = mock()
        playerB = mock()

        // Mock factory to return our mocks
        var callCount = 0
        val factory = { _: Context, handleAudioFocus: Boolean ->
            callCount++
            if (callCount == 1) playerA else playerB
        }

        crossFadePlayer = CrossFadePlayer(context, factory, testScope)
        // Set short duration for testing
        crossFadePlayer.crossFadeDurationMs = 100L
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Swap players after crossfade`() {
        // Given
        val activeBefore = crossFadePlayer.getActivePlayer()

        // When
        crossFadePlayer.startCrossFade()

        // Advance coroutine time to complete crossfade
        testScope.advanceUntilIdle()
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

        // Then (playerA is active, playerB is next)
        testScope.advanceUntilIdle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        verify(playerB).clearMediaItems()
        verify(playerB).setMediaItem(mediaItem)
        verify(playerB).prepare()
    }

    @Test
    fun `onPlayerChanged callback fired after crossfade`() {
        var callbackPlayer: ExoPlayer? = null
        crossFadePlayer.onPlayerChanged = { callbackPlayer = it }

        crossFadePlayer.startCrossFade()
        testScope.advanceUntilIdle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertNotNull(callbackPlayer)
        assertNotEquals(playerA, callbackPlayer)
    }

    @Test
    fun `Prepare next MediaSource sets source on idle player`() {
        val mediaSource = mock<MediaSource>()
        crossFadePlayer.setNextMediaSource(mediaSource)
        testScope.advanceUntilIdle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        verify(playerB).clearMediaItems()
        verify(playerB).setMediaSource(mediaSource)
        verify(playerB).prepare()
    }

    @Test
    fun `setNextTrack during crossfade queues and applies preload after swap`() {
        val queuedAfterCrossfade = MediaItem.fromUri("file://queued_after_crossfade.mp3")

        crossFadePlayer.startCrossFade()
        crossFadePlayer.setNextTrack(queuedAfterCrossfade)

        // While crossfade is active request is queued, not applied immediately.
        verify(playerA, never()).setMediaItem(queuedAfterCrossfade)

        // Advance coroutine time and Robolectric looper to complete crossfade
        testScope.advanceUntilIdle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // After swap, nextPlayer (playerA) receives queued preload.
        // After swap: currentPlayer=playerB, nextPlayer=playerA
        verify(playerA).setMediaItem(queuedAfterCrossfade)
        verify(playerA).prepare()
    }

    @Test
    fun `latest queued preload wins during crossfade`() {
        val first = MediaItem.fromUri("file://first.mp3")
        val second = MediaItem.fromUri("file://second.mp3")

        crossFadePlayer.startCrossFade()
        crossFadePlayer.setNextTrack(first)
        crossFadePlayer.setNextTrack(second)

        // Advance to complete crossfade
        testScope.advanceUntilIdle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        verify(playerA, never()).setMediaItem(first)
        verify(playerA).setMediaItem(second)
    }
}
