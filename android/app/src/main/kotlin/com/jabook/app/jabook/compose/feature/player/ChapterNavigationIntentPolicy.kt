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

/**
 * Interprets chapter-navigation button intent based on current chapter context.
 *
 * Primary UX goals:
 * - Near chapter end, "next" should deterministically move to the next chapter.
 * - Near chapter start, accidental "next" tap is interpreted as "previous chapter".
 */
internal object ChapterNavigationIntentPolicy {
    private const val DEFAULT_NEAR_END_THRESHOLD_MS: Long = 5_000L
    private const val DEFAULT_NEAR_START_THRESHOLD_MS: Long = 3_000L

    fun resolve(
        intent: PlayerIntent,
        state: PlayerState.Active,
        nearEndThresholdMs: Long = DEFAULT_NEAR_END_THRESHOLD_MS,
        nearStartThresholdMs: Long = DEFAULT_NEAR_START_THRESHOLD_MS,
    ): ChapterNavigationDecision {
        if (intent != PlayerIntent.SkipNext) return ChapterNavigationDecision(intent = intent)
        if (state.chapters.isEmpty()) return ChapterNavigationDecision(intent = intent)

        val currentIndex = state.currentChapterIndex.coerceIn(0, state.chapters.lastIndex)
        val canMovePrev = currentIndex > 0
        val canMoveNext = currentIndex < state.chapters.lastIndex
        val nearStart = state.currentPosition <= nearStartThresholdMs
        val chapterDurationMs = state.currentChapter?.duration?.inWholeMilliseconds
        val nearEnd =
            chapterDurationMs != null &&
                chapterDurationMs > 0L &&
                state.currentPosition >= (chapterDurationMs - nearEndThresholdMs).coerceAtLeast(0L)

        val targetIndex =
            when {
                nearStart && canMovePrev -> currentIndex - 1
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
