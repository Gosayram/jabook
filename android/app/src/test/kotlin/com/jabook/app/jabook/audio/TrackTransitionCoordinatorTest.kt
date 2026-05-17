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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TrackTransitionCoordinator].
 *
 * Tests cover track transition deduplication, pending deferred completion,
 * and edge cases (duplicate events within the dedup window, negative indices).
 */
class TrackTransitionCoordinatorTest {
    private var lastActualTrackIndex: Int = -1

    private fun createCoordinator(isPlaylistLoading: (() -> Boolean)? = null): TrackTransitionCoordinator =
        TrackTransitionCoordinator(
            isPlaylistLoading = isPlaylistLoading,
            updateActualTrackIndex = { lastActualTrackIndex = it },
        )

    @Test
    fun `handleTrackTransitionEvent updates track index`() {
        val coordinator = createCoordinator()
        coordinator.handleTrackTransitionEvent(3, "test")
        assertEquals(3, lastActualTrackIndex)
    }

    @Test
    fun `handleTrackTransitionEvent skips negative indices`() {
        val coordinator = createCoordinator()
        coordinator.handleTrackTransitionEvent(-1, "test")
        assertEquals(-1, lastActualTrackIndex)
    }

    @Test
    fun `handleTrackTransitionEvent deduplicates rapid transitions`() {
        val coordinator = createCoordinator()
        coordinator.handleTrackTransitionEvent(2, "first")
        coordinator.handleTrackTransitionEvent(2, "second")
        assertEquals(2, lastActualTrackIndex)
    }

    @Test
    fun `handleTrackTransitionEvent does not deduplicate different indices`() {
        val coordinator = createCoordinator()
        coordinator.handleTrackTransitionEvent(1, "first")
        coordinator.handleTrackTransitionEvent(2, "second")
        assertEquals(2, lastActualTrackIndex)
    }

    @Test
    fun `setPendingTrackSwitchDeferred completes on transition`() =
        runTest {
            val coordinator = createCoordinator()
            val deferred = CompletableDeferred<Int>()
            coordinator.setPendingTrackSwitchDeferred(deferred)

            coordinator.handleTrackTransitionEvent(5, "test")
            assertEquals(5, deferred.await())
        }

    @Test
    fun `clearPendingTrackSwitchDeferred cancels deferred`() {
        val coordinator = createCoordinator()
        val deferred = CompletableDeferred<Int>()
        coordinator.setPendingTrackSwitchDeferred(deferred)
        coordinator.clearPendingTrackSwitchDeferred()
        assertTrue(deferred.isCancelled)
    }

    @Test
    fun `handleTrackTransitionEvent skips index update during loading at index 0`() {
        var loading = true
        val coordinator = createCoordinator(isPlaylistLoading = { loading })
        coordinator.handleTrackTransitionEvent(0, "loading")
        assertEquals(-1, lastActualTrackIndex)
    }

    @Test
    fun `handleTrackTransitionEvent allows index update during loading at non-zero index`() {
        var loading = true
        val coordinator = createCoordinator(isPlaylistLoading = { loading })
        coordinator.handleTrackTransitionEvent(1, "loading")
        assertEquals(1, lastActualTrackIndex)
    }
}
