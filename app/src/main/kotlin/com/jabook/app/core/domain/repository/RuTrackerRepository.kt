package com.jabook.app.core.domain.repository

import com.jabook.app.core.domain.model.RuTrackerAudiobook
import com.jabook.app.core.domain.model.RuTrackerCategory
import com.jabook.app.core.domain.model.RuTrackerSearchResult
import com.jabook.app.core.domain.model.RuTrackerStats
import kotlinx.coroutines.flow.Flow

/** Repository interface for RuTracker integration Provides methods to interact with RuTracker API for audiobook discovery */
interface RuTrackerRepository {
    /**
     * Get all available categories from RuTracker
     *
     * @return Flow of list of categories
     */
    fun getCategories(): Flow<List<RuTrackerCategory>>

    /**
     * Get audiobooks from specific category
     *
     * @param categoryId Category ID to fetch audiobooks from
     * @param page Page number for pagination
     * @param sortBy Sort criteria (seeders, date, size, etc.)
     * @return Flow of list of audiobooks
     */
    fun getAudiobooks(
        categoryId: String,
        page: Int = 1,
        sortBy: String = "seeders",
    ): Flow<List<RuTrackerAudiobook>>

    /**
     * Search audiobooks by query
     *
     * @param query Search query
     * @param categoryId Optional category filter
     * @param page Page number for pagination
     * @return Flow of search results
     */
    fun searchAudiobooks(
        query: String,
        categoryId: String? = null,
        page: Int = 1,
    ): Flow<RuTrackerSearchResult>

    /**
     * Get detailed information about specific audiobook
     *
     * @param audiobookId Audiobook ID
     * @return Flow of audiobook details
     */
    fun getAudiobookDetails(audiobookId: String): Flow<RuTrackerAudiobook>

    /**
     * Get RuTracker statistics
     *
     * @return Flow of statistics
     */
    fun getStats(): Flow<RuTrackerStats>

    /**
     * Check if RuTracker is available
     *
     * @return Flow of availability status
     */
    fun checkAvailability(): Flow<Boolean>

    /**
     * Get trending audiobooks
     *
     * @param limit Number of results to return
     * @return Flow of trending audiobooks
     */
    fun getTrendingAudiobooks(limit: Int = 20): Flow<List<RuTrackerAudiobook>>

    /**
     * Get recently added audiobooks
     *
     * @param limit Number of results to return
     * @return Flow of recently added audiobooks
     */
    fun getRecentlyAdded(limit: Int = 20): Flow<List<RuTrackerAudiobook>>

    /**
     * Get popular audiobooks by category
     *
     * @param categoryId Category ID
     * @param limit Number of results to return
     * @return Flow of popular audiobooks
     */
    fun getPopularByCategory(
        categoryId: String,
        limit: Int = 20,
    ): Flow<List<RuTrackerAudiobook>>

    /**
     * Authorization of the user on RuTracker
     */
    suspend fun login(
        username: String,
        password: String,
    ): Boolean

    /**
     * Logout of the user from RuTracker
     */
    suspend fun logout(): Boolean

    /**
     * Check the status of authorization
     */
    fun isAuthenticated(): Boolean

    /**
     * Get the name of the current user (or null)
     */
    fun getCurrentUser(): String?

    /**
     * Get the state of authorization (guest/authorized/error)
     */
    fun getAuthenticationState(): Flow<com.jabook.app.core.network.AuthenticationState>

    /**
     * Get the magnet link for the audiobook
     */
    suspend fun getMagnetLink(audiobookId: String): String?
}
