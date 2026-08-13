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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class BookmarkVoiceNoteFilesTest {
    @Test
    fun `discardBookmarkVoiceNote deletes only pending app-private recordings`() {
        val filesDir = Files.createTempDirectory("jabook-files").toFile()
        try {
            val noteDirectory = bookmarkVoiceNoteDirectory(filesDir).apply { mkdirs() }
            val pendingNote = File(noteDirectory, "pending.m4a").apply { writeText("note") }
            val unrelatedFile = File(filesDir, "unrelated.m4a").apply { writeText("keep") }

            discardBookmarkVoiceNote(filesDir, pendingNote.path)
            discardBookmarkVoiceNote(filesDir, unrelatedFile.path)

            assertFalse(pendingNote.exists())
            assertTrue(unrelatedFile.exists())
        } finally {
            filesDir.deleteRecursively()
        }
    }
}
