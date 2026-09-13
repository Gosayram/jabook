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

internal data class ChapterOrderCandidate(
    val displayName: String,
    val trackNumber: Int?,
)

internal data class ChapterInfo(
    val partNumber: Int = 0,
    val chapterNumber: Int = 0,
    val hasNumber: Boolean = false,
) {
    fun toSortKey(): Int = partNumber * 1000 + chapterNumber
}

internal object ChapterOrderPolicy {
    private val leadingNumberRegex = Regex("""^\s*(\d{1,3})[\s._-]?.*""")

    // Shared regex patterns for chapter/part number extraction.
    private val PART_RU_REGEX = Regex("""част[\u044cяи]\s*(\d+)""")
    private val PART_EN_REGEX = Regex("""part\s*(\d+)""")
    private val CHAPTER_NUMBER_PATTERNS =
        listOf(
            Regex("""глава\s*(\d+)"""),
            Regex("""chapter\s*(\d+)"""),
            Regex("""(\d+)\s*[-._]"""),
            Regex("""^(\d+)"""),
        )

    // Ponytail: comparator for metadata-based sort (track numbers).
    fun comparator(): Comparator<ChapterOrderCandidate> =
        compareBy<ChapterOrderCandidate>(
            { candidate -> priority(candidate) },
            { candidate -> trackSortKey(candidate) },
            { candidate -> filenameNumericSortKey(candidate) },
            { candidate -> candidate.displayName.lowercase() },
        )

    /**
     * Comparator for audio-file scanning sort.
     *
     * Sort order:
     * 0. Пролог/Prologue
     * 1. Numbered chapters (Глава 1-N)
     * 2. Unnumbered files (alphabetical)
     * 3. Special content (Приложение/От автора/Послесловие)
     * 4. Эпилог/Epilogue (always last)
     */
    fun <T> comparatorForAudioFiles(displayName: (T) -> String): Comparator<T> =
        compareBy<T> { file ->
            getFileCategory(displayName(file))
        }.thenBy { file ->
            val info = extractChapterInfo(displayName(file))
            if (info.hasNumber) info.toSortKey() else 0
        }.thenBy { file ->
            displayName(file).lowercase()
        }

    fun extractChapterInfo(filename: String): ChapterInfo {
        val clean = filename.lowercase()

        val partMatch = PART_RU_REGEX.find(clean) ?: PART_EN_REGEX.find(clean)
        val partNum = partMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0

        var chapterNum = 0
        var found = false
        for (pattern in CHAPTER_NUMBER_PATTERNS) {
            pattern.find(clean)?.let {
                chapterNum = it.groupValues[1].toIntOrNull() ?: 0
                found = true
                return@let
            }
        }

        return ChapterInfo(partNum, chapterNum, found)
    }

    private fun getFileCategory(filename: String): Int {
        val lower = filename.lowercase()
        return when {
            lower.contains("пролог") || lower.contains("prologue") -> 0
            extractChapterInfo(filename).hasNumber && !isSpecialContent(lower) -> 1
            !extractChapterInfo(filename).hasNumber && !isSpecialContent(lower) -> 2
            isSpecialContent(lower) -> 3
            lower.contains("эпилог") || lower.contains("epilogue") -> 4
            else -> 2
        }
    }

    private fun isSpecialContent(filename: String): Boolean =
        filename.contains("приложение") ||
            filename.contains("appendix") ||
            filename.contains("от автора") ||
            filename.contains("from the author") ||
            filename.contains("author") &&
            filename.contains("note") ||
            filename.contains("послесловие") ||
            filename.contains("afterword") ||
            filename.contains("предисловие") ||
            filename.contains("foreword") ||
            filename.contains("preface")

    private fun priority(candidate: ChapterOrderCandidate): Int =
        when {
            (candidate.trackNumber ?: 0) > 0 -> 0
            extractLeadingNumber(candidate.displayName) != null -> 1
            else -> 2
        }

    private fun trackSortKey(candidate: ChapterOrderCandidate): Int = candidate.trackNumber ?: Int.MAX_VALUE

    private fun filenameNumericSortKey(candidate: ChapterOrderCandidate): Int = extractLeadingNumber(candidate.displayName) ?: Int.MAX_VALUE

    private fun extractLeadingNumber(fileName: String): Int? =
        leadingNumberRegex
            .find(fileName)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
}
