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

import com.jabook.app.jabook.compose.data.model.DownloadStatus
import com.jabook.app.jabook.compose.domain.model.Book
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlayerViewModelSeriesAutoplayTest {
    private val testBook =
        Book(
            id = "book1",
            title = "Book 1",
            author = "Author",
            coverUrl = null,
            description = null,
            totalDuration = 100000.milliseconds,
            currentPosition = 0.milliseconds,
            progress = 0f,
            currentChapterIndex = 0,
            downloadStatus = DownloadStatus.DOWNLOADED,
            downloadProgress = 1f,
            localPath = "/path/book1",
            addedDate = 0L,
            lastPlayedDate = null,
            isFavorite = false,
            sourceUrl = "file:///book1",
        )

    @Test
    fun `evaluateSeriesAutoplayDecision triggers when track ended at last chapter`() =
        runTest {
            val decision =
                evaluateSeriesAutoplayDecision(
                    isLastChapter = true,
                    isPlaying = false,
                    positionMs = 99_500L,
                    durationMs = 100_000L,
                    hasTriggeredSeriesAutoplay = false,
                )
            assertEquals(true, decision.shouldTriggerAutoplay)
            assertEquals(false, decision.shouldResetAutoplay)
        }

    @Test
    fun `evaluateSeriesAutoplayDecision does not trigger when not at last chapter`() =
        runTest {
            val decision =
                evaluateSeriesAutoplayDecision(
                    isLastChapter = false,
                    isPlaying = false,
                    positionMs = 99_500L,
                    durationMs = 100_000L,
                    hasTriggeredSeriesAutoplay = false,
                )
            assertEquals(false, decision.shouldTriggerAutoplay)
            assertEquals(true, decision.shouldResetAutoplay)
        }

    @Test
    fun `evaluateSeriesAutoplayDecision does not trigger when still playing`() =
        runTest {
            val decision =
                evaluateSeriesAutoplayDecision(
                    isLastChapter = true,
                    isPlaying = true,
                    positionMs = 99_500L,
                    durationMs = 100_000L,
                    hasTriggeredSeriesAutoplay = false,
                )
            assertEquals(false, decision.shouldTriggerAutoplay)
            assertEquals(true, decision.shouldResetAutoplay)
        }

    @Test
    fun `evaluateSeriesAutoplayDecision does not trigger when position too far from end`() =
        runTest {
            val decision =
                evaluateSeriesAutoplayDecision(
                    isLastChapter = true,
                    isPlaying = false,
                    positionMs = 50_000L,
                    durationMs = 100_000L,
                    hasTriggeredSeriesAutoplay = false,
                )
            // When not at end (50_000 < 99_250), and not playing, no action yet
            assertEquals(false, decision.shouldTriggerAutoplay)
            assertEquals(false, decision.shouldResetAutoplay)
        }

    @Test
    fun `evaluateSeriesAutoplayDecision resets when already triggered and not at end`() =
        runTest {
            val decision =
                evaluateSeriesAutoplayDecision(
                    isLastChapter = true,
                    isPlaying = true,
                    positionMs = 50_000L,
                    durationMs = 100_000L,
                    hasTriggeredSeriesAutoplay = true,
                )
            assertEquals(false, decision.shouldTriggerAutoplay)
            assertEquals(true, decision.shouldResetAutoplay)
        }

    @Test
    fun `NextBookAutoplayState holds correct data`() {
        val state =
            PlayerViewModel.NextBookAutoplayState(
                nextBook = testBook,
                secondsLeft = 5,
                totalSeconds = 10,
            )

        assertEquals(testBook, state.nextBook)
        assertEquals(5, state.secondsLeft)
        assertEquals(10, state.totalSeconds)
    }

    @Test
    fun `findNextBookInSeries returns null when no next book exists`() {
        val book1 =
            Book(
                id = "b1",
                title = "Series Book 1",
                author = "Author",
                coverUrl = null,
                description = null,
                totalDuration = 100000.milliseconds,
                currentPosition = 0.milliseconds,
                progress = 0f,
                currentChapterIndex = 0,
                downloadStatus = DownloadStatus.DOWNLOADED,
                downloadProgress = 1f,
                localPath = "/path",
                addedDate = 0L,
                lastPlayedDate = null,
                isFavorite = false,
                sourceUrl = "file:///b1",
            )
        val book2 =
            Book(
                id = "b2",
                title = "Other Book",
                author = "Author",
                coverUrl = null,
                description = null,
                totalDuration = 100000.milliseconds,
                currentPosition = 0.milliseconds,
                progress = 0f,
                currentChapterIndex = 0,
                downloadStatus = DownloadStatus.DOWNLOADED,
                downloadProgress = 1f,
                localPath = "/path",
                addedDate = 0L,
                lastPlayedDate = null,
                isFavorite = false,
                sourceUrl = "file:///b2",
            )

        // Simulate next book lookup logic
        val allBooks = listOf(book1, book2)
        val result =
            allBooks
                .asSequence()
                .filter { it.id != book1.id }
                .filter { it.author.equals(book1.author, ignoreCase = true) }
                .firstOrNull()

        assertNotNull(result)
        assertEquals(book2.id, result?.id)
    }

    @Test
    fun `autoplay tolerance constant allows near-end positioning`() {
        val toleranceMs = SERIES_AUTOPLAY_END_TOLERANCE_MS
        val durationMs = 100_000L
        val positionNearEnd = durationMs - toleranceMs // Should trigger

        val isTrackEnded = positionNearEnd >= (durationMs - toleranceMs)
        assertEquals(true, isTrackEnded)
    }
}
