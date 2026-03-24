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

package com.jabook.app.jabook.compose.data.remote.parser

/**
 * Result of parsing operation with graceful error handling.
 *
 * This sealed class enables defensive parsing that never crashes,
 * always returning useful information even on partial failures.
 *
 * @param T Type of successfully parsed data
 */
public sealed class ParsingResult<T> {
    /**
     * Complete success - all data parsed without errors.
     *
     * @property data Successfully parsed data
     * @property warnings Non-critical warnings (e.g., optional fields missing)
     */
    public data class Success<T>(
        val data: T,
        val warnings: List<String> = emptyList(),
    ) : ParsingResult<T>()

    /**
     * Partial success - some data parsed, but with errors.
     *
     * Use this when you can extract useful data despite some fields failing.
     * Example: 8 out of 10 search results parsed successfully.
     *
     * @property data Partially parsed data
     * @property errors List of parsing errors encountered
     */
    public data class PartialSuccess<T>(
        val data: T,
        val errors: List<ParsingError>,
    ) : ParsingResult<T>()

    /**
     * Complete failure - parsing failed critically.
     *
     * @property errors List of critical errors
     * @property fallbackData Optional fallback data (e.g., empty list)
     */
    public data class Failure<T>(
        val errors: List<ParsingError>,
        val fallbackData: T? = null,
    ) : ParsingResult<T>()
}

/**
 * Details about a parsing error.
 *
 * @property field Field or element that failed to parse
 * @property reason Human-readable reason for failure
 * @property severity Severity level of the error
 * @property htmlSnippet Optional HTML snippet for debugging
 */
public data class ParsingError(
    val field: String,
    val reason: String,
    val severity: ErrorSeverity,
    val htmlSnippet: String? = null,
)

/**
 * Severity level of parsing errors.
 */
public enum class ErrorSeverity {
    /** Non-critical issue, has fallback value */
    WARNING,

    /** Important field missing, impacts functionality */
    ERROR,

    /** Critical failure, parsing completely failed */
    CRITICAL,
}
