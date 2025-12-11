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

import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.data.remote.model.SearchResult
import com.jabook.app.jabook.compose.data.remote.model.TopicDetails
import com.jabook.app.jabook.compose.data.remote.parser.RutrackerParser
import com.jabook.app.jabook.compose.domain.model.Result
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
    ) : RutrackerRepository {
        override suspend fun search(query: String): Result<List<SearchResult>> {
            return try {
                val response = api.searchTopics(query)

                if (!response.isSuccessful) {
                    return Result.Error(Exception("HTTP ${response.code()}: ${response.message()}"))
                }

                val html = response.body() ?: return Result.Error(Exception("Empty response body"))
                val results = parser.parseSearchResults(html)

                Result.Success(results)
            } catch (e: Exception) {
                Result.Error(e)
            }
        }

        override suspend fun getTopicDetails(topicId: String): Result<TopicDetails> {
            return try {
                val response = api.getTopicDetails(topicId)

                if (!response.isSuccessful) {
                    return Result.Error(Exception("HTTP ${response.code()}: ${response.message()}"))
                }

                val html = response.body() ?: return Result.Error(Exception("Empty response body"))
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
                val response = api.login(username, password)

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
