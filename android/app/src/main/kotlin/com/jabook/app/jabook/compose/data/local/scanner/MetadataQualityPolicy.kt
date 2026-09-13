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

/**
 * Pure-Kotlin heuristics for scoring candidate metadata strings, adapted from
 * Rhythm's `MetadataHeuristics` (GPL-3.0). MediaStore tags are frequently
 * blank, placeholder (`<unknown>`), mojibake, or just the file name — scoring
 * lets callers prefer real tags over first-wins selection.
 *
 * No Android imports so this stays trivially unit-testable.
 */
internal object MetadataQualityPolicy {
    private const val REPLACEMENT_CHAR: Char = '\uFFFD'
    private val MOJIBAKE_LATIN_MARKERS = charArrayOf('Ã', 'Â', 'ï', '½')

    // U+00D0/U+00D1 (Ð/Ñ) prefix UTF-8 lead bytes decoded as Latin-1; only
    // flagged when followed by another Latin-1 range char to avoid false
    // positives on legit words like Spanish "Ñandú".
    private val CYRILLIC_MOJIBAKE = Regex("[ÐÑ][\u0080-\u00BF]")

    private val URLISH = Regex("""^(https?://|www\.)|://""")

    private val TRACK_FILE_NAME =
        Regex("""^(track|audio[ _-]?track|cd|disc|disk|chapter|part|file)[ _\-.]*(\d+|\d+of\d+)$""")

    private const val UNKNOWN_PLACEHOLDER_WEIGHT = 200
    private const val REPLACEMENT_CHAR_WEIGHT = 100
    private const val QUESTION_MARK_WEIGHT = 25
    private const val DIGIT_FLOOD_WEIGHT = 25
    private const val CONTROL_CHAR_WEIGHT = 10

    /**
     * Higher is better. Blank text scores [Int.MIN_VALUE] so any real
     * candidate beats it; hard penalties dominate the length bonus so a
     * short clean title beats a long corrupted one.
     */
    fun qualityScore(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return Int.MIN_VALUE

        // Length bonus is capped so a long corrupted string can't outscore a
        // short clean title on verbosity alone.
        var score = minOf(trimmed.length, 24) * 4
        score -= trimmed.count { it == REPLACEMENT_CHAR } * REPLACEMENT_CHAR_WEIGHT
        score -= trimmed.count { it == '?' } * QUESTION_MARK_WEIGHT
        score -= trimmed.count { it.code < 0x20 && it !in "\t\n\r" } * CONTROL_CHAR_WEIGHT

        if (trimmed.equals("<unknown>", ignoreCase = true) || trimmed == "00 - Track 00") {
            score -= UNKNOWN_PLACEHOLDER_WEIGHT
        }
        if (trimmed.any { it.code > 0x7F }) score += 8 // plausible real-language text
        if (isMojibake(trimmed)) score -= UNKNOWN_PLACEHOLDER_WEIGHT / 2
        if (URLISH.containsMatchIn(trimmed)) score -= UNKNOWN_PLACEHOLDER_WEIGHT / 2
        if (isLikelyTrackFileName(trimmed)) score -= UNKNOWN_PLACEHOLDER_WEIGHT / 4

        // "123456" or mostly digits — rarely a useful title/author.
        val digits = trimmed.count { it.isDigit() }
        if (digits >= 4 && digits * 2 > trimmed.length) score -= DIGIT_FLOOD_WEIGHT

        return score
    }

    /** True when the string is clearly unusable as display metadata. */
    fun isLikelyCorrupted(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank() || trimmed.equals("<unknown>", ignoreCase = true)) return true
        if (trimmed.any { it == REPLACEMENT_CHAR }) return true
        if (isMojibake(trimmed)) return true
        if (URLISH.containsMatchIn(trimmed)) return true

        // 2+ '?' or a '?' inside a word both indicate lossy encoding.
        val questionMarks = trimmed.count { it == '?' }
        if (questionMarks >= 2) return true
        if (questionMarks > 0 && Regex("""\p{L}\?\p{L}""").containsMatchIn(trimmed)) return true

        // Raw control characters mean a broken tag, not styled text.
        if (trimmed.any { it.code < 0x20 && it !in "\t\n\r" }) return true

        return false
    }

    /** Highest score wins; equal scores keep the first candidate; all blank/null → null. */
    fun selectBest(vararg candidates: String?): String? =
        candidates
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .maxByOrNull(::qualityScore)

    /**
     * Detects "Track 1", "01.mp3", "Audio Track 12" — names that carry no
     * book-level meaning and should lose to any real tag.
     */
    fun isLikelyTrackFileName(name: String): Boolean {
        val stem = name.trim().substringBeforeLast('.').trim()
        if (stem.isEmpty()) return false
        if (TRACK_FILE_NAME.containsMatchIn(stem.lowercase())) return true
        // Bare "01" / "0042", but a year-like number ("1984") is a legit
        // book title (Orwell), so keep those.
        if (stem.all { it.isDigit() }) {
            return !(stem.length == 4 && stem.toInt() in 1500..2100)
        }
        return false
    }

    private fun isMojibake(text: String): Boolean = MOJIBAKE_LATIN_MARKERS.any { it in text } || CYRILLIC_MOJIBAKE.containsMatchIn(text)
}
