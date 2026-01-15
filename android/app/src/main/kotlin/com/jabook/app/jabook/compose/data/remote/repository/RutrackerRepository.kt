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

package com.jabook.app.jabook.compose.data.remote.repository

import com.jabook.app.jabook.compose.core.logger.LogLevel
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.core.logger.endOperation
import com.jabook.app.jabook.compose.core.logger.log
import com.jabook.app.jabook.compose.core.logger.logError
import com.jabook.app.jabook.compose.core.logger.logSuccess
import com.jabook.app.jabook.compose.core.logger.logWarning
import com.jabook.app.jabook.compose.core.logger.logWithDuration
import com.jabook.app.jabook.compose.core.logger.startOperation
import com.jabook.app.jabook.compose.core.logger.withOperation
import com.jabook.app.jabook.compose.data.auth.RutrackerAuthService
import com.jabook.app.jabook.compose.data.cache.RutrackerSearchCache
import com.jabook.app.jabook.compose.data.local.dao.OfflineSearchDao
import com.jabook.app.jabook.compose.data.local.entity.toCachedTopicEntity
import com.jabook.app.jabook.compose.data.local.entity.toSearchResult
import com.jabook.app.jabook.compose.data.network.MirrorManager
import com.jabook.app.jabook.compose.data.remote.RuTrackerError
import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.data.remote.mapper.toDomain
import com.jabook.app.jabook.compose.data.remote.mapper.toDomainFromIndex
import com.jabook.app.jabook.compose.data.remote.model.AudiobookCategory
import com.jabook.app.jabook.compose.data.remote.model.SearchResult
import com.jabook.app.jabook.compose.data.remote.parser.CategoryParser
import com.jabook.app.jabook.compose.data.remote.parser.ParsingResult
import com.jabook.app.jabook.compose.data.remote.parser.RutrackerParser
import com.jabook.app.jabook.compose.domain.model.RutrackerSearchResult
import com.jabook.app.jabook.compose.domain.model.RutrackerTopicDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for RuTracker operations.
 *
 * Integrates:
 * - RutrackerApi - network calls
 * - RutrackerParser - HTML parsing with encoding detection
 * - CategoryParser - category structure parsing
 * - RutrackerAuthService - authentication
 *
 * This is the main entry point for RuTracker functionality.
 */
@Singleton
public class RutrackerRepository
    @Inject
    constructor(
        private val api: RutrackerApi,
        private val parser: RutrackerParser,
        private val categoryParser: CategoryParser,
        private val authService: RutrackerAuthService,
        private val searchCache: RutrackerSearchCache,
        private val offlineSearchDao: OfflineSearchDao,
        private val mirrorManager: MirrorManager,
        private val loggerFactory: LoggerFactory,
    ) {
        public companion object {
            private const val TAG = "RutrackerRepository"
        }

        private val logger = loggerFactory.get(TAG)

        /**
         * Search for audiobooks using ONLY indexed topics (offline, no network).
         *
         * This method uses the pre-indexed topics from forums, ensuring:
         * - Fast, offline search
         * - Only audiobook topics (from indexed forums)
         * - No unwanted topics from other forums
         * - No network requests
         *
         * @param query Search query
         * @param forumIds Optional forum IDs (ignored - uses all indexed topics)
         * @return Result with search results (empty if index is empty or search fails)
         */
        public suspend fun searchAudiobooks(
            query: String,
            forumIds: String? = null,
        ): Result<List<RutrackerSearchResult>> =
            withContext(Dispatchers.IO) {
                logger.withOperation("searchAudiobooks") { operationId ->
                    try {
                        logger.log(operationId, "Index-only search started: query='$query', forumIds=$forumIds")

                        // Use ONLY indexed search
                        val indexSize = offlineSearchDao.getTopicCount()
                        if (indexSize > 0) {
                            val indexSearchStartTime = System.currentTimeMillis()
                            val indexedResults = searchIndexedTopics(query, limit = 200)
                            val indexSearchDuration = System.currentTimeMillis() - indexSearchStartTime

                            logger.logSuccess(
                                operationId,
                                "Found ${indexedResults.size} results from index (${indexSearchDuration}ms, index size: $indexSize)",
                            )
                            Result.success(indexedResults)
                        } else {
                            logger.logWarning(
                                operationId,
                                "Index is empty ($indexSize topics) - cannot search. Please run indexing first.",
                            )
                            Result.success(emptyList())
                        }
                    } catch (e: Exception) {
                        logger.logError(operationId, "Indexed search failed", e)
                        Result.success(emptyList()) // Return empty instead of error
                    }
                }
            }

        /**
         * Fast search in indexed topics (offline, no network required).
         *
         * Uses lenient validation (isValidForIndex) since indexed topics:
         * - May have fallback category values
         * - Don't have torrentUrl (retrieved on-demand via getTopicDetails())
         *
         * @param query Search query
         * @param limit Maximum number of results
         * @return List of search results from index
         */
        public suspend fun searchIndexedTopics(
            query: String,
            limit: Int = 100,
        ): List<RutrackerSearchResult> =
            withContext(Dispatchers.IO) {
                try {
                    val searchStartTime = System.currentTimeMillis()
                    val currentMirror = mirrorManager.getCurrentMirrorDomain()
                    logger.i { "=== INDEXED SEARCH START ===" }
                    logger.i { "Input query: '$query', limit: $limit" }
                    logger.i { "Current mirror: $currentMirror" }

                    // Log exact SQL that will be executed (reconstructed for visibility)
                    val sqlPattern =
                        "SELECT * FROM cached_topics " +
                            "WHERE (title LIKE '%$query%' OR author LIKE '%$query%') " +
                            "AND category IS NOT NULL AND category != '' " +
                            "ORDER BY CASE WHEN title LIKE '$query%' THEN 1 ELSE 2 END, " +
                            "seeders DESC, timestamp DESC LIMIT $limit"
                    logger.d { "SQL query pattern: $sqlPattern" }
                    logger.d { "Query filters: title/author contains '$query', category NOT NULL/empty" }

                    val entities = offlineSearchDao.searchIndexedTopics(query, limit)
                    val dbDuration = System.currentTimeMillis() - searchStartTime

                    logger.i { "DB query completed: ${entities.size} entities returned in ${dbDuration}ms" }

                    // Diagnostic: if no results, check why
                    if (entities.isEmpty()) {
                        logger.w { "⚠️ Zero results from DB - running diagnostics..." }
                        val totalTopics = offlineSearchDao.getTopicCount()
                        val topicsWithCategory = offlineSearchDao.getTopicsWithNonEmptyCategory()
                        logger.w { "Total topics in DB: $totalTopics" }
                        logger.w { "Topics with non-empty category: $topicsWithCategory" }
                        logger.w { "Topics filtered by category constraint: ${totalTopics - topicsWithCategory}" }

                        // Sample a few topics to see what's in DB
                        val sampleTopics = offlineSearchDao.getSampleTopics(5)
                        sampleTopics.forEachIndexed { i, topic ->
                            logger.d { "Sample[$i]: title='${topic.title.take(40)}', category='${topic.category}'" }
                        }
                    }

                    // Log first 3 entities for diagnostics
                    entities.take(3).forEachIndexed { i, entity ->
                        logger.d {
                            "Result[$i]: id=${entity.topicId}, " +
                                "title='${entity.title.take(40)}', " +
                                "author='${entity.author.take(20)}', " +
                                "category='${entity.category}', " +
                                "seeders=${entity.seeders}"
                        }
                    }

                    val mapStartTime = System.currentTimeMillis()
                    logger.d { "Mapping ${entities.size} entities to DTO..." }
                    val dtoResults = entities.map { it.toSearchResult() }
                    val dtoMapDuration = System.currentTimeMillis() - mapStartTime
                    logger.d { "DTO mapping: ${entities.size} → ${dtoResults.size} in ${dtoMapDuration}ms" }

                    val domainMapStartTime = System.currentTimeMillis()
                    // Use lenient validation for indexed results
                    val domainResults = dtoResults.toDomainFromIndex()
                    val domainMapDuration = System.currentTimeMillis() - domainMapStartTime

                    val filteredCount = dtoResults.size - domainResults.size
                    if (filteredCount > 0) {
                        logger.w {
                            "⚠️ Validation filtered out $filteredCount results " +
                                "(${dtoResults.size} DTO → ${domainResults.size} domain, ${domainMapDuration}ms)"
                        }
                        // Log why items were filtered
                        val filtered = dtoResults.toDomainFromIndex()
                        if (filtered.isEmpty() && dtoResults.isNotEmpty()) {
                            logger.e { "❌ ALL results filtered out! First DTO: ${dtoResults.first()}" }
                        }
                    } else {
                        logger.d { "Domain mapping: ${dtoResults.size} → ${domainResults.size} in ${domainMapDuration}ms (no filtering)" }
                    }

                    val totalDuration = System.currentTimeMillis() - searchStartTime
                    logger.i {
                        "=== SEARCH COMPLETE === Query: '$query' | Results: ${domainResults.size} | " +
                            "Total: ${totalDuration}ms (DB: ${dbDuration}ms, DTO map: ${dtoMapDuration}ms, Domain map: ${domainMapDuration}ms)"
                    }

                    domainResults
                } catch (e: Exception) {
                    logger.e(
                        { "❌ Indexed search EXCEPTION for query '$query': ${e.message}" },
                        e,
                    )
                    logger.e { "Exception type: ${e.javaClass.simpleName}, stack trace below" }
                    emptyList()
                }
            }

        /**
         * Search using ONLY indexed topics (offline, no network).
         *
         * This method uses the pre-indexed topics from forums, ensuring:
         * - Fast, offline search
         * - Only audiobook topics (from indexed forums)
         * - No unwanted topics from other forums
         * - No network requests
         *
         * If index is empty or search fails, returns empty list (never falls back to network).
         */
        public fun searchAudiobooksFlow(
            query: String,
            forumIds: String? = null,
        ): Flow<Result<List<RutrackerSearchResult>>> =
            flow {
                val currentMirror = mirrorManager.getCurrentMirrorDomain()
                logger.i { "🔍 Index-only search started: query='$query', forumIds=$forumIds" }
                logger.i { "Using mirror: $currentMirror" }

                try {
                    // Check if index exists and has data
                    val indexSize =
                        withContext(Dispatchers.IO) {
                            try {
                                offlineSearchDao.getTopicCount()
                            } catch (e: Exception) {
                                logger.e(
                                    { "Failed to get topic count" },
                                    e,
                                )
                                0
                            }
                        }

                    if (indexSize > 0) {
                        // Check for debug command
                        if (query.trim() == "!index" || query.trim() == ":debug") {
                            val sampleTopics = offlineSearchDao.getSampleTopics(10)
                            val domainResults = sampleTopics.map { it.toSearchResult() }.toDomainFromIndex()
                            emit(Result.success(domainResults))
                            return@flow
                        }

                        // Tokenize query for fuzzy search
                        val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

                        if (tokens.isEmpty()) {
                            emit(Result.success(emptyList()))
                        } else {
                            // Build dynamic SQL query for token-based search
                            val sqlBuilder = StringBuilder("SELECT * FROM cached_topics WHERE ")
                            val args = ArrayList<Any>()

                            tokens.forEachIndexed { index, token ->
                                if (index > 0) sqlBuilder.append(" AND ")
                                sqlBuilder.append("(title LIKE ? OR author LIKE ?)")

                                val likePattern: String = "%$token%"
                                args.add(likePattern)
                                args.add(likePattern)
                            }

                            // Add ordering and limit
                            sqlBuilder.append(" ORDER BY seeders DESC, timestamp DESC LIMIT 200")

                            val simpleQuery = androidx.sqlite.db.SimpleSQLiteQuery(sqlBuilder.toString(), args.toArray())

                            // Emit Flow from Room
                            offlineSearchDao
                                .searchIndexedTopicsRaw(simpleQuery)
                                .map { entities ->
                                    val dtoResults = entities.map { it.toSearchResult() }
                                    val domainResults = dtoResults.toDomainFromIndex()
                                    Result.success(domainResults)
                                }.collect {
                                    emit(it)
                                }
                        }
                    } else {
                        logger.w { "⚠️ Index is empty ($indexSize topics). Returning empty results." }
                        emit(Result.success(emptyList()))
                    }
                } catch (e: Exception) {
                    logger.e(
                        { "❌ Indexed search failed for query '$query'" },
                        e,
                    )
                    emit(Result.success(emptyList()))
                }
            }.catch { e ->
                logger.e(
                    { "Search flow error" },
                    e,
                )
                emit(Result.success(emptyList()))
            }

        /**
         * Fetch topic details and save cover URL to database.
         */
        public suspend fun fetchAndSaveCover(topicId: String): Result<Unit> =
            try {
                // Re-use existing getTopicDetails which fetches HTML and parses it
                val result = getTopicDetails(topicId)

                // Extract success data to check coverUrl
                if (result.isSuccess) {
                    val details = result.getOrNull()
                    val coverUrl: String? = details?.coverUrl
                    if (!coverUrl.isNullOrBlank()) {
                        logger.d { "Updating cover for $topicId: $coverUrl" }
                        offlineSearchDao.updateCoverUrl(topicId, coverUrl)
                        Result.success(Unit)
                    } else {
                        logger.d { "No cover found for $topicId" }
                        Result.success(Unit)
                    }
                } else {
                    Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }

        private suspend fun fetchFromNetwork(
            query: String,
            forumIds: String?,
            operationId: String? = null,
        ): Result<List<RutrackerSearchResult>> {
            val opId = operationId ?: logger.startOperation("fetchFromNetwork")
            val networkStartTime = System.currentTimeMillis()
            val currentMirror = mirrorManager.getCurrentMirrorDomain()
            logger.log(opId, "Fetching from network: query='$query', forumIds=$forumIds")
            logger.log(opId, "Current mirror: $currentMirror")
            // === HTTP REQUEST LOGGING ===
            logger.w { "🔍 === SEARCH REQUEST ===" }
            logger.w { "Query: '$query'" }
            logger.w { "Mirror: $currentMirror" }
            if (forumIds != null) {
                logger.w { "Forum IDs: $forumIds" }
            }

            val response = api.searchTopics(query, forumIds)

            // Log request details
            val requestUrl = response.raw().request.url
            logger.w { "Request URL: $requestUrl" }

            // === HTTP RESPONSE LOGGING ===
            val networkDuration = System.currentTimeMillis() - networkStartTime
            logger.logWithDuration(opId, "Response received: HTTP ${response.code()} ${response.message()}", networkDuration)
            logger.log(opId, "Final URL: ${response.raw().request.url}", LogLevel.DEBUG)

            if (!response.isSuccessful) {
                val error =
                    when (response.code()) {
                        401 -> RuTrackerError.Unauthorized
                        403 -> RuTrackerError.Forbidden
                        404 -> RuTrackerError.NotFound
                        400 -> {
                            logger.w { "⚠️ HTTP 400 Bad Request for query '$query' - returning empty list instead of error" }
                            // For bad request, return empty list instead of error
                            // This prevents showing confusing error to user
                            if (operationId == null) logger.endOperation(opId, success = false)
                            return Result.success(emptyList())
                        }
                        else -> RuTrackerError.Unknown("HTTP ${response.code()}: ${response.message()}")
                    }
                logger.logError(opId, "Request failed: ${error.message}", error)
                if (operationId == null) logger.endOperation(opId, success = false)
                return Result.failure(error)
            }

            // Log important headers
            val headers = response.headers()
            logger.w { "Headers:" }
            listOf("content-type", "content-encoding", "content-length", "location", "set-cookie").forEach { name ->
                headers[name]?.let { value ->
                    if (name == "set-cookie") {
                        logger.w { "  $name: ${value.take(50)}..." }
                    } else {
                        logger.w { "  $name: $value" }
                    }
                }
            }

            // CRITICAL: Check Content-Encoding to see if data was compressed
            // Note: BrotliInterceptor removes "Content-Encoding: br" header after decompression,
            // so if we see it here, BrotliInterceptor didn't process it (shouldn't happen)
            val contentEncoding = headers["Content-Encoding"]
            logger.w { "🔍 Content-Encoding: $contentEncoding" }
            if (contentEncoding != null && contentEncoding.contains("br", ignoreCase = true)) {
                logger.w { "⚠️ WARNING: Content-Encoding still contains 'br' - BrotliInterceptor may not have processed it!" }
            }

            // CRITICAL: ResponseBody can only be read once!
            // Store bytes immediately and reuse
            // Note: OkHttp BrotliInterceptor automatically decompresses Brotli responses
            // After decompression, we get raw bytes that need to be decoded with Windows-1251
            val rawBytes = response.body()?.bytes() ?: ByteArray(0)
            logger.w { "📦 Response Size: ${rawBytes.size} bytes (should be decompressed if was Brotli)" }

            // Check if bytes look like compressed data (Brotli magic bytes)
            if (rawBytes.isNotEmpty()) {
                val firstBytes = rawBytes.take(4).toByteArray()
                val hexPreview = firstBytes.joinToString(" ") { "%02x".format(it) }
                logger.w { "🔍 First 4 bytes (hex): $hexPreview" }

                // Brotli magic bytes: 0x81, 0x1B (or similar)
                // Gzip magic bytes: 0x1F, 0x8B
                val looksLikeBrotli = rawBytes[0] == 0x81.toByte() && rawBytes[1] == 0x1B.toByte()
                val looksLikeGzip = rawBytes[0] == 0x1F.toByte() && rawBytes[1] == 0x8B.toByte()
                logger.w { "🔍 Looks like Brotli: $looksLikeBrotli, Gzip: $looksLikeGzip" }

                if (looksLikeBrotli || looksLikeGzip) {
                    logger.e { "⚠️ WARNING: Data appears to be compressed but OkHttp didn't decompress it!" }
                }

                // Check if bytes look like HTML (should start with < or whitespace before <)
                val startsWithHtml =
                    rawBytes.take(100).any {
                        it == '<'.code.toByte() ||
                            it == 0x20.toByte() ||
                            it == 0x09.toByte() ||
                            it == 0x0A.toByte()
                    }
                logger.w { "🔍 Looks like HTML (contains '<' or whitespace): $startsWithHtml" }

                // Try to see if it's valid Windows-1251 (Cyrillic range)
                val sample = rawBytes.take(1000)
                val hasCyrillicBytes = sample.any { it.toInt() and 0xFF in 0xC0..0xFF } // Windows-1251 Cyrillic range
                logger.w { "🔍 Has potential Cyrillic bytes (0xC0-0xFF): $hasCyrillicBytes" }
            }

            // HTML preview (first 300 chars) - try both UTF-8 and Windows-1251
            val htmlPreviewUtf8 =
                try {
                    String(rawBytes.take(300).toByteArray(), Charsets.UTF_8)
                        .replace(Regex("\\s+"), " ")
                } catch (e: Exception) {
                    "ERROR: ${e.message}"
                }
            val htmlPreviewCp1251 =
                try {
                    String(
                        rawBytes.take(300).toByteArray(),
                        java.nio.charset.Charset
                            .forName("windows-1251"),
                    ).replace(Regex("\\s+"), " ")
                } catch (e: Exception) {
                    "ERROR: ${e.message}"
                }
            logger.w { "📄 Response Start (UTF-8): $htmlPreviewUtf8..." }
            logger.w { "📄 Response Start (CP1251): $htmlPreviewCp1251..." }

            // Get Content-Type for encoding detection
            // Note: After BrotliInterceptor decompression, bytes are ready for charset decoding
            val contentType = response.headers()["Content-Type"]
            // Parse with encoding detection (RutrackerSimpleDecoder will decode bytes with Windows-1251)
            val parsingResult = parser.parseSearchResultsWithEncoding(rawBytes, contentType)

            return when (parsingResult) {
                is ParsingResult.Success -> {
                    val dtoResults = parsingResult.data
                    logger.d { "📊 Parsing success: ${dtoResults.size} DTO results for query '$query'" }
                    val domainResults = dtoResults.toDomain()
                    val filteredCount = dtoResults.size - domainResults.size
                    if (filteredCount > 0) {
                        logger.w {
                            "⚠️ Filtered out $filteredCount invalid results during toDomain() conversion"
                        }
                    }
                    logger.d {
                        "✅ Final domain results: ${domainResults.size} valid results " +
                            "(${dtoResults.size} DTO → ${domainResults.size} domain)"
                    }
                    handleSuccess(query, forumIds, dtoResults) // Cache DTO models
                    val resultCount = domainResults.size
                    logger.logSuccess(
                        opId,
                        "Parsed $resultCount results (${dtoResults.size} DTO, $resultCount valid domain)",
                        networkDuration,
                    )
                    if (operationId == null) logger.endOperation(opId, success = true, "Found $resultCount results")
                    Result.success(domainResults)
                }
                is ParsingResult.PartialSuccess -> {
                    val dtoResults = parsingResult.data
                    logger.w {
                        "📊 Partial parsing: ${dtoResults.size} DTO results, ${parsingResult.errors.size} errors for query '$query'"
                    }
                    val domainResults = dtoResults.toDomain()
                    val filteredCount = dtoResults.size - domainResults.size
                    if (filteredCount > 0) {
                        logger.w {
                            "⚠️ Filtered out $filteredCount invalid results during toDomain() conversion"
                        }
                    }
                    logger.d {
                        "✅ Final domain results: ${domainResults.size} valid results " +
                            "(${dtoResults.size} DTO → ${domainResults.size} domain)"
                    }
                    handleSuccess(query, forumIds, dtoResults) // Cache DTO models
                    val resultCount = domainResults.size
                    logger.logWarning(
                        opId,
                        "Partial success: parsed $resultCount results with ${parsingResult.errors.size} errors (${dtoResults.size} DTO, $resultCount valid domain)",
                    )
                    if (operationId == null) logger.endOperation(opId, success = true, "Found $resultCount results (partial)")
                    Result.success(domainResults)
                }
                is ParsingResult.Failure -> {
                    val errorMessage = parsingResult.errors.firstOrNull()?.reason ?: "Parsing failed"
                    logger.e {
                        "❌ Parsing failed for query '$query': ${parsingResult.errors.size} errors"
                    }
                    parsingResult.errors.take(5).forEachIndexed { index, error ->
                        logger.e {
                            "  Error[$index]: ${error.field} - ${error.reason} " +
                                "(severity: ${error.severity})"
                        }
                    }
                    if (parsingResult.errors.size > 5) {
                        logger.e { "  ... and ${parsingResult.errors.size - 5} more errors" }
                    }

                    // Check if it's a bad request or validation error
                    val isBadRequest =
                        parsingResult.errors.any {
                            it.reason.contains("Bad request", ignoreCase = true) ||
                                it.reason.contains("BadRequest", ignoreCase = true) ||
                                it.reason.contains("Content validation failed", ignoreCase = true)
                        }

                    if (isBadRequest) {
                        logger.w { "⚠️ Bad request or validation error detected in parsing result for query '$query'" }
                        logger.w { "   Returning empty list instead of error to prevent user confusion" }
                        logger.w { "   Error details: $errorMessage" }
                        // Return empty list instead of error for bad request
                        // This prevents showing "bad request" error to user when it's just an empty/invalid result
                        if (operationId == null) logger.endOperation(opId, success = false, "Bad request - returning empty")
                        Result.success(emptyList())
                    } else {
                        val error = RuTrackerError.ParsingError(errorMessage)
                        logger.logError(opId, "Parsing failed: $errorMessage", error)
                        if (operationId == null) logger.endOperation(opId, success = false, errorMessage)
                        Result.failure(error)
                    }
                }
            }
        }

        private suspend fun handleSuccess(
            query: String,
            forumIds: String?,
            results: List<SearchResult>,
        ) {
            // Memory Cache
            searchCache.put(query, forumIds, results)

            // DB Persistence (Only for generic searches without unexpected filters)
            if (forumIds == null) {
                try {
                    val entities = results.map { it.toCachedTopicEntity() }
                    val dbSaveStartTime = System.currentTimeMillis()
                    offlineSearchDao.saveSearchResults(query, entities)
                    val dbSaveDuration = System.currentTimeMillis() - dbSaveStartTime
                    logger.d { "💾 Saved ${entities.size} results to DB cache (query: '$query', ${dbSaveDuration}ms)" }
                } catch (e: Exception) {
                    logger.e(
                        { "Failed to save to DB" },
                        e,
                    )
                }
            }
        }

        /**
         * Get topic details.
         *
         * @param topicId Topic ID
         * @return Result with topic details or error
         */
        public suspend fun getTopicDetails(topicId: String): Result<RutrackerTopicDetails> =
            withContext(Dispatchers.IO) {
                logger.withOperation("getTopicDetails") { operationId ->
                    try {
                        // Validate input
                        if (topicId.isBlank()) {
                            logger.logError(operationId, "Topic ID is blank", IllegalArgumentException("Topic ID cannot be blank"))
                            return@withOperation Result.failure(IllegalArgumentException("Topic ID cannot be blank"))
                        }

                        logger.log(operationId, "Fetching topic details: $topicId")

                        val response = api.getTopicDetails(topicId)

                        if (!response.isSuccessful) {
                            logger.logWarning(operationId, "Topic details failed: HTTP ${response.code()}")
                            val rutrackerError =
                                when (response.code()) {
                                    401 -> RuTrackerError.Unauthorized
                                    403 -> RuTrackerError.Forbidden
                                    404 -> RuTrackerError.NotFound
                                    400 -> RuTrackerError.BadRequest
                                    else -> RuTrackerError.Unknown("HTTP ${response.code()}: ${response.message()}")
                                }
                            logger.logError(operationId, "Failed to fetch topic details", rutrackerError)
                            Result.failure(rutrackerError)
                        } else {
                            // Get raw bytes (OkHttp BrotliInterceptor automatically decompresses Brotli)
                            val rawBytes = response.body()?.bytes() ?: byteArrayOf()
                            if (rawBytes.isEmpty()) {
                                logger.logError(operationId, "Empty response body", IllegalArgumentException("Response body is empty"))
                                return@withOperation Result.failure(IllegalArgumentException("Response body is empty"))
                            }

                            val html = String(rawBytes, charset("windows-1251"))
                            val dtoDetails = parser.parseTopicDetails(html, topicId)

                            if (dtoDetails != null) {
                                // Map DTO to domain model with validation
                                val domainDetails = dtoDetails.toDomain()
                                if (domainDetails.isValid()) {
                                    logger.logSuccess(operationId, "Topic details parsed and validated: ${domainDetails.title}")
                                    Result.success(domainDetails)
                                } else {
                                    logger.logWarning(operationId, "Topic details parsed but failed validation")
                                    Result.failure(RuTrackerError.ParsingError("Topic details failed validation"))
                                }
                            } else {
                                logger.logWarning(operationId, "Failed to parse topic details")
                                Result.failure(RuTrackerError.ParsingError("Failed to parse topic details"))
                            }
                        }
                    } catch (e: java.net.UnknownHostException) {
                        logger.logError(operationId, "Network error - unknown host", e)
                        Result.failure(RuTrackerError.NoConnection)
                    } catch (e: java.io.IOException) {
                        logger.logError(operationId, "Network I/O error", e)
                        Result.failure(RuTrackerError.NoConnection)
                    } catch (e: Exception) {
                        logger.logError(operationId, "Topic details error", e)
                        Result.failure(RuTrackerError.Unknown(e.message))
                    }
                }
            }

        /**
         * Get audiobook categories.
         *
         * @return Result with categories or error
         */
        public suspend fun getCategories(): Result<List<AudiobookCategory>> =
            withContext(Dispatchers.IO) {
                logger.withOperation("getCategories") { operationId ->
                    try {
                        logger.log(operationId, "Fetching categories")

                        val response = api.getIndex()

                        if (!response.isSuccessful) {
                            logger.logWarning(operationId, "Categories failed: HTTP ${response.code()}")
                            val error =
                                when (response.code()) {
                                    401 -> RuTrackerError.Unauthorized
                                    403 -> RuTrackerError.Forbidden
                                    404 -> RuTrackerError.NotFound
                                    400 -> RuTrackerError.BadRequest
                                    else -> RuTrackerError.Unknown("HTTP ${response.code()}: ${response.message()}")
                                }
                            logger.logError(operationId, "Failed to fetch categories", error)
                            Result.failure(error)
                        } else {
                            // Get raw bytes (OkHttp BrotliInterceptor automatically decompresses Brotli)
                            val rawBytes = response.body()?.bytes() ?: ByteArray(0)
                            // Decode HTML (CategoryParser expects decoded string)
                            val html = String(rawBytes, Charsets.UTF_8)

                            val parsingResult = categoryParser.parseCategories(html)

                            when (parsingResult) {
                                is ParsingResult.Success -> {
                                    logger.logSuccess(operationId, "Categories parsed: ${parsingResult.data.size}")
                                    Result.success(parsingResult.data)
                                }
                                is ParsingResult.PartialSuccess -> {
                                    logger.logWarning(
                                        operationId,
                                        "Categories partial: ${parsingResult.data.size} categories with ${parsingResult.errors.size} errors",
                                    )
                                    Result.success(parsingResult.data)
                                }
                                is ParsingResult.Failure -> {
                                    val errorMessage = parsingResult.errors.firstOrNull()?.reason ?: "Failed to parse categories"
                                    logger.logError(
                                        operationId,
                                        "Categories parsing failed: $errorMessage",
                                        RuTrackerError.ParsingError(errorMessage),
                                    )
                                    Result.failure(RuTrackerError.ParsingError(errorMessage))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.logError(operationId, "Categories error", e)
                        Result.failure(RuTrackerError.Unknown(e.message))
                    }
                }
            }

        /**
         * Check if user is authenticated.
         *
         * @return true if authenticated
         */
        public suspend fun isAuthenticated(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val response = api.getProfile()
                    response.isSuccessful
                } catch (e: Exception) {
                    logger.w(
                        { "Auth check failed" },
                        e,
                    )
                    // Return false for any error - let caller handle specific error types if needed
                    false
                }
            }

        /**
         * Get search cache statistics.
         */
        public fun getCacheStatistics(): RutrackerSearchCache.CacheStatistics = searchCache.getStatistics()

        /**
         * Clear search cache.
         */
        public fun clearSearchCache() {
            searchCache.clear()
        }
    }
