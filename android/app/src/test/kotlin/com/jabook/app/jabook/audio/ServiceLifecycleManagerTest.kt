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

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ServiceLifecycleManagerTest {
    private lateinit var service: AudioPlayerService
    private lateinit var player: ExoPlayer
    private lateinit var manager: ServiceLifecycleManager
    private lateinit var serviceScope: CoroutineScope
    private lateinit var durationManager: DurationManager

    @Before
    fun setUp() {
        service = mock()
        player = mock()
        durationManager = mock()
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        whenever(service.getActivePlayer()).thenReturn(player)
        whenever(service.playerServiceScope).thenReturn(serviceScope)
        whenever(service.durationManager).thenReturn(durationManager)
        manager = ServiceLifecycleManager(service)
    }

    @Test
    fun `onTaskRemoved saves position before branching by playback state`() {
        whenever(player.playWhenReady).thenReturn(true)
        whenever(player.playbackState).thenReturn(Player.STATE_READY)

        manager.onTaskRemoved()

        verify(service, times(1)).saveCurrentPosition()
        verify(service, never()).finishListeningSessionIfActive("task_removed")
        verify(service, never()).stopSelf()
    }

    @Test
    fun `onTaskRemoved stops service when player is not actively playing`() {
        whenever(player.playWhenReady).thenReturn(false)
        whenever(player.playbackState).thenReturn(Player.STATE_READY)

        manager.onTaskRemoved()

        verify(service, times(1)).saveCurrentPosition()
        verify(service, times(1)).finishListeningSessionIfActive("task_removed")
        verify(service, times(1)).stopSelf()
    }

    @Test
    fun `onTaskRemoved stops service when playback already ended`() {
        whenever(player.playWhenReady).thenReturn(true)
        whenever(player.playbackState).thenReturn(Player.STATE_ENDED)

        manager.onTaskRemoved()

        verify(service, times(1)).saveCurrentPosition()
        verify(service, times(1)).finishListeningSessionIfActive("task_removed")
        verify(service, times(1)).stopSelf()
    }

    @Test
    fun `onTaskRemoved still stops service when player state lookup fails`() {
        doThrow(IllegalStateException("player unavailable")).whenever(service).getActivePlayer()

        manager.onTaskRemoved()

        verify(service, times(1)).saveCurrentPosition()
        verify(service, times(1)).finishListeningSessionIfActive("task_removed")
        verify(service, times(1)).stopSelf()
    }

    @Test
    fun `onTaskRemoved continues when saveCurrentPosition fails`() {
        doThrow(RuntimeException("save failed")).whenever(service).saveCurrentPosition()
        whenever(player.playWhenReady).thenReturn(false)
        whenever(player.playbackState).thenReturn(Player.STATE_IDLE)

        manager.onTaskRemoved()

        verify(service, times(1)).saveCurrentPosition()
        verify(service, times(1)).finishListeningSessionIfActive("task_removed")
        verify(service, times(1)).stopSelf()
    }

    @Test
    fun `onDestroy saves position and closes listening session before runtime teardown`() {
        manager.onDestroy()

        verify(service, times(1)).saveCurrentPosition()
        verify(service, times(1)).finishListeningSessionIfActive("on_destroy")
    }

    @Test
    fun `onDestroy closes listening session when saving position fails`() {
        doThrow(RuntimeException("save failed")).whenever(service).saveCurrentPosition()

        manager.onDestroy()

        verify(service, times(1)).saveCurrentPosition()
        verify(service, times(1)).finishListeningSessionIfActive("on_destroy")
    }

    @Test
    fun `stopAndCleanup closes listening session before releasing player resources`() {
        manager.stopAndCleanup()

        verify(service, times(1)).finishListeningSessionIfActive("stop_and_cleanup")
        verify(player, times(1)).stop()
    }
}
