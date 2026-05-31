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

package com.jabook.app.jabook.audio

import com.jabook.app.jabook.compose.domain.model.BookmarkItem

/**
 * P-59: Groups bookmarks into clusters by chapter for organized display.
 *
 * After listening to a long book, dozens of bookmarks accumulate.
 * Without clustering, the list becomes unusable. This class groups
 * bookmarks by chapter, creating navigable clusters.
 *
 * Usage:
 * ```
 * val clusters = BookmarkClusterizer.clusterize(bookmarks)
 * // Display clusters in UI with chapter headers
 * ```
 */
public class BookmarkClusterizer {
    /**
     * A cluster of bookmarks within the same chapter.
     */
    public data class BookmarkCluster(
        val chapterIndex: Int,
        val chapterTitle: String,
        val bookmarks: List<BookmarkItem>,
        val firstPositionMs: Long,
        val lastPositionMs: Long,
    ) {
        /** Number of bookmarks in this cluster. */
        val size: Int get() = bookmarks.size
    }

    public companion object {
        /**
         * Groups bookmarks by chapter index.
         *
         * Each chapter with at least one bookmark becomes a cluster.
         * Clusters are sorted by chapter index, bookmarks within clusters
         * are sorted by position.
         *
         * @param bookmarks Flat list of bookmarks
         * @return List of clusters grouped by chapter
         */
        public fun clusterize(bookmarks: List<BookmarkItem>): List<BookmarkCluster> {
            if (bookmarks.isEmpty()) return emptyList()

            return bookmarks
                .groupBy { it.chapterIndex }
                .map { (chapterIndex, items) ->
                    val sorted = items.sortedBy { it.positionMs }
                    BookmarkCluster(
                        chapterIndex = chapterIndex,
                        chapterTitle = "Глава ${chapterIndex + 1}",
                        bookmarks = sorted,
                        firstPositionMs = sorted.first().positionMs,
                        lastPositionMs = sorted.last().positionMs,
                    )
                }.sortedBy { it.chapterIndex }
        }

        /**
         * Filters clusters to only those with auto-bookmarks.
         */
        public fun filterAutoClusters(clusters: List<BookmarkCluster>): List<BookmarkCluster> =
            clusters.filter { cluster ->
                cluster.bookmarks.any { it.noteText?.startsWith("Автозакладка") == true }
            }
    }
}
