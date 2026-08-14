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
    fun resolvePreviousAction(
        chapters: List<Chapter>,
        currentChapterIndex: Int,
        currentChapterPositionMs: Long,
    ): ChapterNavigationAction {
        if (chapters.isEmpty()) return ChapterNavigationAction.RestartCurrentChapter(0)

        val safeIndex = currentChapterIndex.coerceIn(0, chapters.lastIndex)

        if (currentChapterPositionMs >= NEAR_END_THRESHOLD_MS) {
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

internal object ChapterNavigationIntentPolicy {
    fun resolve(
        intent: PlayerIntent,
        state: PlayerState.Active,
        nearEndThresholdMs: Long = NEAR_END_THRESHOLD_MS,
    ): ChapterNavigationDecision {
        if (intent != PlayerIntent.SkipNext) return ChapterNavigationDecision(intent = intent)
        if (state.chapters.isEmpty()) return ChapterNavigationDecision(intent = intent)

        val currentIndex = state.currentChapterIndex.coerceIn(0, state.chapters.lastIndex)
        val canMoveNext = currentIndex < state.chapters.lastIndex
        val chapterDurationMs = state.currentChapter?.duration?.inWholeMilliseconds
        val nearEnd =
            chapterDurationMs != null &&
                chapterDurationMs > 0L &&
                state.currentPosition >= (chapterDurationMs - nearEndThresholdMs).coerceAtLeast(0L)

        val targetIndex =
            when {
                nearEnd && canMoveNext -> currentIndex + 1
                else -> null
            } ?: return ChapterNavigationDecision(intent = intent)

        return ChapterNavigationDecision(
            intent = PlayerIntent.SelectChapter(targetIndex),
            movedToChapterDisplayIndex = targetIndex + 1,
            undoChapterIndex = currentIndex,
        )
    }
}

internal data class ChapterNavigationDecision(
    val intent: PlayerIntent,
    val movedToChapterDisplayIndex: Int? = null,
    val undoChapterIndex: Int? = null,
)

private const val NEAR_END_THRESHOLD_MS = 5_000L
