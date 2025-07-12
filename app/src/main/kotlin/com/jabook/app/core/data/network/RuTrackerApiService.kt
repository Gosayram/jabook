package com.jabook.app.core.data.network

import com.jabook.app.core.domain.model.RuTrackerAudiobook
import com.jabook.app.core.domain.model.RuTrackerCategory
import com.jabook.app.core.domain.model.RuTrackerSearchResult
import com.jabook.app.core.domain.model.RuTrackerStats
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** API service for RuTracker integration Handles HTTP requests to RuTracker endpoints */
interface RuTrackerApiService {

    /**
     * Get categories from RuTracker
     *
     * @return List of categories
     */
    @GET("api/categories") suspend fun getCategories(): Response<List<RuTrackerCategory>>

    /**
     * Get audiobooks from specific category
     *
     * @param categoryId Category ID
     * @param page Page number
     * @param sortBy Sort criteria
     * @return List of audiobooks
     */
    @GET("api/category/{categoryId}/audiobooks")
    suspend fun getAudiobooks(
        @Path("categoryId") categoryId: String,
        @Query("page") page: Int = 1,
        @Query("sort") sortBy: String = "seeders",
    ): Response<List<RuTrackerAudiobook>>

    /**
     * Search audiobooks
     *
     * @param query Search query
     * @param categoryId Optional category filter
     * @param page Page number
     * @return Search results
     */
    @GET("api/search")
    suspend fun searchAudiobooks(
        @Query("q") query: String,
        @Query("category") categoryId: String? = null,
        @Query("page") page: Int = 1,
    ): Response<RuTrackerSearchResult>

    /**
     * Get detailed information about audiobook
     *
     * @param audiobookId Audiobook ID
     * @return Audiobook details
     */
    @GET("api/audiobook/{audiobookId}")
    suspend fun getAudiobookDetails(@Path("audiobookId") audiobookId: String): Response<RuTrackerAudiobook>

    /**
     * Get RuTracker statistics
     *
     * @return Statistics
     */
    @GET("api/stats") suspend fun getStats(): Response<RuTrackerStats>

    /**
     * Check if RuTracker is available
     *
     * @return Availability status
     */
    @GET("api/status") suspend fun checkAvailability(): Response<Map<String, Boolean>>

    /**
     * Get trending audiobooks
     *
     * @param limit Number of results
     * @return List of trending audiobooks
     */
    @GET("api/trending") suspend fun getTrendingAudiobooks(@Query("limit") limit: Int = 20): Response<List<RuTrackerAudiobook>>

    /**
     * Get recently added audiobooks
     *
     * @param limit Number of results
     * @return List of recently added audiobooks
     */
    @GET("api/recent") suspend fun getRecentlyAdded(@Query("limit") limit: Int = 20): Response<List<RuTrackerAudiobook>>

    /**
     * Get popular audiobooks by category
     *
     * @param categoryId Category ID
     * @param limit Number of results
     * @return List of popular audiobooks
     */
    @GET("api/category/{categoryId}/popular")
    suspend fun getPopularByCategory(
        @Path("categoryId") categoryId: String,
        @Query("limit") limit: Int = 20,
    ): Response<List<RuTrackerAudiobook>>

    /**
     * Download torrent file
     *
     * @param torrentId Torrent ID
     * @return Torrent file data
     */
    @GET("api/torrent/{torrentId}/download") suspend fun downloadTorrent(@Path("torrentId") torrentId: String): Response<ByteArray>
}

/**
 * Mock implementation of RuTracker API service for testing
 *
 * FIXME: Replace with actual HTTP client implementation
 */
class MockRuTrackerApiService : RuTrackerApiService {

    override suspend fun getCategories(): Response<List<RuTrackerCategory>> {
        // Return mock response
        return Response.success(emptyList())
    }

    override suspend fun getAudiobooks(categoryId: String, page: Int, sortBy: String): Response<List<RuTrackerAudiobook>> {
        // Return mock response
        return Response.success(emptyList())
    }

    override suspend fun searchAudiobooks(query: String, categoryId: String?, page: Int): Response<RuTrackerSearchResult> {
        // Return mock response
        return Response.success(
            RuTrackerSearchResult(query = query, totalResults = 0, currentPage = page, totalPages = 0, results = emptyList())
        )
    }

    override suspend fun getAudiobookDetails(audiobookId: String): Response<RuTrackerAudiobook> {
        // Return mock response
        throw NotImplementedError("Mock implementation")
    }

    override suspend fun getStats(): Response<RuTrackerStats> {
        // Return mock response
        return Response.success(
            RuTrackerStats(totalAudiobooks = 0, totalCategories = 0, activeUsers = 0, totalSize = "0 MB", lastUpdate = "Never")
        )
    }

    override suspend fun checkAvailability(): Response<Map<String, Boolean>> {
        // Return mock response
        return Response.success(mapOf("available" to true))
    }

    override suspend fun getTrendingAudiobooks(limit: Int): Response<List<RuTrackerAudiobook>> {
        // Return mock response
        return Response.success(emptyList())
    }

    override suspend fun getRecentlyAdded(limit: Int): Response<List<RuTrackerAudiobook>> {
        // Return mock response
        return Response.success(emptyList())
    }

    override suspend fun getPopularByCategory(categoryId: String, limit: Int): Response<List<RuTrackerAudiobook>> {
        // Return mock response
        return Response.success(emptyList())
    }

    override suspend fun downloadTorrent(torrentId: String): Response<ByteArray> {
        // Return mock response
        return Response.success(ByteArray(0))
    }
}
