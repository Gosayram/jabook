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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ServiceLifecycleManagerTest {
    private lateinit var service: AudioPlayerService
    private lateinit var player: ExoPlayer
    private lateinit var manager: ServiceLifecycleManager

    @Before
    fun setUp() {
        service = mock()
        player = mock()

        whenever(service.getActivePlayer()).thenReturn(player)
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
}
