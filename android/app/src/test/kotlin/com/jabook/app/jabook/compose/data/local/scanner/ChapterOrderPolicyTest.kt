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

package com.jabook.app.jabook.compose.data.local.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterOrderPolicyTest {
    @Test
    fun `comparator prioritizes track number over filename`() {
        val ordered =
            listOf(
                ChapterOrderCandidate(displayName = "03-file.mp3", trackNumber = 3),
                ChapterOrderCandidate(displayName = "01-file.mp3", trackNumber = 1),
                ChapterOrderCandidate(displayName = "02-file.mp3", trackNumber = 2),
            ).sortedWith(ChapterOrderPolicy.comparator())

        assertEquals(listOf("01-file.mp3", "02-file.mp3", "03-file.mp3"), ordered.map { it.displayName })
    }

    @Test
    fun `comparator falls back to numeric filename when track number is missing`() {
        val ordered =
            listOf(
                ChapterOrderCandidate(displayName = "10-end.mp3", trackNumber = null),
                ChapterOrderCandidate(displayName = "02-middle.mp3", trackNumber = null),
                ChapterOrderCandidate(displayName = "01-start.mp3", trackNumber = null),
            ).sortedWith(ChapterOrderPolicy.comparator())

        assertEquals(listOf("01-start.mp3", "02-middle.mp3", "10-end.mp3"), ordered.map { it.displayName })
    }

    @Test
    fun `comparator falls back to lexicographic order for non-numbered files`() {
        val ordered =
            listOf(
                ChapterOrderCandidate(displayName = "chapter-c.mp3", trackNumber = null),
                ChapterOrderCandidate(displayName = "chapter-a.mp3", trackNumber = null),
                ChapterOrderCandidate(displayName = "chapter-b.mp3", trackNumber = null),
            ).sortedWith(ChapterOrderPolicy.comparator())

        assertEquals(listOf("chapter-a.mp3", "chapter-b.mp3", "chapter-c.mp3"), ordered.map { it.displayName })
    }
}
