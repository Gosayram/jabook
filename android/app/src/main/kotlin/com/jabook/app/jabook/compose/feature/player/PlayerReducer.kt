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
 * Pure reducer helpers for player state transitions.
 */
public object PlayerReducer {
    public fun nextChapterRepeatMode(current: ChapterRepeatMode): ChapterRepeatMode =
        when (current) {
            ChapterRepeatMode.OFF -> ChapterRepeatMode.ONCE
            ChapterRepeatMode.ONCE -> ChapterRepeatMode.INFINITE
            ChapterRepeatMode.INFINITE -> ChapterRepeatMode.OFF
        }

    public fun reduceChapterEnded(
        mode: ChapterRepeatMode,
        hasRepeatedOnce: Boolean,
    ): ChapterEndReduction =
        when (mode) {
            ChapterRepeatMode.OFF -> ChapterEndReduction(shouldRepeat = false, hasRepeatedOnce = false)
            ChapterRepeatMode.ONCE -> {
                if (!hasRepeatedOnce) {
                    ChapterEndReduction(shouldRepeat = true, hasRepeatedOnce = true)
                } else {
                    ChapterEndReduction(shouldRepeat = false, hasRepeatedOnce = false)
                }
            }
            ChapterRepeatMode.INFINITE -> ChapterEndReduction(shouldRepeat = true, hasRepeatedOnce = hasRepeatedOnce)
        }

    public fun reduceChapterChanged(): Boolean = false
}

public data class ChapterEndReduction(
    val shouldRepeat: Boolean,
    val hasRepeatedOnce: Boolean,
)
