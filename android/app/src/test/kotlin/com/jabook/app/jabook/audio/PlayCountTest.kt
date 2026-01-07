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

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * Unit tests for Play Count functionality in PlayerState.
 *
 * Note: Integration tests for PlayerPersistenceManager.incrementPlayCount
 * require Android instrumentation or Robolectric with real SharedPreferences.
 * These tests focus on the data model behavior.
 */
@RunWith(RobolectricTestRunner::class)
class PlayCountTest {
    @Test
    fun `PlayerState playCount defaults to 0`() {
        // Given: PlayerState with default values
        val state =
            PlayerState(
                bookId = "test",
                positionMs = 0,
                durationMs = 0,
                filePaths = emptyList(),
            )

        // Then: playCount is 0
        assertEquals(0, state.playCount)
    }

    @Test
    fun `PlayerState playCount can be set`() {
        // Given: PlayerState with playCount = 10
        val state =
            PlayerState(
                bookId = "test",
                positionMs = 0,
                durationMs = 0,
                filePaths = emptyList(),
                playCount = 10,
            )

        // Then: playCount is 10
        assertEquals(10, state.playCount)
    }

    @Test
    fun `PlayerState copy increments playCount correctly`() {
        // Given: PlayerState with playCount = 5
        val state =
            PlayerState(
                bookId = "book123",
                positionMs = 1000,
                durationMs = 60000,
                filePaths = listOf("/path/to/file.mp3"),
                playCount = 5,
            )

        // When: copy with incremented playCount
        val newState = state.copy(playCount = state.playCount + 1)

        // Then: playCount is 6
        assertEquals(6, newState.playCount)
        // And other fields are preserved
        assertEquals("book123", newState.bookId)
        assertEquals(1000, newState.positionMs)
    }

    @Test
    fun `PlayerState preserves all fields on copy`() {
        // Given: Fully populated PlayerState
        val state =
            PlayerState(
                bookId = "book456",
                positionMs = 5000,
                durationMs = 120000,
                filePaths = listOf("/a.mp3", "/b.mp3"),
                lastPlayedTimestamp = 1234567890L,
                completedTimestamp = 9876543210L,
                playCount = 42,
            )

        // When: copy with only playCount changed
        val newState = state.copy(playCount = 43)

        // Then: only playCount changed
        assertEquals("book456", newState.bookId)
        assertEquals(5000, newState.positionMs)
        assertEquals(120000, newState.durationMs)
        assertEquals(listOf("/a.mp3", "/b.mp3"), newState.filePaths)
        assertEquals(1234567890L, newState.lastPlayedTimestamp)
        assertEquals(9876543210L, newState.completedTimestamp)
        assertEquals(43, newState.playCount)
    }

    @Test
    fun `PlayerState playCount starts at 0 for new states`() {
        // Given: Minimal PlayerState
        val state =
            PlayerState(
                bookId = "new_book",
                positionMs = 0,
                durationMs = 0,
                filePaths = emptyList(),
            )

        // Then: Default values
        assertEquals(0, state.lastPlayedTimestamp)
        assertEquals(0, state.completedTimestamp)
        assertEquals(0, state.playCount)
    }

    @Test
    fun `playCount can be incremented multiple times`() {
        // Given: Initial state
        var state =
            PlayerState(
                bookId = "test",
                positionMs = 0,
                durationMs = 0,
                filePaths = emptyList(),
                playCount = 0,
            )

        // When: Increment 5 times
        repeat(5) {
            state = state.copy(playCount = state.playCount + 1)
        }

        // Then: playCount is 5
        assertEquals(5, state.playCount)
    }
}
