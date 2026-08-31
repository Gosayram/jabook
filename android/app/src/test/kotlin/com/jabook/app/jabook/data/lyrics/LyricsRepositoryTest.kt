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

package com.jabook.app.jabook.data.lyrics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LyricsRepositoryTest {
    @Test
    fun `getLyrics returns empty when no sidecar lrc file exists`() =
        runBlocking {
            val mediaFile = File(Files.createTempDirectory("jabook-lyrics-test").toFile(), "chapter.mp3")

            try {
                assertTrue(LyricsRepository().getLyrics(mediaFile.path).isEmpty())
            } finally {
                mediaFile.parentFile?.delete()
            }
        }
}
