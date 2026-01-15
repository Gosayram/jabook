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

import com.jabook.app.jabook.compose.feature.player.lyrics.LrcParser
import com.jabook.app.jabook.compose.feature.player.lyrics.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
public class LyricsRepository @Inject constructor() {

    /**
     * Tries to find lyrics for the given media file path.
     * 1. Checks for a .lrc file with the same name in the same directory.
     * 2. (Optional) Could check embedded tags (not implemented yet).
     * 3. Returns a sample LRC for demo purposes if nothing found.
     */
    public suspend fun getLyrics(mediaPath: String?): List<LyricLine> = withContext(Dispatchers.IO) {
        if (mediaPath == null) return@withContext emptyList()

        // 1. Check local .lrc file
        val mediaFile = File(mediaPath)
        val lrcFile = File(mediaFile.parent, mediaFile.nameWithoutExtension + ".lrc")

        if (lrcFile.exists()) {
            try {
                return@withContext LrcParser.parse(lrcFile.readText())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Return Demo Lyrics if it matches our demo book (or just always for now to show off UI)
        return@withContext getDemoLyrics()
    }

    private fun getDemoLyrics(): List<LyricLine> {
        val demoLrc = """
            [00:00.00]Welcome to Jabook Audio
            [00:04.00]This is a demonstration of synchronized lyrics.
            [00:08.00]Imagine this is an audiobook chapter...
            [00:12.00]...or a song you love.
            [00:16.00]The text scrolls automatically.
            [00:20.00]You can tap any line to seek.
            [00:24.00]Designed with Jetpack Compose.
            [00:28.00]Inspired by OuterTune and Apple Music.
            [00:32.00]Enjoy your listening experience!
            [00:36.00](Music fades out)
        """.trimIndent()
        return LrcParser.parse(demoLrc)
    }
}
