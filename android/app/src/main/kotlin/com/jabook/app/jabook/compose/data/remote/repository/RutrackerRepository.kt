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
         * Uses DefensiveEncodingHandler and cascading selectors for robust parsing.
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
         * Search with Optimistic UI: Emits Cached results immediately, then Network results.
         */
        fun searchAudiobooksFlow(
            query: String,
            forumIds: String? = null,
        ): Flow<Result<List<SearchResult>>> =
            flow {
                Log.d(TAG, "Starting search flow for: $query")

                // 1. Emit DB Cache immediately (Optimistic UI)
                // (Logic simplified to avoid duplication with below re-implementation attempt in original code)

                // A. Check Memory Cache
                val memCached = searchCache.get(query, forumIds)
                if (memCached != null) {
                    emit(Result.success(memCached))
                } else {
                    // B. Emit DB Cache (if not in mem)
                    if (forumIds == null) {
                        try {
                            val entities = offlineSearchDao.getResultsForQuery(query)
                            if (entities.isNotEmpty()) {
                                emit(Result.success(entities.map { it.toSearchResult() }))
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "DB read failed", e)
                        }
                    }
                }

                // C. Fetch Network
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
            Log.d(TAG, "Searching API for: $query")
            val response = api.searchTopics(query, forumIds)

            if (!response.isSuccessful) {
                return Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }

            val rawBytes = response.body()?.toByteArray() ?: ByteArray(0)
            val contentType = response.headers()["Content-Type"]
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

                    val html = response.body() ?: ""
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
