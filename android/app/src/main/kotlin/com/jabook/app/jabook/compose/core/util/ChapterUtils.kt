// Copyright 2025 Jabook Contributors
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

package com.jabook.app.jabook.compose.core.util

import com.jabook.app.jabook.compose.domain.model.Chapter

/**
 * Utilities for parsing and formatting chapter information.
 */
object ChapterUtils {
    // Regex patterns for extracting chapter numbers
    private val CHAPTER_NUMBER_PATTERNS =
        listOf(
            // Matches: "001", "01", "1" at start or after common separators
            Regex("""(?:^|[_\-\s.])(\d{1,4})(?:[_\-\s.]|$)"""),
            // Matches: "Chapter 01", "Глава 001", "Ch 1"
            Regex("""(?:chapter|глава|ch|гл)[_\-\s.]*(\d{1,4})""", RegexOption.IGNORE_CASE),
        )

    /**
     * Extract chapter number from filename.
     * Tries multiple patterns to find numbers in various formats.
     *
     * @param filename The chapter filename
     * @param fallbackIndex Fallback index if no number found
     * @return Extracted chapter number or fallbackIndex + 1
     */
    fun extractChapterNumber(
        filename: String,
        fallbackIndex: Int,
    ): Int {
        for (pattern in CHAPTER_NUMBER_PATTERNS) {
            val match = pattern.find(filename)
            if (match != null) {
                val numberStr = match.groupValues.getOrNull(1)
                numberStr?.toIntOrNull()?.let { return it }
            }
        }
        return fallbackIndex + 1
    }

    /**
     * Format chapter name for display.
     * Uses localization to show "Глава N" or "Chapter N".
     *
     * @param chapter The chapter object
     * @param index Chapter index (0-based)
     * @param useRussian Whether to use Russian localization
     * @return Formatted chapter name
     */
    fun formatChapterName(
        chapter: Chapter,
        index: Int,
        localizedPrefix: String,
    ): String {
        val number = extractChapterNumber(chapter.title, index)

        // Regex to match generic prefixes (Chapter, Track, etc) followed by a number
        // Matches: "Chapter 1", "Ch. 1", "Track 01", "Глава 1"
        // Group 1: Prefix
        // Group 2: Number
        // Group 3: Remainder (suffix)
        val genericPattern = Regex("""^(?:Chapter|Глава|Ch|Гл|Track|File|Audio)[ ._-]*(\d+)(.*)$""", RegexOption.IGNORE_CASE)
        val match = genericPattern.find(chapter.title)

        if (match != null) {
            val matchedNumber = match.groupValues[1]
            val suffix = match.groupValues[2]
            // If we found a match, reconstruction using localized prefix
            return "$localizedPrefix $matchedNumber$suffix"
        }

        // If the title is just a number (e.g. "01", "1")
        if (chapter.title.matches(Regex("""^\d+$"""))) {
            return "$localizedPrefix ${chapter.title.toInt()}"
        }

        // If the title contains the number but isn't a direct generic match,
        // we generally default to returning the original title to avoid destroying
        // custom titles like "01 - Intro".
        // However, if we want to ensure "01 - Intro" becomes "Глава 1 - Intro",
        // we would need more aggressive logic.
        // For now, let's stick to the safe path: generic names get localized.

        // Fallback: if extracting number failed (returned index+1) and title doesn't look like a chapter,
        // we keep the title.
        // If we want to force "Chapter N" even for "Prologue", we would return "$localizedPrefix $number".
        // But that deletes "Prologue".

        return chapter.title
    }

    /**
     * Format chapter name with duration.
     *
     * @param chapter The chapter object
     * @param index Chapter index (0-based)
     * @return Formatted "Chapter N • HH:MM:SS"
     */
    fun formatChapterWithDuration(
        chapter: Chapter,
        index: Int,
        localizedPrefix: String,
    ): String {
        val name = formatChapterName(chapter, index, localizedPrefix)
        val duration = formatDuration(chapter.duration.inWholeMilliseconds)
        return "$name • $duration"
    }

    /**
     * Format duration in milliseconds to HH:MM:SS or MM:SS.
     *
     * @param millis Duration in milliseconds
     * @return Formatted duration string
     */
    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
