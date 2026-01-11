// Copyright 2026 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the \"License\");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an \"AS IS\" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.jabook.app.jabook.compose.data.cache

import com.jabook.app.jabook.compose.data.remote.model.SearchResult
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory LRU cache for RuTracker search results.
 *
 * Features:
 * - TTL (Time To Live) expiration
 * - LRU eviction when size limit reached
 * - Thread-safe operations
 * - Memory-efficient cache key generation
 */
@Singleton
public class RutrackerSearchCache
    @Inject
    constructor() {
        // Cache storage: key -> CacheEntry
        private val cache = ConcurrentHashMap<String, CacheEntry>()

        // Access order tracking for LRU
        private val accessOrder = mutableListOf<String>()

        /**
         * Get cached search results if still valid.
         *
         * @param query Search query
         * @param forumIds Optional forum filter
         * @return Cached results or null if not found/expired
         */
        public fun get(
            query: String,
            forumIds: String? = null,
        ): List<SearchResult>? {
            val key = generateKey(query, forumIds)
            val entry = cache[key] ?: return null

            // Check expiration
            if (entry.isExpired()) {
                cache.remove(key)
                synchronized(accessOrder) {
                    accessOrder.remove(key)
                }
                return null
            }

            // Update access order
            synchronized(accessOrder) {
                accessOrder.remove(key)
                accessOrder.add(key)
            }

            return entry.results
        }

        /**
         * Store search results in cache.
         *
         * @param query Search query
         * @param forumIds Optional forum filter
         * @param results Search results to cache
         */
        public fun put(
            query: String,
            forumIds: String?,
            results: List<SearchResult>,
        ) {
            // Don't cache empty results
            if (results.isEmpty()) return

            val key = generateKey(query, forumIds)
            val entry =
                CacheEntry(
                    results = results,
                    timestamp = System.currentTimeMillis(),
                )

            cache[key] = entry

            // Update access order and evict if needed
            synchronized(accessOrder) {
                accessOrder.remove(key)
                accessOrder.add(key)

                // LRU eviction if over limit
                while (accessOrder.size > MAX_CACHE_SIZE) {
                    val oldestKey = accessOrder.removeAt(0)
                    cache.remove(oldestKey)
                }
            }
        }

        /**
         * Clear all cached search results.
         */
        public fun clear(...) {
            cache.clear()
            synchronized(accessOrder) {
                accessOrder.clear()
            }
        }

        /**
         * Get approximate cache size in bytes.
         */
        public fun getCacheSize(): Long {
            // Rough estimation: each SearchResult ~500 bytes
            // Synchronize access to cache to prevent concurrent modification
            val resultsCount =
                synchronized(cache) {
                    cache.values.sumOf { it.results.size }
                }
            return resultsCount * AVERAGE_RESULT_SIZE_BYTES
        }

        /**
         * Get cache statistics.
         */
        public fun getStatistics(): CacheStatistics {
            // Synchronize access to cache to prevent concurrent modification
            val entries =
                synchronized(cache) {
                    cache.values.toList()
                }

            return CacheStatistics(
                entriesCount = cache.size,
                totalResults = entries.sumOf { it.results.size },
                estimatedSize = getCacheSize(),
                oldestEntry = entries.minOfOrNull { it.timestamp } ?: 0L,
                newestEntry = entries.maxOfOrNull { it.timestamp } ?: 0L,
            )
        }

        /**
         * Generate cache key from query and filters.
         */
        private fun generateKey(
            query: String,
            forumIds: String?,
        ): String {
            val normalizedQuery = query.trim().lowercase()
            val normalizedForums = forumIds?.trim() ?: ""
            return "$normalizedQuery|$normalizedForums"
        }

        /**
         * Cache entry with TTL.
         */
        private data class CacheEntry(
            public val results: List<SearchResult>,
            public val timestamp: Long,
        ) {
            public fun isExpired(): Boolean = (System.currentTimeMillis() - timestamp) > CACHE_TTL_MS
        }

        /**
         * Cache statistics.
         */
        public data class CacheStatistics(
            public val entriesCount: Int,
            public val totalResults: Int,
            public val estimatedSize: Long,
            public val oldestEntry: Long,
            public val newestEntry: Long,
        )

        public companion object {
            // Cache TTL: 30 minutes
            private const val CACHE_TTL_MS = 30 * 60 * 1000L

            // Max cache entries (LRU eviction)
            private const val MAX_CACHE_SIZE = 50

            // Average size per SearchResult (bytes)
            private const val AVERAGE_RESULT_SIZE_BYTES = 500L
        }
    }
