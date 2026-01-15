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
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
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
        private val loggerFactory: LoggerFactory,
        @param:ApplicationContext private val context: Context,
    ) {
        private val logger = loggerFactory.get("ForumIndexer")

        // Background scope for non-blocking operations (cover preloading)
        private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        public companion object {
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
        public suspend fun indexForums(
            forumIds: String,
            preloadCovers: Boolean = true,
            onProgress: ((IndexingProgress) -> Unit)? = null,
        ): Int =
            withContext(Dispatchers.IO) {
                val startTime = System.currentTimeMillis()
                val currentIndexVersion = getCurrentIndexVersion() + 1
                val forumIdList = forumIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                var totalIndexed: Int = 0
                val coversToPreload = mutableListOf<String>()

                // Log current mirror at start of indexing
                val initialMirror = mirrorManager.getCurrentMirrorDomain()
                logger.i { "=== FORUM INDEXING START ===" }
                logger.i { "Using mirror: $initialMirror" }
                logger.i { "Indexing version: $currentIndexVersion" }
                logger.i { "Forums to index: ${forumIdList.size}" }

                // Validate that only audiobook forums are being indexed
                val allowedForums =
                    RutrackerApi.AUDIOBOOKS_FORUM_IDS
                        .split(",")
                        .map { it.trim() }
                        .toSet()
                val invalidForums = forumIdList.filter { it !in allowedForums }
                if (invalidForums.isNotEmpty()) {
                    logger.w { "WARNING: Attempting to index non-audiobook forums: $invalidForums" }
                    logger.w { "Only audiobook forums will be indexed. Allowed forums: ${allowedForums.size}" }
                }

                logger.i { "Starting full forum indexing for ${forumIdList.size} forums (version $currentIndexVersion }" }
                logger.i { "Forums to index: ${forumIdList.joinToString(", " }}" }

                // Clear old indexed data before starting new index to ensure only audiobook forums are indexed
                logger.i { "Clearing old indexed data before new index..." }
                val oldCount = getIndexSize()
                clearIndex()
                logger.i { "Old indexed data cleared (was $oldCount topics }" }

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
                                        var forumTopicsCount: Int = 0
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
                                            }
                                        // Update atomic counter after forum completion
                                        topicsIndexedAtomic.addAndGet(indexed)
                                        Pair(indexed, covers)
                                    } catch (e: Exception) {
                                        logger.e(e) { "Failed to index forum $forumId" }
                                        Pair(0, emptyList<String>())
                                    }
                                }
                            }.awaitAll()

                }

                // Cover preloading is disabled (optimization: covers not stored in index)
                // Covers will be loaded on-demand when topic is opened via getTopicDetails()

                val duration = System.currentTimeMillis() - startTime
                val runtime = Runtime.getRuntime()
                val finalMemory = runtime.totalMemory() - runtime.freeMemory()
                val memoryUsed = finalMemory / (1024 * 1024) // MB
                val topicsPerSecond = if (duration > 0) (totalIndexed * 1000) / duration else 0

                // Verify mirror didn't change during indexing
                val finalMirror = mirrorManager.getCurrentMirrorDomain()
                if (finalMirror != initialMirror) {
                    logger.w {
                        "⚠️ Mirror changed during indexing! Initial: $initialMirror, Final: $finalMirror. " +
                            "This may indicate auto-switch occurred or connection issues."
                    }
                } else {
                    logger.d { "Mirror remained stable: $finalMirror" }
                }

                // Verify actual count in database matches indexed count (single source of truth)
                val actualCountInDb = getIndexSize()
                if (actualCountInDb != totalIndexed) {
                    logger.w {
                        "Count mismatch: indexed $totalIndexed topics, but database has $actualCountInDb topics. " +
                            "Using database count as single source of truth."
                    }
                }

                logger.i {
                    "Forum indexing completed. Indexed: $totalIndexed topics, database: $actualCountInDb topics, " +
                        "duration: ${duration}ms (${duration / 1000}s), " +
                        "speed: $topicsPerSecond topics/s, memory: +${memoryUsed}MB, " +
                        "mirror: $finalMirror"
                }

                // Log sample topics to verify index content (AUTOMATIC VERIFICATION)
                try {
                    val sampleTopics = offlineSearchDao.getSampleTopics(5)
                    logger.i { "🔍 === AUTOMATIC INDEX VERIFICATION: DATABASE CONTENT CHECK ===" }
                    logger.i { "Found ${sampleTopics.size} sample topics in database:" }
                    sampleTopics.forEachIndexed { index, topic ->
                        logger.i {
                            "  [$index] ID: ${topic.topicId} | Title: ${topic.title.take(50)}... | " +
                                "Author: ${topic.author} | Cat: ${topic.category} | Size: ${topic.size}"
                        }
                    }
                    logger.i { "✅ === DATABASE CONTENT VERIFIED: ${sampleTopics.size} ITEMS RETRIEVED ===" }
                } catch (e: Exception) {
                    logger.e({ "❌ Failed to perform automatic index verification" }, e)
                }

                // Use database count as single source of truth for completion
                val finalCount = actualCountInDb
                onProgress?.invoke(
                    IndexingProgress.Completed(
                        totalTopics = finalCount, // Use database count, not indexed count
                        durationMs = duration,
                    ),
                )

                finalCount // Return database count as single source of truth
            }

        /**
         * Get current index size (number of topics).
         */
        public suspend fun getIndexSize(): Int = offlineSearchDao.getTopicCount()

        /**
         * Get index metadata/statistics.
         */
        public suspend fun getIndexMetadata(): IndexMetadata? = offlineSearchDao.getIndexMetadata()

        /**
         * Clear the entire index.
         */
        public suspend fun clearIndex() {
            offlineSearchDao.deleteAllTopics()
            offlineSearchDao.deleteAllMappings()
        }

        /**
         * Check if index needs update.
         */
        public suspend fun needsUpdate(): Boolean {
            val metadata = getIndexMetadata() ?: return true
            if (metadata.count == 0) return true

            val oldestUpdated = metadata.oldestUpdated ?: return true
            val age = System.currentTimeMillis() - oldestUpdated
            return age > MAX_AGE_FOR_UPDATE_MS
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
        public suspend fun incrementalUpdate(
            forumIds: String,
            maxAgeMs: Long = MAX_AGE_FOR_UPDATE_MS,
            preloadCovers: Boolean = true,
            onProgress: ((forumId: String, updated: Int, total: Int) -> Unit)? = null,
        ): Int =
            withContext(Dispatchers.IO) {
                val currentIndexVersion = getCurrentIndexVersion()
                val forumIdList = forumIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                var totalUpdated: Int = 0
                val coversToPreload = mutableListOf<String>()

                logger.i { "Starting incremental update (max age: ${maxAgeMs / (1000 * 60 * 60)} hours)" }

                for (forumId in forumIdList) {
                    try {
                        val (updated, covers) = updateForumIncremental(forumId, maxAgeMs, currentIndexVersion, onProgress)
                        totalUpdated += updated
                        coversToPreload.addAll(covers)
                        logger.i { "Updated forum $forumId: $updated topics" }
                    } catch (e: Exception) {
                        logger.e({ "Failed to update forum $forumId" }, e)
                    }
                }

                // Cover preloading is disabled (optimization: covers not stored in index)

                logger.i { "Incremental update completed. Updated: $totalUpdated topics" }
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
            val entitiesBuffer = mutableListOf<CachedTopicEntity>() // Buffer for batched writes

            val forumStartTime = System.currentTimeMillis()
            val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            logger.i { "Starting indexing forum $forumId (version $indexVersion)" }

            while (hasMorePages && page < MAX_PAGES_PER_FORUM) {
                try {
                    val pageStartTime = System.currentTimeMillis()
                    val response = api.getForumPage(forumId, start = page * TOPICS_PER_PAGE)
                    val fetchTime = System.currentTimeMillis() - pageStartTime

                    if (!response.isSuccessful) {
                        logger.w { "Failed to fetch forum $forumId page $page: HTTP ${response.code()} (took ${fetchTime}ms)" }
                        break
                    }

                    val body = response.body() ?: break
                    val bodySize = body.contentLength()
                    val parseStartTime = System.currentTimeMillis()
                    // Use parseForumPageWithPagination to get pagination info
                    val pageResult = parser.parseForumPageWithPagination(body, forumId)
                    val topics = pageResult.topics
                    val parseTime = System.currentTimeMillis() - parseStartTime

                    // Update hasMorePages based on actual pagination detection
                    hasMorePages = pageResult.hasMorePages

                    if (topics.isEmpty()) {
                        logger.d { "Forum $forumId page $page: no topics found, ending (fetch: ${fetchTime}ms, parse: ${parseTime}ms)" }
                        hasMorePages = false
                    } else {
                        val dbStartTime = System.currentTimeMillis()
                        // Validate topics before adding to index - only count valid ones
                        val validTopics =
                            topics.filter { topic ->
                                val domain = topic.toDomain()
                                domain.isValid()
                            }
                        val invalidCount = topics.size - validTopics.size
                        if (invalidCount > 0) {
                            logger.w {
                                "Forum $forumId page $page: filtered out $invalidCount invalid topics " +
                                    "(out of ${topics.size} parsed)"
                            }
                        }
                        logger.d {
                            "Forum $forumId page $page: parsed ${topics.size} topics, " +
                                "${validTopics.size} valid (total: $totalTopics, " +
                                "body: ${bodySize / 1024}KB, fetch: ${fetchTime}ms, parse: ${parseTime}ms)"
                        }
                        // Add only valid topics to buffer with current index version
                        // Note: magnetUrl, torrentUrl, coverUrl are NOT saved (optimization)
                        val newEntities = validTopics.map { it.toCachedTopicEntity(indexVersion) }
                        entitiesBuffer.addAll(newEntities)
                        totalTopics += validTopics.size // Count only valid topics

                        // Cover URLs are no longer collected for preloading since they're not stored in index
                        // Covers will be loaded on-demand when topic is opened via getTopicDetails()

                        // Flush to DB when buffer reaches batch size or at end
                        if (entitiesBuffer.size >= BATCH_SIZE_FOR_DB || !hasMorePages) {
                            val dbWriteStartTime = System.currentTimeMillis()
                            offlineSearchDao.upsertTopics(entitiesBuffer)
                            val dbWriteTime = System.currentTimeMillis() - dbWriteStartTime
                            val writtenCount = entitiesBuffer.size
                            logger.d { "Forum $forumId: wrote $writtenCount topics to DB in ${dbWriteTime}ms" }
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
                    val isNetworkError =
                        e is java.net.UnknownHostException ||
                            e is java.net.ConnectException ||
                            e is java.net.SocketTimeoutException ||
                            (e.message?.contains("Unable to resolve host", ignoreCase = true) == true) ||
                            (e.message?.contains("No address associated with hostname", ignoreCase = true) == true)

                    if (isNetworkError) {
                        logger.w { "Network error indexing forum $forumId page $page (${e.javaClass.simpleName}): ${e.message}" }

                        // Check if auto-switch is enabled before attempting mirror switch
                        val autoSwitchEnabled = mirrorManager.isAutoSwitchEnabled()

                        if (!autoSwitchEnabled) {
                            logger.w { "Auto-switch is disabled, not attempting mirror switch. User must switch mirror manually." }
                            hasMorePages = false
                        } else {
                            // Try switching mirror and retry once
                            var retrySucceeded: Boolean = false
                            try {
                                logger.i { "Auto-switch enabled, attempting to switch mirror..." }
                                val switched = mirrorManager.switchToNextMirror()
                                if (switched) {
                                    val newMirror = mirrorManager.currentMirror.value
                                    logger.i { "Switched to mirror $newMirror, retrying forum $forumId page $page" }
                                    delay(500L) // Brief delay before retry
                                    // Retry the request
                                    val retryResponse = api.getForumPage(forumId, start = page * TOPICS_PER_PAGE)
                                    if (retryResponse.isSuccessful) {
                                        val retryBody = retryResponse.body()
                                        if (retryBody != null) {
                                            val retryParseStartTime = System.currentTimeMillis()
                                            val retryPageResult = parser.parseForumPageWithPagination(retryBody, forumId)
                                            val retryTopics = retryPageResult.topics
                                            val retryParseTime = System.currentTimeMillis() - retryParseStartTime

                                            hasMorePages = retryPageResult.hasMorePages

                                            if (retryTopics.isNotEmpty()) {
                                                val newEntities = retryTopics.map { it.toCachedTopicEntity(indexVersion) }
                                                entitiesBuffer.addAll(newEntities)
                                                totalTopics += retryTopics.size

                                                // Cover URLs are no longer collected (optimization)

                                                logger.i {
                                                    "Retry succeeded after mirror switch: parsed ${retryTopics.size} topics (parse: ${retryParseTime}ms)"
                                                }
                                                retrySucceeded = true

                                                // Flush if needed
                                                if (entitiesBuffer.size >= BATCH_SIZE_FOR_DB || !hasMorePages) {
                                                    val dbWriteStartTime = System.currentTimeMillis()
                                                    offlineSearchDao.upsertTopics(entitiesBuffer)
                                                    val dbWriteTime = System.currentTimeMillis() - dbWriteStartTime
                                                    logger.d {
                                                        "Forum $forumId: wrote ${entitiesBuffer.size} topics to DB in ${dbWriteTime}ms"
                                                    }
                                                    entitiesBuffer.clear()
                                                }

                                                onProgress?.invoke(page, totalTopics)
                                                delay(DELAY_BETWEEN_REQUESTS_MS)
                                                page++
                                            } else {
                                                logger.d { "Retry succeeded but no topics found, ending" }
                                                hasMorePages = false
                                            }
                                        } else {
                                            logger.w { "Retry response body is null, stopping forum indexing" }
                                            hasMorePages = false
                                        }
                                    } else {
                                        logger.w { "Retry failed with HTTP ${retryResponse.code()}, stopping forum indexing" }
                                        hasMorePages = false
                                    }
                                } else {
                                    logger.e { "Failed to switch mirror, stopping forum indexing" }
                                    hasMorePages = false
                                }
                            } catch (retryException: Exception) {
                                logger.e(retryException) { "Error during mirror switch retry for forum $forumId page $page" }
                                hasMorePages = false
                            }

                            if (!retrySucceeded) {
                                hasMorePages = false
                            }
                        }
                    } else {
                        logger.e(e) { "Error indexing forum $forumId page $page" }
                        hasMorePages = false
                    }
                }
            }

            // Flush remaining entities
            if (entitiesBuffer.isNotEmpty()) {
                val dbWriteStartTime = System.currentTimeMillis()
                offlineSearchDao.upsertTopics(entitiesBuffer)
                val dbWriteTime = System.currentTimeMillis() - dbWriteStartTime
                logger.d { "Forum $forumId: flushed ${entitiesBuffer.size} remaining topics to DB in ${dbWriteTime}ms" }
            }

            val forumDuration = System.currentTimeMillis() - forumStartTime
            val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            val memoryUsed = (finalMemory - initialMemory) / (1024 * 1024) // MB
            val avgTimePerTopic = if (totalTopics > 0) forumDuration / totalTopics else 0

            logger.i {
                "Forum $forumId indexing completed: $totalTopics topics, " +
                    "duration: ${forumDuration}ms (${forumDuration / 1000}s), " +
                    "avg: ${avgTimePerTopic}ms/topic, memory: +${memoryUsed}MB"
            }
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
                    val response = api.getForumPage(forumId, start = page * TOPICS_PER_PAGE)
                    if (!response.isSuccessful) {
                        break
                    }

                    val body = response.body() ?: break
                    // Use parseForumPageWithPagination to get pagination info
                    val pageResult = parser.parseForumPageWithPagination(body, forumId)
                    val topics = pageResult.topics

                    // Update hasMorePages based on actual pagination detection
                    hasMorePages = pageResult.hasMorePages

                    if (topics.isEmpty()) {
                        hasMorePages = false
                    } else {
                        // Validate topics before processing - only count valid ones
                        val validTopics =
                            topics.filter { topic ->
                                val domain = topic.toDomain()
                                domain.isValid()
                            }
                        val invalidCount = topics.size - validTopics.size
                        if (invalidCount > 0) {
                            logger.w {
                                "Forum $forumId page $page incremental: filtered out $invalidCount invalid topics " +
                                    "(out of ${topics.size} parsed)"
                            }
                        }
                        // Filter: only update new topics or topics that need updating
                        val topicsToUpdate =
                            validTopics.filter { topic ->
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
                            logger.d { "Forum $forumId incremental: updated ${entities.size} topics in DB (${dbWriteTime}ms)" }
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
                    val isNetworkError =
                        e is java.net.UnknownHostException ||
                            e is java.net.ConnectException ||
                            e is java.net.SocketTimeoutException ||
                            (e.message?.contains("Unable to resolve host", ignoreCase = true) == true) ||
                            (e.message?.contains("No address associated with hostname", ignoreCase = true) == true)

                    if (isNetworkError) {
                        logger.w { "Network error updating forum $forumId page $page (${e.javaClass.simpleName}): ${e.message}" }

                        // Check if auto-switch is enabled before attempting mirror switch
                        val autoSwitchEnabled = mirrorManager.isAutoSwitchEnabled()

                        if (!autoSwitchEnabled) {
                            logger.w { "Auto-switch is disabled, not attempting mirror switch. User must switch mirror manually." }
                            hasMorePages = false
                        } else {
                            // Try switching mirror and retry once
                            try {
                                logger.i { "Auto-switch enabled, attempting to switch mirror..." }
                                val switched = mirrorManager.switchToNextMirror()
                                if (switched) {
                                    val newMirror = mirrorManager.currentMirror.value
                                    logger.i { "Switched to mirror $newMirror, retrying forum $forumId page $page" }
                                    delay(500L) // Brief delay before retry
                                    // Retry the request - will continue in next iteration if successful
                                    val retryResponse = api.getForumPage(forumId, start = page * TOPICS_PER_PAGE)
                                    if (retryResponse.isSuccessful) {
                                        val retryBody = retryResponse.body()
                                        if (retryBody != null) {
                                            val retryPageResult = parser.parseForumPageWithPagination(retryBody, forumId)
                                            val retryTopics = retryPageResult.topics

                                            hasMorePages = retryPageResult.hasMorePages

                                            if (retryTopics.isNotEmpty()) {
                                                // Filter topics to update
                                                val topicsToUpdate =
                                                    retryTopics.filter { topic ->
                                                        val existing = offlineSearchDao.getTopicById(topic.topicId)
                                                        existing == null ||
                                                            existing.lastUpdated < (System.currentTimeMillis() - maxAgeMs) ||
                                                            existing.indexVersion != currentIndexVersion
                                                    }

                                                if (topicsToUpdate.isNotEmpty()) {
                                                    val entities = topicsToUpdate.map { it.toCachedTopicEntity(currentIndexVersion) }
                                                    offlineSearchDao.upsertTopics(entities)
                                                    totalUpdated += topicsToUpdate.size

                                                    // Cover URLs are no longer collected (optimization)
                                                }

                                                onProgress?.invoke(forumId, totalUpdated, retryTopics.size)
                                                delay(DELAY_BETWEEN_REQUESTS_MS)
                                                page++
                                                continue // Continue to next iteration
                                            } else {
                                                logger.d { "Retry succeeded but no topics found, ending" }
                                                hasMorePages = false
                                            }
                                        } else {
                                            logger.w { "Retry response body is null, stopping forum update" }
                                            hasMorePages = false
                                        }
                                    } else {
                                        logger.w { "Retry failed with HTTP ${retryResponse.code()}, stopping forum update" }
                                        hasMorePages = false
                                    }
                                } else {
                                    logger.e { "Failed to switch mirror, stopping forum update" }
                                    hasMorePages = false
                                }
                            } catch (retryException: Exception) {
                                logger.e(retryException) { "Error during mirror switch retry for forum $forumId page $page" }
                                hasMorePages = false
                            }
                        }
                    } else {
                        logger.e(e) { "Error updating forum $forumId page $page" }
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
                val imageLoader = SingletonImageLoader.get(context)
                val uniqueUrls = coverUrls.distinct().take(500) // Limit to 500 covers per batch

                logger.d { "Preloading ${uniqueUrls.size} cover images..." }

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

                logger.d { "Cover preloading completed" }
            }

        /**
         * Get index statistics.
         *
         * @return Total number of indexed topics
         */
        public suspend fun getIndexSize(): Int =
            withContext(Dispatchers.IO) {
                offlineSearchDao.getTopicCount()
            }

        /**
         * Get detailed index metadata.
         *
         * @return IndexMetadata with statistics
         */
        public suspend fun getIndexMetadata(): IndexMetadata? =
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
        public suspend fun needsUpdate(maxAgeMs: Long = MAX_AGE_FOR_UPDATE_MS): Boolean =
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
        public suspend fun clearIndex(): Unit =
            withContext(Dispatchers.IO) {
                offlineSearchDao.deleteAllTopics()
                offlineSearchDao.deleteAllMappings()
                logger.i { "Index cleared" }
            }
    }
