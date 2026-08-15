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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BookCompletionTrackerTest {
    @Test
    fun `does not complete a book three minutes before the last chapter ends`() =
        runTest {
            val player: Player = mock()
            whenever(player.playbackState).thenReturn(Player.STATE_READY)
            whenever(player.currentMediaItemIndex).thenReturn(0)
            whenever(player.isPlaying).thenReturn(true)
            whenever(player.currentPosition).thenReturn(180_000L)
            whenever(player.duration).thenReturn(360_000L)
            val scope = TestScope(StandardTestDispatcher(testScheduler))
            val tracker =
                BookCompletionTracker(
                    context = mock<Context>(),
                    scope = scope,
                    getActivePlayer = { player },
                    getIsBookCompleted = { false },
                    setIsBookCompleted = { },
                    getActualPlaylistSize = { 1 },
                    getLastCompletedTrackIndex = { -1 },
                    setLastCompletedTrackIndex = { },
                    saveCurrentPosition = { },
                    getCurrentBookId = { null },
                    markBookCompleted = null,
                )

            tracker.startPositionCheck()
            testScheduler.runCurrent()

            verify(player, never()).pause()
            tracker.release()
        }

    @Test
    fun `does not complete while the last chapter is still playing near EOF`() =
        runTest {
            val player: Player = mock()
            whenever(player.playbackState).thenReturn(Player.STATE_READY)
            whenever(player.currentMediaItemIndex).thenReturn(0)
            whenever(player.isPlaying).thenReturn(true)
            whenever(player.currentPosition).thenReturn(358_000L)
            whenever(player.duration).thenReturn(360_000L)
            val scope = TestScope(StandardTestDispatcher(testScheduler))
            val tracker =
                BookCompletionTracker(
                    context = mock<Context>(),
                    scope = scope,
                    getActivePlayer = { player },
                    getIsBookCompleted = { false },
                    setIsBookCompleted = { },
                    getActualPlaylistSize = { 1 },
                    getLastCompletedTrackIndex = { -1 },
                    setLastCompletedTrackIndex = { },
                    saveCurrentPosition = { },
                    getCurrentBookId = { null },
                    markBookCompleted = null,
                )

            tracker.startPositionCheck()
            testScheduler.advanceTimeBy(5_000L)

            verify(player, never()).pause()
            tracker.release()
        }

    @Test
    fun `does not complete a repeating book`() {
        val player: Player = mock()
        whenever(player.currentPosition).thenReturn(60_000L)
        whenever(player.duration).thenReturn(60_000L)
        var completed = false
        val tracker =
            BookCompletionTracker(
                context = mock<Context>(),
                scope = TestScope(),
                getActivePlayer = { player },
                getIsBookCompleted = { completed },
                setIsBookCompleted = { completed = it },
                getActualPlaylistSize = { 1 },
                getLastCompletedTrackIndex = { -1 },
                setLastCompletedTrackIndex = { },
                saveCurrentPosition = { },
                getCurrentBookId = { null },
                markBookCompleted = null,
                getRepeatMode = { Player.REPEAT_MODE_ONE },
            )

        assert(!tracker.handleBookCompletion(player, 0))
        assert(!completed)
        verify(player, never()).pause()
    }
}
