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

package com.jabook.app.jabook.compose.data.cache

import com.jabook.app.jabook.compose.data.remote.model.SearchResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory LRU cache for RuTracker search results.
 *
 * Uses [LinkedHashMap] with access-order for O(1) LRU eviction.
 * Thread-safe: all public methods are [synchronized].
 */
@Singleton
public class RutrackerSearchCache
    @Inject
    constructor() {
        // accessOrder=true → get/put moves entry to end (most-recently used)
        private val cache =
            object : LinkedHashMap<String, CacheEntry>(64, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean = size > MAX_CACHE_SIZE
            }

        /**
         * Get cached search results if still valid.
         */
        public fun get(
            query: String,
            forumIds: String? = null,
        ): List<SearchResult>? =
            synchronized(cache) {
                val entry = cache[generateKey(query, forumIds)] ?: return null
                if (entry.isExpired()) {
                    cache.remove(generateKey(query, forumIds))
                    null
                } else {
                    entry.results.toList()
                }
            }

        /**
         * Store search results in cache.
         */
        public fun put(
            query: String,
            forumIds: String?,
            results: List<SearchResult>,
        ) {
            if (results.isEmpty()) return
            synchronized(cache) {
                cache[generateKey(query, forumIds)] =
                    CacheEntry(
                        results = results.toList(),
                        timestamp = System.currentTimeMillis(),
                    )
            }
        }

        /**
         * Clear all cached search results.
         */
        public fun clear(): Unit = synchronized(cache) { cache.clear() }

        /**
         * Get approximate cache size in bytes.
         */
        public fun getCacheSize(): Long =
            synchronized(cache) {
                cache.values.sumOf { it.results.size } * AVERAGE_RESULT_SIZE_BYTES
            }

        /**
         * Get cache statistics.
         */
        public fun getStatistics(): CacheStatistics =
            synchronized(cache) {
                val entries = cache.values.toList()
                CacheStatistics(
                    entriesCount = cache.size,
                    totalResults = entries.sumOf { it.results.size },
                    estimatedSize = getCacheSize(),
                    oldestEntry = entries.minOfOrNull { it.timestamp } ?: 0L,
                    newestEntry = entries.maxOfOrNull { it.timestamp } ?: 0L,
                )
            }

        private fun generateKey(
            query: String,
            forumIds: String?,
        ): String {
            val normalizedQuery = query.trim().lowercase()
            val normalizedForums = forumIds?.trim() ?: ""
            return "$normalizedQuery|$normalizedForums"
        }

        private data class CacheEntry(
            val results: List<SearchResult>,
            val timestamp: Long,
        ) {
            public fun isExpired(): Boolean = (System.currentTimeMillis() - timestamp) > CACHE_TTL_MS
        }

        public data class CacheStatistics(
            val entriesCount: Int,
            val totalResults: Int,
            val estimatedSize: Long,
            val oldestEntry: Long,
            val newestEntry: Long,
        )

        public companion object {
            private const val CACHE_TTL_MS = 30 * 60 * 1000L
            private const val MAX_CACHE_SIZE = 50
            private const val AVERAGE_RESULT_SIZE_BYTES = 500L
        }
    }
