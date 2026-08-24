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
import com.jabook.app.jabook.compose.data.local.dao.IndexMetadata
import com.jabook.app.jabook.compose.data.local.dao.OfflineSearchDao
import com.jabook.app.jabook.compose.data.local.entity.CachedTopicEntity
import com.jabook.app.jabook.compose.data.local.entity.toCachedTopicEntity
import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.data.remote.mapper.toDomain
import com.jabook.app.jabook.compose.data.remote.parser.RutrackerParser
import com.jabook.app.jabook.utils.loggingCoroutineExceptionHandler
import com.jabook.app.jabook.utils.parseRetryAfterMs
import com.jabook.app.jabook.utils.retryWithBackoff
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
        private val backgroundScope =
            CoroutineScope(
                SupervisorJob() + Dispatchers.IO + loggingCoroutineExceptionHandler("ForumIndexer"),
            )

        // Prevent concurrent indexForums() calls from ViewModel + WorkManager
        private val indexingMutex = Mutex()

        // How long a second indexing caller waits for the mutex before bailing
        // with IndexingInProgressException (prevents stuck foreground workers).
        private val mutexAcquireTimeoutMs = 15_000L

        // Real-time progress state — collected by ViewModel
        private val _indexProgress = MutableStateFlow(IndexProgress())
        public val indexProgress: StateFlow<IndexProgress> = _indexProgress.asStateFlow()

        // Per-forum status — collected by ViewModel/UI
        private val _forumStatuses = MutableStateFlow<List<ForumStatus>>(emptyList())
        public val forumStatuses: StateFlow<List<ForumStatus>> = _forumStatuses.asStateFlow()

        // Forum ID → display name mapping (populated at start of indexForums)
        private val forumNames = mutableMapOf<String, String>()

        /**
         * Resolve a human-readable forum name from ID.
         * Falls back to "Forum {id}" if not mapped.
         */
        private fun resolveForumName(forumId: String): String = forumNames[forumId] ?: "Forum $forumId"

        /**
         * Build a descriptive error message for indexing failures.
         */
        private fun buildErrorMessage(
            forumId: String,
            page: Int,
            cause: Exception,
            attempt: Int = 1,
            maxAttempts: Int = 3,
        ): String {
            val forumName = resolveForumName(forumId)
            return when {
                cause is java.net.UnknownHostException ||
                    cause is java.net.ConnectException ||
                    cause is java.net.SocketTimeoutException -> {
                    "Network error on page $page of forum $forumName — " +
                        "retrying (attempt $attempt/$maxAttempts)"
                }
                cause.message?.contains("captcha", ignoreCase = true) == true ||
                    cause.message?.contains("login", ignoreCase = true) == true -> {
                    "Authentication required — please log in to RuTracker"
                }
                cause.message?.contains("429", ignoreCase = true) == true ||
                    cause.message?.contains("503", ignoreCase = true) == true -> {
                    "Rate limited by RuTracker — waiting before retry"
                }
                cause.message?.contains("parse", ignoreCase = true) == true ||
                    cause.message?.contains("HTML", ignoreCase = true) == true -> {
                    "Failed to parse forum $forumName page $page — page structure may have changed"
                }
                else -> {
                    "Error indexing forum $forumName page $page — ${cause.message ?: "unknown error"}"
                }
            }
        }

        private data class ForumBatchResult(
            val forumId: String,
            val indexed: Int,
            val covers: List<String>,
            val failed: Boolean,
            val failureMessage: String? = null,
        )

        public companion object {
            private const val TOPICS_PER_PAGE = 50
            private const val BASE_DELAY_MS = 300L
            private const val JITTER_RANGE_MS = 150L // ±150ms random jitter
            private const val MAX_PAGES_PER_FORUM = 100_000

            private const val INCREMENTAL_UPDATE_INTERVAL_HOURS = 24L
            private const val MAX_AGE_FOR_UPDATE_MS = INCREMENTAL_UPDATE_INTERVAL_HOURS * 60 * 60 * 1000

            private const val PRELOAD_COVERS_BATCH_SIZE = 10
            private const val PRELOAD_COVERS_DELAY_MS = 100L

            private const val MAX_CONCURRENT_FORUMS = 3
            private const val BATCH_SIZE_FOR_DB = 100

            private const val INITIAL_BACKOFF_MS = 1000L
            private const val MAX_BACKOFF_MS = 30_000L
            private const val BACKOFF_MULTIPLIER = 2.0

            /**
             * Polite delay with jitter (±150ms around base).
             * Avoids fixed-interval requests that look like bot behavior.
             */
            private suspend fun politeDelay(baseMs: Long = BASE_DELAY_MS) {
                val jitter = (Math.random() * 2 * JITTER_RANGE_MS - JITTER_RANGE_MS).toLong()
                delay((baseMs + jitter).coerceAtLeast(50L))
            }

            /**
             * Adaptive backoff for rate-limit responses (429/503).
             * Respects Retry-After header if present.
             */
            private suspend fun adaptiveBackoff(
                attempt: Int,
                retryAfterMs: Long? = null,
            ) {
                val backoff =
                    retryAfterMs
                        ?: (INITIAL_BACKOFF_MS * Math.pow(BACKOFF_MULTIPLIER, attempt.toDouble()))
                            .toLong()
                            .coerceAtMost(MAX_BACKOFF_MS)
                delay(backoff)
            }

            private const val MIN_VALID_TOPICS_ABSOLUTE = 10
            private const val MIN_VALID_RATIO = 0.5
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
            onProgress: (suspend (IndexingProgress) -> Unit)? = null,
        ): Int {
            // Bounded wait: a second caller (periodic worker, one-time worker, or a
            // direct UI run) must never block forever on the singleton mutex while
            // another long index is running — that would leave its foreground
            // notification stuck indefinitely.
            if (withTimeoutOrNull(mutexAcquireTimeoutMs) { indexingMutex.lock() } == null) {
                throw IndexingInProgressException()
            }
            return try {
                withContext(Dispatchers.IO) {
                    val startTime = System.currentTimeMillis()
                    val currentIndexVersion = getCurrentIndexVersion() + 1
                    val forumIdList = forumIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                    // Initialize forum name mapping
                    forumNames.clear()
                    for (id in forumIdList) {
                        forumNames[id] = "Forum $id"
                    }

                    var totalIndexed: Int = 0
                    val coversToPreload = mutableListOf<String>()

                    // Initialize per-forum statuses
                    val initialStatuses =
                        forumIdList.map { id ->
                            ForumStatus(
                                forumId = id,
                                forumName = resolveForumName(id),
                                state = ForumState.PENDING,
                            )
                        }
                    _forumStatuses.value = initialStatuses

                    // Log current mirror at start of indexing
                    val initialMirror = mirrorManager.getCurrentMirrorDomain()
                    logger.i { "=== FORUM INDEXING START ===" }
                    logger.i { "Using mirror: $initialMirror" }
                    logger.i { "Indexing version: $currentIndexVersion" }
                    val oldCount = getIndexSize()
                    logger.i { "Existing indexed data: $oldCount topics" }

                    onProgress?.invoke(
                        IndexingProgress.InProgress(
                            IndexProgress(
                                currentForumName = forumIdList.firstOrNull() ?: "",
                                totalForums = forumIdList.size,
                            ),
                        ),
                    )

                    // Use AtomicInteger for thread-safe progress tracking
                    val topicsIndexedAtomic =
                        java.util.concurrent.atomic
                            .AtomicInteger(0)
                    val failedForums =
                        java.util.concurrent.atomic
                            .AtomicInteger(0)
                    val failedForumMessages = mutableListOf<String>()

                    // Process forums in parallel batches
                    forumIdList.chunked(MAX_CONCURRENT_FORUMS).forEach { batch ->
                        batch
                            .map { forumId ->
                                async(Dispatchers.IO) {
                                    // Mark forum as IN_PROGRESS
                                    updateForumStatus(forumId, ForumState.IN_PROGRESS)

                                    try {
                                        val (indexed, covers) =
                                            indexForum(forumId, currentIndexVersion) { page, topicsInForum ->
                                                // Update per-forum page progress
                                                updateForumStatusPage(forumId, page)

                                                if (page == 0 || page % 2 == 0 || topicsInForum < 50) {
                                                    val currentTotal = topicsIndexedAtomic.get()
                                                    // Update the aggregate progress StateFlow
                                                    val completedCount = countCompletedForums()
                                                    _indexProgress.value =
                                                        IndexProgress(
                                                            currentForumName = resolveForumName(forumId),
                                                            currentForumPage = page,
                                                            totalForumsCompleted = completedCount,
                                                            totalForums = forumIdList.size,
                                                            topicsFound = currentTotal,
                                                            errors = failedForumMessages.toList(),
                                                            forumStatuses = _forumStatuses.value,
                                                        )
                                                    onProgress?.invoke(
                                                        IndexingProgress.InProgress(_indexProgress.value),
                                                    )
                                                }
                                            }
                                        ForumBatchResult(
                                            forumId = forumId,
                                            indexed = indexed,
                                            covers = covers,
                                            failed = false,
                                        )
                                    } catch (e: Exception) {
                                        if (e is kotlinx.coroutines.CancellationException) throw e
                                        val errorMsg = buildErrorMessage(forumId, 0, e)
                                        logger.e({ "Failed to index forum $forumId" }, e)
                                        ForumBatchResult(
                                            forumId = forumId,
                                            indexed = 0,
                                            covers = emptyList(),
                                            failed = true,
                                            failureMessage = errorMsg,
                                        )
                                    }
                                }
                            }.awaitAll()
                            .forEach { result ->
                                if (result.failed) {
                                    failedForums.incrementAndGet()
                                    synchronized(failedForumMessages) {
                                        failedForumMessages.add(result.failureMessage ?: "${result.forumId}: unknown error")
                                    }
                                    updateForumStatus(
                                        result.forumId,
                                        ForumState.FAILED,
                                        errorMessage = result.failureMessage,
                                    )
                                } else {
                                    topicsIndexedAtomic.addAndGet(result.indexed)
                                    updateForumStatus(
                                        result.forumId,
                                        ForumState.INDEXED,
                                        topicsCount = result.indexed,
                                        lastUpdated = System.currentTimeMillis(),
                                    )
                                    if (result.covers.isNotEmpty()) {
                                        synchronized(coversToPreload) {
                                            coversToPreload.addAll(result.covers)
                                        }
                                    }
                                }
                            }
                    }

                    totalIndexed = topicsIndexedAtomic.get()
                    val duration = System.currentTimeMillis() - startTime

                    if (failedForums.get() == forumIdList.size) {
                        val message =
                            "Indexing failed for all forums (${failedForums.get()}/${forumIdList.size}). " +
                                failedForumMessages.take(3).joinToString("; ")
                        logger.e { message }
                        _indexProgress.value =
                            _indexProgress.value.copy(errors = failedForumMessages.toList())
                        onProgress?.invoke(IndexingProgress.Error(message))
                        throw IllegalStateException(message)
                    }

                    // If index run produced no data, keep existing index and surface explicit failure.
                    if (totalIndexed == 0) {
                        val failedDetail =
                            if (failedForumMessages.isNotEmpty()) {
                                " Failures: ${failedForumMessages.take(3).joinToString("; ")}"
                            } else {
                                ""
                            }
                        val message =
                            "Indexing returned zero topics. Old index preserved ($oldCount topics)." +
                                "$failedDetail Likely auth/session or parser issue."
                        logger.e { message }
                        onProgress?.invoke(IndexingProgress.Error(message))
                        throw IllegalStateException(message)
                    }

                    if (oldCount > MIN_VALID_TOPICS_ABSOLUTE && totalIndexed < oldCount * MIN_VALID_RATIO) {
                        val message =
                            "Indexing produced too few topics ($totalIndexed) vs existing ($oldCount). " +
                                "Old index preserved (threshold: ${(oldCount * MIN_VALID_RATIO).toInt()})."
                        logger.w { message }
                        onProgress?.invoke(IndexingProgress.Error(message))
                        throw IllegalStateException(message)
                    }

                    if (preloadCovers && coversToPreload.isNotEmpty()) {
                        preloadCovers(coversToPreload)
                    }

                    // Verify actual count
                    val actualCountInDb = getIndexSize()

                    if (failedForums.get() > 0) {
                        logger.w {
                            "Indexing completed with partial forum failures: ${failedForums.get()}/" +
                                "${forumIdList.size}. Sample: ${failedForumMessages.take(3)}"
                        }
                    }

                    logger.i { "Forum indexing completed. Indexed: $totalIndexed topics, duration: ${duration}ms" }

                    onProgress?.invoke(
                        IndexingProgress.Completed(
                            totalTopics = actualCountInDb,
                            durationMs = duration,
                        ),
                    )

                    actualCountInDb
                }
            } finally {
                indexingMutex.unlock()
            }
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
                        val (updated, covers) =
                            updateForumIncremental(
                                forumId,
                                maxAgeMs,
                                currentIndexVersion,
                                onProgress,
                            )
                        totalUpdated += updated
                        coversToPreload.addAll(covers)
                        logger.i { "Updated forum $forumId: $updated topics" }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        logger.e({ "Failed to update forum $forumId" }, e)
                    }
                }

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
            onProgress: (suspend (page: Int, topicsInForum: Int) -> Unit)? = null,
        ): Pair<Int, List<String>> {
            var totalTopics: Int = 0
            var page: Int = 0
            var hasMorePages: Boolean = true
            val coversToPreload = mutableListOf<String>()
            val entitiesBuffer = mutableListOf<CachedTopicEntity>() // Buffer for batched writes
            var lastPageSignature: String? = null
            var repeatedSignatureCount: Int = 0

            val forumStartTime = System.currentTimeMillis()
            val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            logger.i { "Starting indexing forum $forumId (version $indexVersion)" }

            while (hasMorePages && page < MAX_PAGES_PER_FORUM) {
                try {
                    val pageStartTime = System.currentTimeMillis()
                    val response = retryWithBackoff { api.getForumPage(forumId, start = page * TOPICS_PER_PAGE) }
                    val fetchTime = System.currentTimeMillis() - pageStartTime

                    if (!response.isSuccessful) {
                        logger.w {
                            "Failed to fetch forum $forumId page $page: HTTP ${response.code()} (took ${fetchTime}ms)"
                        }
                        // Adaptive backoff for rate-limit responses
                        if (response.code() == 429 || response.code() == 503) {
                            val retryAfter = parseRetryAfterMs(response.headers())
                            logger.i { "Rate-limited (${response.code()}), backing off..." }
                            adaptiveBackoff(attempt = page, retryAfterMs = retryAfter)
                            continue // Retry same page
                        }
                        break
                    }

                    val body = response.body() ?: break
                    val bodySize = body.contentLength()
                    val parseStartTime = System.currentTimeMillis()
                    val pageResult = parser.parseForumPageWithPagination(body, forumId)
                    val topics = pageResult.topics
                    val parseTime = System.currentTimeMillis() - parseStartTime

                    if (page == 0 && topics.isEmpty()) {
                        val bodyStr = body.string()
                        if (!isHealthyForumPage(bodyStr, 0)) {
                            logger.w { "Forum $forumId page 0: unhealthy response (CAPTCHA/login-wall/block page), aborting forum" }
                            break
                        }
                    }

                    hasMorePages = pageResult.hasMorePages

                    if (topics.isEmpty()) {
                        logger.d {
                            "Forum $forumId page $page: no topics found, ending (fetch: ${fetchTime}ms, parse: ${parseTime}ms)"
                        }
                        hasMorePages = false
                    } else {
                        val pageSignature =
                            buildString {
                                append(topics.firstOrNull()?.topicId ?: "none")
                                append('|')
                                append(topics.lastOrNull()?.topicId ?: "none")
                                append('|')
                                append(topics.size)
                            }
                        if (pageSignature == lastPageSignature) {
                            repeatedSignatureCount++
                            if (repeatedSignatureCount >= 3) {
                                logger.w {
                                    "Forum $forumId page $page repeated same signature 3 times; " +
                                        "stopping to prevent infinite pagination loop"
                                }
                                hasMorePages = false
                            }
                        } else {
                            repeatedSignatureCount = 0
                            lastPageSignature = pageSignature
                        }

                        val validTopics = topics.filter { it.toDomain().isValid() }
                        val invalidCount = topics.size - validTopics.size
                        if (invalidCount > 0) {
                            logger.w { "Forum $forumId page $page: filtered out $invalidCount invalid topics" }
                        }

                        val newEntities = validTopics.map { it.toCachedTopicEntity(indexVersion) }
                        entitiesBuffer.addAll(newEntities)
                        totalTopics += validTopics.size
                        coversToPreload.addAll(
                            newEntities
                                .mapNotNull { it.coverUrl?.takeIf(String::isNotBlank) },
                        )

                        if (entitiesBuffer.size >= BATCH_SIZE_FOR_DB || !hasMorePages) {
                            val dbWriteStartTime = System.currentTimeMillis()
                            offlineSearchDao.upsertTopics(entitiesBuffer)
                            val dbWriteTime = System.currentTimeMillis() - dbWriteStartTime
                            logger.d { "Forum $forumId: wrote ${entitiesBuffer.size} topics to DB in ${dbWriteTime}ms" }
                            entitiesBuffer.clear()
                        }

                        onProgress?.invoke(page, totalTopics)
                        politeDelay()
                        page++
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    val isNetworkError =
                        e is java.net.UnknownHostException ||
                            e is java.net.ConnectException ||
                            e is java.net.SocketTimeoutException
                    if (isNetworkError) {
                        val errorMsg = buildErrorMessage(forumId, page, e)
                        logger.w { errorMsg }
                        hasMorePages = false
                    } else {
                        val errorMsg = buildErrorMessage(forumId, page, e)
                        logger.e({ errorMsg }, e)
                        hasMorePages = false
                    }
                }
            }

            if (entitiesBuffer.isNotEmpty()) {
                val dbWriteStartTime = System.currentTimeMillis()
                offlineSearchDao.upsertTopics(entitiesBuffer)
                logger.d { "Forum $forumId: flushed ${entitiesBuffer.size} remaining topics" }
            }

            val forumDuration = System.currentTimeMillis() - forumStartTime
            val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            val memoryUsed = (finalMemory - initialMemory) / (1024 * 1024)
            val avgTimePerTopic = if (totalTopics > 0) forumDuration / totalTopics else 0

            logger.i { "Forum $forumId indexing completed: $totalTopics topics, duration: ${forumDuration}ms" }
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
            val entitiesBuffer = mutableListOf<CachedTopicEntity>()

            // Track IDs of topics found in this update to avoid duplicates if pages shift
            val processedTopicIds = mutableSetOf<String>()

            val forumStartTime = System.currentTimeMillis()
            logger.i { "Starting incremental update for forum $forumId (max age: ${maxAgeMs / 1000}s)" }

            while (hasMorePages && page < MAX_PAGES_PER_FORUM) {
                try {
                    val pageStartTime = System.currentTimeMillis()
                    val response = retryWithBackoff { api.getForumPage(forumId, start = page * TOPICS_PER_PAGE) }
                    val fetchTime = System.currentTimeMillis() - pageStartTime

                    if (!response.isSuccessful) {
                        logger.w { "Failed to fetch forum $forumId page $page: HTTP ${response.code()}" }
                        // Adaptive backoff for rate-limit responses
                        if (response.code() == 429 || response.code() == 503) {
                            val retryAfter = parseRetryAfterMs(response.headers())
                            logger.i { "Rate-limited (${response.code()}), backing off..." }
                            adaptiveBackoff(attempt = page, retryAfterMs = retryAfter)
                            continue // Retry same page
                        }
                        break
                    }

                    val body = response.body() ?: break

                    // Parse page
                    val parseStartTime = System.currentTimeMillis()
                    val pageResult = parser.parseForumPageWithPagination(body, forumId)
                    val topics = pageResult.topics

                    hasMorePages = pageResult.hasMorePages

                    if (topics.isEmpty()) {
                        hasMorePages = false
                    } else {
                        val validTopics = topics.filter { it.toDomain().isValid() }

                        // Check which topics need update
                        val now = System.currentTimeMillis()
                        val existingIds = offlineSearchDao.getExistingTopicIds(validTopics.map { it.topicId }).toSet()
                        val topicsToUpdate =
                            validTopics.filter { topic ->
                                val isNew = !existingIds.contains(topic.topicId)
                                isNew // Only persist topics not already in DB
                            }

                        // Deduplicate against processed
                        val uniqueTopics = topicsToUpdate.filter { !processedTopicIds.contains(it.topicId) }
                        uniqueTopics.forEach { processedTopicIds.add(it.topicId) }

                        val newEntities = uniqueTopics.map { it.toCachedTopicEntity(currentIndexVersion) }
                        entitiesBuffer.addAll(newEntities)
                        totalUpdated += uniqueTopics.size

                        if (entitiesBuffer.size >= BATCH_SIZE_FOR_DB) {
                            offlineSearchDao.upsertTopics(entitiesBuffer)
                            entitiesBuffer.clear()
                        }

                        // Stop incremental update if we encounter *only* topics that are already fresh?
                        // This logic is complex. For now, we iterate until pagination ends or heuristics.
                        // Assuming standard behavior: crawl all pages.

                        politeDelay()
                        page++
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    logger.e({ "Error updating forum $forumId page $page" }, e)
                    hasMorePages = false
                }
            }

            if (entitiesBuffer.isNotEmpty()) {
                offlineSearchDao.upsertTopics(entitiesBuffer)
            }

            val duration = System.currentTimeMillis() - forumStartTime
            logger.i { "Incremental update for $forumId completed: $totalUpdated topics updated in ${duration}ms" }
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
                                    if (e is kotlinx.coroutines.CancellationException) throw e
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
                offlineSearchDao.getMaxIndexVersion()
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
                logger.i { "Index cleared" }
            }

        /**
         * Update a single forum's status in the shared list (thread-safe).
         */
        private fun updateForumStatus(
            forumId: String,
            state: ForumState,
            topicsCount: Int = 0,
            lastUpdated: Long = 0L,
            errorMessage: String? = null,
        ) {
            val updated =
                _forumStatuses.value.map { fs ->
                    if (fs.forumId == forumId) {
                        fs.copy(
                            state = state,
                            topicsCount = if (state == ForumState.INDEXED) topicsCount else fs.topicsCount,
                            lastUpdated = if (state == ForumState.INDEXED && lastUpdated > 0) lastUpdated else fs.lastUpdated,
                            // Record the final page reached as the forum's total page count
                            totalPages = if (state == ForumState.INDEXED) fs.currentPage.coerceAtLeast(1) else fs.totalPages,
                            errorMessage = errorMessage ?: if (state == ForumState.FAILED) fs.errorMessage else null,
                        )
                    } else {
                        fs
                    }
                }
            _forumStatuses.value = updated
            // Also keep the IndexProgress forumStatuses in sync
            val current = _indexProgress.value
            _indexProgress.value = current.copy(forumStatuses = updated)
        }

        /**
         * Update page progress for a forum (thread-safe).
         */
        private fun updateForumStatusPage(
            forumId: String,
            page: Int,
        ) {
            val updated =
                _forumStatuses.value.map { fs ->
                    if (fs.forumId == forumId) {
                        fs.copy(currentPage = page)
                    } else {
                        fs
                    }
                }
            _forumStatuses.value = updated
        }

        /**
         * Count forums that are in INDEXED state.
         */
        private fun countCompletedForums(): Int = _forumStatuses.value.count { it.state == ForumState.INDEXED }

        internal fun isHealthyForumPage(
            html: String,
            parsedRows: Int,
        ): Boolean =
            when {
                parsedRows > 0 -> true
                html.contains("captcha", ignoreCase = true) -> false
                html.contains("login-form", ignoreCase = true) -> false
                html.contains("введите код", ignoreCase = true) -> false
                html.contains("заблокирован", ignoreCase = true) -> false
                html.contains("доступ запрещён", ignoreCase = true) -> false
                html.contains("доступ запрещен", ignoreCase = true) -> false
                html.length < 500 -> false
                else -> false
            }
    }

/**
 * Thrown when [ForumIndexer.indexForums] cannot acquire the indexing mutex within
 * [ForumIndexer.mutexAcquireTimeoutMs] because another indexing run is in progress.
 * Callers should treat this as a benign "nothing to do" condition — never a stuck worker.
 */
public class IndexingInProgressException : Exception("Indexing already in progress; another index run owns the mutex")
