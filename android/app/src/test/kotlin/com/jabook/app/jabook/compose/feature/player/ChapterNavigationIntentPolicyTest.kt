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

import com.jabook.app.jabook.compose.domain.model.Book
import com.jabook.app.jabook.compose.domain.model.Chapter
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ChapterNavigationIntentPolicyTest {
    @Test
    fun `resolve converts skip next to previous chapter when tap is near chapter start`() {
        val state = activeState(currentChapterIndex = 1, currentPositionMs = 2_000L)

        val decision = ChapterNavigationIntentPolicy.resolve(intent = PlayerIntent.SkipNext, state = state)

        assertEquals(PlayerIntent.SelectChapter(0), decision.intent)
        assertEquals(1, decision.movedToChapterDisplayIndex)
        assertEquals(1, decision.undoChapterIndex)
    }

    @Test
    fun `resolve converts skip next to next chapter when tap is near chapter end`() {
        val state = activeState(currentChapterIndex = 1, currentPositionMs = 56_000L)

        val decision = ChapterNavigationIntentPolicy.resolve(intent = PlayerIntent.SkipNext, state = state)

        assertEquals(PlayerIntent.SelectChapter(2), decision.intent)
        assertEquals(3, decision.movedToChapterDisplayIndex)
        assertEquals(1, decision.undoChapterIndex)
    }

    @Test
    fun `resolve keeps skip next unchanged when tap is in chapter middle`() {
        val state = activeState(currentChapterIndex = 1, currentPositionMs = 30_000L)

        val decision = ChapterNavigationIntentPolicy.resolve(intent = PlayerIntent.SkipNext, state = state)

        assertEquals(PlayerIntent.SkipNext, decision.intent)
        assertEquals(null, decision.movedToChapterDisplayIndex)
        assertEquals(null, decision.undoChapterIndex)
    }

    @Test
    fun `resolve keeps skip next unchanged when no chapter can be inferred`() {
        val state = activeState(currentChapterIndex = 0, currentPositionMs = 1_000L)

        val decision = ChapterNavigationIntentPolicy.resolve(intent = PlayerIntent.SkipNext, state = state)

        assertEquals(PlayerIntent.SkipNext, decision.intent)
        assertEquals(null, decision.movedToChapterDisplayIndex)
        assertEquals(null, decision.undoChapterIndex)
    }

    @Test
    fun `resolve keeps non-skip intents untouched`() {
        val state = activeState(currentChapterIndex = 1, currentPositionMs = 2_000L)

        val decision = ChapterNavigationIntentPolicy.resolve(intent = PlayerIntent.SeekForward, state = state)

        assertEquals(PlayerIntent.SeekForward, decision.intent)
        assertEquals(null, decision.movedToChapterDisplayIndex)
        assertEquals(null, decision.undoChapterIndex)
    }

    private fun activeState(
        currentChapterIndex: Int,
        currentPositionMs: Long,
    ): PlayerState.Active {
        val chapters =
            listOf(
                Chapter.preview().copy(
                    id = "chapter-0",
                    bookId = "book-1",
                    chapterIndex = 0,
                    fileIndex = 0,
                    duration = 1.minutes,
                    position = 0.seconds,
                ),
                Chapter.preview().copy(
                    id = "chapter-1",
                    bookId = "book-1",
                    chapterIndex = 1,
                    fileIndex = 1,
                    duration = 1.minutes,
                    position = 0.seconds,
                ),
                Chapter.preview().copy(
                    id = "chapter-2",
                    bookId = "book-1",
                    chapterIndex = 2,
                    fileIndex = 2,
                    duration = 1.minutes,
                    position = 0.seconds,
                ),
            ).toImmutableList()

        return PlayerState.Active(
            book = Book.preview().copy(id = "book-1"),
            chapters = chapters,
            isPlaying = true,
            currentPosition = currentPositionMs,
            currentChapterIndex = currentChapterIndex,
            currentChapter = chapters[currentChapterIndex],
            rewindInterval = 10,
            forwardInterval = 30,
            playbackSpeed = 1.0f,
            sleepTimerMode = PlayerSleepTimerMode.IDLE,
            sleepTimerRemainingSeconds = null,
            chapterRepeatMode = ChapterRepeatMode.OFF,
        )
    }
}
