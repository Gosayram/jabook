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

import androidx.media3.session.MediaConstants
import org.junit.Assert.assertEquals
import org.junit.Test

class CompletionStatusHelperTest {
    @Test
    fun `calculateCompletionPercentage returns 0 for zero duration`() {
        assertEquals(0.0, CompletionStatusHelper.calculateCompletionPercentage(5000L, 0L), 0.0001)
    }

    @Test
    fun `calculateCompletionPercentage returns 0 for negative duration`() {
        assertEquals(0.0, CompletionStatusHelper.calculateCompletionPercentage(5000L, -100L), 0.0001)
    }

    @Test
    fun `calculateCompletionPercentage returns 0 for zero position`() {
        assertEquals(0.0, CompletionStatusHelper.calculateCompletionPercentage(0L, 60000L), 0.0001)
    }

    @Test
    fun `calculateCompletionPercentage returns half for midpoint position`() {
        assertEquals(0.5, CompletionStatusHelper.calculateCompletionPercentage(30000L, 60000L), 0.0001)
    }

    @Test
    fun `calculateCompletionPercentage returns full for complete position`() {
        assertEquals(1.0, CompletionStatusHelper.calculateCompletionPercentage(60000L, 60000L), 0.0001)
    }

    @Test
    fun `calculateCompletionPercentage caps at 1 for over position`() {
        assertEquals(1.0, CompletionStatusHelper.calculateCompletionPercentage(70000L, 60000L), 0.0001)
    }

    @Test
    fun `calculateCompletionPercentageWithTracks returns 0 for empty list`() {
        assertEquals(0.0, CompletionStatusHelper.calculateCompletionPercentageWithTracks(0, 1000L, emptyList()), 0.0001)
    }

    @Test
    fun `calculateCompletionPercentageWithTracks calculates first track progress`() {
        val result =
            CompletionStatusHelper.calculateCompletionPercentageWithTracks(
                0,
                30000L,
                listOf(60000L, 60000L),
            )
        assertEquals(0.25, result, 0.0001) // 30k / 120k
    }

    @Test
    fun `calculateCompletionPercentageWithTracks calculates second track progress`() {
        val result =
            CompletionStatusHelper.calculateCompletionPercentageWithTracks(
                1,
                30000L,
                listOf(60000L, 60000L),
            )
        assertEquals(0.75, result, 0.0001) // (60k + 30k) / 120k
    }

    @Test
    fun `getCompletionStatus returns not played for 0 percent`() {
        assertEquals(
            MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED,
            CompletionStatusHelper.getCompletionStatus(0.0),
        )
    }

    @Test
    fun `getCompletionStatus returns not played for small percent`() {
        assertEquals(
            MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED,
            CompletionStatusHelper.getCompletionStatus(0.005),
        )
    }

    @Test
    fun `getCompletionStatus returns partially played for moderate percent`() {
        assertEquals(
            MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED,
            CompletionStatusHelper.getCompletionStatus(0.5),
        )
    }

    @Test
    fun `getCompletionStatus returns fully played for high percent`() {
        assertEquals(
            MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED,
            CompletionStatusHelper.getCompletionStatus(0.95),
        )
    }
}
