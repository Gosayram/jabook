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

class DetectedChapterTest {
    // --- durationMs ---

    @Test
    fun `durationMs calculated correctly`() {
        val chapter = DetectedChapter(index = 0, startMs = 0, endMs = 60_000, title = "Chapter 1")
        assertEquals(60_000L, chapter.durationMs)
    }

    @Test
    fun `durationMs null for last chapter`() {
        val chapter = DetectedChapter(index = 0, startMs = 0, endMs = null, title = "Chapter 1")
        assertNull(chapter.durationMs)
    }

    // --- isLast ---

    @Test
    fun `isLast true when endMs is null`() {
        val chapter = DetectedChapter(index = 0, startMs = 0, endMs = null, title = "Chapter 1")
        assertTrue(chapter.isLast)
    }

    @Test
    fun `isLast false when endMs is set`() {
        val chapter = DetectedChapter(index = 0, startMs = 0, endMs = 60_000, title = "Chapter 1")
        assertFalse(chapter.isLast)
    }

    // --- formatDuration ---

    @Test
    fun `formatDuration for hours`() {
        val chapter = DetectedChapter(index = 0, startMs = 0, endMs = 3_600_000, title = "Chapter 1")
        assertEquals("1h 0m", chapter.formatDuration())
    }

    @Test
    fun `formatDuration for minutes`() {
        val chapter = DetectedChapter(index = 0, startMs = 0, endMs = 150_000, title = "Chapter 1")
        assertEquals("2m", chapter.formatDuration())
    }

    @Test
    fun `formatDuration for seconds`() {
        val chapter = DetectedChapter(index = 0, startMs = 0, endMs = 30_000, title = "Chapter 1")
        assertEquals("30s", chapter.formatDuration())
    }

    @Test
    fun `formatDuration for ongoing`() {
        val chapter = DetectedChapter(index = 0, startMs = 0, endMs = null, title = "Chapter 1")
        assertEquals("ongoing", chapter.formatDuration())
    }

    // --- generateTitles ---

    @Test
    fun `generateTitles creates correct titles`() {
        val titles = DetectedChapter.generateTitles(3)
        assertEquals(3, titles.size)
        assertEquals("Глава 1", titles[0])
        assertEquals("Глава 2", titles[1])
        assertEquals("Глава 3", titles[2])
    }

    // --- Constants ---

    @Test
    fun `MIN_SILENCE_MS is 2 seconds`() {
        assertEquals(2000L, DetectedChapter.MIN_SILENCE_MS)
    }
}
