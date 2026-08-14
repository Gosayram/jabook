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
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@org.junit.experimental.categories.Category(com.jabook.app.jabook.test.SlowTest::class)
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
        whenever(playerA.audioAttributes).thenReturn(AudioAttributes.DEFAULT)
        whenever(playerB.audioAttributes).thenReturn(AudioAttributes.DEFAULT)
        whenever(playerA.playbackParameters).thenReturn(PlaybackParameters.DEFAULT)

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
    fun `Prepare next queue keeps the requested absolute chapter index`() {
        val sources = listOf(mock<MediaSource>(), mock<MediaSource>(), mock<MediaSource>())

        crossFadePlayer.setNextMediaSources(sources, startIndex = 2)

        verify(playerB).setMediaSources(sources, 2, 0L)
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
    fun `crossfade transfers audio focus to the active player`() {
        crossFadePlayer.startCrossFade()

        testScope.advanceUntilIdle()

        verify(playerA).setAudioAttributes(AudioAttributes.DEFAULT, false)
        verify(playerB).setAudioAttributes(AudioAttributes.DEFAULT, true)
    }

    @Test
    fun `crossfade preserves playback speed on the incoming player`() {
        whenever(playerA.playbackParameters).thenReturn(PlaybackParameters(1.5f))

        crossFadePlayer.startCrossFade()

        verify(playerB).setPlaybackSpeed(1.5f)
    }

    @Test
    fun `pause cancels active crossfade without swapping players`() {
        val activeBefore = crossFadePlayer.getActivePlayer()
        var playerChanged = false
        crossFadePlayer.onPlayerChanged = { playerChanged = true }

        crossFadePlayer.startCrossFade()
        testScope.advanceTimeBy(50)
        clearInvocations(playerA, playerB)
        crossFadePlayer.pause()
        testScope.advanceUntilIdle()

        assertSame(activeBefore, crossFadePlayer.getActivePlayer())
        assertFalse(playerChanged)
        verify(playerA).volume = 1f
        verify(playerB).volume = 1f
    }

    @Test
    fun `transition state is cleared when crossfade is cancelled`() {
        crossFadePlayer.startCrossFade()

        assertTrue(crossFadePlayer.isTransitionRunning())

        crossFadePlayer.pause()

        assertFalse(crossFadePlayer.isTransitionRunning())
    }

    @Test
    fun `restart after pause remains crossfading until replacement transition completes`() {
        crossFadePlayer.startCrossFade()
        testScope.advanceTimeBy(20)
        crossFadePlayer.pause()
        crossFadePlayer.startCrossFade()

        testScope.advanceTimeBy(20)
        crossFadePlayer.startCrossFade()

        verify(playerB, times(2)).play()
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

    @Test
    fun `pause discards preload queued for the cancelled transition`() {
        val nextChapter = MediaItem.fromUri("file://next_chapter.mp3")
        val staleQueuedChapter = MediaItem.fromUri("file://stale_queued_chapter.mp3")
        crossFadePlayer.setNextTrack(nextChapter)

        crossFadePlayer.startCrossFade()
        testScope.advanceTimeBy(1)
        crossFadePlayer.setNextTrack(staleQueuedChapter)
        crossFadePlayer.pause()

        crossFadePlayer.startCrossFade()
        testScope.advanceUntilIdle()

        verify(playerA, never()).setMediaItem(staleQueuedChapter)
    }

    @Test
    fun `recreate players preserves active playback state`() {
        val mediaItem = MediaItem.fromUri("file://current.mp3")
        val replacementActive = mock<ExoPlayer>()
        val replacementNext = mock<ExoPlayer>()
        whenever(playerA.mediaItemCount).thenReturn(1)
        whenever(playerA.getMediaItemAt(0)).thenReturn(mediaItem)
        whenever(playerA.currentMediaItemIndex).thenReturn(0)
        whenever(playerA.currentPosition).thenReturn(1_234L)
        whenever(playerA.playWhenReady).thenReturn(true)
        whenever(playerA.playbackParameters).thenReturn(PlaybackParameters(1.5f))
        whenever(playerA.shuffleModeEnabled).thenReturn(true)
        whenever(playerA.repeatMode).thenReturn(2)

        crossFadePlayer.recreatePlayers(
            factory = { _, handleAudioFocus -> if (handleAudioFocus) replacementActive else replacementNext },
        )

        assertSame(replacementActive, crossFadePlayer.getActivePlayer())
        assertSame(replacementNext, crossFadePlayer.getNextPlayer())
        verify(replacementActive).setMediaItems(eq(listOf(mediaItem)), eq(0), eq(1_234L))
        verify(replacementActive).setPlaybackSpeed(1.5f)
        verify(replacementActive).playWhenReady = true
        verify(replacementActive).prepare()
        verify(playerA).release()
        verify(playerB).release()
    }

    @Test
    fun `recreate players transfers an external active player`() {
        val sourcePlayer = mock<ExoPlayer>()
        val mediaItem = MediaItem.fromUri("file://external.mp3")
        val replacementActive = mock<ExoPlayer>()
        val replacementNext = mock<ExoPlayer>()
        whenever(sourcePlayer.mediaItemCount).thenReturn(1)
        whenever(sourcePlayer.getMediaItemAt(0)).thenReturn(mediaItem)
        whenever(sourcePlayer.currentMediaItemIndex).thenReturn(0)
        whenever(sourcePlayer.currentPosition).thenReturn(456L)
        whenever(sourcePlayer.playWhenReady).thenReturn(false)
        whenever(sourcePlayer.playbackParameters).thenReturn(PlaybackParameters.DEFAULT)
        whenever(sourcePlayer.shuffleModeEnabled).thenReturn(false)
        whenever(sourcePlayer.repeatMode).thenReturn(0)

        crossFadePlayer.recreatePlayers(
            factory = { _, handleAudioFocus -> if (handleAudioFocus) replacementActive else replacementNext },
            sourcePlayer = sourcePlayer,
        )

        verify(replacementActive).setMediaItems(eq(listOf(mediaItem)), eq(0), eq(456L))
        verify(replacementActive).prepare()
        verify(sourcePlayer, never()).release()
        verify(sourcePlayer).pause()
    }

    @Test
    fun `finalizeTransitionNow completes swap synchronously and cleans up outgoing player`() {
        crossFadePlayer.startCrossFade()
        assertTrue(crossFadePlayer.isTransitionRunning())

        crossFadePlayer.finalizeTransitionNow()

        assertSame(playerB, crossFadePlayer.getActivePlayer())
        assertSame(playerA, crossFadePlayer.getNextPlayer())
        assertFalse(crossFadePlayer.isTransitionRunning())
        verify(playerA).pause()
        verify(playerA).seekTo(0L)
        verify(playerA).clearMediaItems()
        verify(playerA).setAudioAttributes(AudioAttributes.DEFAULT, false)
        verify(playerB).setAudioAttributes(AudioAttributes.DEFAULT, true)

        // The cancelled fade coroutine must not re-run cleanup or swap again.
        testScope.advanceUntilIdle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        assertSame(playerB, crossFadePlayer.getActivePlayer())
        verify(playerA, times(1)).pause()
    }

    @Test
    fun `finalizeTransitionNow is a no-op without a running transition`() {
        crossFadePlayer.finalizeTransitionNow()

        assertSame(playerA, crossFadePlayer.getActivePlayer())
        assertFalse(crossFadePlayer.isTransitionRunning())
        verify(playerA, never()).pause()
        verify(playerA, never()).clearMediaItems()
    }

    @Test
    fun `finalizeTransitionNow invokes the completion callback exactly once`() {
        var completions = 0
        crossFadePlayer.startCrossFade { completions++ }

        crossFadePlayer.finalizeTransitionNow()
        testScope.advanceUntilIdle()

        assertEquals(1, completions)
    }

    @Test
    fun `outgoing player items are cleared after the active player swap rebinds listeners`() {
        val events = mutableListOf<String>()
        crossFadePlayer.onPlayerChanged = { events.add("changed") }
        doAnswer {
            events.add("cleared")
            Unit
        }.whenever(playerA).clearMediaItems()

        crossFadePlayer.startCrossFade()
        testScope.advanceUntilIdle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        assertEquals(listOf("changed", "cleared"), events)
    }

    @Test
    fun `zero duration crossfade swaps synchronously without a fade loop`() {
        crossFadePlayer.crossFadeDurationMs = 0L
        var completions = 0

        crossFadePlayer.startCrossFade { completions++ }

        assertSame(playerB, crossFadePlayer.getActivePlayer())
        assertFalse(crossFadePlayer.isTransitionRunning())
        assertEquals(1, completions)
        testScope.advanceUntilIdle()
        assertEquals(1, completions)
    }

    @Test
    fun `crossfade uses equal-power curve with clamped minimum step count`() {
        val outVolumes = mutableListOf<Float>()
        val inVolumes = mutableListOf<Float>()
        doAnswer {
            outVolumes.add(it.getArgument(0))
            Unit
        }.whenever(playerA).volume = any()
        doAnswer {
            inVolumes.add(it.getArgument(0))
            Unit
        }.whenever(playerB).volume = any()

        crossFadePlayer.crossFadeDurationMs = 100L // 100/20=5 -> clamped to 16 steps
        crossFadePlayer.startCrossFade()
        testScope.advanceUntilIdle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Initial volume + 16 fade steps + final restore = 18 setter calls per player.
        assertEquals(18, outVolumes.size)
        assertEquals(18, inVolumes.size)
        // Equal-power signature at mid-fade: both channels ~0.707 (linear fade dips to 0.5).
        assertEquals(0.7071f, outVolumes[8], 0.001f)
        assertEquals(0.7071f, inVolumes[8], 0.001f)
        // Curve ends: full incoming gain, silent outgoing.
        assertEquals(1.0f, inVolumes[16], 0.0001f)
        assertEquals(0.0f, outVolumes[16], 0.001f)
    }

    @Test
    fun `long fade clamps step count to 200`() {
        val outVolumes = mutableListOf<Float>()
        doAnswer {
            outVolumes.add(it.getArgument(0))
            Unit
        }.whenever(playerA).volume = any()

        crossFadePlayer.crossFadeDurationMs = 6000L // 6000/20=300 -> clamped to 200
        crossFadePlayer.startCrossFade()
        testScope.advanceUntilIdle()
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // Initial volume + 200 fade steps + final restore.
        assertEquals(202, outVolumes.size)
    }

    @Test
    fun `pause during crossfade empties the fading-in player and keeps current audible`() {
        crossFadePlayer.startCrossFade()
        testScope.advanceTimeBy(10)

        crossFadePlayer.pause()

        assertSame(playerA, crossFadePlayer.getActivePlayer())
        assertFalse(crossFadePlayer.isTransitionRunning())
        verify(playerB).clearMediaItems()
        verify(playerA).pause()
        verify(playerB).pause()
    }

    @Test
    fun `pause without transition keeps the prefetched standby player loaded`() {
        crossFadePlayer.setNextTrack(MediaItem.fromUri("file://prefetched.mp3"))
        testScope.advanceUntilIdle()

        crossFadePlayer.pause()

        // Only the preload-time clear happened; pause must not drop the prefetch.
        verify(playerB, times(1)).clearMediaItems()
    }
}
