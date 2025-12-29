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
import com.jabook.app.jabook.compose.data.auth.RutrackerAuthService
import com.jabook.app.jabook.compose.data.cache.RutrackerSearchCache
import com.jabook.app.jabook.compose.data.local.dao.OfflineSearchDao
import com.jabook.app.jabook.compose.data.local.entity.toCachedTopicEntity
import com.jabook.app.jabook.compose.data.local.entity.toSearchResult
import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.data.remote.model.AudiobookCategory
import com.jabook.app.jabook.compose.data.remote.model.SearchResult
import com.jabook.app.jabook.compose.data.remote.model.TopicDetails
import com.jabook.app.jabook.compose.data.remote.parser.CategoryParser
import com.jabook.app.jabook.compose.data.remote.parser.ParsingResult
import com.jabook.app.jabook.compose.data.remote.parser.RutrackerParser
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
        ): Result<List<SearchResult>> =
            withContext(Dispatchers.IO) {
                try {
                    // 1. Try Memory Cache
                    val memCached = searchCache.get(query, forumIds)
                    if (memCached != null) {
                        return@withContext Result.success(memCached)
                    }

                    // 2. Try Network
                    val networkResult = fetchFromNetwork(query, forumIds)

                    if (networkResult.isSuccess) {
                        return@withContext networkResult
                    }

                    // 3. Fallback to Database (Offline Mode)
                    // Only for general search (no specific forum filter usually in DB mapping unless we handle it)
                    if (forumIds == null) {
                        val entities = offlineSearchDao.getResultsForQuery(query)
                        val dbList = entities.map { it.toSearchResult() }

                        if (dbList.isNotEmpty()) {
                            Log.i(TAG, "Network failed, returned ${dbList.size} results from DB")
                            return@withContext Result.success(dbList)
                        }
                    }

                    // Return network error if DB is empty
                    networkResult
                } catch (e: Exception) {
                    Result.failure(e)
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
        ): List<SearchResult> =
            withContext(Dispatchers.IO) {
                try {
                    val entities = offlineSearchDao.searchIndexedTopics(query, limit)
                    entities.map { it.toSearchResult() }
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
         * 2. Network search (only if index doesn't have enough results or query is new)
         */
        fun searchAudiobooksFlow(
            query: String,
            forumIds: String? = null,
        ): Flow<Result<List<SearchResult>>> =
            flow {
                Log.d(TAG, "Starting search flow for: $query")

                // 1. ALWAYS try indexed search first (mandatory for audiobooks)
                if (forumIds == null || forumIds == RutrackerApi.AUDIOBOOKS_FORUM_IDS) {
                    try {
                        val indexedResults = searchIndexedTopics(query, limit = 100)
                        if (indexedResults.isNotEmpty()) {
                            Log.d(TAG, "Found ${indexedResults.size} results from index")
                            emit(Result.success(indexedResults))

                            // If we have good results from index, we're done
                            // Only fetch from network if index has very few results (< 5)
                            if (indexedResults.size >= 5) {
                                return@flow
                            }
                            Log.d(TAG, "Index has only ${indexedResults.size} results, fetching from network for more")
                        } else {
                            Log.d(TAG, "No results in index, fetching from network")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Indexed search failed, falling back to network", e)
                    }
                }

                // 2. Check Memory Cache (quick lookup)
                val memCached = searchCache.get(query, forumIds)
                if (memCached != null) {
                    emit(Result.success(memCached))
                    return@flow
                }

                // 3. Check DB Cache (previous searches)
                if (forumIds == null) {
                    try {
                        val entities = offlineSearchDao.getResultsForQuery(query)
                        if (entities.isNotEmpty()) {
                            val dbResults = entities.map { it.toSearchResult() }
                            Log.d(TAG, "Found ${dbResults.size} results from DB cache")
                            emit(Result.success(dbResults))
                            return@flow
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "DB read failed", e)
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
        ): Result<List<SearchResult>> {
            Log.w(TAG, "🔍 fetchFromNetwork called: query=$query, forumIds=$forumIds")
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
            Log.w(TAG, "📥 === SEARCH RESPONSE ===")
            Log.w(TAG, "Status: ${response.code()} ${response.message()}")
            Log.w(TAG, "Final URL: ${response.raw().request.url}") // Detect redirects

            if (!response.isSuccessful) {
                Log.e(TAG, "❌ Request failed: HTTP ${response.code()}: ${response.message()}")
                return Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
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
                    handleSuccess(query, forumIds, parsingResult.data)
                    Result.success(parsingResult.data)
                }
                is ParsingResult.PartialSuccess -> {
                    handleSuccess(query, forumIds, parsingResult.data)
                    Result.success(parsingResult.data)
                }
                is ParsingResult.Failure -> {
                    Result.failure(Exception(parsingResult.errors.firstOrNull()?.reason ?: "Parsing failed"))
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
                    offlineSearchDao.saveSearchResults(query, entities)
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
        suspend fun getTopicDetails(topicId: String): Result<TopicDetails> =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "Fetching topic details: $topicId")

                    val response = api.getTopicDetails(topicId)

                    if (!response.isSuccessful) {
                        Log.w(TAG, "Topic details failed: HTTP ${response.code()}")
                        return@withContext Result.failure(
                            Exception("HTTP ${response.code()}: ${response.message()}"),
                        )
                    }

                    // Get raw bytes (OkHttp BrotliInterceptor automatically decompresses Brotli)
                    val rawBytes = response.body()?.bytes() ?: byteArrayOf()
                    val html = String(rawBytes, charset("windows-1251"))
                    val details = parser.parseTopicDetails(html, topicId)

                    if (details != null) {
                        Log.i(TAG, "Topic details parsed: ${details.title}")
                        Result.success(details)
                    } else {
                        Log.w(TAG, "Failed to parse topic details")
                        Result.failure(Exception("Failed to parse topic details"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Topic details error", e)
                    Result.failure(e)
                }
            }

        /**
         * Get audiobook categories.
         *
         * @return Result with categories or error
         */
        suspend fun getCategories(): Result<List<AudiobookCategory>> =
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "Fetching categories")

                    val response = api.getIndex()

                    if (!response.isSuccessful) {
                        Log.w(TAG, "Categories failed: HTTP ${response.code()}")
                        return@withContext Result.failure(
                            Exception("HTTP ${response.code()}: ${response.message()}"),
                        )
                    }

                    // Get raw bytes (OkHttp BrotliInterceptor automatically decompresses Brotli)
                    val rawBytes = response.body()?.bytes() ?: ByteArray(0)
                    // Decode HTML (CategoryParser expects decoded string)
                    val html = String(rawBytes, Charsets.UTF_8)

                    val parsingResult = categoryParser.parseCategories(html)

                    when (parsingResult) {
                        is ParsingResult.Success -> {
                            Log.i(TAG, "Categories parsed: ${parsingResult.data.size}")
                            Result.success(parsingResult.data)
                        }
                        is ParsingResult.PartialSuccess -> {
                            Log.w(TAG, "Categories partial: ${parsingResult.data.size} categories")
                            Result.success(parsingResult.data)
                        }
                        is ParsingResult.Failure -> {
                            Log.e(TAG, "Categories parsing failed")
                            Result.failure(Exception("Failed to parse categories"))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Categories error", e)
                    Result.failure(e)
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
