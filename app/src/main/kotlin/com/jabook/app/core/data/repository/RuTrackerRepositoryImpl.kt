package com.jabook.app.core.data.repository

import com.jabook.app.core.domain.model.RuTrackerAudiobook
import com.jabook.app.core.domain.model.RuTrackerCategory
import com.jabook.app.core.domain.model.RuTrackerSearchResult
import com.jabook.app.core.domain.repository.RuTrackerRepository
import com.jabook.app.core.network.AuthenticationState
import com.jabook.app.core.network.RuTrackerApiClient
import com.jabook.app.core.network.RuTrackerApiService
import com.jabook.app.shared.debug.IDebugLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RuTracker Repository implementation with dual mode support
 *
 * Uses new RuTrackerApiService for guest mode operations
 * Falls back to RuTrackerApiClient for authenticated operations
 */
@Singleton
class RuTrackerRepositoryImpl
    @Inject
    constructor(
        private val ruTrackerApiClient: RuTrackerApiClient,
        private val ruTrackerApiService: RuTrackerApiService,
        private val debugLogger: IDebugLogger,
    ) : RuTrackerRepository {
        override fun getCategories(): Flow<List<RuTrackerCategory>> =
            flow {
                try {
                    debugLogger.logDebug("RuTrackerRepository: Getting categories")

                    val categories = ruTrackerApiService.getCategories()
                    emit(categories)
                } catch (e: Exception) {
                    debugLogger.logError("RuTrackerRepository: Error getting categories", e)
                    emit(emptyList())
                }
            }

        override fun getAudiobooks(
            categoryId: String,
            page: Int,
            sortBy: String,
        ): Flow<List<RuTrackerAudiobook>> =
            flow {
                try {
                    debugLogger.logDebug("RuTrackerRepository: Getting audiobooks for category: $categoryId, page: $page, sort: $sortBy")

                    val audiobooks =
                        if (ruTrackerApiClient.isAuthenticated()) {
                            val result = ruTrackerApiClient.searchAuthenticated("", sortBy, "desc", page)
                            if (result.isSuccess) {
                                result.getOrNull()?.results ?: emptyList()
                            } else {
                                emptyList()
                            }
                        } else {
                            // Use guest mode with empty query to get category audiobooks
                            ruTrackerApiService.searchAudiobooks("")
                        }

                    emit(audiobooks)
                } catch (e: Exception) {
                    debugLogger.logError("RuTrackerRepository: Error getting audiobooks", e)
                    emit(emptyList())
                }
            }

        override fun searchAudiobooks(
            query: String,
            categoryId: String?,
            page: Int,
        ): Flow<RuTrackerSearchResult> =
            flow {
                try {
                    debugLogger.logDebug("RuTrackerRepository: Searching audiobooks - query: $query, category: $categoryId, page: $page")

                    val searchResult =
                        if (ruTrackerApiClient.isAuthenticated()) {
                            val result = ruTrackerApiClient.searchAuthenticated(query, "seeds", "desc", page)
                            if (result.isSuccess) {
                                result.getOrNull() ?: RuTrackerSearchResult.empty(query)
                            } else {
                                RuTrackerSearchResult.empty(query)
                            }
                        } else {
                            // Use guest mode search
                            val audiobooks = ruTrackerApiService.searchAudiobooks(query)
                            RuTrackerSearchResult(
                                query = query,
                                totalResults = audiobooks.size,
                                currentPage = page,
                                totalPages = 1,
                                results = audiobooks,
                            )
                        }

                    emit(searchResult)
                } catch (e: Exception) {
                    debugLogger.logError("RuTrackerRepository: Error searching audiobooks", e)
                    emit(RuTrackerSearchResult.empty(query))
                }
            }

        override fun getAudiobookDetails(audiobookId: String): Flow<RuTrackerAudiobook> =
            flow {
                try {
                    debugLogger.logDebug("RuTrackerRepository: Getting audiobook details - id: $audiobookId")

                    val audiobook =
                        if (ruTrackerApiClient.isAuthenticated()) {
                            val result = ruTrackerApiClient.getTorrentDetailsAuthenticated(audiobookId)
                            if (result.isSuccess) {
                                result.getOrNull()
                            } else {
                                null
                            }
                        } else {
                            // Use guest mode details
                            ruTrackerApiService.getAudiobookDetails(audiobookId)
                        }

                    if (audiobook != null) {
                        emit(audiobook)
                    } else {
                        debugLogger.logWarning("RuTrackerRepository: Audiobook not found - id: $audiobookId")
                        emit(RuTrackerAudiobook.empty())
                    }
                } catch (e: Exception) {
                    debugLogger.logError("RuTrackerRepository: Error getting audiobook details", e)
                    emit(RuTrackerAudiobook.empty())
                }
            }

        override fun getStats(): Flow<com.jabook.app.core.domain.model.RuTrackerStats> =
            flow {
                try {
                    debugLogger.logDebug("RuTrackerRepository: Getting stats")

                    // For now, return mock stats
                    emit(
                        com.jabook.app.core.domain.model.RuTrackerStats(
                            totalAudiobooks = 0,
                            totalCategories = 0,
                            activeUsers = 0,
                            totalSize = "0 GB",
                            lastUpdate = "",
                        ),
                    )
                } catch (e: Exception) {
                    debugLogger.logError("RuTrackerRepository: Error getting stats", e)
                    emit(
                        com.jabook.app.core.domain.model.RuTrackerStats(
                            totalAudiobooks = 0,
                            totalCategories = 0,
                            activeUsers = 0,
                            totalSize = "0 GB",
                            lastUpdate = "",
                        ),
                    )
                }
            }

        override fun checkAvailability(): Flow<Boolean> =
            flow {
                try {
                    debugLogger.logDebug("RuTrackerRepository: Checking availability")

                    val result = ruTrackerApiClient.checkAvailability()
                    emit(result.getOrNull() ?: false)
                } catch (e: Exception) {
                    debugLogger.logError("RuTrackerRepository: Error checking availability", e)
                    emit(false)
                }
            }

        // Authentication methods
        override suspend fun login(
            username: String,
            password: String,
        ): Boolean =
            try {
                debugLogger.logDebug("RuTrackerRepository: Attempting login for user: $username")

                val result = ruTrackerApiClient.login(username, password)
                val success = result.getOrNull() ?: false

                if (success) {
                    debugLogger.logInfo("RuTrackerRepository: Login successful for user: $username")
                } else {
                    debugLogger.logWarning("RuTrackerRepository: Login failed for user: $username")
                }

                success
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerRepository: Login error", e)
                false
            }

        override suspend fun logout(): Boolean =
            try {
                debugLogger.logDebug("RuTrackerRepository: Logging out")

                val result = ruTrackerApiClient.logout()
                val success = result.getOrNull() ?: false

                if (success) {
                    debugLogger.logInfo("RuTrackerRepository: Logout successful")
                } else {
                    debugLogger.logWarning("RuTrackerRepository: Logout failed")
                }

                success
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerRepository: Logout error", e)
                false
            }

        override fun isAuthenticated(): Boolean = ruTrackerApiClient.isAuthenticated()

        override fun getCurrentUser(): String? = ruTrackerApiClient.getCurrentUser()

        override fun getAuthenticationState(): Flow<AuthenticationState> = ruTrackerApiClient.getAuthenticationState()

        override fun getTrendingAudiobooks(limit: Int): Flow<List<RuTrackerAudiobook>> =
            flow {
                try {
                    debugLogger.logDebug("RuTrackerRepository: Getting trending audiobooks - limit: $limit")

                    val result = ruTrackerApiClient.getTopAudiobooksGuest(limit)
                    if (result.isSuccess) {
                        emit(result.getOrNull() ?: emptyList())
                    } else {
                        debugLogger.logError("RuTrackerRepository: Failed to get trending audiobooks", result.exceptionOrNull())
                        emit(emptyList())
                    }
                } catch (e: Exception) {
                    debugLogger.logError("RuTrackerRepository: Error getting trending audiobooks", e)
                    emit(emptyList())
                }
            }

        override fun getRecentlyAdded(limit: Int): Flow<List<RuTrackerAudiobook>> =
            flow {
                try {
                    debugLogger.logDebug("RuTrackerRepository: Getting recently added audiobooks - limit: $limit")

                    val result = ruTrackerApiClient.getNewAudiobooksGuest(limit)
                    if (result.isSuccess) {
                        emit(result.getOrNull() ?: emptyList())
                    } else {
                        debugLogger.logError("RuTrackerRepository: Failed to get recently added audiobooks", result.exceptionOrNull())
                        emit(emptyList())
                    }
                } catch (e: Exception) {
                    debugLogger.logError("RuTrackerRepository: Error getting recently added audiobooks", e)
                    emit(emptyList())
                }
            }

        override fun getPopularByCategory(
            categoryId: String,
            limit: Int,
        ): Flow<List<RuTrackerAudiobook>> =
            flow {
                try {
                    debugLogger.logDebug(
                        "RuTrackerRepository: Getting popular audiobooks by category - category: $categoryId, limit: $limit",
                    )

                    val result = ruTrackerApiClient.searchGuest("", categoryId, 1)
                    if (result.isSuccess) {
                        val searchResult = result.getOrNull()
                        emit(searchResult?.results?.take(limit) ?: emptyList())
                    } else {
                        debugLogger.logError("RuTrackerRepository: Failed to get popular audiobooks by category", result.exceptionOrNull())
                        emit(emptyList())
                    }
                } catch (e: Exception) {
                    debugLogger.logError("RuTrackerRepository: Error getting popular audiobooks by category", e)
                    emit(emptyList())
                }
            }

        override suspend fun getMagnetLink(audiobookId: String): String? =
            try {
                debugLogger.logDebug("RuTrackerRepository: Getting magnet link for audiobook: $audiobookId")

                val magnetLink =
                    if (ruTrackerApiClient.isAuthenticated()) {
                        val result = ruTrackerApiClient.getMagnetLinkAuthenticated(audiobookId)
                        result.getOrNull()
                    } else {
                        // For guest mode, we need to get the HTML first and extract magnet link
                        val audiobook = ruTrackerApiService.getAudiobookDetails(audiobookId)
                        audiobook?.magnetUri
                    }

                magnetLink
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerRepository: Error getting magnet link", e)
                null
            }
    }
