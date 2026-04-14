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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PlayerIntentCommandRouterTest {
    @Test
    fun `isPlaybackIntent returns true for playback controls`() {
        assertTrue(PlayerIntentCommandRouter.isPlaybackIntent(PlayerIntent.Play))
        assertTrue(PlayerIntentCommandRouter.isPlaybackIntent(PlayerIntent.Pause))
        assertTrue(PlayerIntentCommandRouter.isPlaybackIntent(PlayerIntent.SeekForward))
        assertTrue(PlayerIntentCommandRouter.isPlaybackIntent(PlayerIntent.SeekBackward))
        assertTrue(PlayerIntentCommandRouter.isPlaybackIntent(PlayerIntent.SeekTo(1_000L)))
    }

    @Test
    fun `isPlaybackIntent returns false for non-playback intents`() {
        assertFalse(PlayerIntentCommandRouter.isPlaybackIntent(PlayerIntent.InitializePlayer))
        assertFalse(PlayerIntentCommandRouter.isPlaybackIntent(PlayerIntent.CancelSleepTimer))
        assertFalse(PlayerIntentCommandRouter.isPlaybackIntent(PlayerIntent.UpdateAudioSettings(skipSilence = true)))
    }

    @Test
    fun `routePlaybackIntent returns null for idempotent play command`() {
        val state = activeState(isPlaying = true)

        val command = PlayerIntentCommandRouter.routePlaybackIntent(PlayerIntent.Play, state, state)

        assertNull(command)
    }

    @Test
    fun `routePlaybackIntent maps toggle to play command`() {
        val currentState = activeState(isPlaying = false)
        val reducedState = currentState.copy(isPlaying = true)

        val command =
            PlayerIntentCommandRouter.routePlaybackIntent(
                intent = PlayerIntent.TogglePlayPause,
                currentState = currentState,
                reducedState = reducedState,
            )

        assertEquals(PlayerCommand.Play, command)
    }

    @Test
    fun `routePlaybackIntent maps seek forward to reduced position`() {
        val reducedState = activeState(currentPosition = 23_000L)

        val command =
            PlayerIntentCommandRouter.routePlaybackIntent(
                intent = PlayerIntent.SeekForward,
                currentState = activeState(currentPosition = 10_000L),
                reducedState = reducedState,
            )

        assertEquals(PlayerCommand.SeekTo(23_000L), command)
    }

    @Test
    fun `routePlaybackIntent maps select chapter to reduced chapter index`() {
        val reducedState = activeState(currentChapterIndex = 2)

        val command =
            PlayerIntentCommandRouter.routePlaybackIntent(
                intent = PlayerIntent.SelectChapter(chapterIndex = 99),
                currentState = activeState(currentChapterIndex = 0),
                reducedState = reducedState,
            )

        assertEquals(PlayerCommand.SkipToChapter(2), command)
    }

    @Test
    fun `routePlaybackIntent maps playback speed only when reduced state changed`() {
        val unchanged = activeState(playbackSpeed = 1.5f)
        val changed = unchanged.copy(playbackSpeed = 1.75f)

        val unchangedCommand =
            PlayerIntentCommandRouter.routePlaybackIntent(
                intent = PlayerIntent.SetPlaybackSpeed(1.75f),
                currentState = unchanged,
                reducedState = unchanged,
            )
        val changedCommand =
            PlayerIntentCommandRouter.routePlaybackIntent(
                intent = PlayerIntent.SetPlaybackSpeed(1.75f),
                currentState = unchanged,
                reducedState = changed,
            )

        assertNull(unchangedCommand)
        assertEquals(PlayerCommand.SetPlaybackSpeed(1.75f), changedCommand)
    }

    private fun activeState(
        isPlaying: Boolean = false,
        currentPosition: Long = 0L,
        currentChapterIndex: Int = 0,
        playbackSpeed: Float = 1.0f,
    ): PlayerState.Active {
        val chapter =
            Chapter.preview().copy(
                id = "chapter-1",
                bookId = "book-1",
                chapterIndex = currentChapterIndex,
                fileIndex = currentChapterIndex,
                duration = 30.minutes,
                position = 0.seconds,
            )
        return PlayerState.Active(
            book = Book.preview().copy(id = "book-1"),
            chapters = listOf(chapter).toImmutableList(),
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            currentChapterIndex = currentChapterIndex,
            currentChapter = chapter,
            rewindInterval = 10,
            forwardInterval = 30,
            playbackSpeed = playbackSpeed,
            sleepTimerMode = PlayerSleepTimerMode.IDLE,
            sleepTimerRemainingSeconds = null,
            chapterRepeatMode = ChapterRepeatMode.OFF,
        )
    }
}
