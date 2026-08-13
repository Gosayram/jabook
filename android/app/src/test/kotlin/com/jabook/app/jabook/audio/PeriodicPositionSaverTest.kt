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

import androidx.media3.exoplayer.ExoPlayer
import com.jabook.app.jabook.audio.data.repository.PlaybackPositionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PeriodicPositionSaverTest {
    @Test
    fun `final save survives service scope cancellation`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val serviceScope = CoroutineScope(SupervisorJob() + dispatcher)
            val player: ExoPlayer = mock()
            val repository: PlaybackPositionRepository = mock()
            whenever(player.mediaItemCount).thenReturn(1)
            whenever(player.currentMediaItemIndex).thenReturn(2)
            whenever(player.currentPosition).thenReturn(45_000L)

            PeriodicPositionSaver(
                scope = serviceScope,
                repository = repository,
                getActivePlayer = { player },
                getCurrentBookId = { "book-1" },
                ioDispatcher = dispatcher,
            ).save()
            serviceScope.cancel()
            advanceUntilIdle()

            verify(repository).savePosition(eq("book-1"), eq(2), eq(45_000L))
        }

    @Test
    fun `save keeps the position captured before asynchronous persistence`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val player: ExoPlayer = mock()
            val repository: PlaybackPositionRepository = mock()
            var trackIndex = 2
            var position = 45_000L
            whenever(player.mediaItemCount).thenReturn(1)
            whenever(player.currentMediaItemIndex).thenAnswer { trackIndex }
            whenever(player.currentPosition).thenAnswer { position }

            PeriodicPositionSaver(
                scope = CoroutineScope(SupervisorJob() + dispatcher),
                repository = repository,
                getActivePlayer = { player },
                getCurrentBookId = { "book-1" },
                ioDispatcher = dispatcher,
            ).save()
            trackIndex = 3
            position = 60_000L
            advanceUntilIdle()

            verify(repository).savePosition(eq("book-1"), eq(2), eq(45_000L))
        }
}
