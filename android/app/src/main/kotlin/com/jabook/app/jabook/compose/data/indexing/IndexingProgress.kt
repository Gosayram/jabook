// Copyright 2025 Jabook Contributors
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
sealed class IndexingProgress {
    /**
     * Indexing not started or completed.
     */
    data object Idle : IndexingProgress()

    /**
     * Indexing in progress.
     *
     * @param currentForum Current forum being indexed
     * @param currentForumIndex Index of current forum (0-based)
     * @param totalForums Total number of forums to index
     * @param currentPage Current page in current forum
     * @param topicsIndexed Total topics indexed so far
     * @param estimatedTotalTopics Estimated total topics (may be 0 if unknown)
     */
    data class InProgress(
        val currentForum: String,
        val currentForumIndex: Int,
        val totalForums: Int,
        val currentPage: Int,
        val topicsIndexed: Int,
        val estimatedTotalTopics: Int = 0,
    ) : IndexingProgress() {
        /**
         * Overall progress percentage (0.0 to 1.0).
         *
         * Improved calculation: uses forum index and topics indexed for more accurate progress.
         * Progress is based on completed forums + current forum progress.
         */
        val progress: Float
            get() =
                if (totalForums == 0) {
                    0f
                } else {
                    // Base progress from completed forums (forums before current)
                    val completedForums = currentForumIndex.toFloat()
                    val forumProgress = completedForums / totalForums.toFloat()

                    // Progress from current forum: estimate based on page number
                    // Use a conservative estimate: assume forums have varying page counts
                    // Current page gives us a rough estimate (normalized to max 50 pages per forum)
                    val maxPagesPerForum = 50f
                    val currentForumPageProgress = (currentPage.toFloat() / maxPagesPerForum).coerceIn(0f, 1f)
                    val currentForumContribution = currentForumPageProgress / totalForums.toFloat()

                    (forumProgress + currentForumContribution).coerceIn(0f, 1f)
                }
    }

    /**
     * Indexing completed successfully.
     *
     * @param totalTopics Total number of topics indexed
     * @param durationMs Duration in milliseconds
     */
    data class Completed(
        val totalTopics: Int,
        val durationMs: Long,
    ) : IndexingProgress()

    /**
     * Indexing failed with error.
     *
     * @param message Error message
     * @param forumId Forum ID where error occurred (if applicable)
     */
    data class Error(
        val message: String,
        val forumId: String? = null,
    ) : IndexingProgress()
}
