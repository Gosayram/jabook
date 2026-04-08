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
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaylistManagerEdgeCaseTest {
    private lateinit var context: Context
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var playlistManager: PlaylistManager
    private lateinit var playerPersistenceManager: PlayerPersistenceManager
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
        playerPersistenceManager = mock()

        playlistManager =
            PlaylistManager(
                context = context,
                mediaCache = mock(),
                getActivePlayer = { exoPlayer },
                playerServiceScope = testScope,
                mediaItemDispatcher = testDispatcher,
                getFlavorSuffix = { "" },
                durationManager = mock(),
                playerPersistenceManager = playerPersistenceManager,
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

    @Test
    fun `preparePlaybackOptimized falls back to polling when deferred track switch times out`() =
        testScope.runTest {
            val largePlaylist = (0 until 50).map { index -> "/storage/book/$index.mp3" }
            val targetIndex = 3
            val targetPosition = 1_234L
            var pendingDeferred: CompletableDeferred<Int>? = null

            val timeoutFallbackManager =
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
                    setPendingTrackSwitchDeferred = { deferred -> pendingDeferred = deferred },
                )

            whenever(exoPlayer.playbackState).thenReturn(Player.STATE_READY)
            whenever(exoPlayer.mediaItemCount).thenReturn(largePlaylist.size)
            whenever(exoPlayer.currentMediaItemIndex).thenReturn(0, 0, targetIndex, targetIndex)
            whenever(exoPlayer.currentPosition).thenReturn(0L)
            whenever(
                exoPlayer.getMediaItemAt(any()),
            ).thenReturn(MediaItem.fromUri("file:///storage/book/placeholder.mp3"))

            timeoutFallbackManager.preparePlaybackOptimized(
                filePaths = largePlaylist,
                metadata = null,
                initialTrackIndex = targetIndex,
                initialPosition = targetPosition,
            )

            // Trigger deferred timeout branch (5 seconds) and allow fallback polling to run.
            advanceTimeBy(5_200L)
            advanceUntilIdle()

            assertTrue(pendingDeferred != null)
            assertTrue(requireNotNull(pendingDeferred).isCancelled)
            verify(exoPlayer, times(1)).seekToDefaultPosition(eq(targetIndex))
            verify(exoPlayer).seekTo(eq(targetIndex), eq(targetPosition))
        }

    @Test
    fun `preparePlaybackOptimized retries seek when verifyPosition detects index mismatch`() =
        testScope.runTest {
            val largePlaylist = (0 until 50).map { index -> "/storage/book/$index.mp3" }
            val targetIndex = 4
            val targetPosition = 2_345L
            var indexReadCount = 0
            val indexSequence = listOf(0, targetIndex, targetIndex - 1, targetIndex)

            whenever(exoPlayer.playbackState).thenReturn(Player.STATE_READY)
            whenever(exoPlayer.mediaItemCount).thenReturn(largePlaylist.size)
            whenever(exoPlayer.currentPosition).thenReturn(0L)
            whenever(
                exoPlayer.getMediaItemAt(any()),
            ).thenReturn(MediaItem.fromUri("file:///storage/book/placeholder.mp3"))
            whenever(exoPlayer.currentMediaItemIndex).thenAnswer {
                val value =
                    if (indexReadCount < indexSequence.size) {
                        indexSequence[indexReadCount]
                    } else {
                        targetIndex
                    }
                indexReadCount++
                value
            }

            playlistManager.preparePlaybackOptimized(
                filePaths = largePlaylist,
                metadata = null,
                initialTrackIndex = targetIndex,
                initialPosition = targetPosition,
            )
            advanceUntilIdle()

            // First call is from switchToTargetTrack, second is retry in verifyPositionApplied.
            verify(exoPlayer, times(2)).seekToDefaultPosition(eq(targetIndex))
            // First call applies initial position, second call is retry.
            verify(exoPlayer, times(2)).seekTo(eq(targetIndex), eq(targetPosition))
        }

    @Test
    fun `mutateQueueAtomically syncs queue snapshot to persistence`() =
        testScope.runTest {
            whenever(exoPlayer.currentPosition).thenReturn(2_500L)
            whenever(exoPlayer.duration).thenReturn(30_000L)
            whenever(exoPlayer.mediaItemCount).thenReturn(2)
            whenever(exoPlayer.currentMediaItemIndex).thenReturn(0)
            whenever(exoPlayer.playbackState).thenReturn(Player.STATE_READY)

            playlistManager.setPlaylist(
                filePaths = listOf("/storage/book/1.mp3", "/storage/book/2.mp3"),
                metadata = mapOf("title" to "Queue Book", "artist" to "Narrator"),
                groupPath = "book://queue",
            )
            advanceUntilIdle()

            val snapshot =
                playlistManager.mutateQueueAtomically(
                    PlaylistQueueOperation.Add(path = "/storage/book/3.mp3", index = 2),
                )

            assertTrue(snapshot != null)
            assertEquals(3, snapshot?.filePaths?.size)
            assertEquals(0, snapshot?.currentIndex)

            val persistedCaptor = argumentCaptor<PlayerPersistenceManager.PersistedPlayerState>()
            verify(playerPersistenceManager).savePersistedPlayerState(persistedCaptor.capture())

            val persisted = persistedCaptor.firstValue
            assertEquals("book://queue", persisted.groupPath)
            assertEquals(
                listOf("/storage/book/1.mp3", "/storage/book/2.mp3", "/storage/book/3.mp3"),
                persisted.filePaths,
            )
            assertEquals(0, persisted.currentIndex)
            assertEquals(2_500L, persisted.currentPosition)
            assertEquals("Queue Book", persisted.metadata?.get("title"))

            verify(playerPersistenceManager).saveCurrentMediaItem(
                mediaId = "/storage/book/1.mp3",
                positionMs = 2_500L,
                durationMs = 30_000L,
                artworkPath = "",
                title = "Queue Book",
                artist = "Narrator",
                groupPath = "book://queue",
            )
        }
}
