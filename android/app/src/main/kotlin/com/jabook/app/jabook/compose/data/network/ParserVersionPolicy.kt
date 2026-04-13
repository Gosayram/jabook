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

package com.jabook.app.jabook.compose.data.network

/**
 * Policy for tracking parser versions and detecting potential HTML parser breakage.
 *
 * Each parser is tagged with a version. When a parser returns suspiciously
 * empty results (0 results on a query that should return at least 1), this
 * policy flags it as a possible breakage event for diagnostics.
 *
 * BP-37.1 reference: HTML parser версионирование — `ParserVersion` к каждому
 * парсеру + механизм детекции breakage (0 результатов → parser_possible_breakage).
 */
public object ParserVersionPolicy {
    /**
     * Current parser versions.
     *
     * Increment when parser logic changes significantly.
     */
    public const val SEARCH_PARSER_VERSION: Int = 1
    public const val TOPIC_PARSER_VERSION: Int = 1
    public const val CATEGORY_PARSER_VERSION: Int = 1
    public const val LOGIN_PARSER_VERSION: Int = 1

    /**
     * Result of a breakage detection check.
     *
     * @property parserName Name of the parser (e.g., "search", "topic").
     * @property parserVersion Current version of the parser.
     * @property isPossibleBreakage Whether the result suggests parser breakage.
     * @property reason Human-readable reason for the decision.
     * @property resultCount Number of results returned by the parser.
     * @property query The query that was attempted (if applicable).
     */
    public data class BreakageCheckResult(
        public val parserName: String,
        public val parserVersion: Int,
        public val isPossibleBreakage: Boolean,
        public val reason: String,
        public val resultCount: Int,
        public val query: String? = null,
    )

    /**
     * Checks if a parser's result count indicates possible breakage.
     *
     * A result is flagged as possible breakage if:
     * - The query is non-blank (meaningful search)
     * - The HTML response was non-empty (page loaded)
     * - The result count is exactly 0
     *
     * @param parserName Name of the parser.
     * @param parserVersion Current version of the parser.
     * @param resultCount Number of results the parser returned.
     * @param query The search query (if applicable).
     * @param responseHtmlLength Length of the HTML response in characters.
     * @return [BreakageCheckResult] with the breakage assessment.
     */
    public fun checkBreakage(
        parserName: String,
        parserVersion: Int,
        resultCount: Int,
        query: String? = null,
        responseHtmlLength: Int = 0,
    ): BreakageCheckResult {
        val isMeaningfulQuery = !query.isNullOrBlank()
        val hasResponse = responseHtmlLength > 0

        if (!isMeaningfulQuery && resultCount == 0) {
            return BreakageCheckResult(
                parserName = parserName,
                parserVersion = parserVersion,
                isPossibleBreakage = false,
                reason = "Empty/blank query — 0 results expected",
                resultCount = resultCount,
                query = query,
            )
        }

        if (!hasResponse && resultCount == 0) {
            return BreakageCheckResult(
                parserName = parserName,
                parserVersion = parserVersion,
                isPossibleBreakage = false,
                reason = "Empty HTML response — network/server error, not parser breakage",
                resultCount = resultCount,
                query = query,
            )
        }

        if (resultCount == 0 && isMeaningfulQuery && hasResponse) {
            return BreakageCheckResult(
                parserName = parserName,
                parserVersion = parserVersion,
                isPossibleBreakage = true,
                reason = "parser_possible_breakage: non-blank query returned 0 results with non-empty HTML (v$parserVersion)",
                resultCount = resultCount,
                query = query,
            )
        }

        return BreakageCheckResult(
            parserName = parserName,
            parserVersion = parserVersion,
            isPossibleBreakage = false,
            reason = "Normal result count: $resultCount",
            resultCount = resultCount,
            query = query,
        )
    }

    /**
     * Formats a breakage event for diagnostic logging.
     */
    public fun formatBreakageLog(result: BreakageCheckResult): String =
        if (result.isPossibleBreakage) {
            "[PARSER_BREAKAGE] parser=${result.parserName} version=${result.parserVersion} " +
                "query=${result.query ?: "N/A"} results=${result.resultCount} " +
                "reason=${result.reason}"
        } else {
            "[PARSER_OK] parser=${result.parserName} version=${result.parserVersion} " +
                "results=${result.resultCount}"
        }
}
