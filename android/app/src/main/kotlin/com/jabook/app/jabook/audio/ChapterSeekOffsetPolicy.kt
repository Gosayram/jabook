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

/**
 * Applies a small backtrack when seeking to chapter boundaries.
 *
 * Audio codecs can land slightly after the exact requested point on seek, so this
 * guard helps avoid clipping first syllables at chapter starts.
 */
internal object ChapterSeekOffsetPolicy {
    private const val CHAPTER_SEEK_BACKTRACK_MS: Long = 300L

    fun adjust(positionMs: Long): Long {
        if (positionMs <= 0L) {
            return 0L
        }
        return (positionMs - CHAPTER_SEEK_BACKTRACK_MS).coerceAtLeast(0L)
    }
}
