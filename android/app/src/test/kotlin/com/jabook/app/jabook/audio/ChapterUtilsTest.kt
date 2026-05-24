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

class ChapterUtilsTest {
    @Test
    fun `calculateChapterIndex returns zero for empty list`() {
        assertEquals(0, calculateChapterIndex(emptyList(), 100.0))
    }

    @Test
    fun `calculateChapterIndex returns first chapter for position before first chapter`() {
        val durations = listOf(300.0, 600.0, 450.0) // 5min, 10min, 7.5min
        assertEquals(0, calculateChapterIndex(durations, 100.0))
    }

    @Test
    fun `calculateChapterIndex returns correct chapter for middle position`() {
        val durations = listOf(300.0, 600.0, 450.0) // 5min, 10min, 7.5min
        assertEquals(1, calculateChapterIndex(durations, 650.0))
    }

    @Test
    fun `calculateChapterIndex returns last chapter for position beyond all chapters`() {
        val durations = listOf(300.0, 600.0, 450.0)
        assertEquals(2, calculateChapterIndex(durations, 5000.0))
    }

    @Test
    fun `calculateChapterIndex handles position exactly at chapter boundary`() {
        val durations = listOf(300.0, 600.0, 450.0)
        // With 0.1s tolerance, position 300.0 should be in chapter 1 (second chapter)
        assertEquals(1, calculateChapterIndex(durations, 300.0))
    }

    @Test
    fun `calculateChapterPosition returns zero for empty list`() {
        assertEquals(0.0, calculateChapterPosition(emptyList(), 100.0), 0.001)
    }

    @Test
    fun `calculateChapterPosition returns position within first chapter`() {
        val durations = listOf(300.0, 600.0, 450.0)
        assertEquals(100.0, calculateChapterPosition(durations, 100.0), 0.001)
    }

    @Test
    fun `calculateChapterPosition returns position within second chapter`() {
        val durations = listOf(300.0, 600.0, 450.0)
        // 650s total, chapter 1 ends at 900s, position within chapter = 650 - 300 = 350s
        assertEquals(350.0, calculateChapterPosition(durations, 650.0), 0.001)
    }

    @Test
    fun `calculateChapterPosition returns zero for position beyond all chapters`() {
        val durations = listOf(300.0, 600.0, 450.0)
        assertEquals(0.0, calculateChapterPosition(durations, 5000.0), 0.001)
    }

    // Millisecond versions
    @Test
    fun `calculateChapterIndexMs returns zero for empty list`() {
        assertEquals(0, calculateChapterIndexMs(emptyList(), 100_000L))
    }

    @Test
    fun `calculateChapterIndexMs returns correct chapter`() {
        val durations = listOf(300_000L, 600_000L, 450_000L) // 5min, 10min, 7.5min in ms
        assertEquals(0, calculateChapterIndexMs(durations, 100_000L))
        assertEquals(1, calculateChapterIndexMs(durations, 650_000L))
        assertEquals(2, calculateChapterIndexMs(durations, 5_000_000L))
    }

    @Test
    fun `calculateChapterPositionMs returns zero for empty list`() {
        assertEquals(0L, calculateChapterPositionMs(emptyList(), 100_000L))
    }

    @Test
    fun `calculateChapterPositionMs returns correct position within chapter`() {
        val durations = listOf(300_000L, 600_000L, 450_000L)
        assertEquals(100_000L, calculateChapterPositionMs(durations, 100_000L))
        assertEquals(350_000L, calculateChapterPositionMs(durations, 650_000L))
    }
}
