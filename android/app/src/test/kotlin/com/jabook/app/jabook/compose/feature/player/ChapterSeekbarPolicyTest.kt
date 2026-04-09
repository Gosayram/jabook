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
import io.kotest.property.Arb
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class ChapterSeekbarPolicyTest {
    private val chapters =
        listOf(
            chapter(index = 0, durationMs = 1_000L),
            chapter(index = 1, durationMs = 2_000L),
            chapter(index = 2, durationMs = 3_000L),
        )

    @Test
    fun `buildTimeline returns total duration and markers`() {
        val timeline =
            ChapterSeekbarPolicy.buildTimeline(
                chapters = chapters,
                currentChapterIndex = 1,
                currentChapterPositionMs = 500L,
            )

        assertEquals(6_000L, timeline.totalDurationMs)
        assertEquals(1_500L, timeline.globalPositionMs)
        assertEquals(2, timeline.chapterMarkersFractions.size)
        assertEquals(1f / 6f, timeline.chapterMarkersFractions[0], 0.0001f)
        assertEquals(3f / 6f, timeline.chapterMarkersFractions[1], 0.0001f)
    }

    @Test
    fun `resolveSeekTarget maps progress into chapter index and local position`() {
        val target = ChapterSeekbarPolicy.resolveSeekTarget(chapters, progress = 0.60f)

        assertEquals(2, target.chapterIndex)
        assertEquals(600L, target.chapterPositionMs)
    }

    @Test
    fun `resolveSeekTarget clamps invalid progress to start`() {
        val target = ChapterSeekbarPolicy.resolveSeekTarget(chapters, progress = Float.NaN)

        assertEquals(0, target.chapterIndex)
        assertEquals(0L, target.chapterPositionMs)
    }

    @Test
    fun `buildTimeline handles empty chapters`() {
        val timeline =
            ChapterSeekbarPolicy.buildTimeline(
                chapters = emptyList(),
                currentChapterIndex = 0,
                currentChapterPositionMs = 0L,
            )

        assertEquals(0L, timeline.totalDurationMs)
        assertEquals(0L, timeline.globalPositionMs)
        assertTrue(timeline.chapterMarkersFractions.isEmpty())
    }

    @Test
    fun `property - buildTimeline keeps progress and markers in valid bounds`() {
        runBlocking {
            checkAll(
                Arb.list(Arb.long(min = 0L, max = 300_000L), range = 1..8),
                Arb.int(min = -10, max = 20),
                Arb.long(min = -120_000L, max = 600_000L),
            ) { durations, chapterIndex, chapterPositionMs ->
                val generatedChapters = durations.mapIndexed { index, duration -> chapter(index, duration) }
                val timeline =
                    ChapterSeekbarPolicy.buildTimeline(
                        chapters = generatedChapters,
                        currentChapterIndex = chapterIndex,
                        currentChapterPositionMs = chapterPositionMs,
                    )

                assertTrue(timeline.totalDurationMs >= 0L)
                assertTrue(timeline.globalPositionMs in 0L..timeline.totalDurationMs)
                assertTrue(timeline.progress in 0f..1f)
                assertTrue(timeline.chapterMarkersFractions.all { it in 0f..1f })
                assertTrue(timeline.chapterMarkersFractions.zipWithNext().all { (a, b) -> b > a })
            }
        }
    }

    @Test
    fun `property - resolveSeekTarget always returns in-range index and chapter position`() {
        runBlocking {
            checkAll(
                Arb.list(Arb.long(min = 0L, max = 300_000L), range = 1..8),
                Arb.float(min = -5f, max = 5f),
            ) { durations, progress ->
                val generatedChapters = durations.mapIndexed { index, duration -> chapter(index, duration) }
                val target = ChapterSeekbarPolicy.resolveSeekTarget(generatedChapters, progress)
                val safeIndex = target.chapterIndex
                assertTrue(safeIndex in generatedChapters.indices)

                val chapterDuration = generatedChapters[safeIndex].duration.inWholeMilliseconds.coerceAtLeast(0L)
                assertTrue(target.chapterPositionMs in 0L..chapterDuration)
            }
        }
    }

    private fun chapter(
        index: Int,
        durationMs: Long,
    ): Chapter =
        Chapter(
            id = "c$index",
            bookId = "b",
            title = "Chapter $index",
            chapterIndex = index,
            fileIndex = index,
            duration = durationMs.milliseconds,
            fileUrl = null,
            position = 0.milliseconds,
            isCompleted = false,
            isDownloaded = true,
        )
}
