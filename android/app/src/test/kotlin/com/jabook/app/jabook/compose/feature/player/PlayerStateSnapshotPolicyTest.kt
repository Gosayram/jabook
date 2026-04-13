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

package com.jabook.app.jabook.compose.feature.player

import com.jabook.app.jabook.compose.domain.model.Chapter
import com.jabook.app.jabook.compose.domain.model.SleepTimerState
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerStateSnapshotPolicyTest {
    @Test
    fun `capture stores clamped active state values`() {
        val activeState =
            PlayerState.Active(
                book =
                    com.jabook.app.jabook.compose.domain.model.Book
                        .preview(),
                chapters = persistentListOf(Chapter.preview()),
                isPlaying = true,
                currentPosition = -1L,
                currentChapterIndex = -3,
                currentChapter = Chapter.preview(),
                rewindInterval = 10,
                forwardInterval = 30,
                playbackSpeed = -2f,
            )

        val snapshot =
            PlayerStateSnapshotPolicy.capture(
                bookId = "book-1",
                state = activeState,
                sleepTimerState = SleepTimerState.EndOfChapter,
            )

        assertEquals("book-1", snapshot.bookId)
        assertEquals(0L, snapshot.positionMs)
        assertEquals(0, snapshot.chapterIndex)
        assertEquals(0f, snapshot.playbackSpeed)
        assertEquals(PlayerStateSnapshotPolicy.MODE_END_OF_CHAPTER, snapshot.sleepTimerMode)
    }

    @Test
    fun `sleepTimerModeOf maps all sleep timer states`() {
        assertEquals(PlayerStateSnapshotPolicy.MODE_IDLE, PlayerStateSnapshotPolicy.sleepTimerModeOf(SleepTimerState.Idle))
        assertEquals(
            PlayerStateSnapshotPolicy.MODE_ACTIVE,
            PlayerStateSnapshotPolicy.sleepTimerModeOf(SleepTimerState.Active(remainingSeconds = 30)),
        )
        assertEquals(
            PlayerStateSnapshotPolicy.MODE_END_OF_CHAPTER,
            PlayerStateSnapshotPolicy.sleepTimerModeOf(SleepTimerState.EndOfChapter),
        )
        assertEquals(
            PlayerStateSnapshotPolicy.MODE_END_OF_TRACK,
            PlayerStateSnapshotPolicy.sleepTimerModeOf(SleepTimerState.EndOfTrack(fallbackFromChapter = true)),
        )
    }
}
