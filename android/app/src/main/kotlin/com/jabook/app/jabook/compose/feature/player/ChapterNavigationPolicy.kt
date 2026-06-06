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

internal sealed class ChapterNavigationAction {
    data class RestartCurrentChapter(
        val chapterIndex: Int,
    ) : ChapterNavigationAction()

    data class JumpToChapter(
        val chapterIndex: Int,
    ) : ChapterNavigationAction()

    data object EndOfBook : ChapterNavigationAction()
}

internal object ChapterNavigationPolicy {
    private const val RESTART_THRESHOLD_MS = 5000L

    fun resolvePreviousAction(
        chapters: List<Chapter>,
        currentChapterIndex: Int,
        currentChapterPositionMs: Long,
    ): ChapterNavigationAction {
        if (chapters.isEmpty()) return ChapterNavigationAction.RestartCurrentChapter(0)

        val safeIndex = currentChapterIndex.coerceIn(0, chapters.lastIndex)

        if (currentChapterPositionMs > RESTART_THRESHOLD_MS) {
            return ChapterNavigationAction.RestartCurrentChapter(safeIndex)
        }

        val previousIndex = safeIndex - 1
        return if (previousIndex >= 0) {
            ChapterNavigationAction.JumpToChapter(previousIndex)
        } else {
            ChapterNavigationAction.RestartCurrentChapter(0)
        }
    }

    fun resolveNextAction(
        chapters: List<Chapter>,
        currentChapterIndex: Int,
    ): ChapterNavigationAction {
        if (chapters.isEmpty()) return ChapterNavigationAction.EndOfBook

        val safeIndex = currentChapterIndex.coerceIn(0, chapters.lastIndex)
        val nextIndex = safeIndex + 1

        return if (nextIndex <= chapters.lastIndex) {
            ChapterNavigationAction.JumpToChapter(nextIndex)
        } else {
            ChapterNavigationAction.EndOfBook
        }
    }
}
