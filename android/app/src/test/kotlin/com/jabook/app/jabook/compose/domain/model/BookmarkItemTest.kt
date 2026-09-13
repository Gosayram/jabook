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

import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkItemTest {
    @Test
    fun `resolvePositionMs preserves relative bookmark location after chapter duration changes`() {
        val bookmark =
            BookmarkItem(
                id = "bookmark",
                bookId = "book",
                chapterIndex = 1,
                positionMs = 30_000L,
                normalizedPosition = 0.5f,
                createdAt = 0L,
                updatedAt = 0L,
            )

        assertEquals(45_000L, bookmark.resolvePositionMs(chapterDurationMs = 90_000L))
    }
}
