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

package com.jabook.app.jabook.compose.data.repository

import com.jabook.app.jabook.compose.data.local.dao.OfflineSearchDao
import com.jabook.app.jabook.compose.data.local.entity.toCachedTopicEntity
import com.jabook.app.jabook.compose.data.local.entity.toSearchResult
import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.data.remote.mapper.toDomain
import com.jabook.app.jabook.compose.data.remote.mapper.toDomainFromIndex
import com.jabook.app.jabook.compose.data.remote.model.SearchResult
import com.jabook.app.jabook.compose.data.remote.parser.RutrackerParser
import com.jabook.app.jabook.compose.domain.model.Result
import com.jabook.app.jabook.compose.domain.model.RutrackerSearchResult
import com.jabook.app.jabook.compose.domain.model.RutrackerTopicDetails
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
    suspend fun search(query: String): Result<List<RutrackerSearchResult>>

    /**
     * Get topic details.
     *
     * @param topicId Topic ID
     * @return Result with topic details
     */
    suspend fun getTopicDetails(topicId: String): Result<RutrackerTopicDetails>

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

    /**
     * Get topic details at a specific page.
     *
     * @param topicId Topic ID
     * @param page Page number (1-indexed)
     * @return Result with topic details for that page
     */
    suspend fun getTopicDetailsPage(
        topicId: String,
        page: Int,
    ): Result<RutrackerTopicDetails>
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
        override suspend fun search(query: String): Result<List<RutrackerSearchResult>> {
            // Use ONLY indexed search (no network)
            android.util.Log.d("RutrackerRepositoryImpl", "🔍 Search started: query='$query'")
            return try {
                val countStartTime = System.currentTimeMillis()
                val indexSize = offlineSearchDao.getTopicCount()
                val countDuration = System.currentTimeMillis() - countStartTime
                android.util.Log.d(
                    "RutrackerRepositoryImpl",
                    "Index size check: $indexSize topics (${countDuration}ms)",
                )

                if (indexSize > 0) {
                    // Check for debug command
                    if (query.trim() == "!index" || query.trim() == ":debug") {
                        android.util.Log.d("RutrackerRepositoryImpl", "🐞 Debug command detected, fetching sample topics")
                        val sampleTopics = offlineSearchDao.getSampleTopics(10)
                        val domainResults =
                            sampleTopics
                                .map {
                                    it.toSearchResult().copy(
                                        title = "[DEBUG] ${it.title}",
                                        author = "[${it.author}] (ID: ${it.topicId}, Ver: ${it.indexVersion})",
                                    )
                                }.toDomainFromIndex()
                        return Result.Success(domainResults)
                    }

                    val searchStartTime = System.currentTimeMillis()

                    // Tokenize query for fuzzy search
                    val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

                    val entities =
                        if (tokens.isEmpty()) {
                            emptyList()
                        } else {
                            // Build dynamic SQL query for token-based search
                            // Each token must be present in either title OR author
                            val sqlBuilder = StringBuilder("SELECT * FROM cached_topics WHERE ")
                            val args = ArrayList<Any>()

                            tokens.forEachIndexed { index, token ->
                                if (index > 0) sqlBuilder.append(" AND ")
                                sqlBuilder.append("(title LIKE ? OR author LIKE ?)")
                                val likePattern = "%$token%"
                                args.add(likePattern)
                                args.add(likePattern)
                            }

                            // Add ordering and limit
                            sqlBuilder.append(" ORDER BY seeders DESC, timestamp DESC LIMIT 200")

                            val simpleQuery = androidx.sqlite.db.SimpleSQLiteQuery(sqlBuilder.toString(), args.toArray())
                            offlineSearchDao.searchIndexedTopicsRaw(simpleQuery)
                        }

                    val searchDuration = System.currentTimeMillis() - searchStartTime
                    android.util.Log.d(
                        "RutrackerRepositoryImpl",
                        "DB search (fuzzy): ${entities.size} entities (${searchDuration}ms)",
                    )

                    val mapStartTime = System.currentTimeMillis()
                    val dtoResults = entities.map { it.toSearchResult() }
                    // Use toDomainFromIndex() for offline results (lenient validation, allows missing torrentUrl)
                    val domainResults = dtoResults.toDomainFromIndex()
                    val mapDuration = System.currentTimeMillis() - mapStartTime
                    android.util.Log.d(
                        "RutrackerRepositoryImpl",
                        "Mapping: ${domainResults.size} results (${mapDuration}ms)",
                    )

                    android.util.Log.i(
                        "RutrackerRepositoryImpl",
                        "✅ Search completed: ${domainResults.size} results " +
                            "(total: ${System.currentTimeMillis() - countStartTime}ms)",
                    )
                    Result.Success(domainResults)
                } else {
                    android.util.Log.w("RutrackerRepositoryImpl", "⚠️ Index is empty, returning empty results")
                    // Index is empty - return empty results
                    Result.Success(emptyList())
                }
            } catch (e: Exception) {
                android.util.Log.e("RutrackerRepositoryImpl", "❌ Search failed for query '$query'", e)
                // Search failed - return empty results
                Result.Success(emptyList())
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

        override suspend fun getTopicDetails(topicId: String): Result<RutrackerTopicDetails> {
            return try {
                val response = api.getTopicDetails(topicId)

                if (!response.isSuccessful) {
                    return Result.Error(Exception("HTTP ${response.code()}: ${response.message()}"))
                }

                // Get raw bytes and decode as Windows-1251
                val rawBytes = response.body()?.bytes() ?: return Result.Error(Exception("Empty response body"))
                val html = String(rawBytes, charset("windows-1251"))

                val dtoDetails =
                    parser.parseTopicDetails(html, topicId)
                        ?: return Result.Error(Exception("Failed to parse topic details"))

                // Map DTO to domain model
                val domainDetails = dtoDetails.toDomain()
                if (domainDetails.isValid()) {
                    Result.Success(domainDetails)
                } else {
                    Result.Error(Exception("Topic details failed validation"))
                }
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

        override suspend fun getTopicDetailsPage(
            topicId: String,
            page: Int,
        ): Result<RutrackerTopicDetails> {
            return try {
                // Calculate offset: each page has 30 comments, offset = (page - 1) * 30
                val offset = (page - 1) * 30
                val response = api.getTopicDetailsAtPage(topicId, offset)

                if (!response.isSuccessful) {
                    return Result.Error(Exception("HTTP ${response.code()}: ${response.message()}"))
                }

                val rawBytes = response.body()?.bytes() ?: return Result.Error(Exception("Empty response body"))
                val html = String(rawBytes, charset("windows-1251"))

                val dtoDetails =
                    parser.parseTopicDetails(html, topicId)
                        ?: return Result.Error(Exception("Failed to parse topic details"))

                val domainDetails = dtoDetails.toDomain()
                if (domainDetails.isValid()) {
                    Result.Success(domainDetails)
                } else {
                    Result.Error(Exception("Topic details failed validation"))
                }
            } catch (e: Exception) {
                Result.Error(e)
            }
        }
    }
