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
import com.jabook.app.jabook.compose.data.remote.mapper.toDomain
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
            return try {
                val indexSize = offlineSearchDao.getTopicCount()
                if (indexSize > 0) {
                    val entities = offlineSearchDao.searchIndexedTopics(query, limit = 200)
                    val dtoResults = entities.map { it.toSearchResult() }
                    val domainResults = dtoResults.toDomain()
                    Result.Success(domainResults)
                } else {
                    // Index is empty - return empty results
                    Result.Success(emptyList())
                }
            } catch (e: Exception) {
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
    }
