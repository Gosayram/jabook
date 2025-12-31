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

package com.jabook.app.jabook.compose.data.remote.api

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Rutracker API interface.
 *
 * Note: Rutracker doesn't have an official REST API.
 * This interface defines HTTP endpoints that return HTML,
 * which will be parsed by RutrackerParser.
 */
interface RutrackerApi {
    /**
     * Search for topics on Rutracker.
     *
     * @param query Search query
     * @param forumIds Optional comma-separated list of forum IDs to search in
     * @return HTML response with search results (raw bytes for encoding detection)
     */
    @GET("tracker.php")
    suspend fun searchTopics(
        @Query("nm") query: String,
        @Query("f") forumIds: String? = null,
    ): Response<ResponseBody>

    /**
     * Get topic details page.
     *
     * @param topicId Topic ID
     * @return HTML response with topic details (raw bytes for encoding detection)
     */
    @GET("viewtopic.php")
    suspend fun getTopicDetails(
        @Query("t") topicId: String,
    ): Response<ResponseBody>

    /**
     * Download torrent file.
     *
     * @param topicId Topic ID
     * @return Torrent file as ResponseBody
     */
    @GET("dl.php")
    suspend fun downloadTorrent(
        @Query("t") topicId: String,
    ): Response<ResponseBody>

    /**
     * Login to Rutracker.
     *
     * @param username Username
     * @param password Password
     * @return HTML response (will set cookies on success)
     */

    /**
     * Login to Rutracker.
     *
     * @param body Form-url-encoded body with CP1251 encoded credentials
     * @return Response body (raw bytes for CP1251 decoding)
     */
    @POST("login.php")
    suspend fun login(
        @Body body: RequestBody,
    ): Response<ResponseBody>

    /**
     * Get profile page to validate authentication.
     *
     * @return HTML response of profile page
     */
    @GET("profile.php?mode=viewprofile")
    suspend fun getProfile(): Response<ResponseBody>

    @GET("index.php")
    suspend fun getIndex(): Response<ResponseBody>

    /**
     * Get forum page with topics.
     *
     * @param forumId Forum ID
     * @param start Starting index (for pagination, typically 0, 50, 100, etc.)
     * @return HTML response with forum topics (raw bytes for encoding detection)
     */
    @GET("viewforum.php")
    suspend fun getForumPage(
        @Query("f") forumId: String,
        @Query("start") start: Int = 0,
    ): Response<ResponseBody>

    companion object {
        // Audiobook categories forum IDs
        // Format: comma-separated list for search query parameter
        // ONLY child forums with actual topics - parent category IDs removed
        // (2326, 2389, 2327, 2324, 2328 are parent groups showing only subforum lists)
        const val AUDIOBOOKS_FORUM_IDS =
            "574,1036,400,2388,2387,661,2348,695,399,402,467,490,499,2137,2127,2325,2342,530,2152,1350,403,1279,716,2165,401"
    }
}
