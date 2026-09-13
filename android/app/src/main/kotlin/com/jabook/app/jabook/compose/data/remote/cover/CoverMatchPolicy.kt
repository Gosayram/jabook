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

    private const val TITLE_WEIGHT = 0.5f
    private const val AUTHOR_WEIGHT = 0.5f
    private const val PARTIAL_SURNAME_MATCH = 0.5f

    /**
     * @param candidateTitle title from the search result (may be null)
     * @param candidateAuthors authors from the search result (may be empty)
     * @param queryTitle local book title
     * @param queryAuthor local book author
     * @return match strength in 0..1, where 0 means "reject outright".
     */
    public fun score(
        candidateTitle: String?,
        candidateAuthors: List<String>,
        queryTitle: String,
        queryAuthor: String,
    ): Float {
        val query = normalize(queryTitle)
        val candidate = normalize(candidateTitle)
        if (query.length <= 3 || candidate.length <= 3) return 0f
        if (!candidate.contains(query) && !query.contains(candidate)) return 0f
        val titleSimilarity =
            minOf(query.length, candidate.length).toFloat() / maxOf(query.length, candidate.length)

        val queryAuthorNorm = normalize(queryAuthor)
        val candidateAuthorsNorm = candidateAuthors.map(::normalize).filter(String::isNotEmpty)
        if (queryAuthorNorm.isEmpty() || candidateAuthorsNorm.isEmpty()) {
            // No author to compare: title evidence alone, capped below confident.
            return TITLE_WEIGHT * titleSimilarity
        }

        val authorSimilarity = candidateAuthorsNorm.maxOf { surnameSimilarity(it, queryAuthorNorm) }
        // Both author sets are known and share no surname: a title hit alone is not
        // evidence — same title by a different author is a classic false positive.
        if (authorSimilarity == 0f) return 0f
        return TITLE_WEIGHT * titleSimilarity + AUTHOR_WEIGHT * authorSimilarity
    }

    /**
     * @return true when [score] clears the acceptance threshold.
     */
    public fun isAcceptable(
        candidateTitle: String?,
        candidateAuthors: List<String>,
        queryTitle: String,
        queryAuthor: String,
    ): Boolean = score(candidateTitle, candidateAuthors, queryTitle, queryAuthor) > REJECT_THRESHOLD

    /** 1.0 when the normalized surnames are equal, 0.5 on partial overlap, 0 on none. */
    private fun surnameSimilarity(
        author: String,
        queryAuthor: String,
    ): Float {
        val querySurname = queryAuthor.substringAfterLast(' ')
        if (querySurname.length <= 2) return 0f
        val authorSurname = author.substringAfterLast(' ')
        if (authorSurname.length > 2 && authorSurname == querySurname) return 1f
        val partial =
            author.length > 2 && (author.contains(querySurname) || querySurname.contains(author))
        return if (partial) PARTIAL_SURNAME_MATCH else 0f
    }

    private fun normalize(raw: String?): String {
        var value = (raw ?: "").lowercase().trim()
        value = SPACES.replace(NON_WORD.replace(value, " "), " ").trim()
        for (suffix in COMMON_TITLE_SUFFIXES) {
            if (value.endsWith(" $suffix")) value = value.removeSuffix(" $suffix").trim()
        }
        return value
    }

    private const val REJECT_THRESHOLD = 0.35f
}
