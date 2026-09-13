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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
class ChapterNavigationPolicyTest {
    private fun chapter(
        index: Int,
        durationMs: Long,
    ) = Chapter(
        id = "ch-$index",
        bookId = "book-1",
        title = "Chapter $index",
        chapterIndex = index,
        fileIndex = index,
        duration = durationMs.milliseconds,
        fileUrl = null,
        position = 0L.milliseconds,
        isCompleted = false,
        isDownloaded = false,
    )

    private val chapters =
        listOf(
            chapter(0, 60_000L),
            chapter(1, 60_000L),
            chapter(2, 60_000L),
        )

    // --- resolvePreviousAction ---

    @Test
    fun `previous restarts current chapter when past threshold`() {
        val action =
            ChapterNavigationPolicy.resolvePreviousAction(
                chapters = chapters,
                currentChapterIndex = 1,
                currentChapterPositionMs = 10_000L,
            )
        assertEquals(ChapterNavigationAction.RestartCurrentChapter(1), action)
    }

    @Test
    fun `previous jumps to previous chapter when near start`() {
        val action =
            ChapterNavigationPolicy.resolvePreviousAction(
                chapters = chapters,
                currentChapterIndex = 1,
                currentChapterPositionMs = 3_000L,
            )
        assertEquals(ChapterNavigationAction.JumpToChapter(0), action)
    }

    @Test
    fun `previous restarts chapter 0 when at start of first chapter`() {
        val action =
            ChapterNavigationPolicy.resolvePreviousAction(
                chapters = chapters,
                currentChapterIndex = 0,
                currentChapterPositionMs = 1_000L,
            )
        assertEquals(ChapterNavigationAction.RestartCurrentChapter(0), action)
    }

    @Test
    fun `previous restarts chapter 0 when past threshold in first chapter`() {
        val action =
            ChapterNavigationPolicy.resolvePreviousAction(
                chapters = chapters,
                currentChapterIndex = 0,
                currentChapterPositionMs = 30_000L,
            )
        assertEquals(ChapterNavigationAction.RestartCurrentChapter(0), action)
    }

    @Test
    fun `previous handles empty chapters`() {
        val action =
            ChapterNavigationPolicy.resolvePreviousAction(
                chapters = emptyList(),
                currentChapterIndex = 0,
                currentChapterPositionMs = 0L,
            )
        assertEquals(ChapterNavigationAction.RestartCurrentChapter(0), action)
    }

    @Test
    fun `previous handles exactly at threshold`() {
        val action =
            ChapterNavigationPolicy.resolvePreviousAction(
                chapters = chapters,
                currentChapterIndex = 2,
                currentChapterPositionMs = 5000L,
            )
        assertEquals(ChapterNavigationAction.RestartCurrentChapter(2), action)
    }

    // --- resolveNextAction ---

    @Test
    fun `next jumps to next chapter when available`() {
        val action =
            ChapterNavigationPolicy.resolveNextAction(
                chapters = chapters,
                currentChapterIndex = 0,
            )
        assertEquals(ChapterNavigationAction.JumpToChapter(1), action)
    }

    @Test
    fun `next returns end of book at last chapter`() {
        val action =
            ChapterNavigationPolicy.resolveNextAction(
                chapters = chapters,
                currentChapterIndex = 2,
            )
        assertEquals(ChapterNavigationAction.EndOfBook, action)
    }

    @Test
    fun `next handles empty chapters`() {
        val action =
            ChapterNavigationPolicy.resolveNextAction(
                chapters = emptyList(),
                currentChapterIndex = 0,
            )
        assertEquals(ChapterNavigationAction.EndOfBook, action)
    }

    @Test
    fun `next handles single chapter`() {
        val singleChapter = listOf(chapter(0, 60_000L))
        val action =
            ChapterNavigationPolicy.resolveNextAction(
                chapters = singleChapter,
                currentChapterIndex = 0,
            )
        assertEquals(ChapterNavigationAction.EndOfBook, action)
    }

    @Test
    fun `next handles out of bounds index`() {
        val action =
            ChapterNavigationPolicy.resolveNextAction(
                chapters = chapters,
                currentChapterIndex = 10,
            )
        assertEquals(ChapterNavigationAction.EndOfBook, action)
    }

    @Test
    fun `previous handles out of bounds index`() {
        val action =
            ChapterNavigationPolicy.resolvePreviousAction(
                chapters = chapters,
                currentChapterIndex = 10,
                currentChapterPositionMs = 10_000L,
            )
        assertEquals(ChapterNavigationAction.RestartCurrentChapter(2), action)
    }
}

class ChapterNavigationIntentPolicyTest {
    @Test
    fun `resolve keeps skip next unchanged when tap is near chapter start`() {
        val state = activeState(currentChapterIndex = 1)

        val decision = ChapterNavigationIntentPolicy.resolve(intent = PlayerIntent.SkipNext, state = state, currentPositionMs = 2_000L)

        assertEquals(PlayerIntent.SkipNext, decision.intent)
        assertEquals(null, decision.movedToChapterDisplayIndex)
        assertEquals(null, decision.undoChapterIndex)
    }

    @Test
    fun `resolve converts skip next to next chapter when tap is near chapter end`() {
        val state = activeState(currentChapterIndex = 1)

        val decision = ChapterNavigationIntentPolicy.resolve(intent = PlayerIntent.SkipNext, state = state, currentPositionMs = 56_000L)

        assertEquals(PlayerIntent.SelectChapter(2), decision.intent)
        assertEquals(3, decision.movedToChapterDisplayIndex)
        assertEquals(1, decision.undoChapterIndex)
    }

    @Test
    fun `resolve keeps skip next unchanged when tap is in chapter middle`() {
        val state = activeState(currentChapterIndex = 1)

        val decision = ChapterNavigationIntentPolicy.resolve(intent = PlayerIntent.SkipNext, state = state, currentPositionMs = 30_000L)

        assertEquals(PlayerIntent.SkipNext, decision.intent)
        assertEquals(null, decision.movedToChapterDisplayIndex)
        assertEquals(null, decision.undoChapterIndex)
    }

    @Test
    fun `resolve keeps skip next unchanged when no chapter can be inferred`() {
        val state = activeState(currentChapterIndex = 0)

        val decision = ChapterNavigationIntentPolicy.resolve(intent = PlayerIntent.SkipNext, state = state, currentPositionMs = 1_000L)

        assertEquals(PlayerIntent.SkipNext, decision.intent)
        assertEquals(null, decision.movedToChapterDisplayIndex)
        assertEquals(null, decision.undoChapterIndex)
    }

    @Test
    fun `resolve keeps non-skip intents untouched`() {
        val state = activeState(currentChapterIndex = 1)

        val decision = ChapterNavigationIntentPolicy.resolve(intent = PlayerIntent.SeekForward, state = state, currentPositionMs = 2_000L)

        assertEquals(PlayerIntent.SeekForward, decision.intent)
        assertEquals(null, decision.movedToChapterDisplayIndex)
        assertEquals(null, decision.undoChapterIndex)
    }

    @Test
    fun `resolve converts skip previous to chapter restart when past threshold`() {
        val state = activeState(currentChapterIndex = 1)

        val decision = ChapterNavigationIntentPolicy.resolve(intent = PlayerIntent.SkipPrevious, state = state, currentPositionMs = 10_000L)

        assertEquals(PlayerIntent.SelectChapter(1), decision.intent)
        assertEquals(null, decision.movedToChapterDisplayIndex)
        assertEquals(null, decision.undoChapterIndex)
    }

    @Test
    fun `resolve converts skip previous to previous chapter when near start`() {
        val state = activeState(currentChapterIndex = 1)

        val decision = ChapterNavigationIntentPolicy.resolve(intent = PlayerIntent.SkipPrevious, state = state, currentPositionMs = 3_000L)

        assertEquals(PlayerIntent.SelectChapter(0), decision.intent)
        assertEquals(null, decision.movedToChapterDisplayIndex)
        assertEquals(null, decision.undoChapterIndex)
    }

    private fun activeState(currentChapterIndex: Int): PlayerState.Active {
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
