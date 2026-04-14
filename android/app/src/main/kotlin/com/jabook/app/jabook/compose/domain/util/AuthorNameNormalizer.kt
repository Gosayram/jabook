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

package com.jabook.app.jabook.compose.domain.util

/**
 * Normalizes author names from various tag formats into a consistent display format.
 *
 * Many audiobook sources (RuTracker, ID3 tags) store author names as "Last, First"
 * while the preferred display format is "First Last". This utility handles:
 * - Inversion of "Last, First" → "First Last"
 * - Multi-part names with commas (e.g., "Иванов, Иван Иванович")
 * - Cyrillic and Latin names
 * - Configurable normalization mode (always invert, never invert, auto-detect)
 *
 * BP-24.4 reference: Нормализация авторов: "Last, First" → "First Last"
 */
public object AuthorNameNormalizer {
    /**
     * Normalization mode for author name display.
     */
    public enum class NormalizationMode {
        /** Automatically detect "Last, First" pattern and invert. */
        AUTO,

        /** Always invert comma-separated parts (assumes "Last, First" format). */
        ALWAYS_INVERT,

        /** Keep the original name format unchanged. */
        AS_IS,
    }

    /**
     * Result of a normalization operation.
     *
     * @property original The original input name.
     * @property normalized The normalized display name.
     * @property wasInverted Whether the name was inverted from "Last, First" format.
     */
    public data class Result(
        public val original: String,
        public val normalized: String,
        public val wasInverted: Boolean,
    )

    /**
     * Normalizes a single author name according to the given mode.
     *
     * Rules:
     * - `AS_IS`: returns the name unchanged.
     * - `ALWAYS_INVERT`: if the name contains exactly one comma with two non-empty parts,
     *   swaps them from "Last, First" → "First Last".
     * - `AUTO`: inverts only if the name matches the "Last, First" heuristic:
     *   exactly one comma, two non-empty parts, and the first part looks like a surname
     *   (does not end with a period, not a known prefix like "van", "de", "von").
     *
     * @param name The author name to normalize.
     * @param mode The normalization mode (default: [NormalizationMode.AUTO]).
     * @return [Result] with the normalized name and metadata.
     */
    public fun normalize(
        name: String,
        mode: NormalizationMode = NormalizationMode.AUTO,
    ): Result {
        if (name.isBlank()) {
            return Result(original = name, normalized = name.trim(), wasInverted = false)
        }

        val trimmed = name.trim()

        if (mode == NormalizationMode.AS_IS) {
            return Result(original = name, normalized = trimmed, wasInverted = false)
        }

        val parts = splitByComma(trimmed)
        if (parts == null) {
            return Result(original = name, normalized = trimmed, wasInverted = false)
        }

        val (first, second) = parts

        if (mode == NormalizationMode.ALWAYS_INVERT) {
            val inverted = "$second $first"
            return Result(original = name, normalized = inverted, wasInverted = true)
        }

        // AUTO mode: apply heuristic
        if (shouldInvert(first, second)) {
            val inverted = "$second $first"
            return Result(original = name, normalized = inverted, wasInverted = true)
        }

        return Result(original = name, normalized = trimmed, wasInverted = false)
    }

    /**
     * Normalizes a list of author names.
     *
     * @param names List of author names to normalize.
     * @param mode The normalization mode (default: [NormalizationMode.AUTO]).
     * @return List of [Result] objects in the same order.
     */
    public fun normalizeAll(
        names: List<String>,
        mode: NormalizationMode = NormalizationMode.AUTO,
    ): List<Result> = names.map { normalize(it, mode) }

    /**
     * Quick normalize that returns just the display string.
     *
     * @param name The author name to normalize.
     * @param mode The normalization mode (default: [NormalizationMode.AUTO]).
     * @return The normalized display name.
     */
    public fun normalizeToString(
        name: String,
        mode: NormalizationMode = NormalizationMode.AUTO,
    ): String = normalize(name, mode).normalized

    /**
     * Splits a name by comma into exactly two non-empty parts.
     * Returns null if there isn't exactly one comma with two non-empty parts.
     */
    private fun splitByComma(name: String): Pair<String, String>? {
        val commaIndex = name.indexOf(',')
        if (commaIndex < 0) return null

        // Only handle single-comma names (exactly one comma)
        if (name.indexOf(',', commaIndex + 1) >= 0) return null

        val first = name.substring(0, commaIndex).trim()
        val second = name.substring(commaIndex + 1).trim()

        if (first.isBlank() || second.isBlank()) return null

        return Pair(first, second)
    }

    /**
     * Heuristic to determine if a comma-separated name should be inverted.
     *
     * A name "Last, First" should be inverted if:
     * - The first part (presumed surname) doesn't look like a prefix/particle
     * - The second part (presumed given name) doesn't look like a suffix
     */
    private fun shouldInvert(
        surname: String,
        givenName: String,
    ): Boolean {
        // Known name particles/prefixes that should NOT be treated as surnames
        val particles =
            setOf(
                "van",
                "von",
                "de",
                "del",
                "der",
                "di",
                "da",
                "dos",
                "das",
                "du",
                "le",
                "la",
                "el",
                "al",
                "bin",
                "ibn",
                "ben",
                "mac",
                "mc",
                "o'",
                "st",
                "san",
                "santa",
                "do",
                "da",
            )

        val lowerSurname = surname.lowercase()

        // If the first part is a known particle, don't invert
        if (lowerSurname in particles) return false

        // If the first part ends with a period (e.g., "J.R.R."), it's likely initials, not a surname
        if (surname.endsWith('.')) return false

        // If the second part starts with a Roman numeral, don't invert (e.g., "Smith, III")
        val romanNumerals = setOf("ii", "iii", "iv", "v", "vi", "vii", "viii", "jr", "sr")
        if (givenName.lowercase() in romanNumerals) return false

        // If the second part ends with a period, it might be an abbreviation — still invert
        // as "Иванов, И." → "И. Иванов" is the desired format

        return true
    }
}
