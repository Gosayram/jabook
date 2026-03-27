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

import org.junit.Assert.assertEquals
import org.junit.Test

class BookCompletionIndexPolicyTest {
    @Test
    fun `keeps valid index unchanged`() {
        val resolved =
            BookCompletionIndexPolicy.resolveCompletionIndex(
                currentIndex = 3,
                totalTracks = 10,
                savedCompletedIndex = -1,
                currentPositionMs = 5000L,
                durationMs = 6000L,
            )

        assertEquals(3, resolved)
    }

    @Test
    fun `uses saved completed index for invalid current index`() {
        val resolved =
            BookCompletionIndexPolicy.resolveCompletionIndex(
                currentIndex = 0,
                totalTracks = 12,
                savedCompletedIndex = 11,
                currentPositionMs = 0L,
                durationMs = 0L,
            )

        assertEquals(11, resolved)
    }

    @Test
    fun `falls back to last index when position and duration indicate near-end`() {
        val resolved =
            BookCompletionIndexPolicy.resolveCompletionIndex(
                currentIndex = -1,
                totalTracks = 7,
                savedCompletedIndex = -1,
                currentPositionMs = 120_000L,
                durationMs = 121_000L,
            )

        assertEquals(6, resolved)
    }

    @Test
    fun `falls back to last index when current index is out of bounds`() {
        val resolved =
            BookCompletionIndexPolicy.resolveCompletionIndex(
                currentIndex = 9,
                totalTracks = 4,
                savedCompletedIndex = -1,
                currentPositionMs = 0L,
                durationMs = 0L,
            )

        assertEquals(3, resolved)
    }

    @Test
    fun `returns current index when no reliable fallback exists`() {
        val resolved =
            BookCompletionIndexPolicy.resolveCompletionIndex(
                currentIndex = -1,
                totalTracks = 5,
                savedCompletedIndex = -1,
                currentPositionMs = 0L,
                durationMs = 0L,
            )

        assertEquals(-1, resolved)
    }
}
