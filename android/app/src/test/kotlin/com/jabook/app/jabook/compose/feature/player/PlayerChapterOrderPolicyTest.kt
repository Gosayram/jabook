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
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration

class PlayerChapterOrderPolicyTest {
    @Test
    fun `chapters use the same natural order as the playback playlist`() {
        val chapters =
            listOf(
                chapter(id = "10", path = "/book/10.mp3"),
                chapter(id = "2", path = "/book/2.mp3"),
                chapter(id = "1", path = "/book/01.mp3"),
                chapter(id = "10-copy", path = "/book/10.mp3"),
                chapter(id = "missing", path = null),
            )

        assertEquals(listOf("1", "2", "10", "10-copy"), sortChaptersForPlayback(chapters).map(Chapter::id))
    }

    private fun chapter(
        id: String,
        path: String?,
    ): Chapter =
        Chapter(
            id = id,
            bookId = "book",
            title = id,
            chapterIndex = 0,
            fileIndex = 0,
            duration = Duration.ZERO,
            fileUrl = path,
            position = Duration.ZERO,
            isCompleted = false,
            isDownloaded = true,
        )
}
