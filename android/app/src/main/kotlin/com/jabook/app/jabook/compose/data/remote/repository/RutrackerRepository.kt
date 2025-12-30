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

package com.jabook.app.jabook.compose.data.remote.repository

import android.util.Log
import com.jabook.app.jabook.compose.core.util.StructuredLogger
import com.jabook.app.jabook.compose.data.auth.RutrackerAuthService
import com.jabook.app.jabook.compose.data.cache.RutrackerSearchCache
import com.jabook.app.jabook.compose.data.local.dao.OfflineSearchDao
import com.jabook.app.jabook.compose.data.local.entity.toCachedTopicEntity
import com.jabook.app.jabook.compose.data.local.entity.toSearchResult
import com.jabook.app.jabook.compose.data.remote.RuTrackerError
import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.data.remote.mapper.toDomain
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
class RutrackerRepository
    @Inject
    constructor(
        private val api: RutrackerApi,
        private val parser: RutrackerParser,
        private val categoryParser: CategoryParser,
        private val authService: RutrackerAuthService,
        private val searchCache: RutrackerSearchCache,
        private val offlineSearchDao: OfflineSearchDao,
    ) {
        companion object {
            private const val TAG = "RutrackerRepository"
        }

        private val logger = StructuredLogger(TAG)

        /**
         * Search for audiobooks on RuTracker.
         *
         * Uses RutrackerSimpleDecoder (matching Flutter implementation) and cascading selectors for robust parsing.
         *
         * @param query Search query
         * @param forumIds Optional forum IDs to search in (e.g., "2388,2389")
         * @return Result with search results or error
         */
        suspend fun searchAudiobooks(
            query: String,
            forumIds: String? = null,
        ): Result<List<RutrackerSearchResult>> =
            withContext(Dispatchers.IO) {
                logger.withOperation("searchAudiobooks") { operationId ->
                    try {
                        logger.log(operationId, "Search started: query='$query', forumIds=$forumIds")

                        // 1. Try Memory Cache
                        val memCached = searchCache.get(query, forumIds)
                        if (memCached != null) {
                            val domainResults = memCached.toDomain()
                            logger.logSuccess(operationId, "Found ${domainResults.size} results from memory cache")
                            Result.success(domainResults)
                        } else {
                            // 2. Try Network
                            val networkResult = fetchFromNetwork(query, forumIds, operationId)

                            if (networkResult.isSuccess) {
                                val count = networkResult.getOrNull()?.size ?: 0
                                logger.logSuccess(operationId, "Found $count results from network")
                                networkResult
                            } else {
                                // 3. Fallback to Database (Offline Mode)
                                // Only for general search (no specific forum filter usually in DB mapping unless we handle it)
                                if (forumIds == null) {
                                    val entities = offlineSearchDao.getResultsForQuery(query)
                                    val dbList = entities.map { it.toSearchResult() }
                                    val domainResults = dbList.toDomain()

                                    if (domainResults.isNotEmpty()) {
                                        logger.log(
                                            operationId,
                                            "Network failed, returned ${domainResults.size} results from DB (${dbList.size} DTO, ${domainResults.size} valid domain)",
                                            StructuredLogger.LogLevel.INFO,
                                        )
                                        Result.success(domainResults)
                                    } else {
                                        // Return network error if DB is empty
                                        networkResult
                                    }
                                } else {
                                    // Return network error if DB is empty
                                    networkResult
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.logError(operationId, "Search failed", e)
                        Result.failure(e)
                    }
                }
            }

        /**
         * Fast search in indexed topics (offline, no network required).
         *
         * @param query Search query
         * @param limit Maximum number of results
         * @return List of search results from index
         */
        suspend fun searchIndexedTopics(
            query: String,
            limit: Int = 100,
        ): List<RutrackerSearchResult> =
            withContext(Dispatchers.IO) {
                try {
                    val entities = offlineSearchDao.searchIndexedTopics(query, limit)
                    val dtoResults = entities.map { it.toSearchResult() }
                    dtoResults.toDomain()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to search indexed topics", e)
                    emptyList()
                }
            }

        /**
         * Search with Index-First strategy: Always try index first, then network if needed.
         *
         * Priority:
         * 1. Indexed topics (fast, offline) - MANDATORY for audiobooks
         * 2. Network search (only if index doesn't exist or is empty)
         *
         * For audiobooks, if index exists and has any results, we use ONLY index (no network).
         * This ensures:
         * - Fast, offline search
         * - Only audiobook topics (from indexed forums)
         * - No unwanted topics from other forums
         */
        fun searchAudiobooksFlow(
            query: String,
            forumIds: String? = null,
        ): Flow<Result<List<RutrackerSearchResult>>> =
            flow {
                Log.i(TAG, "🔍 Search started: query='$query', forumIds=$forumIds")

                // 1. ALWAYS try indexed search first (mandatory for audiobooks)
                if (forumIds == null || forumIds == RutrackerApi.AUDIOBOOKS_FORUM_IDS) {
                    try {
                        // Check if index exists and has data
                        val indexSize = offlineSearchDao.getTopicCount()
                        if (indexSize > 0) {
                            val indexSearchStartTime = System.currentTimeMillis()
                            val indexedResults = searchIndexedTopics(query, limit = 200) // Increased limit for better coverage
                            val indexSearchDuration = System.currentTimeMillis() - indexSearchStartTime

                            if (indexedResults.isNotEmpty()) {
                                Log.i(
                                    TAG,
                                    "✅ Found ${indexedResults.size} results from index (query: '$query', ${indexSearchDuration}ms, index size: $indexSize)",
                                )
                                emit(Result.success(indexedResults))
                                // For audiobooks with existing index, ALWAYS use index results only
                                // This ensures we only get audiobook topics, no unwanted results
                                return@flow
                            } else {
                                Log.i(TAG, "⚠️ Index exists ($indexSize topics) but no results for query '$query', returning empty")
                                // Index exists but no matches - return empty (don't search network)
                                // This prevents "bad request" errors when index exists
                                emit(Result.success(emptyList()))
                                return@flow
                            }
                        } else {
                            Log.i(TAG, "⚠️ Index is empty ($indexSize topics), will try network search if needed")
                            // Index is empty - proceed to network search only if no DB cache
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Indexed search failed for query '$query'", e)
                        // If index exists but search failed, return empty instead of network search
                        // This prevents "bad request" errors when index exists but has issues
                        val indexSize =
                            try {
                                offlineSearchDao.getTopicCount()
                            } catch (ex: Exception) {
                                0
                            }
                        if (indexSize > 0) {
                            Log.w(TAG, "Index exists ($indexSize topics) but search failed, returning empty to avoid network bad request")
                            emit(Result.success(emptyList()))
                            return@flow
                        }
                        // Only proceed to network if index doesn't exist
                        Log.w(TAG, "Index doesn't exist or is empty, will try network search")
                    }
                }

                // 2. Check Memory Cache (quick lookup)
                val memCached = searchCache.get(query, forumIds)
                if (memCached != null) {
                    val domainResults = memCached.toDomain()
                    emit(Result.success(domainResults))
                    return@flow
                }

                // 3. Check DB Cache (previous searches)
                if (forumIds == null) {
                    try {
                        val dbReadStartTime = System.currentTimeMillis()
                        val entities = offlineSearchDao.getResultsForQuery(query)
                        val dbReadDuration = System.currentTimeMillis() - dbReadStartTime
                        if (entities.isNotEmpty()) {
                            val dbResults = entities.map { it.toSearchResult() }
                            val domainResults = dbResults.toDomain()
                            Log.i(
                                TAG,
                                "💾 Found ${domainResults.size} results from DB cache (query: '$query', ${dbReadDuration}ms, ${dbResults.size} DTO, ${domainResults.size} valid domain)",
                            )
                            emit(Result.success(domainResults))
                            return@flow
                        } else {
                            Log.d(TAG, "💾 No results in DB cache for query: '$query' (${dbReadDuration}ms)")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "❌ DB read failed for query: '$query'", e)
                    }
                }

                // 4. Fetch from Network (only if index/DB don't have results)
                val networkResult = fetchFromNetwork(query, forumIds)
                emit(networkResult)
            }.catch { e ->
                Log.e(TAG, "Search flow error", e)
                emit(Result.failure(e))
            }

        private suspend fun fetchFromNetwork(
            query: String,
            forumIds: String?,
            operationId: String? = null,
        ): Result<List<RutrackerSearchResult>> {
            val opId = operationId ?: logger.startOperation("fetchFromNetwork")
            val networkStartTime = System.currentTimeMillis()
            logger.log(opId, "Fetching from network: query='$query', forumIds=$forumIds")
            // === HTTP REQUEST LOGGING ===
            Log.w(TAG, "🔍 === SEARCH REQUEST ===")
            Log.w(TAG, "Query: '$query'")
            if (forumIds != null) {
                Log.w(TAG, "Forum IDs: $forumIds")
            }

            val response = api.searchTopics(query, forumIds)

            // Log request details
            val requestUrl = response.raw().request.url
            Log.w(TAG, "Request URL: $requestUrl")

            // === HTTP RESPONSE LOGGING ===
            val networkDuration = System.currentTimeMillis() - networkStartTime
            logger.logWithDuration(opId, "Response received: HTTP ${response.code()} ${response.message()}", networkDuration)
            logger.log(opId, "Final URL: ${response.raw().request.url}", StructuredLogger.LogLevel.DEBUG)

            if (!response.isSuccessful) {
                val error =
                    when (response.code()) {
                        401 -> RuTrackerError.Unauthorized
                        403 -> RuTrackerError.Forbidden
                        404 -> RuTrackerError.NotFound
                        400 -> {
                            Log.w(TAG, "⚠️ HTTP 400 Bad Request for query '$query' - returning empty list instead of error")
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
            Log.w(TAG, "Headers:")
            listOf("content-type", "content-encoding", "content-length", "location", "set-cookie").forEach { name ->
                headers[name]?.let { value ->
                    if (name == "set-cookie") {
                        Log.w(TAG, "  $name: ${value.take(50)}...")
                    } else {
                        Log.w(TAG, "  $name: $value")
                    }
                }
            }

            // CRITICAL: Check Content-Encoding to see if data was compressed
            // Note: BrotliInterceptor removes "Content-Encoding: br" header after decompression,
            // so if we see it here, BrotliInterceptor didn't process it (shouldn't happen)
            val contentEncoding = headers["Content-Encoding"]
            Log.w(TAG, "🔍 Content-Encoding: $contentEncoding")
            if (contentEncoding != null && contentEncoding.contains("br", ignoreCase = true)) {
                Log.w(TAG, "⚠️ WARNING: Content-Encoding still contains 'br' - BrotliInterceptor may not have processed it!")
            }

            // CRITICAL: ResponseBody can only be read once!
            // Store bytes immediately and reuse
            // Note: OkHttp BrotliInterceptor automatically decompresses Brotli responses
            // After decompression, we get raw bytes that need to be decoded with Windows-1251
            val rawBytes = response.body()?.bytes() ?: ByteArray(0)
            Log.w(TAG, "📦 Response Size: ${rawBytes.size} bytes (should be decompressed if was Brotli)")

            // Check if bytes look like compressed data (Brotli magic bytes)
            if (rawBytes.isNotEmpty()) {
                val firstBytes = rawBytes.take(4).toByteArray()
                val hexPreview = firstBytes.joinToString(" ") { "%02x".format(it) }
                Log.w(TAG, "🔍 First 4 bytes (hex): $hexPreview")

                // Brotli magic bytes: 0x81, 0x1B (or similar)
                // Gzip magic bytes: 0x1F, 0x8B
                val looksLikeBrotli = rawBytes[0] == 0x81.toByte() && rawBytes[1] == 0x1B.toByte()
                val looksLikeGzip = rawBytes[0] == 0x1F.toByte() && rawBytes[1] == 0x8B.toByte()
                Log.w(TAG, "🔍 Looks like Brotli: $looksLikeBrotli, Gzip: $looksLikeGzip")

                if (looksLikeBrotli || looksLikeGzip) {
                    Log.e(TAG, "⚠️ WARNING: Data appears to be compressed but OkHttp didn't decompress it!")
                }

                // Check if bytes look like HTML (should start with < or whitespace before <)
                val startsWithHtml =
                    rawBytes.take(100).any {
                        it == '<'.code.toByte() ||
                            it == 0x20.toByte() ||
                            it == 0x09.toByte() ||
                            it == 0x0A.toByte()
                    }
                Log.w(TAG, "🔍 Looks like HTML (contains '<' or whitespace): $startsWithHtml")

                // Try to see if it's valid Windows-1251 (Cyrillic range)
                val sample = rawBytes.take(1000)
                val hasCyrillicBytes = sample.any { it.toInt() and 0xFF in 0xC0..0xFF } // Windows-1251 Cyrillic range
                Log.w(TAG, "🔍 Has potential Cyrillic bytes (0xC0-0xFF): $hasCyrillicBytes")
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
            Log.w(TAG, "📄 Response Start (UTF-8): $htmlPreviewUtf8...")
            Log.w(TAG, "📄 Response Start (CP1251): $htmlPreviewCp1251...")

            // Get Content-Type for encoding detection
            // Note: After BrotliInterceptor decompression, bytes are ready for charset decoding
            val contentType = response.headers()["Content-Type"]
            // Parse with encoding detection (RutrackerSimpleDecoder will decode bytes with Windows-1251)
            val parsingResult = parser.parseSearchResultsWithEncoding(rawBytes, contentType)

            return when (parsingResult) {
                is ParsingResult.Success -> {
                    val dtoResults = parsingResult.data
                    Log.d(TAG, "📊 Parsing success: ${dtoResults.size} DTO results for query '$query'")
                    val domainResults = dtoResults.toDomain()
                    val filteredCount = dtoResults.size - domainResults.size
                    if (filteredCount > 0) {
                        Log.w(
                            TAG,
                            "⚠️ Filtered out $filteredCount invalid results during toDomain() conversion",
                        )
                    }
                    Log.d(
                        TAG,
                        "✅ Final domain results: ${domainResults.size} valid results " +
                            "(${dtoResults.size} DTO → ${domainResults.size} domain)",
                    )
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
                    Log.w(
                        TAG,
                        "📊 Partial parsing: ${dtoResults.size} DTO results, ${parsingResult.errors.size} errors for query '$query'",
                    )
                    val domainResults = dtoResults.toDomain()
                    val filteredCount = dtoResults.size - domainResults.size
                    if (filteredCount > 0) {
                        Log.w(
                            TAG,
                            "⚠️ Filtered out $filteredCount invalid results during toDomain() conversion",
                        )
                    }
                    Log.d(
                        TAG,
                        "✅ Final domain results: ${domainResults.size} valid results " +
                            "(${dtoResults.size} DTO → ${domainResults.size} domain)",
                    )
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
                    Log.e(
                        TAG,
                        "❌ Parsing failed for query '$query': ${parsingResult.errors.size} errors",
                    )
                    parsingResult.errors.take(5).forEachIndexed { index, error ->
                        Log.e(
                            TAG,
                            "  Error[$index]: ${error.field} - ${error.reason} " +
                                "(severity: ${error.severity})",
                        )
                    }
                    if (parsingResult.errors.size > 5) {
                        Log.e(TAG, "  ... and ${parsingResult.errors.size - 5} more errors")
                    }

                    // Check if it's a bad request or validation error
                    val isBadRequest =
                        parsingResult.errors.any {
                            it.reason.contains("Bad request", ignoreCase = true) ||
                                it.reason.contains("BadRequest", ignoreCase = true) ||
                                it.reason.contains("Content validation failed", ignoreCase = true)
                        }

                    if (isBadRequest) {
                        Log.w(TAG, "⚠️ Bad request or validation error detected in parsing result for query '$query'")
                        Log.w(TAG, "   Returning empty list instead of error to prevent user confusion")
                        Log.w(TAG, "   Error details: $errorMessage")
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
                    Log.d(TAG, "💾 Saved ${entities.size} results to DB cache (query: '$query', ${dbSaveDuration}ms)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save to DB", e)
                }
            }
        }

        /**
         * Get topic details.
         *
         * @param topicId Topic ID
         * @return Result with topic details or error
         */
        suspend fun getTopicDetails(topicId: String): Result<RutrackerTopicDetails> =
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
        suspend fun getCategories(): Result<List<AudiobookCategory>> =
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
        suspend fun isAuthenticated(): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val response = api.getProfile()
                    response.isSuccessful
                } catch (e: Exception) {
                    Log.w(TAG, "Auth check failed", e)
                    // Return false for any error - let caller handle specific error types if needed
                    false
                }
            }

        /**
         * Get search cache statistics.
         */
        fun getCacheStatistics(): RutrackerSearchCache.CacheStatistics = searchCache.getStatistics()

        /**
         * Clear search cache.
         */
        fun clearSearchCache() {
            searchCache.clear()
        }
    }
