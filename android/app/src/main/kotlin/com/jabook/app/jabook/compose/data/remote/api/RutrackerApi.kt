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

package com.jabook.app.jabook.compose.data.remote.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
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
     * @return HTML response with search results
     */
    @GET("tracker.php")
    suspend fun searchTopics(
        @Query("nm") query: String,
        @Query("f") forumIds: String? = null,
    ): Response<String>

    /**
     * Get topic details page.
     *
     * @param topicId Topic ID
     * @return HTML response with topic details
     */
    @GET("viewtopic.php")
    suspend fun getTopicDetails(
        @Query("t") topicId: String,
    ): Response<String>

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
    @GET("login.php")
    suspend fun login(
        @Query("login_username") username: String,
        @Query("login_password") password: String,
        @Query("login") login: String = "Вход",
    ): Response<String>
}
