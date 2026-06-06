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

import com.jabook.app.jabook.compose.domain.model.Chapter
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class ChapterNavigationPolicyTest {
    private fun chapter(
        index: Int,
        durationMs: Long,
    ) = Chapter(
        id = "ch-$index",
        bookId = "book-1",
        title = "Chapter $index",
        chapterIndex = index,
        fileIndex = index,
        duration = durationMs.milliseconds,
        fileUrl = null,
        position = 0L.milliseconds,
        isCompleted = false,
        isDownloaded = false,
    )

    private val chapters =
        listOf(
            chapter(0, 60_000L),
            chapter(1, 60_000L),
            chapter(2, 60_000L),
        )

    // --- resolvePreviousAction ---

    @Test
    fun `previous restarts current chapter when past threshold`() {
        val action =
            ChapterNavigationPolicy.resolvePreviousAction(
                chapters = chapters,
                currentChapterIndex = 1,
                currentChapterPositionMs = 10_000L,
            )
        assertEquals(ChapterNavigationAction.RestartCurrentChapter(1), action)
    }

    @Test
    fun `previous jumps to previous chapter when near start`() {
        val action =
            ChapterNavigationPolicy.resolvePreviousAction(
                chapters = chapters,
                currentChapterIndex = 1,
                currentChapterPositionMs = 3_000L,
            )
        assertEquals(ChapterNavigationAction.JumpToChapter(0), action)
    }

    @Test
    fun `previous restarts chapter 0 when at start of first chapter`() {
        val action =
            ChapterNavigationPolicy.resolvePreviousAction(
                chapters = chapters,
                currentChapterIndex = 0,
                currentChapterPositionMs = 1_000L,
            )
        assertEquals(ChapterNavigationAction.RestartCurrentChapter(0), action)
    }

    @Test
    fun `previous restarts chapter 0 when past threshold in first chapter`() {
        val action =
            ChapterNavigationPolicy.resolvePreviousAction(
                chapters = chapters,
                currentChapterIndex = 0,
                currentChapterPositionMs = 30_000L,
            )
        assertEquals(ChapterNavigationAction.RestartCurrentChapter(0), action)
    }

    @Test
    fun `previous handles empty chapters`() {
        val action =
            ChapterNavigationPolicy.resolvePreviousAction(
                chapters = emptyList(),
                currentChapterIndex = 0,
                currentChapterPositionMs = 0L,
            )
        assertEquals(ChapterNavigationAction.RestartCurrentChapter(0), action)
    }

    @Test
    fun `previous handles exactly at threshold`() {
        val action =
            ChapterNavigationPolicy.resolvePreviousAction(
                chapters = chapters,
                currentChapterIndex = 2,
                currentChapterPositionMs = 5000L,
            )
        assertEquals(ChapterNavigationAction.RestartCurrentChapter(2), action)
    }

    // --- resolveNextAction ---

    @Test
    fun `next jumps to next chapter when available`() {
        val action =
            ChapterNavigationPolicy.resolveNextAction(
                chapters = chapters,
                currentChapterIndex = 0,
            )
        assertEquals(ChapterNavigationAction.JumpToChapter(1), action)
    }

    @Test
    fun `next returns end of book at last chapter`() {
        val action =
            ChapterNavigationPolicy.resolveNextAction(
                chapters = chapters,
                currentChapterIndex = 2,
            )
        assertEquals(ChapterNavigationAction.EndOfBook, action)
    }

    @Test
    fun `next handles empty chapters`() {
        val action =
            ChapterNavigationPolicy.resolveNextAction(
                chapters = emptyList(),
                currentChapterIndex = 0,
            )
        assertEquals(ChapterNavigationAction.EndOfBook, action)
    }

    @Test
    fun `next handles single chapter`() {
        val singleChapter = listOf(chapter(0, 60_000L))
        val action =
            ChapterNavigationPolicy.resolveNextAction(
                chapters = singleChapter,
                currentChapterIndex = 0,
            )
        assertEquals(ChapterNavigationAction.EndOfBook, action)
    }

    @Test
    fun `next handles out of bounds index`() {
        val action =
            ChapterNavigationPolicy.resolveNextAction(
                chapters = chapters,
                currentChapterIndex = 10,
            )
        assertEquals(ChapterNavigationAction.EndOfBook, action)
    }

    @Test
    fun `previous handles out of bounds index`() {
        val action =
            ChapterNavigationPolicy.resolvePreviousAction(
                chapters = chapters,
                currentChapterIndex = 10,
                currentChapterPositionMs = 10_000L,
            )
        assertEquals(ChapterNavigationAction.RestartCurrentChapter(2), action)
    }
}
