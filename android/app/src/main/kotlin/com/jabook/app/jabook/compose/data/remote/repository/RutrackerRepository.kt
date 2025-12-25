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
import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.data.remote.model.AudiobookCategory
import com.jabook.app.jabook.compose.data.remote.model.SearchResult
import com.jabook.app.jabook.compose.data.remote.model.TopicDetails
import com.jabook.app.jabook.compose.data.remote.parser.CategoryParser
import com.jabook.app.jabook.compose.data.remote.parser.ParsingResult
import com.jabook.app.jabook.compose.data.remote.parser.RutrackerParser
import kotlinx.coroutines.Dispatchers
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
                    Log.d(TAG, "Searching for: $query, forumIds: $forumIds")

                    val response = api.searchTopics(query, forumIds)

                    if (!response.isSuccessful) {
                        Log.w(TAG, "Search failed: HTTP ${response.code()}")
                        return@withContext Result.failure(
                            Exception("HTTP ${response.code()}: ${response.message()}"),
                        )
                    }

                    val rawBytes = response.body()?.toByteArray() ?: ByteArray(0)
                    val contentType = response.headers()["Content-Type"]

                    // Use encoding-aware parsing
                    val parsingResult = parser.parseSearchResultsWithEncoding(rawBytes, contentType)

                    when (parsingResult) {
                        is ParsingResult.Success -> {
                            Log.i(TAG, "Search successful: ${parsingResult.data.size} results")
                            Result.success(parsingResult.data)
                        }
                        is ParsingResult.PartialSuccess -> {
                            Log.w(
                                TAG,
                                "Search partial success: ${parsingResult.data.size} results, ${parsingResult.errors.size} errors",
                            )
                            // Return partial results (better than nothing)
                            Result.success(parsingResult.data)
                        }
                        is ParsingResult.Failure -> {
                            Log.e(TAG, "Search parsing failed: ${parsingResult.errors.size} errors")
                            val errorMsg =
                                parsingResult.errors.firstOrNull()?.reason ?: "Parsing failed"
                            Result.failure(Exception(errorMsg))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Search error", e)
                    Result.failure(e)
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
    }
