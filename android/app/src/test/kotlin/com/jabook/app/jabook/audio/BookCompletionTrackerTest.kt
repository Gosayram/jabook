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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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
}
