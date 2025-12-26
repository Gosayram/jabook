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

package com.jabook.app.jabook.compose.data.repository

import com.jabook.app.jabook.compose.data.local.dao.OfflineSearchDao
import com.jabook.app.jabook.compose.data.local.entity.toCachedTopicEntity
import com.jabook.app.jabook.compose.data.local.entity.toSearchResult
import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.data.remote.model.SearchResult
import com.jabook.app.jabook.compose.data.remote.model.TopicDetails
import com.jabook.app.jabook.compose.data.remote.parser.RutrackerParser
import com.jabook.app.jabook.compose.domain.model.Result
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Rutracker data.
 *
 * Handles network requests and HTML parsing for Rutracker search and details.
 */
interface RutrackerRepository {
    /**
     * Search for audiobooks on Rutracker.
     *
     * @param query Search query
     * @return Result with list of search results
     */
    suspend fun search(query: String): Result<List<SearchResult>>

    /**
     * Get topic details.
     *
     * @param topicId Topic ID
     * @return Result with topic details
     */
    suspend fun getTopicDetails(topicId: String): Result<TopicDetails>

    /**
     * Login to Rutracker.
     *
     * @param username Username
     * @param password Password
     * @return Result indicating success or failure
     */
    suspend fun login(
        username: String,
        password: String,
    ): Result<Unit>
}

/**
 * Implementation of RutrackerRepository.
 */
@Singleton
class RutrackerRepositoryImpl
    @Inject
    constructor(
        private val api: RutrackerApi,
        private val parser: RutrackerParser,
        private val offlineSearchDao: OfflineSearchDao,
    ) : RutrackerRepository {
        override suspend fun search(query: String): Result<List<SearchResult>> {
            try {
                // 1. Try Network
                val response = api.searchTopics(query)

                if (response.isSuccessful) {
                    // Get raw bytes to handle Windows-1251 encoding properly
                    val rawBytes = response.body()?.bytes()
                    if (rawBytes != null) {
                        // Use encoding-aware parsing
                        val contentType = response.headers()["Content-Type"]
                        val parsingResult = parser.parseSearchResultsWithEncoding(rawBytes, contentType)

                        return when (parsingResult) {
                            is com.jabook.app.jabook.compose.data.remote.parser.ParsingResult.Success -> {
                                // Save to DB (Background)
                                if (parsingResult.data.isNotEmpty()) {
                                    saveResultsToDb(query, parsingResult.data)
                                }
                                Result.Success(parsingResult.data)
                            }
                            is com.jabook.app.jabook.compose.data.remote.parser.ParsingResult.PartialSuccess -> {
                                // Save to DB even with warnings
                                if (parsingResult.data.isNotEmpty()) {
                                    saveResultsToDb(query, parsingResult.data)
                                }
                                Result.Success(parsingResult.data)
                            }
                            is com.jabook.app.jabook.compose.data.remote.parser.ParsingResult.Failure -> {
                                // Parsing failed, fallback to DB
                                Result.Error(Exception(parsingResult.errors.firstOrNull()?.reason ?: "Parsing failed"))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Log error
                // Log.w("RutrackerRepo", "Network search failed: ${e.message}")
            }

            // 3. Fallback to DB
            return try {
                val cached = offlineSearchDao.getResultsForQuery(query)
                if (cached.isNotEmpty()) {
                    val results = cached.map { it.toSearchResult() }
                    Result.Success(results)
                } else {
                    Result.Error(Exception("No cached results found and network failed"))
                }
            } catch (dbEx: Exception) {
                Result.Error(dbEx)
            }
        }

        private suspend fun saveResultsToDb(
            query: String,
            results: List<SearchResult>,
        ) {
            try {
                offlineSearchDao.saveSearchResults(query, results.map { it.toCachedTopicEntity() })
            } catch (e: Exception) {
                // Log.e("RutrackerRepo", "Failed to save results", e)
            }
        }

        override suspend fun getTopicDetails(topicId: String): Result<TopicDetails> {
            return try {
                val response = api.getTopicDetails(topicId)

                if (!response.isSuccessful) {
                    return Result.Error(Exception("HTTP ${response.code()}: ${response.message()}"))
                }

                // Get raw bytes and decode as Windows-1251
                val rawBytes = response.body()?.bytes() ?: return Result.Error(Exception("Empty response body"))
                val html = String(rawBytes, charset("windows-1251"))

                val details =
                    parser.parseTopicDetails(html, topicId)
                        ?: return Result.Error(Exception("Failed to parse topic details"))

                Result.Success(details)
            } catch (e: Exception) {
                Result.Error(e)
            }
        }

        override suspend fun login(
            username: String,
            password: String,
        ): Result<Unit> {
            return try {
                // Create form-url-encoded request body with CP1251 encoding
                val formBody = "login_username=$username&login_password=$password&login=%C2%F5%EE%E4"
                val requestBody =
                    formBody
                        .toByteArray(charset("windows-1251"))
                        .toRequestBody("application/x-www-form-urlencoded; charset=windows-1251".toMediaType())

                val response = api.login(requestBody)

                if (!response.isSuccessful) {
                    return Result.Error(Exception("Login failed: HTTP ${response.code()}"))
                }

                // Check if login was successful by checking cookies or response content
                // Rutracker sets session cookies on successful login
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }
