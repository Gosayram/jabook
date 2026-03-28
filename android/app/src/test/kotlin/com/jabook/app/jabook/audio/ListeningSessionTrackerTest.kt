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

import com.jabook.app.jabook.audio.data.repository.ListeningSessionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ListeningSessionTrackerTest {
    private val repository: ListeningSessionRepository = mock()
    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    @Test
    fun `onPlaybackStarted creates single active session for same book`() =
        runTest(dispatcher) {
            whenever(
                repository.startSession(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                ),
            ).thenReturn("session-1")

            val tracker =
                ListeningSessionTracker(
                    repository = repository,
                    scope = scope,
                    ioDispatcher = dispatcher,
                    getCurrentBookId = { "book-1" },
                    getCurrentPositionMs = { 10_000L },
                    getCurrentSpeed = { 1.25f },
                    getCurrentChapterIndex = { 2 },
                )

            tracker.onPlaybackStarted()
            tracker.onPlaybackStarted()
            scope.advanceUntilIdle()

            verify(repository, times(1)).startSession(
                bookId = eq("book-1"),
                positionStartMs = eq(10_000L),
                speedFactor = eq(1.25f),
                chapterIndex = eq(2),
                startedAt = any(),
            )
        }

    @Test
    fun `onPlaybackStopped finishes active session`() =
        runTest(dispatcher) {
            whenever(
                repository.startSession(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                ),
            ).thenReturn("session-1")

            val tracker =
                ListeningSessionTracker(
                    repository = repository,
                    scope = scope,
                    ioDispatcher = dispatcher,
                    getCurrentBookId = { "book-1" },
                    getCurrentPositionMs = { 45_000L },
                    getCurrentSpeed = { 1.5f },
                    getCurrentChapterIndex = { 4 },
                )

            tracker.onPlaybackStarted()
            scope.advanceUntilIdle()
            tracker.onPlaybackStopped("pause")
            scope.advanceUntilIdle()

            verify(repository, times(1)).finishSession(
                sessionId = eq("session-1"),
                positionEndMs = eq(45_000L),
                speedFactor = eq(1.5f),
                chapterIndex = eq(4),
                endedAt = any(),
            )
        }

    @Test
    fun `onPlaybackStarted ignores blank book id`() =
        runTest(dispatcher) {
            val tracker =
                ListeningSessionTracker(
                    repository = repository,
                    scope = scope,
                    ioDispatcher = dispatcher,
                    getCurrentBookId = { " " },
                    getCurrentPositionMs = { 1_000L },
                    getCurrentSpeed = { 1f },
                    getCurrentChapterIndex = { 0 },
                )

            tracker.onPlaybackStarted()
            scope.advanceUntilIdle()

            verify(repository, never()).startSession(
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
}
