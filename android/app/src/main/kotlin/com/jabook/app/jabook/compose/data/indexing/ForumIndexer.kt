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

import android.content.Context
import android.util.Log
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.jabook.app.jabook.compose.data.indexing.IndexingProgress
import com.jabook.app.jabook.compose.data.local.dao.IndexMetadata
import com.jabook.app.jabook.compose.data.local.dao.OfflineSearchDao
import com.jabook.app.jabook.compose.data.local.entity.CachedTopicEntity
import com.jabook.app.jabook.compose.data.local.entity.toCachedTopicEntity
import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.data.remote.parser.RutrackerParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for indexing all audiobook forums on RuTracker.
 *
 * This service pre-indexes all topics from audiobook forums to enable
 * fast offline search without network requests.
 *
 * Features:
 * - Full indexing: Indexes all topics from all audiobook forums
 * - Incremental updates: Only updates topics that are old or missing (daily by default)
 * - Cover preloading: Preloads cover images to Coil cache for instant display
 * - Version tracking: Tracks index version for incremental updates
 * - Smart caching: Uses database indices for fast search queries
 *
 * Update Strategy:
 * - Full index: Recommended once per week (or on first install)
 * - Incremental update: Daily (updates topics older than 24 hours)
 * - Automatic: Check needsUpdate() to determine if update is needed
 *
 * What's stored in index:
 * - Topic metadata: title, author, category, size, seeders, leechers
 * - Download links: magnet URL, torrent URL
 * - Cover URL: For preloading and display
 * - Timestamps: For tracking freshness and incremental updates
 * - Index version: For tracking which version of index created the entry
 */
@Singleton
class ForumIndexer
    @Inject
    constructor(
        private val api: RutrackerApi,
        private val parser: RutrackerParser,
        private val offlineSearchDao: OfflineSearchDao,
        private val mirrorManager: MirrorManager,
        @param:ApplicationContext private val context: Context,
    ) {
        // Background scope for non-blocking operations (cover preloading)
        private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        companion object {
            private const val TAG = "ForumIndexer"
            private const val TOPICS_PER_PAGE = 50 // Typical RuTracker forum page size
            private const val DELAY_BETWEEN_REQUESTS_MS = 300L // Rate limiting (reduced for faster indexing)
            private const val MAX_PAGES_PER_FORUM = 100 // Safety limit

            // Update strategy constants
            private const val FULL_UPDATE_INTERVAL_DAYS = 7L // Full re-index every 7 days
            private const val INCREMENTAL_UPDATE_INTERVAL_HOURS = 24L // Incremental update daily
            private const val MAX_AGE_FOR_UPDATE_MS = INCREMENTAL_UPDATE_INTERVAL_HOURS * 60 * 60 * 1000

            // Cover preloading
            private const val PRELOAD_COVERS_BATCH_SIZE = 10 // Preload covers in batches
            private const val PRELOAD_COVERS_DELAY_MS = 100L // Delay between cover preloads

            // Performance optimization
            private const val MAX_CONCURRENT_FORUMS = 3 // Increased from 2 to 3 for better performance
            private const val BATCH_SIZE_FOR_DB = 100 // Increased from 50 to 100 for faster DB writes
            private const val MAX_MEMORY_TOPICS = 200 // Max topics to keep in memory before DB flush
            private const val DELAY_BETWEEN_REQUESTS_MS = 300L // Reduced from 500ms to 300ms for faster indexing
        }

        /**
         * Index all audiobook forums (full index) with optimized parallel processing.
         *
         * @param forumIds Comma-separated list of forum IDs to index
         * @param preloadCovers Whether to preload cover images to Coil cache (default: true)
         * @param onProgress Callback with IndexingProgress updates
         * @return Total number of topics indexed
         */
        suspend fun indexForums(
            forumIds: String,
            preloadCovers: Boolean = true,
            onProgress: ((IndexingProgress) -> Unit)? = null,
        ): Int =
            withContext(Dispatchers.IO) {
                val startTime = System.currentTimeMillis()
                val currentIndexVersion = getCurrentIndexVersion() + 1
                val forumIdList = forumIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                var totalIndexed = 0
                val coversToPreload = mutableListOf<String>()

                Log.i(TAG, "Starting full forum indexing for ${forumIdList.size} forums (version $currentIndexVersion)")

                // Clear old indexed data before starting new index to ensure only audiobook forums are indexed
                Log.i(TAG, "Clearing old indexed data before new index...")
                clearIndex()
                Log.i(TAG, "Old indexed data cleared")

                onProgress?.invoke(
                    IndexingProgress.InProgress(
                        currentForum = forumIdList.firstOrNull() ?: "",
                        currentForumIndex = 0,
                        totalForums = forumIdList.size,
                        currentPage = 0,
                        topicsIndexed = 0,
                    ),
                )

                // Use AtomicInteger for thread-safe progress tracking
                val topicsIndexedAtomic =
                    java.util.concurrent.atomic
                        .AtomicInteger(0)

                // Process forums in parallel batches for better performance
                forumIdList.chunked(MAX_CONCURRENT_FORUMS).forEachIndexed { batchIndex, batch ->
                    val batchResults =
                        batch
                            .mapIndexed { indexInBatch, forumId ->
                                async(Dispatchers.IO) {
                                    try {
                                        val forumIndex = batchIndex * MAX_CONCURRENT_FORUMS + indexInBatch
                                        var forumTopicsCount = 0
                                        val (indexed, covers) =
                                            indexForum(
                                                forumId,
                                                currentIndexVersion,
                                            ) { page, topicsInForum ->
                                                forumTopicsCount = topicsInForum
                                                // Update progress with thread-safe counter
                                                val currentTotal = topicsIndexedAtomic.get()
                                                onProgress?.invoke(
                                                    IndexingProgress.InProgress(
                                                        currentForum = forumId,
                                                        currentForumIndex = forumIndex,
                                                        totalForums = forumIdList.size,
                                                        currentPage = page,
                                                        topicsIndexed = currentTotal,
                                                    ),
                                                )
                                            }
                                        // Update atomic counter after forum completion
                                        topicsIndexedAtomic.addAndGet(indexed)
                                        Pair(indexed, covers)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to index forum $forumId", e)
                                        onProgress?.invoke(
                                            IndexingProgress.Error(
                                                message = "Failed to index forum $forumId: ${e.message}",
                                                forumId = forumId,
                                            ),
                                        )
                                        Pair(0, emptyList<String>())
                                    }
                                }
                            }.awaitAll()

                    // Aggregate results
                    batchResults.forEach { (indexed, covers) ->
                        totalIndexed += indexed
                        coversToPreload.addAll(covers)
                    }
                }

                // Preload covers in background (non-blocking)
                if (preloadCovers && coversToPreload.isNotEmpty()) {
                    backgroundScope.launch {
                        preloadCovers(coversToPreload)
                    }
                }

                val duration = System.currentTimeMillis() - startTime
                val runtime = Runtime.getRuntime()
                val finalMemory = runtime.totalMemory() - runtime.freeMemory()
                val memoryUsed = finalMemory / (1024 * 1024) // MB
                val topicsPerSecond = if (duration > 0) (totalIndexed * 1000) / duration else 0

                Log.i(
                    TAG,
                    "Forum indexing completed. Total topics: $totalIndexed, covers: ${coversToPreload.size}, " +
                        "duration: ${duration}ms (${duration / 1000}s), " +
                        "speed: $topicsPerSecond topics/s, memory: +${memoryUsed}MB",
                )

                onProgress?.invoke(
                    IndexingProgress.Completed(
                        totalTopics = totalIndexed,
                        durationMs = duration,
                    ),
                )

                totalIndexed
            }

        /**
         * Incremental update: only update topics that are old or missing.
         *
         * @param forumIds Comma-separated list of forum IDs to check
         * @param maxAgeMs Maximum age in milliseconds (topics older than this will be updated)
         * @param preloadCovers Whether to preload cover images (default: true)
         * @param onProgress Progress callback
         * @return Number of topics updated
         */
        suspend fun incrementalUpdate(
            forumIds: String,
            maxAgeMs: Long = MAX_AGE_FOR_UPDATE_MS,
            preloadCovers: Boolean = true,
            onProgress: ((forumId: String, updated: Int, total: Int) -> Unit)? = null,
        ): Int =
            withContext(Dispatchers.IO) {
                val currentIndexVersion = getCurrentIndexVersion()
                val forumIdList = forumIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                var totalUpdated = 0
                val coversToPreload = mutableListOf<String>()

                Log.i(TAG, "Starting incremental update (max age: ${maxAgeMs / (1000 * 60 * 60)} hours)")

                for (forumId in forumIdList) {
                    try {
                        val (updated, covers) = updateForumIncremental(forumId, maxAgeMs, currentIndexVersion, onProgress)
                        totalUpdated += updated
                        coversToPreload.addAll(covers)
                        Log.i(TAG, "Updated forum $forumId: $updated topics")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update forum $forumId", e)
                    }
                }

                // Preload new covers
                if (preloadCovers && coversToPreload.isNotEmpty()) {
                    preloadCovers(coversToPreload)
                }

                Log.i(TAG, "Incremental update completed. Updated: $totalUpdated topics, covers: ${coversToPreload.size}")
                totalUpdated
            }

        /**
         * Index a single forum by fetching all pages with batched DB writes.
         *
         * @param forumId Forum ID to index
         * @param indexVersion Current index version
         * @param onProgress Progress callback with (page, topicsInForum)
         * @return Pair of (number of topics indexed, list of cover URLs to preload)
         */
        private suspend fun indexForum(
            forumId: String,
            indexVersion: Int,
            onProgress: ((page: Int, topicsInForum: Int) -> Unit)? = null,
        ): Pair<Int, List<String>> {
            var totalTopics = 0
            var page = 0
            var hasMorePages = true
            val coversToPreload = mutableListOf<String>()
            val entitiesBuffer = mutableListOf<CachedTopicEntity>() // Buffer for batched writes

            val forumStartTime = System.currentTimeMillis()
            val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            Log.i(TAG, "Starting indexing forum $forumId (version $indexVersion)")

            while (hasMorePages && page < MAX_PAGES_PER_FORUM) {
                try {
                    val pageStartTime = System.currentTimeMillis()
                    val response = api.getForumPage(forumId, start = page * TOPICS_PER_PAGE)
                    val fetchTime = System.currentTimeMillis() - pageStartTime

                    if (!response.isSuccessful) {
                        Log.w(TAG, "Failed to fetch forum $forumId page $page: HTTP ${response.code()} (took ${fetchTime}ms)")
                        break
                    }

                    val body = response.body() ?: break
                    val bodySize = body.contentLength()
                    val parseStartTime = System.currentTimeMillis()
                    val topics = parser.parseForumPage(body, forumId)
                    val parseTime = System.currentTimeMillis() - parseStartTime

                    if (topics.isEmpty()) {
                        Log.d(TAG, "Forum $forumId page $page: no topics found, ending (fetch: ${fetchTime}ms, parse: ${parseTime}ms)")
                        hasMorePages = false
                    } else {
                        val dbStartTime = System.currentTimeMillis()
                        Log.d(
                            TAG,
                            "Forum $forumId page $page: parsed ${topics.size} topics (total: $totalTopics, " +
                                "body: ${bodySize / 1024}KB, fetch: ${fetchTime}ms, parse: ${parseTime}ms)",
                        )
                        // Add to buffer with current index version
                        val newEntities = topics.map { it.toCachedTopicEntity(indexVersion) }
                        entitiesBuffer.addAll(newEntities)
                        totalTopics += topics.size

                        // Collect cover URLs for preloading (normalize URLs using current mirror)
                        val baseUrl = mirrorManager.getBaseUrl()
                        topics
                            .mapNotNull { topic ->
                                topic.coverUrl?.let { url ->
                                    when {
                                        url.startsWith("http://") || url.startsWith("https://") -> url
                                        url.startsWith("//") -> "https:$url"
                                        url.startsWith("/") -> "$baseUrl$url"
                                        else -> "$baseUrl/$url"
                                    }
                                }
                            }.forEach { coversToPreload.add(it) }

                        // Flush to DB when buffer reaches batch size or at end
                        if (entitiesBuffer.size >= BATCH_SIZE_FOR_DB || !hasMorePages) {
                            val dbWriteStartTime = System.currentTimeMillis()
                            offlineSearchDao.upsertTopics(entitiesBuffer)
                            val dbWriteTime = System.currentTimeMillis() - dbWriteStartTime
                            Log.d(TAG, "Forum $forumId: wrote ${entitiesBuffer.size} topics to DB in ${dbWriteTime}ms")
                            entitiesBuffer.clear()
                        }

                        onProgress?.invoke(page, totalTopics)

                        // Rate limiting
                        delay(DELAY_BETWEEN_REQUESTS_MS)
                        page++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error indexing forum $forumId page $page", e)
                    hasMorePages = false
                }
            }

            // Flush remaining entities
            if (entitiesBuffer.isNotEmpty()) {
                val dbWriteStartTime = System.currentTimeMillis()
                offlineSearchDao.upsertTopics(entitiesBuffer)
                val dbWriteTime = System.currentTimeMillis() - dbWriteStartTime
                Log.d(TAG, "Forum $forumId: flushed ${entitiesBuffer.size} remaining topics to DB in ${dbWriteTime}ms")
            }

            val forumDuration = System.currentTimeMillis() - forumStartTime
            val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            val memoryUsed = (finalMemory - initialMemory) / (1024 * 1024) // MB
            val avgTimePerTopic = if (totalTopics > 0) forumDuration / totalTopics else 0

            Log.i(
                TAG,
                "Forum $forumId indexing completed: $totalTopics topics, ${coversToPreload.size} covers, " +
                    "duration: ${forumDuration}ms (${forumDuration / 1000}s), " +
                    "avg: ${avgTimePerTopic}ms/topic, memory: +${memoryUsed}MB",
            )
            return Pair(totalTopics, coversToPreload)
        }

        /**
         * Incrementally update a single forum (only fetch new/updated topics).
         *
         * @param forumId Forum ID to update
         * @param maxAgeMs Maximum age for topics to update
         * @param currentIndexVersion Current index version
         * @param onProgress Progress callback
         * @return Pair of (number of topics updated, list of cover URLs to preload)
         */
        private suspend fun updateForumIncremental(
            forumId: String,
            maxAgeMs: Long,
            currentIndexVersion: Int,
            onProgress: ((forumId: String, updated: Int, total: Int) -> Unit)?,
        ): Pair<Int, List<String>> {
            var totalUpdated = 0
            var page = 0
            var hasMorePages = true
            val coversToPreload = mutableListOf<String>()

            while (hasMorePages && page < MAX_PAGES_PER_FORUM) {
                try {
                    val response = api.getForumPage(forumId, start = page * TOPICS_PER_PAGE)
                    if (!response.isSuccessful) {
                        break
                    }

                    val body = response.body() ?: break
                    val topics = parser.parseForumPage(body, forumId)

                    if (topics.isEmpty()) {
                        hasMorePages = false
                    } else {
                        // Filter: only update new topics or topics that need updating
                        val topicsToUpdate =
                            topics.filter { topic ->
                                val existing = offlineSearchDao.getTopicById(topic.topicId)
                                existing == null ||
                                    // New topic
                                    existing.lastUpdated < (System.currentTimeMillis() - maxAgeMs) ||
                                    // Old topic
                                    existing.indexVersion != currentIndexVersion // Different version
                            }

                        if (topicsToUpdate.isNotEmpty()) {
                            val entities = topicsToUpdate.map { it.toCachedTopicEntity(currentIndexVersion) }
                            val dbWriteStartTime = System.currentTimeMillis()
                            offlineSearchDao.upsertTopics(entities)
                            val dbWriteTime = System.currentTimeMillis() - dbWriteStartTime
                            Log.d(TAG, "Forum $forumId incremental: updated ${entities.size} topics in DB (${dbWriteTime}ms)")
                            totalUpdated += topicsToUpdate.size

                            // Collect cover URLs (normalize URLs using current mirror)
                            val baseUrl = mirrorManager.getBaseUrl()
                            topicsToUpdate
                                .mapNotNull { topic ->
                                    topic.coverUrl?.let { url ->
                                        when {
                                            url.startsWith("http://") || url.startsWith("https://") -> url
                                            url.startsWith("//") -> "https:$url"
                                            url.startsWith("/") -> "$baseUrl$url"
                                            else -> "$baseUrl/$url"
                                        }
                                    }
                                }.forEach { coversToPreload.add(it) }
                        }

                        onProgress?.invoke(forumId, totalUpdated, topics.size)

                        // Rate limiting
                        delay(DELAY_BETWEEN_REQUESTS_MS)
                        page++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating forum $forumId page $page", e)
                    hasMorePages = false
                }
            }

            return Pair(totalUpdated, coversToPreload)
        }

        /**
         * Preload cover images to Coil cache for faster display.
         *
         * @param coverUrls List of cover URLs to preload
         */
        private suspend fun preloadCovers(coverUrls: List<String>) =
            withContext(Dispatchers.IO) {
                val imageLoader = SingletonImageLoader.get(context)
                val uniqueUrls = coverUrls.distinct().take(500) // Limit to 500 covers per batch

                Log.d(TAG, "Preloading ${uniqueUrls.size} cover images...")

                // Preload in batches to avoid overwhelming the system
                uniqueUrls.chunked(PRELOAD_COVERS_BATCH_SIZE).forEach { batch ->
                    batch
                        .map { url ->
                            async(Dispatchers.IO) {
                                try {
                                    val request =
                                        ImageRequest
                                            .Builder(context)
                                            .data(url)
                                            .build()
                                    imageLoader.enqueue(request)
                                } catch (e: Exception) {
                                    // Silently fail - covers will load on demand
                                }
                            }
                        }.awaitAll()

                    delay(PRELOAD_COVERS_DELAY_MS)
                }

                Log.d(TAG, "Cover preloading completed")
            }

        /**
         * Get index statistics.
         *
         * @return Total number of indexed topics
         */
        suspend fun getIndexSize(): Int =
            withContext(Dispatchers.IO) {
                offlineSearchDao.getTopicCount()
            }

        /**
         * Get detailed index metadata.
         *
         * @return IndexMetadata with statistics
         */
        suspend fun getIndexMetadata(): IndexMetadata? =
            withContext(Dispatchers.IO) {
                offlineSearchDao.getIndexMetadata()
            }

        /**
         * Get current index version (highest version in database).
         *
         * @return Current index version
         */
        private suspend fun getCurrentIndexVersion(): Int =
            withContext(Dispatchers.IO) {
                val metadata = offlineSearchDao.getIndexMetadata()
                // For now, return 1. In future, can track version separately
                1
            }

        /**
         * Check if index needs update based on age.
         *
         * @param maxAgeMs Maximum age in milliseconds
         * @return True if index needs update
         */
        suspend fun needsUpdate(maxAgeMs: Long = MAX_AGE_FOR_UPDATE_MS): Boolean =
            withContext(Dispatchers.IO) {
                val metadata = offlineSearchDao.getIndexMetadata()
                if (metadata == null || metadata.count == 0) {
                    return@withContext true // No index, needs full index
                }

                val oldestUpdated = metadata.oldestUpdated ?: return@withContext true
                val age = System.currentTimeMillis() - oldestUpdated
                age > maxAgeMs
            }

        /**
         * Clear the entire index.
         */
        suspend fun clearIndex() =
            withContext(Dispatchers.IO) {
                offlineSearchDao.deleteAllTopics()
                offlineSearchDao.deleteAllMappings()
                Log.i(TAG, "Index cleared")
            }
    }
