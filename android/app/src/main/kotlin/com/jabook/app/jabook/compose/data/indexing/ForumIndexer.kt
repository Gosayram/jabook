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

import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.local.dao.IndexMetadata
import com.jabook.app.jabook.compose.data.local.dao.OfflineSearchDao
import com.jabook.app.jabook.compose.data.local.entity.CachedTopicEntity
import com.jabook.app.jabook.compose.data.local.entity.toCachedTopicEntity
import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.data.remote.mapper.toDomain
import com.jabook.app.jabook.compose.data.remote.model.SearchResult
import com.jabook.app.jabook.compose.data.remote.parser.RutrackerParser
import com.jabook.app.jabook.compose.data.repository.UserPreferencesRepository
import com.jabook.app.jabook.utils.parseRetryAfterMs
import com.jabook.app.jabook.utils.retryWithBackoff
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for indexing all audiobook forums on RuTracker.
 *
 * This service pre-indexes all topics from audiobook forums to enable
 * fast offline search without network requests.
 *
 * Features:
 * - Two-phase fresh-first crawl: newest pages of every forum first, then
 *   history backfill (resumable via persisted page cursors)
 * - Incremental updates: Only updates topics that are old or missing (daily by default)
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
        private val forumCatalog: ForumCatalog,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val loggerFactory: LoggerFactory,
    ) {
        private val logger = loggerFactory.get("ForumIndexer")

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

        public companion object {
            private const val TOPICS_PER_PAGE = 50
            private const val BASE_DELAY_MS = 300L
            private const val JITTER_RANGE_MS = 150L // ±150ms random jitter
            internal const val MAX_PAGES_PER_FORUM = 100_000

            private const val INCREMENTAL_UPDATE_INTERVAL_HOURS = 24L
            private const val MAX_AGE_FOR_UPDATE_MS = INCREMENTAL_UPDATE_INTERVAL_HOURS * 60 * 60 * 1000

            private const val MAX_CONCURRENT_FORUMS = 3
            private const val BATCH_SIZE_FOR_DB = 100

            // Fresh-first phase: newest pages crawled for every forum before any
            // history backfill, so page-0 topics become searchable within seconds.
            private const val FRESH_MAX_PAGES = 2

            // Backfill page budget per forum per run when a depth window is set;
            // explicit "All" (daysWindow == 0) stays a full crawl.
            private const val BACKFILL_PAGES_PER_RUN = 10

            /**
             * Per-run page budget for the backfill phase.
             */
            internal fun backfillPageBudget(daysWindow: Int): Int = if (daysWindow > 0) BACKFILL_PAGES_PER_RUN else MAX_PAGES_PER_FORUM

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

            /**
             * Pure cutoff predicate for the date-driven depth window: true when
             * EVERY topic on the page was registered before [cutoffMs]. A topic
             * with unknown date (null) counts as fresh → page is never "older".
             * Listings are date-ordered desc, so a true result means every
             * deeper page is also outside the window.
             */
            internal fun isPageOlderThan(
                cutoffMs: Long,
                topics: List<SearchResult>,
            ): Boolean =
                topics.isNotEmpty() &&
                    topics.all { topic ->
                        (topic.registeredAtEpochSec?.times(1000) ?: Long.MAX_VALUE) < cutoffMs
                    }
        }

        /**
         * Index all audiobook forums with a two-phase fresh-first crawl.
         *
         * Forums run in parallel, bounded by [MAX_CONCURRENT_FORUMS] (semaphore — a
         * finished forum immediately frees its slot for the next one instead of
         * waiting on the slowest sibling of a fixed chunk). Pages within a forum
         * stay sequential because RuTracker listings are sorted by recent activity,
         * so the [daysWindow] early-exit relies on page order.
         *
         * Phase 1 (fresh, [IndexProgress.PHASE_FRESH]): every forum crawls from
         * page 0, capped at [FRESH_MAX_PAGES] pages or the date/known-topics
         * boundary — newest topics become searchable within seconds.
         *
         * Phase 2 (backfill, [IndexProgress.PHASE_BACKFILL]): every forum continues
         * from its persisted page cursor (or where fresh stopped) until the
         * [daysWindow] cutoff or the per-run page budget ([backfillPageBudget]).
         * Forums whose fresh pass already hit the boundary are skipped.
         *
         * @param forumIds Comma-separated list of forum IDs to index
         * @param daysWindow Depth window in days (quick indexing). 0 = legacy full
         *   crawl. When > 0, a forum's crawl stops at the first page whose topics
         *   are all already indexed — the listing's date-sorted boundary. Zero new
         *   topics across all forums is then a success ("nothing newer than window"),
         *   not an error. Explicit 0 (All) keeps the full-crawl backfill budget.
         * @param onProgress Callback with IndexingProgress updates
         * @return Total number of topics indexed
         */
        public suspend fun indexForums(
            forumIds: String,
            daysWindow: Int = 0,
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

                    // Initialize forum name mapping from the local catalog
                    // (real names); refresh opportunistically when the table is
                    // cold. "Forum $id" stays only as last-resort fallback.
                    forumNames.clear()
                    runCatching {
                        if (forumCatalog.namesById().isEmpty()) forumCatalog.refresh()
                    }
                    val catalogNames =
                        runCatching { forumCatalog.namesById() }
                            .onFailure { logger.w { "Forum catalog unavailable: ${it.message}" } }
                            .getOrDefault(emptyMap())
                    for (id in forumIdList) {
                        forumNames[id] = catalogNames[id] ?: "Forum $id"
                    }

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
                    if (daysWindow > 0) {
                        logger.i { "Depth window: $daysWindow days (quick indexing)" }
                    }
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

                    // Thread-safe progress tracking
                    val topicsIndexedAtomic =
                        java.util.concurrent.atomic
                            .AtomicInteger(0)
                    val failedForums =
                        java.util.concurrent.atomic
                            .AtomicInteger(0)
                    val failedForumMessages = mutableListOf<String>()

                    // Cross-phase per-forum state
                    val freshStopPages = ConcurrentHashMap<String, Int>()
                    val freshBoundaryForums = ConcurrentHashMap.newKeySet<String>()
                    val freshFailedForums = ConcurrentHashMap.newKeySet<String>()
                    val forumTopicCounts = ConcurrentHashMap<String, Int>()

                    // Resume cursors from a previous interrupted backfill run
                    val persistedCursors =
                        runCatching { userPreferencesRepository.getIndexingPageCursors() }
                            .onFailure { logger.w { "Could not read indexing page cursors: ${it.message}" } }
                            .getOrDefault(emptyMap())

                    suspend fun emitProgress(
                        phase: String?,
                        forumId: String,
                        page: Int,
                        topicsInForum: Int,
                    ) {
                        _indexProgress.value =
                            IndexProgress(
                                currentForumName = resolveForumName(forumId),
                                currentForumPage = page,
                                totalForumsCompleted = countCompletedForums(),
                                totalForums = forumIdList.size,
                                topicsFound = topicsIndexedAtomic.get(),
                                errors = synchronized(failedForumMessages) { failedForumMessages.toList() },
                                forumStatuses = _forumStatuses.value,
                                phase = phase,
                            )
                        onProgress?.invoke(IndexingProgress.InProgress(_indexProgress.value))
                    }

                    fun recordForumFailure(
                        forumId: String,
                        page: Int,
                        e: Exception,
                    ) {
                        val errorMsg = buildErrorMessage(forumId, page, e)
                        logger.e({ "Failed to index forum $forumId" }, e)
                        synchronized(failedForumMessages) {
                            failedForumMessages.add(errorMsg)
                        }
                        failedForums.incrementAndGet()
                        updateForumStatus(
                            forumId,
                            ForumState.FAILED,
                            errorMessage = errorMsg,
                        )
                    }

                    suspend fun finishForum(forumId: String) {
                        updateForumStatus(
                            forumId,
                            ForumState.INDEXED,
                            topicsCount = forumTopicCounts.getOrDefault(forumId, 0),
                            lastUpdated = System.currentTimeMillis(),
                        )
                    }

                    // ---- Phase 1: FRESH — newest pages of every forum first ----
                    coroutineScope {
                        val forumSlots = Semaphore(MAX_CONCURRENT_FORUMS)
                        forumIdList
                            .map { forumId ->
                                async(Dispatchers.IO) {
                                    forumSlots.withPermit {
                                        updateForumStatus(forumId, ForumState.IN_PROGRESS)
                                        try {
                                            val result =
                                                indexForum(
                                                    forumId = forumId,
                                                    indexVersion = currentIndexVersion,
                                                    daysWindow = daysWindow,
                                                    phase = IndexProgress.PHASE_FRESH,
                                                    startPage = 0,
                                                    maxPages = FRESH_MAX_PAGES,
                                                    persistCursor = null, // fresh never uses cursors
                                                ) { page, topicsInForum ->
                                                    updateForumStatusPage(forumId, page)
                                                    if (page == 0 || page % 2 == 0 || topicsInForum < 50) {
                                                        emitProgress(IndexProgress.PHASE_FRESH, forumId, page, topicsInForum)
                                                    }
                                                }
                                            forumTopicCounts.merge(forumId, result.topicsIndexed, Int::plus)
                                            topicsIndexedAtomic.addAndGet(result.topicsIndexed)
                                            freshStopPages[forumId] = result.nextUncrawledPage
                                            if (result.boundaryHit) freshBoundaryForums.add(forumId)
                                        } catch (e: Exception) {
                                            if (e is kotlinx.coroutines.CancellationException) throw e
                                            recordForumFailure(forumId, 0, e)
                                            freshFailedForums.add(forumId)
                                        }
                                    }
                                }
                            }.awaitAll()
                    }

                    // ---- Phase 2: BACKFILL — history from persisted cursors ----
                    val backfillBudget = backfillPageBudget(daysWindow)
                    coroutineScope {
                        val forumSlots = Semaphore(MAX_CONCURRENT_FORUMS)
                        forumIdList
                            .map { forumId ->
                                async(Dispatchers.IO) {
                                    if (forumId in freshFailedForums) return@async
                                    updateForumStatus(forumId, ForumState.IN_PROGRESS)
                                    if (forumId in freshBoundaryForums) {
                                        // Fresh pass proved this forum is up to date —
                                        // nothing to backfill; drop any stale resume cursor.
                                        runCatching { userPreferencesRepository.clearIndexingPageCursor(forumId) }
                                        finishForum(forumId)
                                        return@async
                                    }
                                    try {
                                        val cursor = persistedCursors[forumId]?.coerceAtLeast(0) ?: 0
                                        val startIndex = maxOf(cursor, freshStopPages[forumId] ?: 0)
                                        val result =
                                            indexForum(
                                                forumId = forumId,
                                                indexVersion = currentIndexVersion,
                                                daysWindow = daysWindow,
                                                phase = IndexProgress.PHASE_BACKFILL,
                                                startPage = startIndex,
                                                maxPages = backfillBudget,
                                                persistCursor = { nextPage ->
                                                    runCatching {
                                                        if (nextPage == null) {
                                                            userPreferencesRepository.clearIndexingPageCursor(forumId)
                                                        } else {
                                                            userPreferencesRepository.updateIndexingPageCursor(forumId, nextPage)
                                                        }
                                                    }
                                                },
                                            ) { page, topicsInForum ->
                                                updateForumStatusPage(forumId, page)
                                                if (page == 0 || page % 2 == 0 || topicsInForum < 50) {
                                                    emitProgress(IndexProgress.PHASE_BACKFILL, forumId, page, topicsInForum)
                                                }
                                            }
                                        forumTopicCounts.merge(forumId, result.topicsIndexed, Int::plus)
                                        topicsIndexedAtomic.addAndGet(result.topicsIndexed)
                                        finishForum(forumId)
                                    } catch (e: Exception) {
                                        if (e is kotlinx.coroutines.CancellationException) throw e
                                        recordForumFailure(forumId, 0, e)
                                    }
                                }
                            }.awaitAll()
                    }

                    val totalIndexed: Int = topicsIndexedAtomic.get()
                    val duration = System.currentTimeMillis() - startTime

                    if (failedForums.get() == forumIdList.size) {
                        val messages = synchronized(failedForumMessages) { failedForumMessages.toList() }
                        val message =
                            "Indexing failed for all forums (${failedForums.get()}/${forumIdList.size}). " +
                                messages.take(3).joinToString("; ")
                        logger.e { message }
                        _indexProgress.value =
                            _indexProgress.value.copy(errors = messages)
                        onProgress?.invoke(IndexingProgress.Error(message))
                        throw IllegalStateException(message)
                    }

                    if (daysWindow <= 0) {
                        // Full-crawl sanity guards — a full crawl must never return
                        // (near-)empty when the old index was healthy.
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
                    } else if (totalIndexed == 0) {
                        logger.i {
                            "Quick indexing found nothing newer than the $daysWindow-day window " +
                                "(existing index: $oldCount topics) — nothing to do"
                        }
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
         * @param onProgress Progress callback
         * @return Number of topics updated
         */
        public suspend fun incrementalUpdate(
            forumIds: String,
            maxAgeMs: Long = MAX_AGE_FOR_UPDATE_MS,
            onProgress: ((forumId: String, updated: Int, total: Int) -> Unit)? = null,
        ): Int =
            withContext(Dispatchers.IO) {
                val currentIndexVersion = getCurrentIndexVersion()
                val forumIdList = forumIds.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                var totalUpdated: Int = 0

                logger.i { "Starting incremental update (max age: ${maxAgeMs / (1000 * 60 * 60)} hours)" }

                for (forumId in forumIdList) {
                    try {
                        val updated =
                            updateForumIncremental(
                                forumId,
                                maxAgeMs,
                                currentIndexVersion,
                                onProgress,
                            )
                        totalUpdated += updated
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
         * Index a single forum by fetching pages sequentially, flushing each
         * page's topics to the DB before advancing (fresh page-0 topics become
         * searchable within seconds).
         *
         * @param forumId Forum ID to index
         * @param indexVersion Current index version
         * @param daysWindow Depth window in days; > 0 enables the known-page early-exit
         *   (listings are sorted by recent activity, so a page whose topics are all
         *   already indexed marks the boundary — everything deeper is older).
         * @param phase Crawl phase reported through progress (fresh/backfill)
         * @param startPage First page to fetch (backfill resume point)
         * @param maxPages Page budget for this crawl
         * @param persistCursor Backfill resume-cursor sink: invoked with the next
         *   page to crawl after every processed page, and with null once the
         *   forum is fully crawled (boundary or listing end)
         * @param onProgress Progress callback with (page, topicsInForum)
         * @return [ForumCrawlResult] with topics indexed, boundary flag and resume page
         */
        private suspend fun indexForum(
            forumId: String,
            indexVersion: Int,
            daysWindow: Int = 0,
            phase: String? = null,
            startPage: Int = 0,
            maxPages: Int = MAX_PAGES_PER_FORUM,
            persistCursor: (suspend (nextPage: Int?) -> Unit)? = null,
            onProgress: (suspend (page: Int, topicsInForum: Int) -> Unit)? = null,
        ): ForumCrawlResult {
            var totalTopics: Int = 0
            var page: Int = startPage
            var hasMorePages: Boolean = true
            var boundaryReached: Boolean = false
            val entitiesBuffer = mutableListOf<CachedTopicEntity>() // Buffer for intra-page batching
            var lastPageSignature: String? = null
            var repeatedSignatureCount: Int = 0
            val pageLimit = startPage + maxPages

            val forumStartTime = System.currentTimeMillis()
            val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            logger.i { "Starting indexing forum $forumId (version $indexVersion, phase $phase, pages $startPage..${pageLimit - 1})" }

            while (hasMorePages && page < pageLimit) {
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
                    // Read body bytes ONCE — parser needs them, health check needs them
                    val rawBytes = RutrackerParser.readCappedBody(body)
                    val contentType = body.contentType()?.toString()
                    val parseStartTime = System.currentTimeMillis()
                    val pageResult = parser.parseForumPageFromBytes(rawBytes, contentType, forumId)
                    val topics = pageResult.topics
                    val parseTime = System.currentTimeMillis() - parseStartTime

                    if (topics.isEmpty()) {
                        if (page == 0) {
                            val decodedHtml =
                                try {
                                    parser.decodeBytes(rawBytes, contentType)
                                } catch (e: Exception) {
                                    String(rawBytes, Charsets.UTF_8)
                                }
                            if (!isHealthyForumPage(decodedHtml, 0)) {
                                val errorMsg = "Forum $forumId page 0: unhealthy response (CAPTCHA/login-wall/block page)"
                                logger.w { errorMsg }
                                throw IllegalStateException(errorMsg)
                            }
                        }
                        logger.d {
                            "Forum $forumId page $page: no topics found, ending (fetch: ${fetchTime}ms, parse: ${parseTime}ms)"
                        }
                        hasMorePages = false
                        persistCursor?.invoke(null) // listing exhausted — forum done
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
                                persistCursor?.invoke(null) // pagination loop — treat as done
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

                        // Depth window: with quick indexing enabled, stop once the
                        // listing's date-sorted boundary is passed. Two equivalent
                        // boundary signals (either suffices, date check is cheaper
                        // and works on a cold DB):
                        // 1. Date: from page 1 on, a page whose topics are ALL
                        //    older than the window — deeper pages are older still.
                        // 2. Known: a page whose topics are all already indexed.
                        var newTopics = validTopics
                        if (daysWindow > 0 && validTopics.isNotEmpty()) {
                            val cutoffMs = System.currentTimeMillis() - daysWindow * 86_400_000L
                            if (page >= 1 && isPageOlderThan(cutoffMs, validTopics)) {
                                logger.i {
                                    "Forum $forumId page $page: all ${validTopics.size} topics older than " +
                                        "$daysWindow-day window — date boundary reached, stopping crawl"
                                }
                                boundaryReached = true
                                hasMorePages = false
                            } else {
                                val existingIds =
                                    offlineSearchDao
                                        .getExistingTopicIds(validTopics.map { it.topicId })
                                        .toSet()
                                newTopics = validTopics.filter { it.topicId !in existingIds }
                                if (newTopics.isEmpty()) {
                                    logger.i {
                                        "Forum $forumId page $page: all ${validTopics.size} topics already " +
                                            "indexed — depth window boundary reached, stopping crawl"
                                    }
                                    boundaryReached = true
                                    hasMorePages = false
                                }
                            }
                            if (boundaryReached) {
                                persistCursor?.invoke(null) // forum fully crawled — drop resume cursor
                            }
                        }

                        if (newTopics.isNotEmpty()) {
                            val newEntities =
                                newTopics.map { it.toCachedTopicEntity(indexVersion, it.registeredAtEpochSec) }
                            entitiesBuffer.addAll(newEntities)
                            totalTopics += newTopics.size
                        }

                        // Flush at the end of EVERY page — per-page visibility
                        // beats the old 100-topic batch threshold.
                        if (entitiesBuffer.isNotEmpty()) {
                            val dbWriteStartTime = System.currentTimeMillis()
                            offlineSearchDao.upsertTopics(entitiesBuffer)
                            val dbWriteTime = System.currentTimeMillis() - dbWriteStartTime
                            logger.d { "Forum $forumId: wrote ${entitiesBuffer.size} topics to DB in ${dbWriteTime}ms" }
                            entitiesBuffer.clear()
                        }

                        // Only the window boundary skips page advancement; a page of
                        // all-invalid topics still advances (legacy behavior).
                        if (!boundaryReached) {
                            onProgress?.invoke(page, totalTopics)
                            politeDelay()
                            page++
                            if (hasMorePages) {
                                persistCursor?.invoke(page) // resume here if the run dies
                            }
                        }
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
            return ForumCrawlResult(
                topicsIndexed = totalTopics,
                boundaryHit = boundaryReached,
                nextUncrawledPage = page,
            )
        }

        /**
         * Incrementally update a single forum (only fetch new/updated topics).
         *
         * @param forumId Forum ID to update
         * @param maxAgeMs Maximum age for topics to update
         * @param currentIndexVersion Current index version
         * @param onProgress Progress callback
         * @return Number of topics updated
         */
        private suspend fun updateForumIncremental(
            forumId: String,
            maxAgeMs: Long,
            currentIndexVersion: Int,
            onProgress: ((forumId: String, updated: Int, total: Int) -> Unit)?,
        ): Int {
            var totalUpdated: Int = 0
            var page: Int = 0
            var hasMorePages: Boolean = true
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

                        val newEntities =
                            uniqueTopics.map { it.toCachedTopicEntity(currentIndexVersion, it.registeredAtEpochSec) }
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
            return totalUpdated
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
                // If no unhealthy markers found, page is likely healthy
                // (parser may just not find matching rows — selectors may need updating)
                else -> true
            }
    }

/**
 * Thrown when [ForumIndexer.indexForums] cannot acquire the indexing mutex within
 * [ForumIndexer.mutexAcquireTimeoutMs] because another indexing run is in progress.
 * Callers should treat this as a benign "nothing to do" condition — never a stuck worker.
 */
public class IndexingInProgressException : Exception("Indexing already in progress; another index run owns the mutex")

/**
 * Outcome of a single [ForumIndexer.indexForum] crawl.
 *
 * @property topicsIndexed Number of new topics persisted
 * @property boundaryHit Whether the depth-window boundary stopped the crawl
 * @property nextUncrawledPage First page not processed (resume point)
 */
private data class ForumCrawlResult(
    val topicsIndexed: Int,
    val boundaryHit: Boolean,
    val nextUncrawledPage: Int,
)
