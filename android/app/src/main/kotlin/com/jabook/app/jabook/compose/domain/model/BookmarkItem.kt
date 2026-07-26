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

package com.jabook.app.jabook.compose.domain.model

import androidx.compose.runtime.Immutable

@Immutable
public data class BookmarkItem(
    val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val positionMs: Long,
    val normalizedPosition: Float = 0f,
    val noteText: String? = null,
    val noteAudioPath: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /**
     * Resolves an absolute seek position against a (possibly changed) chapter duration.
     *
     * After a library re-scan the chapter file may differ, making [positionMs] stale. When a valid
     * [normalizedPosition] was captured at creation time we prefer it; otherwise we fall back to the
     * raw absolute position. Returns 0 when neither is usable.
     */
    public fun resolvePositionMs(chapterDurationMs: Long): Long {
        if (normalizedPosition in 0.0001f..1f && chapterDurationMs > 0L) {
            return (normalizedPosition * chapterDurationMs.toFloat()).toLong().coerceAtLeast(0L)
        }
        return positionMs.coerceAtLeast(0L)
    }
}
