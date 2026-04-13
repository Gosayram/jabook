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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PlayerReducerTest {
    @Test
    fun `reduce keeps loading state when play intent received`() {
        val state = PlayerState.Loading

        val reduced = PlayerReducer.reduce(state, PlayerIntent.Play)

        assertSame(state, reduced)
    }

    @Test
    fun `reduce clamps seek intent to chapter duration in active state`() {
        val state =
            PlayerState.Active(
                book = Book.preview().copy(id = "book-1", title = "Title", author = "Author"),
                chapters =
                    listOf(
                        Chapter.preview().copy(
                            id = "c1",
                            bookId = "book-1",
                            title = "Chapter 1",
                            chapterIndex = 0,
                            fileIndex = 0,
                            duration = 1.minutes,
                            position = 0.seconds,
                        ),
                    ).toImmutableList(),
                isPlaying = true,
                currentPosition = 10_000L,
                currentChapterIndex = 0,
                currentChapter = Chapter.preview().copy(id = "c1", bookId = "book-1", duration = 1.minutes),
                rewindInterval = 10,
                forwardInterval = 30,
                playbackSpeed = 1.0f,
                sleepTimerMode = PlayerSleepTimerMode.IDLE,
                sleepTimerRemainingSeconds = null,
            )

        val reduced = PlayerReducer.reduce(state, PlayerIntent.SeekTo(positionMs = 120_000L))

        require(reduced is PlayerState.Active)
        assertEquals(60_000L, reduced.currentPosition)
    }

    @Test
    fun `reduce transitions to error state on report error intent`() {
        val reduced = PlayerReducer.reduce(PlayerState.Loading, PlayerIntent.ReportError("boom"))

        require(reduced is PlayerState.Error)
        assertEquals("boom", reduced.message)
    }

    @Test
    fun `reduce toggles play state with toggle intent`() {
        val paused = activeStateTemplate().copy(isPlaying = false)
        val playing = activeStateTemplate().copy(isPlaying = true)

        val pausedToPlaying = PlayerReducer.reduce(paused, PlayerIntent.TogglePlayPause)
        val playingToPaused = PlayerReducer.reduce(playing, PlayerIntent.TogglePlayPause)

        require(pausedToPlaying is PlayerState.Active)
        require(playingToPaused is PlayerState.Active)
        assertTrue(pausedToPlaying.isPlaying)
        assertFalse(playingToPaused.isPlaying)
    }

    @Test
    fun `reduce seek forward uses forward interval and clamps by duration`() {
        val state = activeStateTemplate().copy(currentPosition = 50_000L, forwardInterval = 15)

        val reduced = PlayerReducer.reduce(state, PlayerIntent.SeekForward)

        require(reduced is PlayerState.Active)
        assertEquals(60_000L, reduced.currentPosition)
    }

    @Test
    fun `reduce seek backward uses rewind interval and clamps to zero`() {
        val state = activeStateTemplate().copy(currentPosition = 5_000L, rewindInterval = 10)

        val reduced = PlayerReducer.reduce(state, PlayerIntent.SeekBackward)

        require(reduced is PlayerState.Active)
        assertEquals(0L, reduced.currentPosition)
    }

    @Test
    fun `reduce select chapter clamps index and resets position`() {
        val chapters =
            listOf(
                Chapter.preview().copy(id = "c1", chapterIndex = 0),
                Chapter.preview().copy(id = "c2", chapterIndex = 1),
                Chapter.preview().copy(id = "c3", chapterIndex = 2),
            ).toImmutableList()
        val state =
            activeStateTemplate().copy(
                chapters = chapters,
                currentChapterIndex = 0,
                currentChapter = chapters.first(),
                currentPosition = 42_000L,
            )

        val reduced = PlayerReducer.reduce(state, PlayerIntent.SelectChapter(chapterIndex = 99))

        require(reduced is PlayerState.Active)
        assertEquals(2, reduced.currentChapterIndex)
        assertEquals("c3", reduced.currentChapter?.id)
        assertEquals(0L, reduced.currentPosition)
    }

    @Test
    fun `reduce keeps state when fixed sleep timer request is idempotent`() {
        val state = activeStateTemplate().copy(sleepTimerMode = PlayerSleepTimerMode.FIXED, sleepTimerRemainingSeconds = 300)

        val reduced = PlayerReducer.reduce(state, PlayerIntent.StartSleepTimer(minutes = 5))

        assertEquals(state, reduced)
    }

    @Test
    fun `reduce updates state when fixed sleep timer request differs`() {
        val state = activeStateTemplate().copy(sleepTimerMode = PlayerSleepTimerMode.FIXED, sleepTimerRemainingSeconds = 120)

        val reduced = PlayerReducer.reduce(state, PlayerIntent.StartSleepTimer(minutes = 5))

        require(reduced is PlayerState.Active)
        assertEquals(PlayerSleepTimerMode.FIXED, reduced.sleepTimerMode)
        assertEquals(300, reduced.sleepTimerRemainingSeconds)
    }

    @Test
    fun `reduce keeps state when end-of-chapter timer already active`() {
        val state = activeStateTemplate().copy(sleepTimerMode = PlayerSleepTimerMode.END_OF_CHAPTER, sleepTimerRemainingSeconds = null)

        val reduced = PlayerReducer.reduce(state, PlayerIntent.StartSleepTimerEndOfChapter)

        assertEquals(state, reduced)
    }

    @Test
    fun `nextChapterRepeatMode cycles through all modes`() {
        assertEquals(ChapterRepeatMode.ONCE, PlayerReducer.nextChapterRepeatMode(ChapterRepeatMode.OFF))
        assertEquals(ChapterRepeatMode.INFINITE, PlayerReducer.nextChapterRepeatMode(ChapterRepeatMode.ONCE))
        assertEquals(ChapterRepeatMode.OFF, PlayerReducer.nextChapterRepeatMode(ChapterRepeatMode.INFINITE))
    }

    @Test
    fun `reduceChapterEnded in OFF mode never repeats and resets flag`() {
        val reduction =
            PlayerReducer.reduceChapterEnded(
                mode = ChapterRepeatMode.OFF,
                hasRepeatedOnce = true,
            )

        assertFalse(reduction.shouldRepeat)
        assertFalse(reduction.hasRepeatedOnce)
    }

    @Test
    fun `reduceChapterEnded in ONCE mode repeats once when not repeated yet`() {
        val reduction =
            PlayerReducer.reduceChapterEnded(
                mode = ChapterRepeatMode.ONCE,
                hasRepeatedOnce = false,
            )

        assertTrue(reduction.shouldRepeat)
        assertTrue(reduction.hasRepeatedOnce)
    }

    @Test
    fun `reduceChapterEnded in ONCE mode stops repeating after first repeat`() {
        val reduction =
            PlayerReducer.reduceChapterEnded(
                mode = ChapterRepeatMode.ONCE,
                hasRepeatedOnce = true,
            )

        assertFalse(reduction.shouldRepeat)
        assertFalse(reduction.hasRepeatedOnce)
    }

    @Test
    fun `reduceChapterEnded in INFINITE mode always repeats and keeps flag`() {
        val reductionWithFalseFlag =
            PlayerReducer.reduceChapterEnded(
                mode = ChapterRepeatMode.INFINITE,
                hasRepeatedOnce = false,
            )
        val reductionWithTrueFlag =
            PlayerReducer.reduceChapterEnded(
                mode = ChapterRepeatMode.INFINITE,
                hasRepeatedOnce = true,
            )

        assertTrue(reductionWithFalseFlag.shouldRepeat)
        assertFalse(reductionWithFalseFlag.hasRepeatedOnce)
        assertTrue(reductionWithTrueFlag.shouldRepeat)
        assertTrue(reductionWithTrueFlag.hasRepeatedOnce)
    }

    @Test
    fun `reduceChapterChanged resets repeat flag`() {
        assertFalse(PlayerReducer.reduceChapterChanged())
    }

    private fun activeStateTemplate(): PlayerState.Active =
        PlayerState.Active(
            book = Book.preview().copy(id = "book-1"),
            chapters =
                listOf(
                    Chapter.preview().copy(
                        id = "c1",
                        bookId = "book-1",
                        chapterIndex = 0,
                        fileIndex = 0,
                        duration = 1.minutes,
                        position = 0.seconds,
                    ),
                ).toImmutableList(),
            isPlaying = true,
            currentPosition = 10_000L,
            currentChapterIndex = 0,
            currentChapter = Chapter.preview().copy(id = "c1", bookId = "book-1", duration = 1.minutes),
            rewindInterval = 10,
            forwardInterval = 30,
            playbackSpeed = 1.0f,
            sleepTimerMode = PlayerSleepTimerMode.IDLE,
            sleepTimerRemainingSeconds = null,
        )
}
