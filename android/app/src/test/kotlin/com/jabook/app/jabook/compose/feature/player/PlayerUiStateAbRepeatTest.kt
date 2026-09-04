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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerUiStateAbRepeatTest {
    @Test
    fun `isValidABRepeatRange returns false for negative pointA`() {
        assertFalse(isValidABRepeatRange(pointA = -1L, pointB = 100L))
    }

    @Test
    fun `isValidABRepeatRange returns false for equal points`() {
        assertFalse(isValidABRepeatRange(pointA = 50L, pointB = 50L))
    }

    @Test
    fun `isValidABRepeatRange returns false for reversed points`() {
        assertFalse(isValidABRepeatRange(pointA = 100L, pointB = 50L))
    }

    @Test
    fun `isValidABRepeatRange returns true for valid range`() {
        assertTrue(isValidABRepeatRange(pointA = 0L, pointB = 100L))
    }

    @Test
    fun `isValidABRepeatRange returns true for zero A and positive B`() {
        assertTrue(isValidABRepeatRange(pointA = 0L, pointB = 1L))
    }

    @Test
    fun `ABRepeatState copy preserves chapterIndex`() {
        val original = ABRepeatState(pointA = 100L, pointB = 200L, chapterIndex = 5, phase = ABRepeatPhase.A_SET)
        val copy = original.copy(pointA = 300L)
        assertEquals(5, copy.chapterIndex)
        assertEquals(300L, copy.pointA)
        assertEquals(200L, copy.pointB)
    }

    @Test
    fun `ABRepeatState default values are inactive`() {
        val state = ABRepeatState()
        assertEquals(-1L, state.pointA)
        assertEquals(-1L, state.pointB)
        assertEquals(-1, state.chapterIndex)
        assertEquals(ABRepeatPhase.INACTIVE, state.phase)
    }
}
