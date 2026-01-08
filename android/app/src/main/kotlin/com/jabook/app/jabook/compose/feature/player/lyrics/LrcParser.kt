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

package com.jabook.app.jabook.compose.feature.player.lyrics

/**
 * Represents a single line of synchronized lyrics.
 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
)

/**
 * Validates and parses LRC format lyrics.
 * Supports standard format [mm:ss.xx] or [mm:ss.xxx].
 */
object LrcParser {
    private val TIME_TAG_REGEX = "\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})]".toRegex()

    fun parse(lrcContent: String): List<LyricLine> {
        val parsedLines = mutableListOf<LyricLine>()

        lrcContent.lines().forEach { line ->
            if (line.isBlank()) return@forEach

            // Extract all time tags from the line (some lines have multiple timestamps)
            // e.g. [00:12.00][00:24.00]Chorus line
            val matches = TIME_TAG_REGEX.findAll(line)
            if (matches.none()) return@forEach

            val text = line.replace(TIME_TAG_REGEX, "").trim()

            matches.forEach { match ->
                val (minStr, secStr, msStr) = match.destructured
                val minutes = minStr.toLongOrNull() ?: 0L
                val seconds = secStr.toLongOrNull() ?: 0L
                // Normalize milliseconds: .xx (hundredths) -> *10, .xxx (millis) -> *1
                val milliseconds =
                    if (msStr.length == 2) {
                        msStr.toLongOrNull()?.times(10) ?: 0L
                    } else {
                        msStr.toLongOrNull() ?: 0L
                    }

                val totalTimeMs = (minutes * 60 * 1000) + (seconds * 1000) + milliseconds
                parsedLines.add(LyricLine(totalTimeMs, text))
            }
        }

        return parsedLines.sortedBy { it.timeMs }
    }
}
