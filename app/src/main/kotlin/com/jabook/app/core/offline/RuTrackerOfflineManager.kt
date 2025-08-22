package com.jabook.app.core.offline

import com.jabook.app.core.cache.CacheKey
import com.jabook.app.core.cache.RuTrackerCacheManager
import com.jabook.app.core.exceptions.RuTrackerException.OfflineException
import com.jabook.app.core.network.models.RuTrackerCategory
import com.jabook.app.core.network.models.RuTrackerSearchResult
import com.jabook.app.core.network.models.RuTrackerTorrentDetails
import com.jabook.app.shared.debug.IDebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RuTracker Offline Manager for managing offline content and search capabilities
 */
@Singleton
class RuTrackerOfflineManager
    @Inject
    constructor(
        private val cacheManager: RuTrackerCacheManager,
        private val debugLogger: IDebugLogger,
    ) {
        companion object {
            private const val OFFLINE_CACHE_TTL = 7 * 24 * 60 * 60 * 1000L // 7 days
            private const val OFFLINE_SEARCH_INDEX_TTL = 24 * 60 * 60 * 1000L // 24 hours
            private const val MAX_OFFLINE_RESULTS = 1000
            private const val MAX_OFFLINE_CATEGORIES = 50
            private const val MAX_OFFLINE_DETAILS = 200
            private const val OFFLINE_DATA_VERSION = 1
        }

        // Offline data storage
        private val offlineSearchResults = ConcurrentHashMap<String, List<RuTrackerSearchResult>>()
        private val offlineCategories = mutableListOf<RuTrackerCategory>()
        private val offlineDetails = ConcurrentHashMap<String, RuTrackerTorrentDetails>()
        private val offlineSearchIndex = mutableMapOf<String, Set<String>>() // keyword -> topicIds

        // Offline state
        private val _isOfflineMode = MutableStateFlow(false)
        val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

        // Offline data status
        private val _offlineDataStatus = MutableStateFlow(OfflineDataStatus())
        val offlineDataStatus: StateFlow<OfflineDataStatus> = _offlineDataStatus.asStateFlow()

        // Offline search analytics
        private val _offlineSearchAnalytics = MutableStateFlow(OfflineSearchAnalytics())
        val offlineSearchAnalytics: StateFlow<OfflineSearchAnalytics> = _offlineSearchAnalytics.asStateFlow()

        /**
         * Enable or disable offline mode
         */
        fun setOfflineMode(enabled: Boolean) {
            _isOfflineMode.value = enabled
            debugLogger.logInfo("RuTrackerOfflineManager: Offline mode ${if (enabled) "enabled" else "disabled"}")
        }

        /**
         * Check if offline mode is enabled
         */
        fun isOfflineModeEnabled(): Boolean = _isOfflineMode.value

        /**
         * Store search results for offline access
         */
        suspend fun storeSearchResults(
            query: String,
            categoryId: Int?,
            results: List<RuTrackerSearchResult>,
        ): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    if (results.isEmpty()) {
                        return@withContext Result.success(Unit)
                    }

                    // Store in memory
                    val cacheKey = "${query}_$categoryId"
                    offlineSearchResults[cacheKey] = results.take(MAX_OFFLINE_RESULTS)

                    // Store in cache
                    val persistentCacheKey =
                        CacheKey(
                            namespace = "offline_search",
                            key = "search_$cacheKey",
                            version = OFFLINE_DATA_VERSION,
                        )

                    cacheManager.put(
                        key = persistentCacheKey,
                        data = results,
                        ttl = OFFLINE_CACHE_TTL,
                        serializer = { Json.encodeToString(it) },
                    )

                    // Update search index
                    updateSearchIndex(query, results)

                    // Update status
                    updateOfflineDataStatus()

                    debugLogger.logDebug("RuTrackerOfflineManager: Stored ${results.size} search results for offline access")
                    Result.success(Unit)
                } catch (e: Exception) {
                    debugLogger.logError("RuTrackerOfflineManager: Failed to store search results", e)
                    Result.failure(e)
                }
            }

        /**
         * Store categories for offline access
         */
        suspend fun storeCategories(categories: List<RuTrackerCategory>): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    if (categories.isEmpty()) {
                        return@withContext Result.success(Unit)
                    }

                    // Store in memory
                    offlineCategories.clear()
                    offlineCategories.addAll(categories.take(MAX_OFFLINE_CATEGORIES))

                    // Store in cache
                    val cacheKey =
                        CacheKey(
                            namespace = "offline_categories",
                            key = "categories",
                            version = OFFLINE_DATA_VERSION,
                        )

                    cacheManager.put(
                        key = cacheKey,
                        data = categories,
                        ttl = OFFLINE_CACHE_TTL,
                        serializer = { Json.encodeToString(it) },
                    )

                    // Update status
                    updateOfflineDataStatus()

                    debugLogger.logDebug("RuTrackerOfflineManager: Stored ${categories.size} categories for offline access")
                    Result.success(Unit)
                } catch (e: Exception) {
                    debugLogger.logError("RuTrackerOfflineManager: Failed to store categories", e)
                    Result.failure(e)
                }
            }

        /**
         * Store torrent details for offline access
         */
        suspend fun storeTorrentDetails(
            topicId: String,
            details: RuTrackerTorrentDetails,
        ): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    // Store in memory
                    offlineDetails[topicId] = details

                    // Store in cache
                    val cacheKey =
                        CacheKey(
                            namespace = "offline_details",
                            key = "details_$topicId",
                            version = OFFLINE_DATA_VERSION,
                        )

                    cacheManager.put(
                        key = cacheKey,
                        data = details,
                        ttl = OFFLINE_CACHE_TTL,
                        serializer = { Json.encodeToString(it) },
                    )

                    // Update status
                    updateOfflineDataStatus()

                    debugLogger.logDebug("RuTrackerOfflineManager: Stored torrent details for topic: $topicId")
                    Result.success(Unit)
                } catch (e: Exception) {
                    debugLogger.logError("RuTrackerOfflineManager: Failed to store torrent details", e)
                    Result.failure(e)
                }
            }

        /**
         * Search offline content
         */
        suspend fun searchOffline(
            query: String,
            categoryId: Int? = null,
        ): Result<List<RuTrackerSearchResult>> =
            withContext(Dispatchers.IO) {
                try {
                    if (!isOfflineModeEnabled()) {
                        return@withContext Result.failure(OfflineException("Offline mode is not enabled"))
                    }

                    debugLogger.logDebug("RuTrackerOfflineManager: Searching offline for: $query")

                    // Record search attempt
                    recordOfflineSearchAttempt(query)

                    // Try exact match first
                    val exactMatchKey = "${query}_$categoryId"
                    val exactResults = offlineSearchResults[exactMatchKey]

                    if (!exactResults.isNullOrEmpty()) {
                        debugLogger.logDebug("RuTrackerOfflineManager: Found exact match with ${exactResults.size} results")
                        recordOfflineSearchSuccess(query, exactResults.size)
                        return@withContext Result.success(exactResults)
                    }

                    // Try keyword-based search
                    val keywordResults = searchByKeywords(query)

                    if (keywordResults.isNotEmpty()) {
                        debugLogger.logDebug("RuTrackerOfflineManager: Found ${keywordResults.size} results via keyword search")
                        recordOfflineSearchSuccess(query, keywordResults.size)
                        return@withContext Result.success(keywordResults)
                    }

                    // Try fuzzy search
                    val fuzzyResults = searchFuzzy(query)

                    if (fuzzyResults.isNotEmpty()) {
                        debugLogger.logDebug("RuTrackerOfflineManager: Found ${fuzzyResults.size} results via fuzzy search")
                        recordOfflineSearchSuccess(query, fuzzyResults.size)
                        return@withContext Result.success(fuzzyResults)
                    }

                    debugLogger.logDebug("RuTrackerOfflineManager: No offline results found for: $query")
                    recordOfflineSearchFailure(query)
                    Result.success(emptyList())
                } catch (e: Exception) {
                    debugLogger.logError("RuTrackerOfflineManager: Offline search failed", e)
                    Result.failure(e)
                }
            }

        /**
         * Get categories offline
         */
        suspend fun getCategoriesOffline(): Result<List<RuTrackerCategory>> =
            withContext(Dispatchers.IO) {
                try {
                    if (!isOfflineModeEnabled()) {
                        return@withContext Result.failure(OfflineException("Offline mode is not enabled"))
                    }

                    if (offlineCategories.isNotEmpty()) {
                        debugLogger.logDebug("RuTrackerOfflineManager: Retrieved ${offlineCategories.size} categories from memory")
                        return@withContext Result.success(offlineCategories)
                    }

                    // Try to load from cache
                    val cacheKey =
                        CacheKey(
                            namespace = "offline_categories",
                            key = "categories",
                            version = OFFLINE_DATA_VERSION,
                        )

                    val cachedResult =
                        cacheManager.get(cacheKey) { json ->
                            Json.decodeFromString<List<RuTrackerCategory>>(json)
                        }

                    if (cachedResult.isSuccess) {
                        val categories = cachedResult.getOrNull() ?: emptyList()
                        offlineCategories.addAll(categories)
                        debugLogger.logDebug("RuTrackerOfflineManager: Retrieved ${categories.size} categories from cache")
                        return@withContext Result.success(categories)
                    }

                    debugLogger.logDebug("RuTrackerOfflineManager: No offline categories available")
                    Result.success(emptyList())
                } catch (e: Exception) {
                    debugLogger.logError("RuTrackerOfflineManager: Failed to get offline categories", e)
                    Result.failure(e)
                }
            }

        /**
         * Get torrent details offline
         */
        suspend fun getTorrentDetailsOffline(topicId: String): Result<RuTrackerTorrentDetails?> =
            withContext(Dispatchers.IO) {
                try {
                    if (!isOfflineModeEnabled()) {
                        return@withContext Result.failure(OfflineException("Offline mode is not enabled"))
                    }

                    // Check memory first
                    val details = offlineDetails[topicId]
                    if (details != null) {
                        debugLogger.logDebug("RuTrackerOfflineManager: Retrieved torrent details from memory for topic: $topicId")
                        return@withContext Result.success(details)
                    }

                    // Try to load from cache
                    val cacheKey =
                        CacheKey(
                            namespace = "offline_details",
                            key = "details_$topicId",
                            version = OFFLINE_DATA_VERSION,
                        )

                    val cachedResult =
                        cacheManager.get(cacheKey) { json ->
                            Json.decodeFromString<RuTrackerTorrentDetails>(json)
                        }

                    if (cachedResult.isSuccess) {
                        val cachedDetails = cachedResult.getOrNull()
                        if (cachedDetails != null) {
                            offlineDetails[topicId] = cachedDetails
                            debugLogger.logDebug("RuTrackerOfflineManager: Retrieved torrent details from cache for topic: $topicId")
                            return@withContext Result.success(cachedDetails)
                        }
                    }

                    debugLogger.logDebug("RuTrackerOfflineManager: No offline details available for topic: $topicId")
                    Result.success(null)
                } catch (e: Exception) {
                    debugLogger.logError("RuTrackerOfflineManager: Failed to get offline torrent details", e)
                    Result.failure(e)
                }
            }

        /**
         * Load offline data from cache
         */
        suspend fun loadOfflineData(): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    debugLogger.logInfo("RuTrackerOfflineManager: Loading offline data from cache")

                    // Load search results
                    loadOfflineSearchResults()

                    // Load categories
                    loadOfflineCategories()

                    // Load torrent details
                    loadOfflineTorrentDetails()

                    // Load search index
                    loadOfflineSearchIndex()

                    // Update status
                    updateOfflineDataStatus()

                    debugLogger.logInfo("RuTrackerOfflineManager: Offline data loaded successfully")
                    Result.success(Unit)
                } catch (e: Exception) {
                    debugLogger.logError("RuTrackerOfflineManager: Failed to load offline data", e)
                    Result.failure(e)
                }
            }

        /**
         * Clear all offline data
         */
        suspend fun clearOfflineData(): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    debugLogger.logInfo("RuTrackerOfflineManager: Clearing all offline data")

                    // Clear memory
                    offlineSearchResults.clear()
                    offlineCategories.clear()
                    offlineDetails.clear()
                    offlineSearchIndex.clear()

                    // Clear cache
                    cacheManager.clearNamespace("offline_search")
                    cacheManager.clearNamespace("offline_categories")
                    cacheManager.clearNamespace("offline_details")
                    cacheManager.clearNamespace("offline_index")

                    // Reset analytics
                    _offlineSearchAnalytics.value = OfflineSearchAnalytics()

                    // Update status
                    updateOfflineDataStatus()

                    debugLogger.logInfo("RuTrackerOfflineManager: Offline data cleared successfully")
                    Result.success(Unit)
                } catch (e: Exception) {
                    debugLogger.logError("RuTrackerOfflineManager: Failed to clear offline data", e)
                    Result.failure(e)
                }
            }

        /**
         * Get offline data statistics
         */
        fun getOfflineDataStatistics(): OfflineDataStatistics =
            OfflineDataStatistics(
                searchResultsCount = offlineSearchResults.values.sumOf { it.size },
                categoriesCount = offlineCategories.size,
                detailsCount = offlineDetails.size,
                searchIndexSize = offlineSearchIndex.size,
                totalCacheSize = calculateTotalCacheSize(),
                lastUpdated = System.currentTimeMillis(),
            )

        /**
         * Update search index
         */
        private suspend fun updateSearchIndex(
            query: String,
            results: List<RuTrackerSearchResult>,
        ) {
            try {
                // Extract keywords from query
                val keywords = extractKeywords(query)

                // Update index for each keyword
                for (keyword in keywords) {
                    val existingTopicIds = offlineSearchIndex[keyword] ?: emptySet()
                    val newTopicIds = results.map { it.topicId }.toSet()
                    offlineSearchIndex[keyword] = existingTopicIds + newTopicIds
                }

                // Store index in cache
                val cacheKey =
                    CacheKey(
                        namespace = "offline_index",
                        key = "search_index",
                        version = OFFLINE_DATA_VERSION,
                    )

                cacheManager.put(
                    key = cacheKey,
                    data = offlineSearchIndex,
                    ttl = OFFLINE_SEARCH_INDEX_TTL,
                    serializer = { Json.encodeToString(it) },
                )
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerOfflineManager: Failed to update search index", e)
            }
        }

        /**
         * Search by keywords
         */
        private suspend fun searchByKeywords(query: String): List<RuTrackerSearchResult> {
            try {
                val keywords = extractKeywords(query)
                val matchingTopicIds = mutableSetOf<String>()

                // Find all topic IDs that match any keyword
                for (keyword in keywords) {
                    val topicIds = offlineSearchIndex[keyword] ?: emptySet()
                    matchingTopicIds.addAll(topicIds)
                }

                // Get all results for matching topic IDs
                val allResults = offlineSearchResults.values.flatten()

                // Filter and sort by relevance
                return allResults
                    .filter { it.topicId in matchingTopicIds }
                    .distinctBy { it.topicId }
                    .sortedByDescending { calculateRelevanceScore(it, query) }
                    .take(MAX_OFFLINE_RESULTS)
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerOfflineManager: Keyword search failed", e)
                return emptyList()
            }
        }

        /**
         * Fuzzy search implementation
         */
        private suspend fun searchFuzzy(query: String): List<RuTrackerSearchResult> {
            try {
                val allResults = offlineSearchResults.values.flatten()

                // Simple fuzzy matching based on word overlap
                val queryWords = query.lowercase().split(Regex("\\s+"))

                return allResults
                    .filter { result ->
                        val titleWords = result.title.lowercase().split(Regex("\\s+"))
                        val authorWords = result.author.lowercase().split(Regex("\\s+"))

                        // Calculate fuzzy match score
                        val titleMatches =
                            queryWords.count { queryWord ->
                                titleWords.any { titleWord ->
                                    titleWord.contains(queryWord) || queryWord.contains(titleWord)
                                }
                            }

                        val authorMatches =
                            queryWords.count { queryWord ->
                                authorWords.any { authorWord ->
                                    authorWord.contains(queryWord) || queryWord.contains(authorWord)
                                }
                            }

                        titleMatches > 0 || authorMatches > 0
                    }.distinctBy { it.topicId }
                    .sortedByDescending { calculateFuzzyMatchScore(it, query) }
                    .take(MAX_OFFLINE_RESULTS)
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerOfflineManager: Fuzzy search failed", e)
                return emptyList()
            }
        }

        /**
         * Extract keywords from query
         */
        private fun extractKeywords(query: String): List<String> {
            val commonWords = setOf("и", "в", "на", "с", "по", "для", "не", "что", "как", "все", "или")

            return query
                .split(Regex("\\s+"))
                .filter { it.length > 2 && !commonWords.contains(it.lowercase()) }
                .map { it.lowercase() }
                .distinct()
        }

        /**
         * Calculate relevance score
         */
        private fun calculateRelevanceScore(
            result: RuTrackerSearchResult,
            query: String,
        ): Double {
            var score = 0.0

            if (result.title.contains(query, ignoreCase = true)) {
                score += 10.0
            }

            if (result.author.contains(query, ignoreCase = true)) {
                score += 5.0
            }

            score += result.seeders * 0.1
            score += (result.size / (1024 * 1024)) * 0.001

            return score
        }

        /**
         * Calculate fuzzy match score
         */
        private fun calculateFuzzyMatchScore(
            result: RuTrackerSearchResult,
            query: String,
        ): Double {
            var score = 0.0

            val queryWords = query.lowercase().split(Regex("\\s+"))
            val titleWords = result.title.lowercase().split(Regex("\\s+"))
            val authorWords = result.author.lowercase().split(Regex("\\s+"))

            val titleMatches =
                queryWords.count { queryWord ->
                    titleWords.any { titleWord -> titleWord.contains(queryWord) }
                }

            val authorMatches =
                queryWords.count { queryWord ->
                    authorWords.any { authorWord -> authorWord.contains(queryWord) }
                }

            score += titleMatches * 5.0
            score += authorMatches * 3.0
            score += result.seeders * 0.1

            return score
        }

        /**
         * Load offline search results
         */
        private suspend fun loadOfflineSearchResults() {
            try {
                // This would need to be implemented based on cache structure
                // For now, we'll assume it's handled by the cache manager
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerOfflineManager: Failed to load offline search results", e)
            }
        }

        /**
         * Load offline categories
         */
        private suspend fun loadOfflineCategories() {
            try {
                val cacheKey =
                    CacheKey(
                        namespace = "offline_categories",
                        key = "categories",
                        version = OFFLINE_DATA_VERSION,
                    )

                val cachedResult =
                    cacheManager.get(cacheKey) { json ->
                        Json.decodeFromString<List<RuTrackerCategory>>(json)
                    }

                if (cachedResult.isSuccess) {
                    val categories = cachedResult.getOrNull() ?: emptyList()
                    offlineCategories.addAll(categories)
                }
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerOfflineManager: Failed to load offline categories", e)
            }
        }

        /**
         * Load offline torrent details
         */
        private suspend fun loadOfflineTorrentDetails() {
            try {
                // This would need to be implemented based on cache structure
                // For now, we'll assume it's handled by the cache manager
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerOfflineManager: Failed to load offline torrent details", e)
            }
        }

        /**
         * Load offline search index
         */
        private suspend fun loadOfflineSearchIndex() {
            try {
                val cacheKey =
                    CacheKey(
                        namespace = "offline_index",
                        key = "search_index",
                        version = OFFLINE_DATA_VERSION,
                    )

                val cachedResult =
                    cacheManager.get(cacheKey) { json ->
                        Json.decodeFromString<Map<String, Set<String>>>(json)
                    }

                if (cachedResult.isSuccess) {
                    val index = cachedResult.getOrNull() ?: emptyMap()
                    offlineSearchIndex.putAll(index)
                }
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerOfflineManager: Failed to load offline search index", e)
            }
        }

        /**
         * Update offline data status
         */
        private suspend fun updateOfflineDataStatus() {
            _offlineDataStatus.value =
                OfflineDataStatus(
                    hasSearchResults = offlineSearchResults.isNotEmpty(),
                    hasCategories = offlineCategories.isNotEmpty(),
                    hasDetails = offlineDetails.isNotEmpty(),
                    hasSearchIndex = offlineSearchIndex.isNotEmpty(),
                    lastUpdated = System.currentTimeMillis(),
                )
        }

        /**
         * Record offline search attempt
         */
        private suspend fun recordOfflineSearchAttempt(query: String) {
            _offlineSearchAnalytics.update { current ->
                current.copy(
                    totalSearches = current.totalSearches + 1,
                    lastSearchTime = System.currentTimeMillis(),
                )
            }
        }

        /**
         * Record offline search success
         */
        private suspend fun recordOfflineSearchSuccess(
            query: String,
            resultCount: Int,
        ) {
            _offlineSearchAnalytics.update { current ->
                current.copy(
                    successfulSearches = current.successfulSearches + 1,
                    totalResults = current.totalResults + resultCount,
                    lastSuccessTime = System.currentTimeMillis(),
                )
            }
        }

        /**
         * Record offline search failure
         */
        private suspend fun recordOfflineSearchFailure(query: String) {
            _offlineSearchAnalytics.update { current ->
                current.copy(
                    failedSearches = current.failedSearches + 1,
                    lastFailureTime = System.currentTimeMillis(),
                )
            }
        }

        /**
         * Calculate total cache size
         */
        private suspend fun calculateTotalCacheSize(): Long =
            try {
                // This would need to be implemented based on cache manager capabilities
                0L // Placeholder
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerOfflineManager: Failed to calculate cache size", e)
                0L
            }

        /**
         * Offline data status data class
         */
        data class OfflineDataStatus(
            val hasSearchResults: Boolean = false,
            val hasCategories: Boolean = false,
            val hasDetails: Boolean = false,
            val hasSearchIndex: Boolean = false,
            val lastUpdated: Long = 0,
        )

        /**
         * Offline data statistics data class
         */
        data class OfflineDataStatistics(
            val searchResultsCount: Int = 0,
            val categoriesCount: Int = 0,
            val detailsCount: Int = 0,
            val searchIndexSize: Int = 0,
            val totalCacheSize: Long = 0,
            val lastUpdated: Long = 0,
        )

        /**
         * Offline search analytics data class
         */
        data class OfflineSearchAnalytics(
            val totalSearches: Int = 0,
            val successfulSearches: Int = 0,
            val failedSearches: Int = 0,
            val totalResults: Int = 0,
            val lastSearchTime: Long = 0,
            val lastSuccessTime: Long = 0,
            val lastFailureTime: Long = 0,
        )
    }
