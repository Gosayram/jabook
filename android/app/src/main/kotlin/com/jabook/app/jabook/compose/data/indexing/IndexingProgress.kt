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

package com.jabook.app.jabook.compose.data.indexing

/**
 * Progress state for forum indexing operation.
 */
public sealed class IndexingProgress {
    /**
     * Indexing not started or completed.
     */
    public data object Idle : IndexingProgress()

    /**
     * Indexing in progress.
     *
     * @param detail Aggregated progress including per-forum details,
     *   overall percentage ([IndexProgress.percentComplete]) and topics found.
     */
    public data class InProgress(
        val detail: IndexProgress,
    ) : IndexingProgress()

    /**
     * Indexing completed successfully.
     *
     * @param totalTopics Total number of topics indexed
     * @param durationMs Duration in milliseconds
     */
    public data class Completed(
        val totalTopics: Int,
        val durationMs: Long,
    ) : IndexingProgress()

    /**
     * Indexing cancelled by the user ("Pause"); persisted page cursors allow
     * resuming where it stopped.
     */
    public data object Paused : IndexingProgress()

    /**
     * Indexing failed with error.
     *
     * @param message Error message
     * @param forumId Forum ID where error occurred (if applicable)
     * @param errorReason Machine-readable reason for structured handling
     *   (e.g. [ERROR_REASON_AUTH_EXPIRED]); null for generic errors.
     */
    public data class Error(
        val message: String,
        val forumId: String? = null,
        val errorReason: String? = null,
    ) : IndexingProgress()

    public companion object {
        /** RuTracker session died mid-run; cursors preserved, re-login required. */
        public const val ERROR_REASON_AUTH_EXPIRED: String = "auth_expired"
    }
}

/**
 * State of a single forum during indexing.
 */
public enum class ForumState {
    PENDING,
    IN_PROGRESS,
    INDEXED,
    FAILED,

    /** Crawl stopped because the RuTracker session expired; cursors preserved. */
    PAUSED,
}

/**
 * Per-forum status tracking for the indexing UI.
 *
 * @param forumId Forum ID (e.g. "574")
 * @param forumName Display name for the forum
 * @param state Current state
 * @param topicsCount Number of topics indexed from this forum (0 until completed)
 * @param lastUpdated Timestamp of last update (epoch ms), 0 if never
 * @param errorMessage Error details if state == FAILED
 * @param currentPage Current page being processed (0 until completed)
 * @param totalPages Total pages processed (estimated)
 */
public data class ForumStatus(
    val forumId: String,
    val forumName: String,
    val state: ForumState = ForumState.PENDING,
    val topicsCount: Int = 0,
    val lastUpdated: Long = 0L,
    val errorMessage: String? = null,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
)

/**
 * Aggregated indexing progress including per-forum details.
 *
 * @param currentForumName Name of forum currently being indexed
 * @param currentForumPage Current page in the current forum
 * @param totalForumsCompleted Number of forums fully indexed
 * @param totalForums Total number of forums to index
 * @param topicsFound Total topics found so far across all forums
 * @param errors List of error messages from failed forums
 * @param forumStatuses Per-forum status list
 * @param phase Current crawl phase ([PHASE_FRESH], [PHASE_BACKFILL]) or null
 *   when not applicable (legacy full crawl).
 * @param reportedPercent Progress percent (0..1) reported by the background
 *   worker, when known; null = compute from per-forum counters.
 */
public data class IndexProgress(
    val currentForumName: String = "",
    val currentForumPage: Int = 0,
    val totalForumsCompleted: Int = 0,
    val totalForums: Int = 0,
    val topicsFound: Int = 0,
    val errors: List<String> = emptyList(),
    val forumStatuses: List<ForumStatus> = emptyList(),
    val phase: String? = null,
    val reportedPercent: Float? = null,
) {
    public companion object {
        /** Fresh-first phase: newest pages crawled first for all forums. */
        public const val PHASE_FRESH: String = "fresh"

        /** Backfill phase: older history crawled after the fresh pass. */
        public const val PHASE_BACKFILL: String = "backfill"
    }

    public val hasDetailedProgress: Boolean
        get() = totalForums > 0

    /**
     * Overall progress percentage (0.0 to 1.0).
     * Prefers the worker-reported percent; otherwise each forum contributes
     * 1/totalForums, subdivided by page count.
     */
    val percentComplete: Float
        get() {
            reportedPercent?.let { return it.coerceIn(0f, 1f) }
            if (totalForums == 0) return 0f
            val forumContribution = 1f / totalForums
            // Prefer the current forum's recorded page total; fall back to a 50-page estimate
            val currentForumTotalPages =
                forumStatuses
                    .firstOrNull { it.state == ForumState.IN_PROGRESS && it.totalPages > 0 }
                    ?.totalPages
                    ?.toFloat()
                    ?: 50f
            val pageInForum = currentForumPage.toFloat()
            val forumProgress = forumContribution * minOf(pageInForum / currentForumTotalPages, 1f)
            return (totalForumsCompleted.toFloat() * forumContribution + forumProgress).coerceIn(0f, 1f)
        }
}
