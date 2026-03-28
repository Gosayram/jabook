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

class BookStructureHeuristicsTest {
    @Test
    fun `classify returns single file for large standalone audiobook`() {
        val result =
            BookStructureHeuristics.classify(
                fileNames = listOf("book.m4b"),
                hasNestedDirectories = false,
                singleFileDurationMs = 45 * 60 * 1000L,
            )

        assertEquals(BookStructureType.SINGLE_FILE, result)
    }

    @Test
    fun `classify returns numbered files for chapter-like naming`() {
        val result =
            BookStructureHeuristics.classify(
                fileNames = listOf("01_intro.mp3", "02_chapter.mp3", "03_final.mp3"),
                hasNestedDirectories = false,
                singleFileDurationMs = null,
            )

        assertEquals(BookStructureType.NUMBERED_FILES, result)
    }

    @Test
    fun `classify returns nested series when directory has subfolders`() {
        val result =
            BookStructureHeuristics.classify(
                fileNames = listOf("part1.mp3"),
                hasNestedDirectories = true,
                singleFileDurationMs = 10_000L,
            )

        assertEquals(BookStructureType.NESTED_SERIES, result)
    }
}
