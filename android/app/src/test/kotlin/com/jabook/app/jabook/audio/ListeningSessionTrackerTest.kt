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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
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
    fun `onPlaybackStopped finishes active session when listen floor is met`() =
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

            var positionMs = 0L
            val tracker =
                ListeningSessionTracker(
                    repository = repository,
                    scope = scope,
                    ioDispatcher = dispatcher,
                    getCurrentBookId = { "book-1" },
                    getCurrentPositionMs = { positionMs },
                    getCurrentSpeed = { 1.5f },
                    getCurrentChapterIndex = { 4 },
                    getCurrentDurationMs = { 600_000L },
                )

            tracker.onPlaybackStarted()
            scope.advanceUntilIdle()
            positionMs = 300_000L
            tracker.onPlaybackStopped("pause")
            scope.advanceUntilIdle()

            verify(repository, times(1)).finishSession(
                sessionId = eq("session-1"),
                positionEndMs = eq(300_000L),
                speedFactor = eq(1.5f),
                chapterIndex = eq(4),
                endedAt = any(),
            )
        }

    @Test
    fun `onPlaybackStopped discards session below min listen floor`() =
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

            var positionMs = 0L
            val tracker =
                ListeningSessionTracker(
                    repository = repository,
                    scope = scope,
                    ioDispatcher = dispatcher,
                    getCurrentBookId = { "book-1" },
                    getCurrentPositionMs = { positionMs },
                    getCurrentSpeed = { 1.5f },
                    getCurrentChapterIndex = { 4 },
                    getCurrentDurationMs = { 600_000L },
                )

            tracker.onPlaybackStarted()
            scope.advanceUntilIdle()
            positionMs = 100_000L
            tracker.onPlaybackStopped("pause")
            scope.advanceUntilIdle()

            verify(repository, times(1)).discardSession(sessionId = eq("session-1"))
            verify(repository, never()).finishSession(
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }

    @Test
    fun `session credit is applied once per chapter item`() =
        runTest(dispatcher) {
            whenever(
                repository.startSession(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                ),
            ).thenReturn("session-1", "session-2", "session-3")

            var positionMs = 0L
            var chapterIndex = 4
            val tracker =
                ListeningSessionTracker(
                    repository = repository,
                    scope = scope,
                    ioDispatcher = dispatcher,
                    getCurrentBookId = { "book-1" },
                    getCurrentPositionMs = { positionMs },
                    getCurrentSpeed = { 1f },
                    getCurrentChapterIndex = { chapterIndex },
                    getCurrentDurationMs = { 600_000L },
                )

            tracker.onPlaybackStarted()
            scope.advanceUntilIdle()
            positionMs = 300_000L
            tracker.onPlaybackStopped("pause")
            scope.advanceUntilIdle()

            // Second full-length session of the same chapter: already credited.
            positionMs = 300_000L
            tracker.onPlaybackStarted()
            scope.advanceUntilIdle()
            positionMs = 590_000L
            tracker.onPlaybackStopped("pause")
            scope.advanceUntilIdle()

            // Different chapter: floor met again, credits once more.
            chapterIndex = 5
            tracker.onPlaybackStarted()
            scope.advanceUntilIdle()
            positionMs = 890_000L
            tracker.onPlaybackStopped("pause")
            scope.advanceUntilIdle()

            verify(repository, times(2)).finishSession(
                any(),
                any(),
                any(),
                any(),
                any(),
            )
            verify(repository, times(1)).discardSession(sessionId = eq("session-2"))
        }

    @Test
    fun `onPlaybackStopped discards a session that is still starting`() =
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
            tracker.onPlaybackStopped("pause")
            scope.advanceUntilIdle()

            verify(repository).discardSession(sessionId = eq("session-1"))
            verify(repository, never()).finishSession(
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }

    @Test
    fun `session close survives service scope cancellation`() =
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
            val serviceScope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + dispatcher)
            var positionMs = 0L
            val tracker =
                ListeningSessionTracker(
                    repository = repository,
                    scope = serviceScope,
                    ioDispatcher = dispatcher,
                    getCurrentBookId = { "book-1" },
                    getCurrentPositionMs = { positionMs },
                    getCurrentSpeed = { 1.5f },
                    getCurrentChapterIndex = { 4 },
                    getCurrentDurationMs = { 600_000L },
                )

            tracker.onPlaybackStarted()
            advanceUntilIdle()
            positionMs = 300_000L
            tracker.onPlaybackStopped("on_destroy")
            serviceScope.cancel()
            advanceUntilIdle()

            verify(repository).finishSession(
                sessionId = eq("session-1"),
                positionEndMs = eq(300_000L),
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

    @Test
    fun `late session creation for replaced book is discarded`() =
        runTest(dispatcher) {
            whenever(
                repository.startSession(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                ),
            ).thenAnswer { invocation -> "session-${invocation.getArgument<String>(0)}" }
            var bookId = "book-a"
            val tracker =
                ListeningSessionTracker(
                    repository = repository,
                    scope = scope,
                    ioDispatcher = dispatcher,
                    getCurrentBookId = { bookId },
                    getCurrentPositionMs = { 10_000L },
                    getCurrentSpeed = { 1f },
                    getCurrentChapterIndex = { 0 },
                )

            tracker.onPlaybackStarted()
            bookId = "book-b"
            tracker.onPlaybackStarted()
            scope.advanceUntilIdle()

            verify(repository).discardSession(sessionId = eq("session-book-a"))
        }
}
