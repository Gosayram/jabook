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

package com.jabook.app.jabook.compose.data.network

import com.jabook.app.jabook.compose.data.network.model.TopicSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Rutracker API interface for search and content access.
 *
 * Base URL is dynamically set via MirrorManager and DynamicBaseUrlInterceptor.
 * The baseUrl in Retrofit is just a placeholder - actual requests use current mirror.
 */
public interface RutrackerApi {
    /**
     * Search for topics/torrents on Rutracker.
     *
     * @param query Search query (book title, author, etc.)
     * @param category Optional category filter (e.g., "audiobooks")
     * @return List of search results
     */
    @GET("forum/tracker.php")
    suspend fun searchTopics(
        @Query("nm") query: String,
        @Query("f") category: String? = null,
    ): TopicSearchResponse

    /**
     * Get topic details by ID.
     *
     * @param topicId Rutracker topic ID
     * @return Topic details including torrent link
     */
    @GET("forum/viewtopic.php")
    suspend fun getTopicDetails(
        @Query("t") topicId: String,
    ): String // Returns HTML, will need parsing

    public companion object {
        // Note: BASE_URL is not used - MirrorManager and DynamicBaseUrlInterceptor handle base URL dynamically
        // This constant is kept for reference only
        @Deprecated("Use MirrorManager.getBaseUrl() instead", ReplaceWith("mirrorManager.getBaseUrl()"))
        public const val BASE_URL = "https://rutracker.org/"

        // Common category filters
        public const val CATEGORY_AUDIOBOOKS = "2389"
        public const val CATEGORY_AUDIOBOOKS_RU = "2389"
    }
}
