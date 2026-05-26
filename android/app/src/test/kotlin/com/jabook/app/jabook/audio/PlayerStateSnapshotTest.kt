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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerStateSnapshotTest {
    // --- Progress ---

    @Test
    fun `progress is zero when duration is zero`() {
        val snapshot = PlayerStateSnapshot.EMPTY
        assertEquals(0.0f, snapshot.progress, 0.001f)
    }

    @Test
    fun `progress at midpoint`() {
        val snapshot = PlayerStateSnapshot.EMPTY.copy(currentPositionMs = 5_000L, durationMs = 10_000L)
        assertEquals(0.5f, snapshot.progress, 0.001f)
    }

    @Test
    fun `progress clamped to 1_0`() {
        val snapshot = PlayerStateSnapshot.EMPTY.copy(currentPositionMs = 15_000L, durationMs = 10_000L)
        assertEquals(1.0f, snapshot.progress, 0.001f)
    }

    // --- isAtEnd ---

    @Test
    fun `isAtEnd when position equals duration`() {
        val snapshot = PlayerStateSnapshot.EMPTY.copy(currentPositionMs = 10_000L, durationMs = 10_000L)
        assertTrue(snapshot.isAtEnd)
    }

    @Test
    fun `isAtEnd false when not at end`() {
        val snapshot = PlayerStateSnapshot.EMPTY.copy(currentPositionMs = 5_000L, durationMs = 10_000L)
        assertFalse(snapshot.isAtEnd)
    }

    // --- isSleepTimerActive ---

    @Test
    fun `sleep timer active with positive seconds`() {
        val snapshot = PlayerStateSnapshot.EMPTY.copy(sleepTimerRemainingSeconds = 300)
        assertTrue(snapshot.isSleepTimerActive)
    }

    @Test
    fun `sleep timer inactive with null`() {
        val snapshot = PlayerStateSnapshot.EMPTY.copy(sleepTimerRemainingSeconds = null)
        assertFalse(snapshot.isSleepTimerActive)
    }

    @Test
    fun `sleep timer inactive with zero`() {
        val snapshot = PlayerStateSnapshot.EMPTY.copy(sleepTimerRemainingSeconds = 0)
        assertFalse(snapshot.isSleepTimerActive)
    }

    // --- EMPTY ---

    @Test
    fun `EMPTY has correct defaults`() {
        assertFalse(PlayerStateSnapshot.EMPTY.isPlaying)
        assertEquals(0L, PlayerStateSnapshot.EMPTY.currentPositionMs)
        assertEquals(0L, PlayerStateSnapshot.EMPTY.durationMs)
        assertEquals(0, PlayerStateSnapshot.EMPTY.currentTrackIndex)
        assertEquals(1.0f, PlayerStateSnapshot.EMPTY.playbackSpeed, 0.001f)
        assertNull(PlayerStateSnapshot.EMPTY.sleepTimerRemainingSeconds)
    }
}
