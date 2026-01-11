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
import com.jabook.app.jabook.compose.data.remote.mapper.toDomain
import com.jabook.app.jabook.compose.data.remote.parser.RutrackerParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
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
public class ForumIndexer
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

        public companion object {
            private const val TAG = "ForumIndexer"
            private const val TOPICS_PER_PAGE = 50 // Typical RuTracker forum page size
            private const val DELAY_BETWEEN_REQUESTS_MS = 300L // Rate limiting (reduced for faster indexing)
            private const val MAX_PAGES_PER_FORUM = 100_000 // Effectively unlimited (some forums have 350+ pages)

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
                public val startTime = System.currentTimeMillis()
                public val currentIndexVersion = getCurrentIndexVersion() + 1
                public val forumIdList = forumIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                
                var totalIndexed: Int = 0
                val coversToPreload = mutableListOf<String>()

                // Log current mirror at start of indexing
                public val initialMirror = mirrorManager.getCurrentMirrorDomain()
                Log.i(TAG, "=== FORUM INDEXING START ===")
                Log.i(TAG, "Using mirror: $initialMirror")
                Log.i(TAG, "Indexing version: $currentIndexVersion")
                Log.i(TAG, "Forums to index: ${forumIdList.size}")

                // Validate that only audiobook forums are being indexed
                public val allowedForums =
                    RutrackerApi.AUDIOBOOKS_FORUM_IDS
                        .split(",")
                        .map { it.trim() }
                        .toSet()
                public val invalidForums = forumIdList.filter { it !in allowedForums }
                if (invalidForums.isNotEmpty()) {
                    Log.w(TAG, "WARNING: Attempting to index non-audiobook forums: $invalidForums")
                    Log.w(TAG, "Only audiobook forums will be indexed. Allowed forums: ${allowedForums.size}")
                }

                Log.i(TAG, "Starting full forum indexing for ${forumIdList.size} forums (version $currentIndexVersion)")
                Log.i(TAG, "Forums to index: ${forumIdList.joinToString(", ")}")

                // Clear old indexed data before starting new index to ensure only audiobook forums are indexed
                Log.i(TAG, "Clearing old indexed data before new index...")
                public val oldCount = getIndexSize()
                clearIndex()
                Log.i(TAG, "Old indexed data cleared (was $oldCount topics)")

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
                public val topicsIndexedAtomic =
                    java.util.concurrent.atomic
                        .AtomicInteger(0)

                // Process forums in parallel batches for better performance
                forumIdList.chunked(MAX_CONCURRENT_FORUMS).forEachIndexed { batchIndex, batch ->
                    public val batchResults =
                        batch
                            .mapIndexed { indexInBatch, forumId ->
                                async(Dispatchers.IO) {
                                    try {
                                        public val forumIndex = batchIndex * MAX_CONCURRENT_FORUMS + indexInBatch
                                                                                public var forumTopicsCount: Int = 0
                                        val (indexed, covers) =
                                            indexForum(
                                                forumId,
                                                currentIndexVersion,
                                            ) { page, topicsInForum ->
                                                forumTopicsCount = topicsInForum
                                                // Update progress less frequently to reduce jitter
                                                // Only update every 2 pages or on first/last page
                                                if (page == 0 || page % 2 == 0 || topicsInForum < 50) {
                                                    // Update progress with thread-safe counter
                                                    public val currentTotal = topicsIndexedAtomic.get()
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

                    // Aggregate results and update progress after batch completion
                    batchResults.forEach { (indexed, covers) ->
                        totalIndexed += indexed
                        coversToPreload.addAll(covers)
                    }

                    // Update progress after batch completion with accurate count
                    public val currentTotal = topicsIndexedAtomic.get()
                    public val nextBatchStartIndex = ((batchIndex + 1) * MAX_CONCURRENT_FORUMS).coerceAtMost(forumIdList.size)
                    onProgress?.invoke(
                        IndexingProgress.InProgress(
                            currentForum = batch.lastOrNull() ?: "",
                            currentForumIndex = nextBatchStartIndex,
                            totalForums = forumIdList.size,
                            currentPage = 0, // Reset page for next batch
                            topicsIndexed = currentTotal,
                        ),
                    )
                }

                // Cover preloading is disabled (optimization: covers not stored in index)
                // Covers will be loaded on-demand when topic is opened via getTopicDetails()

                public val duration = System.currentTimeMillis() - startTime
                public val runtime = Runtime.getRuntime()
                public val finalMemory = runtime.totalMemory() - runtime.freeMemory()
                public val memoryUsed = finalMemory / (1024 * 1024) // MB
                public val topicsPerSecond = if (duration > 0) (totalIndexed * 1000) / duration else 0

                // Verify mirror didn't change during indexing
                public val finalMirror = mirrorManager.getCurrentMirrorDomain()
                if (finalMirror != initialMirror) {
                    Log.w(
                        TAG,
                        "⚠️ Mirror changed during indexing! Initial: $initialMirror, Final: $finalMirror. " +
                            "This may indicate auto-switch occurred or connection issues.",
                    )
                } else {
                    Log.d(TAG, "Mirror remained stable: $finalMirror")
                }

                // Verify actual count in database matches indexed count (single source of truth)
                public val actualCountInDb = getIndexSize()
                if (actualCountInDb != totalIndexed) {
                    Log.w(
                        TAG,
                        "Count mismatch: indexed $totalIndexed topics, but database has $actualCountInDb topics. " +
                            "Using database count as single source of truth.",
                    )
                }

                Log.i(
                    TAG,
                    "Forum indexing completed. Indexed: $totalIndexed topics, database: $actualCountInDb topics, " +
                        "duration: ${duration}ms (${duration / 1000}s), " +
                        "speed: $topicsPerSecond topics/s, memory: +${memoryUsed}MB, " +
                        "mirror: $finalMirror",
                )

                // Log sample topics to verify index content (AUTOMATIC VERIFICATION)
                try {
                    public val sampleTopics = offlineSearchDao.getSampleTopics(5)
                    Log.i(TAG, "🔍 === AUTOMATIC INDEX VERIFICATION: DATABASE CONTENT CHECK ===")
                    Log.i(TAG, "Found ${sampleTopics.size} sample topics in database:")
                    sampleTopics.forEachIndexed { index, topic ->
                        Log.i(
                            TAG,
                            "  [$index] ID: ${topic.topicId} | Title: ${topic.title.take(50)}... | " +
                                "Author: ${topic.author} | Cat: ${topic.category} | Size: ${topic.size}",
                        )
                    }
                    Log.i(TAG, "✅ === DATABASE CONTENT VERIFIED: ${sampleTopics.size} ITEMS RETRIEVED ===")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to perform automatic index verification", e)
                }

                // Use database count as single source of truth for completion
                public val finalCount = actualCountInDb
                onProgress?.invoke(
                    IndexingProgress.Completed(
                        totalTopics = finalCount, // Use database count, not indexed count
                        durationMs = duration,
                    ),
                )

                finalCount // Return database count as single source of truth
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
                public val currentIndexVersion = getCurrentIndexVersion()
                public val forumIdList = forumIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                
                var totalUpdated: Int = 0
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

                // Cover preloading is disabled (optimization: covers not stored in index)

                Log.i(TAG, "Incremental update completed. Updated: $totalUpdated topics")
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
            
            var totalTopics: Int = 0
            var page: Int = 0
            var hasMorePages: Boolean = true
            val coversToPreload = mutableListOf<String>()
            public val entitiesBuffer = mutableListOf<CachedTopicEntity>() // Buffer for batched writes

            public val forumStartTime = System.currentTimeMillis()
            public val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            Log.i(TAG, "Starting indexing forum $forumId (version $indexVersion)")

            while (hasMorePages && page < MAX_PAGES_PER_FORUM) {
                try {
                    public val pageStartTime = System.currentTimeMillis()
                    public val response = api.getForumPage(forumId, start = page * TOPICS_PER_PAGE)
                    public val fetchTime = System.currentTimeMillis() - pageStartTime

                    if (!response.isSuccessful) {
                        Log.w(TAG, "Failed to fetch forum $forumId page $page: HTTP ${response.code()} (took ${fetchTime}ms)")
                        break
                    }

                    public val body = response.body() ?: break
                    public val bodySize = body.contentLength()
                    public val parseStartTime = System.currentTimeMillis()
                    // Use parseForumPageWithPagination to get pagination info
                    public val pageResult = parser.parseForumPageWithPagination(body, forumId)
                    public val topics = pageResult.topics
                    public val parseTime = System.currentTimeMillis() - parseStartTime

                    // Update hasMorePages based on actual pagination detection
                    hasMorePages = pageResult.hasMorePages

                    if (topics.isEmpty()) {
                        Log.d(TAG, "Forum $forumId page $page: no topics found, ending (fetch: ${fetchTime}ms, parse: ${parseTime}ms)")
                        hasMorePages = false
                    } else {
                        public val dbStartTime = System.currentTimeMillis()
                        // Validate topics before adding to index - only count valid ones
                        public val validTopics =
                            topics.filter { topic ->
                                public val domain = topic.toDomain()
                                domain.isValid()
                            }
                        public val invalidCount = topics.size - validTopics.size
                        if (invalidCount > 0) {
                            Log.w(
                                TAG,
                                "Forum $forumId page $page: filtered out $invalidCount invalid topics " +
                                    "(out of ${topics.size} parsed)",
                            )
                        }
                        Log.d(
                            TAG,
                            "Forum $forumId page $page: parsed ${topics.size} topics, " +
                                "${validTopics.size} valid (total: $totalTopics, " +
                                "body: ${bodySize / 1024}KB, fetch: ${fetchTime}ms, parse: ${parseTime}ms)",
                        )
                        // Add only valid topics to buffer with current index version
                        // Note: magnetUrl, torrentUrl, coverUrl are NOT saved (optimization)
                        public val newEntities = validTopics.map { it.toCachedTopicEntity(indexVersion) }
                        entitiesBuffer.addAll(newEntities)
                        totalTopics += validTopics.size // Count only valid topics

                        // Cover URLs are no longer collected for preloading since they're not stored in index
                        // Covers will be loaded on-demand when topic is opened via getTopicDetails()

                        // Flush to DB when buffer reaches batch size or at end
                        if (entitiesBuffer.size >= BATCH_SIZE_FOR_DB || !hasMorePages) {
                            public val dbWriteStartTime = System.currentTimeMillis()
                            offlineSearchDao.upsertTopics(entitiesBuffer)
                            public val dbWriteTime = System.currentTimeMillis() - dbWriteStartTime
                            public val writtenCount = entitiesBuffer.size
                            Log.d(TAG, "Forum $forumId: wrote $writtenCount topics to DB in ${dbWriteTime}ms")
                            entitiesBuffer.clear()
                        }

                        // Update progress callback (will throttle updates internally)
                        onProgress?.invoke(page, totalTopics)

                        // Rate limiting
                        delay(DELAY_BETWEEN_REQUESTS_MS)
                        page++
                    }
                } catch (e: Exception) {
                    // Check if this is a network/DNS error that might be resolved by switching mirror
                    public val isNetworkError =
                        e is java.net.UnknownHostException ||
                            e is java.net.ConnectException ||
                            e is java.net.SocketTimeoutException ||
                            (e.message?.contains("Unable to resolve host", ignoreCase = true) == true) ||
                            (e.message?.contains("No address associated with hostname", ignoreCase = true) == true)

                    if (isNetworkError) {
                        Log.w(TAG, "Network error indexing forum $forumId page $page (${e.javaClass.simpleName}): ${e.message}")

                        // Check if auto-switch is enabled before attempting mirror switch
                        public val autoSwitchEnabled = mirrorManager.isAutoSwitchEnabled()

                        if (!autoSwitchEnabled) {
                            Log.w(TAG, "Auto-switch is disabled, not attempting mirror switch. User must switch mirror manually.")
                            hasMorePages = false
                        } else {
                            // Try switching mirror and retry once
                            public var retrySucceeded: Boolean = false                            try {
                                Log.i(TAG, "Auto-switch enabled, attempting to switch mirror...")
                                public val switched = mirrorManager.switchToNextMirror()
                                if (switched) {
                                    public val newMirror = mirrorManager.currentMirror.value
                                    Log.i(TAG, "Switched to mirror $newMirror, retrying forum $forumId page $page")
                                    delay(500) // Brief delay before retry
                                    // Retry the request
                                    public val retryResponse = api.getForumPage(forumId, start = page * TOPICS_PER_PAGE)
                                    if (retryResponse.isSuccessful) {
                                        public val retryBody = retryResponse.body()
                                        if (retryBody != null) {
                                            public val retryParseStartTime = System.currentTimeMillis()
                                            public val retryPageResult = parser.parseForumPageWithPagination(retryBody, forumId)
                                            public val retryTopics = retryPageResult.topics
                                            public val retryParseTime = System.currentTimeMillis() - retryParseStartTime

                                            hasMorePages = retryPageResult.hasMorePages

                                            if (retryTopics.isNotEmpty()) {
                                                public val newEntities = retryTopics.map { it.toCachedTopicEntity(indexVersion) }
                                                entitiesBuffer.addAll(newEntities)
                                                totalTopics += retryTopics.size

                                                // Cover URLs are no longer collected (optimization)

                                                Log.i(
                                                    TAG,
                                                    "Retry succeeded after mirror switch: parsed ${retryTopics.size} topics (parse: ${retryParseTime}ms)",
                                                )
                                                retrySucceeded = true

                                                // Flush if needed
                                                if (entitiesBuffer.size >= BATCH_SIZE_FOR_DB || !hasMorePages) {
                                                    public val dbWriteStartTime = System.currentTimeMillis()
                                                    offlineSearchDao.upsertTopics(entitiesBuffer)
                                                    public val dbWriteTime = System.currentTimeMillis() - dbWriteStartTime
                                                    Log.d(
                                                        TAG,
                                                        "Forum $forumId: wrote ${entitiesBuffer.size} topics to DB in ${dbWriteTime}ms",
                                                    )
                                                    entitiesBuffer.clear()
                                                }

                                                onProgress?.invoke(page, totalTopics)
                                                delay(DELAY_BETWEEN_REQUESTS_MS)
                                                page++
                                            } else {
                                                Log.d(TAG, "Retry succeeded but no topics found, ending")
                                                hasMorePages = false
                                            }
                                        } else {
                                            Log.w(TAG, "Retry response body is null, stopping forum indexing")
                                            hasMorePages = false
                                        }
                                    } else {
                                        Log.w(TAG, "Retry failed with HTTP ${retryResponse.code()}, stopping forum indexing")
                                        hasMorePages = false
                                    }
                                } else {
                                    Log.e(TAG, "Failed to switch mirror, stopping forum indexing")
                                    hasMorePages = false
                                }
                            } catch (retryException: Exception) {
                                Log.e(TAG, "Error during mirror switch retry for forum $forumId page $page", retryException)
                                hasMorePages = false
                            }

                            if (!retrySucceeded) {
                                hasMorePages = false
                            }
                        }
                    } else {
                        Log.e(TAG, "Error indexing forum $forumId page $page", e)
                        hasMorePages = false
                    }
                }
            }

            // Flush remaining entities
            if (entitiesBuffer.isNotEmpty()) {
                public val dbWriteStartTime = System.currentTimeMillis()
                offlineSearchDao.upsertTopics(entitiesBuffer)
                public val dbWriteTime = System.currentTimeMillis() - dbWriteStartTime
                Log.d(TAG, "Forum $forumId: flushed ${entitiesBuffer.size} remaining topics to DB in ${dbWriteTime}ms")
            }

            public val forumDuration = System.currentTimeMillis() - forumStartTime
            public val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            public val memoryUsed = (finalMemory - initialMemory) / (1024 * 1024) // MB
            public val avgTimePerTopic = if (totalTopics > 0) forumDuration / totalTopics else 0

            Log.i(
                TAG,
                "Forum $forumId indexing completed: $totalTopics topics, " +
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
            
            var totalUpdated: Int = 0
            var page: Int = 0
            var hasMorePages: Boolean = true
            val coversToPreload = mutableListOf<String>()

            while (hasMorePages && page < MAX_PAGES_PER_FORUM) {
                try {
                    public val response = api.getForumPage(forumId, start = page * TOPICS_PER_PAGE)
                    if (!response.isSuccessful) {
                        break
                    }

                    public val body = response.body() ?: break
                    // Use parseForumPageWithPagination to get pagination info
                    public val pageResult = parser.parseForumPageWithPagination(body, forumId)
                    public val topics = pageResult.topics

                    // Update hasMorePages based on actual pagination detection
                    hasMorePages = pageResult.hasMorePages

                    if (topics.isEmpty()) {
                        hasMorePages = false
                    } else {
                        // Validate topics before processing - only count valid ones
                        public val validTopics =
                            topics.filter { topic ->
                                public val domain = topic.toDomain()
                                domain.isValid()
                            }
                        public val invalidCount = topics.size - validTopics.size
                        if (invalidCount > 0) {
                            Log.w(
                                TAG,
                                "Forum $forumId page $page incremental: filtered out $invalidCount invalid topics " +
                                    "(out of ${topics.size} parsed)",
                            )
                        }
                        // Filter: only update new topics or topics that need updating
                        public val topicsToUpdate =
                            validTopics.filter { topic ->
                                public val existing = offlineSearchDao.getTopicById(topic.topicId)
                                existing == null ||
                                    // New topic
                                    existing.lastUpdated < (System.currentTimeMillis() - maxAgeMs) ||
                                    // Old topic
                                    existing.indexVersion != currentIndexVersion // Different version
                            }

                        if (topicsToUpdate.isNotEmpty()) {
                            public val entities = topicsToUpdate.map { it.toCachedTopicEntity(currentIndexVersion) }
                            public val dbWriteStartTime = System.currentTimeMillis()
                            offlineSearchDao.upsertTopics(entities)
                            public val dbWriteTime = System.currentTimeMillis() - dbWriteStartTime
                            Log.d(TAG, "Forum $forumId incremental: updated ${entities.size} topics in DB (${dbWriteTime}ms)")
                            totalUpdated += topicsToUpdate.size

                            // Cover URLs are no longer collected (optimization)
                        }

                        onProgress?.invoke(forumId, totalUpdated, validTopics.size)

                        // Rate limiting
                        delay(DELAY_BETWEEN_REQUESTS_MS)
                        page++
                    }
                } catch (e: Exception) {
                    // Check if this is a network/DNS error that might be resolved by switching mirror
                    public val isNetworkError =
                        e is java.net.UnknownHostException ||
                            e is java.net.ConnectException ||
                            e is java.net.SocketTimeoutException ||
                            (e.message?.contains("Unable to resolve host", ignoreCase = true) == true) ||
                            (e.message?.contains("No address associated with hostname", ignoreCase = true) == true)

                    if (isNetworkError) {
                        Log.w(TAG, "Network error updating forum $forumId page $page (${e.javaClass.simpleName}): ${e.message}")

                        // Check if auto-switch is enabled before attempting mirror switch
                        public val autoSwitchEnabled = mirrorManager.isAutoSwitchEnabled()

                        if (!autoSwitchEnabled) {
                            Log.w(TAG, "Auto-switch is disabled, not attempting mirror switch. User must switch mirror manually.")
                            hasMorePages = false
                        } else {
                            // Try switching mirror and retry once
                            try {
                                Log.i(TAG, "Auto-switch enabled, attempting to switch mirror...")
                                public val switched = mirrorManager.switchToNextMirror()
                                if (switched) {
                                    public val newMirror = mirrorManager.currentMirror.value
                                    Log.i(TAG, "Switched to mirror $newMirror, retrying forum $forumId page $page")
                                    delay(500) // Brief delay before retry
                                    // Retry the request - will continue in next iteration if successful
                                    public val retryResponse = api.getForumPage(forumId, start = page * TOPICS_PER_PAGE)
                                    if (retryResponse.isSuccessful) {
                                        public val retryBody = retryResponse.body()
                                        if (retryBody != null) {
                                            public val retryPageResult = parser.parseForumPageWithPagination(retryBody, forumId)
                                            public val retryTopics = retryPageResult.topics

                                            hasMorePages = retryPageResult.hasMorePages

                                            if (retryTopics.isNotEmpty()) {
                                                // Filter topics to update
                                                public val topicsToUpdate =
                                                    retryTopics.filter { topic ->
                                                        public val existing = offlineSearchDao.getTopicById(topic.topicId)
                                                        existing == null ||
                                                            existing.lastUpdated < (System.currentTimeMillis() - maxAgeMs) ||
                                                            existing.indexVersion != currentIndexVersion
                                                    }

                                                if (topicsToUpdate.isNotEmpty()) {
                                                    public val entities = topicsToUpdate.map { it.toCachedTopicEntity(currentIndexVersion) }
                                                    offlineSearchDao.upsertTopics(entities)
                                                    totalUpdated += topicsToUpdate.size

                                                    // Cover URLs are no longer collected (optimization)
                                                }

                                                onProgress?.invoke(forumId, totalUpdated, retryTopics.size)
                                                delay(DELAY_BETWEEN_REQUESTS_MS)
                                                page++
                                                continue // Continue to next iteration
                                            } else {
                                                Log.d(TAG, "Retry succeeded but no topics found, ending")
                                                hasMorePages = false
                                            }
                                        } else {
                                            Log.w(TAG, "Retry response body is null, stopping forum update")
                                            hasMorePages = false
                                        }
                                    } else {
                                        Log.w(TAG, "Retry failed with HTTP ${retryResponse.code()}, stopping forum update")
                                        hasMorePages = false
                                    }
                                } else {
                                    Log.e(TAG, "Failed to switch mirror, stopping forum update")
                                    hasMorePages = false
                                }
                            } catch (retryException: Exception) {
                                Log.e(TAG, "Error during mirror switch retry for forum $forumId page $page", retryException)
                                hasMorePages = false
                            }
                        }
                    } else {
                        Log.e(TAG, "Error updating forum $forumId page $page", e)
                        hasMorePages = false
                    }
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
                public val imageLoader = SingletonImageLoader.get(context)
                public val uniqueUrls = coverUrls.distinct().take(500) // Limit to 500 covers per batch

                Log.d(TAG, "Preloading ${uniqueUrls.size} cover images...")

                // Preload in batches to avoid overwhelming the system
                uniqueUrls.chunked(PRELOAD_COVERS_BATCH_SIZE).forEach { batch ->
                    batch
                        .map { url ->
                            async(Dispatchers.IO) {
                                try {
                                    public val request =
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
                public val metadata = offlineSearchDao.getIndexMetadata()
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

                public val oldestUpdated = metadata.oldestUpdated ?: return@withContext true
                public val age = System.currentTimeMillis() - oldestUpdated
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
