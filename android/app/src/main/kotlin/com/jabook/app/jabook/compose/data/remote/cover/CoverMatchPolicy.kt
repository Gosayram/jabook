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

package com.jabook.app.jabook.compose.data.remote.cover

/**
 * Accuracy guard for remote cover lookup: decides whether a search-result
 * candidate is close enough to the queried book to take its cover.
 *
 * Pure logic — no I/O — so false positives must be impossible here.
 */
public object CoverMatchPolicy {
    private val COMMON_TITLE_SUFFIXES = listOf("аудиокнига", "audiobook", "книга", "book")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}\\s]")
    private val SPACES = Regex("\\s+")

    /**
     * @param candidateTitle title from the search result (may be null)
     * @param candidateAuthors authors from the search result (may be empty)
     * @param queryTitle local book title
     * @param queryAuthor local book author
     */
    public fun isAcceptable(
        candidateTitle: String?,
        candidateAuthors: List<String>,
        queryTitle: String,
        queryAuthor: String,
    ): Boolean {
        val query = normalize(queryTitle)
        val candidate = normalize(candidateTitle)
        if (query.length <= 3 || candidate.length <= 3) return false
        if (!candidate.contains(query) && !query.contains(candidate)) return false

        val queryAuthorNorm = normalize(queryAuthor)
        val candidateAuthorsNorm = candidateAuthors.map(::normalize).filter(String::isNotEmpty)
        if (queryAuthorNorm.isEmpty() || candidateAuthorsNorm.isEmpty()) return true

        val surname = queryAuthorNorm.substringAfterLast(' ')
        if (surname.length <= 2) return false
        return candidateAuthorsNorm.any { author ->
            author.length > 2 && (author.contains(surname) || surname.contains(author))
        }
    }

    private fun normalize(raw: String?): String {
        var value = (raw ?: "").lowercase().trim()
        value = SPACES.replace(NON_WORD.replace(value, " "), " ").trim()
        for (suffix in COMMON_TITLE_SUFFIXES) {
            if (value.endsWith(" $suffix")) value = value.removeSuffix(" $suffix").trim()
        }
        return value
    }
}
