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

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [PlaybackSpeedSheet] utility functions.
 *
 * Covers:
 * - addRecentSpeed: maintains max 3 unique recent speeds
 */
class PlaybackSpeedSheetTest {
    // --- addRecentSpeed ---

    @Test
    fun `addRecentSpeed adds new speed to empty list`() {
        val recentSpeeds = mutableListOf<Float>()
        addRecentSpeed(recentSpeeds, 1.5f)
        assertEquals(listOf(1.5f), recentSpeeds)
    }

    @Test
    fun `addRecentSpeed adds to front of list`() {
        val recentSpeeds = mutableListOf(1.0f, 1.25f, 1.5f)
        addRecentSpeed(recentSpeeds, 2.0f)
        assertEquals(listOf(2.0f, 1.0f, 1.25f), recentSpeeds)
        assertEquals(3, recentSpeeds.size)
    }

    @Test
    fun `addRecentSpeed removes duplicates and moves to front`() {
        val recentSpeeds = mutableListOf(1.0f, 1.5f, 2.0f)
        addRecentSpeed(recentSpeeds, 1.5f)
        assertEquals(listOf(1.5f, 1.0f, 2.0f), recentSpeeds)
    }

    @Test
    fun `addRecentSpeed trims to max 3 speeds`() {
        val recentSpeeds = mutableListOf(1.0f, 1.25f, 1.5f)
        addRecentSpeed(recentSpeeds, 1.75f)
        assertEquals(listOf(1.75f, 1.0f, 1.25f), recentSpeeds)
        assertEquals(3, recentSpeeds.size)
    }

    @Test
    fun `addRecentSpeed handles floating point precision`() {
        val recentSpeeds = mutableListOf(1.5001f, 1.25f)
        addRecentSpeed(recentSpeeds, 1.5f) // Should be treated as same as 1.5001
        assertEquals(2, recentSpeeds.size) // Removed old, added new
    }
}
