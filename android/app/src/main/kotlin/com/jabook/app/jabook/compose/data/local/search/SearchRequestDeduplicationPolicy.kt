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
 * Policy for deduplicating search requests to prevent parallel execution of
 * identical queries and cancel stale requests.
 *
 * When a user rapidly types or presses search multiple times with the same query,
 * this policy ensures:
 * - Only one active request per normalized query at a time
 * - Rapid re-submissions are deduped within a configurable window
 * - Stale requests are cancelled via [SearchRequestDeduplicationPolicy.shouldCancel]
 *
 * BP-15.4 reference: Request deduplication — cancel previous, don't launch parallel.
 * Реализовать через `MutableStateFlow` + `debounce(300ms)` + `flatMapLatest`.
 */
public object SearchRequestDeduplicationPolicy {
    /**
     * Deduplication window in milliseconds.
     * Requests with the same normalized query within this window are considered duplicates.
     */
    public const val DEDUPLICATION_WINDOW_MS: Long = 300L

    /**
     * Result of a deduplication check.
     *
     * @property shouldExecute Whether the request should be executed.
     * @property reason Why the decision was made.
     * @property cancelledQuery The query that should be cancelled (if any).
     */
    public data class DeduplicationResult(
        public val shouldExecute: Boolean,
        public val reason: String,
        public val cancelledQuery: String? = null,
    )

    /**
     * Active request tracker entry.
     *
     * @property normalizedQuery The normalized query string.
     * @property timestampMs When the request was registered.
     * @property requestId Unique identifier for this specific request.
     */
    public data class ActiveRequest(
        public val normalizedQuery: String,
        public val timestampMs: Long,
        public val requestId: String,
    )

    /**
     * Normalizes a search query for deduplication purposes.
     *
     * - Trims whitespace
     * - Collapses multiple spaces
     * - Lowercases for case-insensitive comparison
     */
    public fun normalizeQuery(query: String): String = query.trim().lowercase().replace(Regex("\\s+"), " ")

    /**
     * Checks whether a new search request should be executed or deduplicated.
     *
     * @param query The incoming search query.
     * @param activeRequests Currently active (in-flight) requests.
     * @param currentTimeMs Current timestamp in milliseconds.
     * @return [DeduplicationResult] with the decision.
     */
    public fun check(
        query: String,
        activeRequests: List<ActiveRequest>,
        currentTimeMs: Long,
    ): DeduplicationResult {
        val normalized = normalizeQuery(query)

        if (normalized.isBlank()) {
            return DeduplicationResult(
                shouldExecute = false,
                reason = "Blank query rejected",
            )
        }

        // Check for exact match among active requests
        val exactMatch = activeRequests.find { it.normalizedQuery == normalized }
        if (exactMatch != null) {
            val elapsed = currentTimeMs - exactMatch.timestampMs
            if (elapsed < DEDUPLICATION_WINDOW_MS) {
                return DeduplicationResult(
                    shouldExecute = false,
                    reason = "Duplicate query within ${DEDUPLICATION_WINDOW_MS}ms (elapsed: ${elapsed}ms)",
                    cancelledQuery = null,
                )
            }

            // Outside dedup window: cancel old and allow new
            return DeduplicationResult(
                shouldExecute = true,
                reason = "Stale request cancelled (age: ${elapsed}ms)",
                cancelledQuery = exactMatch.requestId,
            )
        }

        // No active request for this query — allow execution
        return DeduplicationResult(
            shouldExecute = true,
            reason = "No active request for query",
        )
    }

    /**
     * Determines which active requests should be cancelled when a new query arrives.
     *
     * All requests that don't match the new normalized query should be cancelled
     * (flatMapLatest behavior).
     *
     * @param newQuery The incoming query.
     * @param activeRequests Currently active requests.
     * @return List of request IDs that should be cancelled.
     */
    public fun getRequestsToCancel(
        newQuery: String,
        activeRequests: List<ActiveRequest>,
    ): List<String> {
        val normalized = normalizeQuery(newQuery)
        return activeRequests
            .filter { it.normalizedQuery != normalized }
            .map { it.requestId }
    }

    /**
     * Creates a new [ActiveRequest] entry.
     */
    public fun createActiveRequest(
        query: String,
        requestId: String,
        currentTimeMs: Long,
    ): ActiveRequest =
        ActiveRequest(
            normalizedQuery = normalizeQuery(query),
            timestampMs = currentTimeMs,
            requestId = requestId,
        )
}
