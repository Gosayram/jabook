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

import com.google.common.jimfs.Configuration
import com.google.common.jimfs.Jimfs
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class ScannerStructureIntegrationTest {
    @Test
    fun `jimfs layout with nested folders is classified as nested series`() {
        val fs = Jimfs.newFileSystem(Configuration.unix())
        val root = fs.getPath("/books")
        Files.createDirectories(root.resolve("BookOne"))
        Files.createDirectories(root.resolve("BookTwo"))
        Files.write(root.resolve("BookOne/01_intro.mp3"), "a".toByteArray())
        Files.write(root.resolve("BookTwo/01_intro.mp3"), "b".toByteArray())

        val result =
            BookStructureHeuristics.classify(
                fileNames = listAudioFileNames(root),
                hasNestedDirectories = hasNestedDirectories(root),
                singleFileDurationMs = null,
            )

        assertEquals(BookStructureType.NESTED_SERIES, result)
    }

    @Test
    fun `jimfs numbered files keep chapter ordering by filename numbers`() {
        val fs = Jimfs.newFileSystem(Configuration.unix())
        val root = fs.getPath("/book")
        Files.createDirectories(root)
        Files.write(root.resolve("10_end.mp3"), "x".toByteArray())
        Files.write(root.resolve("02_middle.mp3"), "x".toByteArray())
        Files.write(root.resolve("01_start.mp3"), "x".toByteArray())

        val ordered =
            listAudioFileNames(root)
                .map { fileName ->
                    ChapterOrderCandidate(displayName = fileName, trackNumber = null)
                }.sortedWith(ChapterOrderPolicy.comparator())
                .map { it.displayName }

        assertEquals(
            listOf("01_start.mp3", "02_middle.mp3", "10_end.mp3"),
            ordered,
        )
    }

    private fun listAudioFileNames(root: Path): List<String> =
        Files
            .walk(root)
            .use { paths ->
                paths
                    .filter(Files::isRegularFile)
                    .map { path -> path.fileName.toString() }
                    .filter { name ->
                        name.endsWith(".mp3", ignoreCase = true) ||
                            name.endsWith(".m4b", ignoreCase = true)
                    }.toList()
            }

    private fun hasNestedDirectories(root: Path): Boolean =
        Files
            .list(root)
            .use { entries ->
                entries.anyMatch { entry -> Files.isDirectory(entry) }
            }
}
