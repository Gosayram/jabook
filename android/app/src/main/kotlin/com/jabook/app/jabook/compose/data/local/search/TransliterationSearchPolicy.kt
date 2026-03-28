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

package com.jabook.app.jabook.compose.data.local.search

/**
 * Builds search variants for mixed Cyrillic/Latin user input.
 *
 * This is an interim fallback before full FTS5 transliteration indexing.
 */
public object TransliterationSearchPolicy {
    private val cyrToLatMulti =
        mapOf(
            'ё' to "yo",
            'ж' to "zh",
            'х' to "kh",
            'ц' to "ts",
            'ч' to "ch",
            'ш' to "sh",
            'щ' to "shch",
            'ю' to "yu",
            'я' to "ya",
        )

    private val cyrToLatSingle =
        mapOf(
            'а' to "a",
            'б' to "b",
            'в' to "v",
            'г' to "g",
            'д' to "d",
            'е' to "e",
            'з' to "z",
            'и' to "i",
            'й' to "y",
            'к' to "k",
            'л' to "l",
            'м' to "m",
            'н' to "n",
            'о' to "o",
            'п' to "p",
            'р' to "r",
            'с' to "s",
            'т' to "t",
            'у' to "u",
            'ф' to "f",
            'ы' to "y",
            'э' to "e",
        )

    private val latToCyrMulti =
        listOf(
            "shch" to "щ",
            "yo" to "ё",
            "zh" to "ж",
            "kh" to "х",
            "ts" to "ц",
            "ch" to "ч",
            "sh" to "ш",
            "yu" to "ю",
            "ya" to "я",
        )

    private val latToCyrSingle =
        mapOf(
            'a' to "а",
            'b' to "б",
            'c' to "к",
            'd' to "д",
            'e' to "е",
            'f' to "ф",
            'g' to "г",
            'h' to "х",
            'i' to "и",
            'j' to "й",
            'k' to "к",
            'l' to "л",
            'm' to "м",
            'n' to "н",
            'o' to "о",
            'p' to "п",
            'q' to "к",
            'r' to "р",
            's' to "с",
            't' to "т",
            'u' to "у",
            'v' to "в",
            'w' to "в",
            'x' to "кс",
            'y' to "й",
            'z' to "з",
        )

    public fun buildVariants(query: String): List<String> {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) {
            return emptyList()
        }

        val toLatin = transliterateToLatin(normalized)
        val toCyrillic = transliterateToCyrillic(normalized)

        return linkedSetOf(normalized, toLatin, toCyrillic)
            .filter { it.isNotBlank() }
            .toList()
    }

    public fun buildFtsMatchQuery(query: String): String = buildFtsMatchQuery(buildVariants(query))

    public fun buildFtsMatchQuery(variants: List<String>): String {
        val variantQueries =
            variants
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { variant ->
                    variant
                        .split(WHITESPACE_REGEX)
                        .mapNotNull { token ->
                            val normalized = token.trim().lowercase()
                            if (normalized.isBlank()) null else "\"${escapeToken(normalized)}*\""
                        }.distinct()
                        .joinToString(" AND ")
                }.filter { it.isNotBlank() }
                .distinct()
                .toList()

        if (variantQueries.isEmpty()) {
            return ""
        }

        return variantQueries.joinToString(" OR ", prefix = "(", postfix = ")")
    }

    private fun transliterateToLatin(input: String): String {
        val result = StringBuilder(input.length * 2)
        for (char in input) {
            val mapped = cyrToLatMulti[char] ?: cyrToLatSingle[char]
            if (mapped != null) {
                result.append(mapped)
            } else {
                result.append(char)
            }
        }
        return result.toString()
    }

    private fun transliterateToCyrillic(input: String): String {
        var result = input
        for ((latin, cyrillic) in latToCyrMulti) {
            result = result.replace(latin, cyrillic)
        }

        val builder = StringBuilder(result.length)
        for (char in result) {
            val mapped = latToCyrSingle[char]
            if (mapped != null) {
                builder.append(mapped)
            } else {
                builder.append(char)
            }
        }
        return builder.toString()
    }

    private fun escapeToken(token: String): String = token.replace("\"", "\"\"")

    private val WHITESPACE_REGEX = "\\s+".toRegex()
}
