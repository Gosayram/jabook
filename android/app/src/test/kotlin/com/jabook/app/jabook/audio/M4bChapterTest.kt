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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class M4bChapterTest {
    // --- endMs ---

    @Test
    fun `endMs calculated from start and duration`() {
        val chapter = M4bChapter(index = 0, startMs = 10_000, title = "Ch1", durationMs = 60_000)
        assertEquals(70_000L, chapter.endMs)
    }

    @Test
    fun `endMs null when duration unknown`() {
        val chapter = M4bChapter(index = 0, startMs = 10_000, title = "Ch1")
        assertNull(chapter.endMs)
    }

    // --- isLast ---

    @Test
    fun `isLast true when duration unknown`() {
        val chapter = M4bChapter(index = 0, startMs = 0, title = "Ch1")
        assertTrue(chapter.isLast)
    }

    // --- formatDuration ---

    @Test
    fun `formatDuration for hours`() {
        val chapter = M4bChapter(index = 0, startMs = 0, title = "Ch1", durationMs = 3_600_000)
        assertEquals("1h 0m", chapter.formatDuration())
    }

    @Test
    fun `formatDuration for minutes`() {
        val chapter = M4bChapter(index = 0, startMs = 0, title = "Ch1", durationMs = 150_000)
        assertEquals("2m 30s", chapter.formatDuration())
    }

    @Test
    fun `formatDuration for ongoing`() {
        val chapter = M4bChapter(index = 0, startMs = 0, title = "Ch1")
        assertEquals("ongoing", chapter.formatDuration())
    }

    // --- formatStart ---

    @Test
    fun `formatStart with hours`() {
        val chapter = M4bChapter(index = 0, startMs = 3_661_000, title = "Ch1")
        assertEquals("1:01:01", chapter.formatStart())
    }

    @Test
    fun `formatStart without hours`() {
        val chapter = M4bChapter(index = 0, startMs = 90_000, title = "Ch1")
        assertEquals("1:30", chapter.formatStart())
    }

    // --- calculateDurations ---

    @Test
    fun `calculateDurations computes durations from gaps`() {
        val chapters =
            listOf(
                M4bChapter(index = 0, startMs = 0, title = "Ch1"),
                M4bChapter(index = 1, startMs = 60_000, title = "Ch2"),
                M4bChapter(index = 2, startMs = 150_000, title = "Ch3"),
            )
        val result = M4bChapter.calculateDurations(chapters)
        assertEquals(60_000L, result[0].durationMs)
        assertEquals(90_000L, result[1].durationMs)
        assertNull(result[2].durationMs)
    }

    // --- generateTitle ---

    @Test
    fun `generateTitle creates correct title`() {
        assertEquals("Глава 1", M4bChapter.generateTitle(0))
        assertEquals("Глава 5", M4bChapter.generateTitle(4))
    }
}
