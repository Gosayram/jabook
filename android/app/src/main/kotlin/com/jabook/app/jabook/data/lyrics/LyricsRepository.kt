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
import com.jabook.app.jabook.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
public class LyricsRepository
    @Inject
    constructor() {
        /**
         * Tries to find lyrics for the given media file path.
         * 1. Checks for a .lrc file with the same name in the same directory.
         * 2. Returns no lyrics when no sidecar file exists.
         */
        public suspend fun getLyrics(mediaPath: String?): List<LyricLine> =
            withContext(Dispatchers.IO) {
                if (mediaPath == null) return@withContext emptyList()

                // 1. Check local .lrc file
                val mediaFile = File(mediaPath)
                val lrcFile = File(mediaFile.parent, mediaFile.nameWithoutExtension + ".lrc")

                if (lrcFile.exists()) {
                    try {
                        return@withContext LrcParser.parse(lrcFile.readText())
                    } catch (e: Exception) {
                        LogUtils.e("LyricsRepository", "Failed to parse lyrics file: ${lrcFile.name}", e)
                    }
                }

                emptyList()
            }
    }
