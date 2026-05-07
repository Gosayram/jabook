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

import com.jabook.app.jabook.compose.core.theme.PlayerThemeColors
import com.jabook.app.jabook.compose.data.model.DownloadStatus
import com.jabook.app.jabook.compose.domain.model.Book
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for PlayerScreen state-key routing and chapter boundary haptic suppression.
 */
public class PlayerScreenStateAndHapticsPolicyTest {
    @Test
    public fun `playerStateContentKey returns loading for Loading`() {
        assertEquals("loading", playerStateContentKey(PlayerState.Loading))
    }

    @Test
    public fun `playerStateContentKey returns active for Active`() {
        assertEquals(
            "active",
            playerStateContentKey(
                PlayerState.Active(
                    book =
                        Book
                            .preview()
                            .copy(
                                id = "book-id",
                                title = "Title",
                                author = "Author",
                                downloadStatus = DownloadStatus.NOT_DOWNLOADED,
                            ),
                    chapters = persistentListOf(),
                    isPlaying = false,
                    currentPosition = 0L,
                    currentChapterIndex = 0,
                    currentChapter = null,
                    rewindInterval = 10,
                    forwardInterval = 30,
                    playbackSpeed = 1f,
                    sleepTimerMode = PlayerSleepTimerMode.IDLE,
                    sleepTimerRemainingSeconds = null,
                    chapterRepeatMode = ChapterRepeatMode.OFF,
                    themeColors = PlayerThemeColors(),
                ),
            ),
        )
    }

    @Test
    public fun `playerStateContentKey returns error for Error`() {
        assertEquals("error", playerStateContentKey(PlayerState.Error("boom")))
    }

    @Test
    public fun `resolveChapterBoundaryHapticDecision returns null when chapter index unchanged`() {
        val decision =
            resolveChapterBoundaryHapticDecision(
                previousChapterIndex = 2,
                newChapterIndex = 2,
                skipTriggeredHaptic = false,
            )
        assertNull(decision)
    }

    @Test
    public fun `resolveChapterBoundaryHapticDecision suppresses boundary haptic after explicit skip`() {
        val decision =
            resolveChapterBoundaryHapticDecision(
                previousChapterIndex = 2,
                newChapterIndex = 3,
                skipTriggeredHaptic = true,
            )

        assertNotNull(decision)
        assertFalse(decision!!.shouldPerformHaptic)
        assertFalse(decision.nextSkipTriggeredHaptic)
        assertEquals(3, decision.nextLastChapterBoundaryIndex)
    }

    @Test
    public fun `resolveChapterBoundaryHapticDecision emits haptic on passive boundary transition`() {
        val decision =
            resolveChapterBoundaryHapticDecision(
                previousChapterIndex = 2,
                newChapterIndex = 3,
                skipTriggeredHaptic = false,
            )

        assertNotNull(decision)
        assertTrue(decision!!.shouldPerformHaptic)
        assertFalse(decision.nextSkipTriggeredHaptic)
        assertEquals(3, decision.nextLastChapterBoundaryIndex)
    }
}
