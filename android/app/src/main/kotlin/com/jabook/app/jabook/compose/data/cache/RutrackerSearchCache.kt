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
        private val lock = Any()

        // Both structures are guarded by [lock]. Keeping their mutations together prevents an
        // in-flight put/get from reviving an entry while a user-initiated cache clear is running.
        private val cache = mutableMapOf<String, CacheEntry>()

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
        ): List<SearchResult>? =
            synchronized(lock) {
                val key = generateKey(query, forumIds)
                val entry = cache[key]
                when {
                    entry == null -> null
                    entry.isExpired() -> {
                        cache.remove(key)
                        accessOrder.remove(key)
                        null
                    }
                    else -> {
                        // Update access order
                        accessOrder.remove(key)
                        accessOrder.add(key)
                        entry.results.toList()
                    }
                }
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
        ): Unit =
            synchronized(lock) {
                // Don't cache empty results
                if (results.isNotEmpty()) {
                    val key = generateKey(query, forumIds)
                    val entry =
                        CacheEntry(
                            // The network/parser layer can reuse a mutable list. Retain a snapshot so a
                            // later mutation cannot silently alter a cached result set.
                            results = results.toList(),
                            timestamp = System.currentTimeMillis(),
                        )

                    cache[key] = entry

                    // Update access order and evict if needed
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
        public fun clear(): Unit =
            synchronized(lock) {
                cache.clear()
                accessOrder.clear()
            }

        /**
         * Get approximate cache size in bytes.
         */
        public fun getCacheSize(): Long =
            synchronized(lock) {
                // Rough estimation: each SearchResult ~500 bytes
                cache.values.sumOf { it.results.size } * AVERAGE_RESULT_SIZE_BYTES
            }

        /**
         * Get cache statistics.
         */
        public fun getStatistics(): CacheStatistics =
            synchronized(lock) {
                val entries = cache.values.toList()
                CacheStatistics(
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
            val results: List<SearchResult>,
            val timestamp: Long,
        ) {
            public fun isExpired(): Boolean = (System.currentTimeMillis() - timestamp) > CACHE_TTL_MS
        }

        /**
         * Cache statistics.
         */
        public data class CacheStatistics(
            val entriesCount: Int,
            val totalResults: Int,
            val estimatedSize: Long,
            val oldestEntry: Long,
            val newestEntry: Long,
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
