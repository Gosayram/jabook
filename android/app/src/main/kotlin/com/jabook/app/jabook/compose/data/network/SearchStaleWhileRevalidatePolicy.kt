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

import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.domain.model.RutrackerSearchResult

/**
 * Policy for stale-while-revalidate search behavior.
 */
public object SearchStaleWhileRevalidatePolicy {
    /**
     * Persists forum-scoped search results for default audiobooks scope and generic scope.
     */
    public fun shouldPersistResults(forumIds: String?): Boolean = forumIds.isNullOrBlank() || forumIds == RutrackerApi.AUDIOBOOKS_FORUM_IDS

    /**
     * Returns true when refreshed data meaningfully differs from current stale data.
     */
    public fun isMeaningfulRefresh(
        stale: List<RutrackerSearchResult>,
        refreshed: List<RutrackerSearchResult>,
    ): Boolean {
        if (stale.isEmpty() && refreshed.isNotEmpty()) return true
        if (stale.isNotEmpty() && refreshed.isEmpty()) return false

        val staleIds = stale.map { it.topicId }
        val refreshedIds = refreshed.map { it.topicId }
        if (staleIds != refreshedIds) return true

        // Same set/order of topics, but details may have changed (e.g. coverUrl loaded in background).
        // This is essential for re-rendering search cards after delayed cover resolution.
        return stale.zip(refreshed).any { (oldItem, newItem) ->
            oldItem.coverUrl.orEmpty() != newItem.coverUrl.orEmpty() ||
                oldItem.seeders != newItem.seeders ||
                oldItem.leechers != newItem.leechers
        }
    }
}
