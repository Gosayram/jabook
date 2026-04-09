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

internal data class ChapterSeekTarget(
    val chapterIndex: Int,
    val chapterPositionMs: Long,
)

internal data class ChapterSeekbarTimeline(
    val totalDurationMs: Long,
    val globalPositionMs: Long,
    val chapterMarkersFractions: List<Float>,
) {
    val progress: Float
        get() =
            if (totalDurationMs > 0) {
                (globalPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
}

internal object ChapterSeekbarPolicy {
    fun buildTimeline(
        chapters: List<Chapter>,
        currentChapterIndex: Int,
        currentChapterPositionMs: Long,
    ): ChapterSeekbarTimeline {
        if (chapters.isEmpty()) {
            return ChapterSeekbarTimeline(
                totalDurationMs = 0L,
                globalPositionMs = 0L,
                chapterMarkersFractions = emptyList(),
            )
        }

        val durations = chapters.map { it.duration.inWholeMilliseconds.coerceAtLeast(0L) }
        val totalDuration = durations.sum().coerceAtLeast(0L)
        if (totalDuration <= 0L) {
            return ChapterSeekbarTimeline(
                totalDurationMs = 0L,
                globalPositionMs = 0L,
                chapterMarkersFractions = emptyList(),
            )
        }

        val safeChapterIndex = currentChapterIndex.coerceIn(0, chapters.lastIndex)
        val chapterOffset = durations.take(safeChapterIndex).sum()
        val localPosition = currentChapterPositionMs.coerceAtLeast(0L)
        val safeLocalPosition = localPosition.coerceAtMost(durations[safeChapterIndex])
        val globalPosition = (chapterOffset + safeLocalPosition).coerceIn(0L, totalDuration)

        val markers = mutableListOf<Float>()
        var cumulative = 0L
        for (i in chapters.indices) {
            if (i > 0) {
                val fraction = (cumulative.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                if (fraction > 0f && fraction < 1f) {
                    if (markers.lastOrNull() != fraction) {
                        markers += fraction
                    }
                }
            }
            cumulative += durations[i]
        }

        return ChapterSeekbarTimeline(
            totalDurationMs = totalDuration,
            globalPositionMs = globalPosition,
            chapterMarkersFractions = markers,
        )
    }

    fun resolveSeekTarget(
        chapters: List<Chapter>,
        progress: Float,
    ): ChapterSeekTarget {
        if (chapters.isEmpty()) {
            return ChapterSeekTarget(chapterIndex = 0, chapterPositionMs = 0L)
        }
        val durations = chapters.map { it.duration.inWholeMilliseconds.coerceAtLeast(0L) }
        val totalDuration = durations.sum().coerceAtLeast(0L)
        if (totalDuration <= 0L) {
            return ChapterSeekTarget(
                chapterIndex = 0,
                chapterPositionMs = 0L,
            )
        }

        val clampedProgress = progress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        val targetGlobalPosition = (clampedProgress * totalDuration.toFloat()).toLong().coerceIn(0L, totalDuration)

        var offset = 0L
        for (index in durations.indices) {
            val chapterDuration = durations[index]
            val nextOffset = offset + chapterDuration
            val isLast = index == durations.lastIndex
            if (targetGlobalPosition < nextOffset || isLast) {
                return ChapterSeekTarget(
                    chapterIndex = index,
                    chapterPositionMs = (targetGlobalPosition - offset).coerceAtLeast(0L).coerceAtMost(chapterDuration),
                )
            }
            offset = nextOffset
        }

        return ChapterSeekTarget(
            chapterIndex = durations.lastIndex,
            chapterPositionMs = durations.last(),
        )
    }
}
