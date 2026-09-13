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

import com.jabook.app.jabook.compose.domain.model.BookmarkItem
import com.jabook.app.jabook.compose.domain.model.Chapter

internal object BookmarkMarkerPolicy {
    fun calculateBookmarkMarkerFractions(
        bookmarks: List<BookmarkItem>,
        chapters: List<Chapter>,
    ): List<Float> {
        if (chapters.isEmpty() || bookmarks.isEmpty()) return emptyList()
        val durations = chapters.map { it.duration.inWholeMilliseconds.coerceAtLeast(0L) }
        val totalDuration = durations.sum().coerceAtLeast(0L)
        if (totalDuration <= 0L) return emptyList()

        return bookmarks
            .asSequence()
            .mapNotNull { bookmark ->
                val chapterIdx = bookmark.chapterIndex.coerceIn(0, chapters.lastIndex)
                val chapterOffset = durations.take(chapterIdx).sumOf { it }
                val globalPositionMs =
                    (chapterOffset + bookmark.positionMs).coerceIn(0L, totalDuration)
                (globalPositionMs.toFloat() / totalDuration.toFloat())
                    .takeIf { it.isFinite() && it >= 0f && it <= 1f }
            }.sorted()
            .distinct()
            .toList()
    }

    fun isDuplicateBookmark(
        existing: List<BookmarkItem>,
        chapterIndex: Int,
        positionMs: Long,
        thresholdMs: Long = 5000L,
    ): Boolean =
        existing.any { bm ->
            bm.chapterIndex == chapterIndex &&
                kotlin.math.abs(bm.positionMs - positionMs) < thresholdMs
        }
}
