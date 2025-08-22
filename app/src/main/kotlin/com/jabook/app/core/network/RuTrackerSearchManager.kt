package com.jabook.app.core.network

import com.jabook.app.core.cache.CacheKey
import com.jabook.app.core.cache.RuTrackerCacheManager
import com.jabook.app.core.network.domain.RuTrackerDomainManager
import com.jabook.app.core.network.models.RuTrackerCategory
import com.jabook.app.core.network.models.RuTrackerSearchResult
import com.jabook.app.shared.debug.IDebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RuTracker Search Manager with fallback strategies and analytics
 */
@Singleton
class RuTrackerSearchManager
    @Inject
    constructor(
        private val apiService: RuTrackerApiServiceEnhanced,
        private val cacheManager: RuTrackerCacheManager,
        private val domainManager: RuTrackerDomainManager,
        private val debugLogger: IDebugLogger,
    ) {
        companion object {
            private const val SEARCH_CACHE_TTL = 5 * 60 * 1000L // 5 minutes
            private const val CATEGORY_CACHE_TTL = 30 * 60 * 1000L // 30 minutes
            private const val SEARCH_HISTORY_TTL = 24 * 60 * 60 * 1000L // 24 hours
            private const val MAX_SEARCH_RESULTS = 100
            private const val MAX_CACHED_SEARCHES = 50
            private const val SEARCH_ANALYTICS_TTL = 7 * 24 * 60 * 60 * 1000L // 7 days
        }

        // Search analytics
        private val searchAnalytics = mutableMapOf<String, SearchAnalytics>()

        // Search history
        private val searchHistory = mutableListOf<SearchHistoryItem>()

        // Popular searches
        private val popularSearches = mutableListOf<PopularSearchItem>()

        // Search strategies
        private val searchStrategies =
            listOf(
                SearchStrategy.DirectSearch,
                SearchStrategy.CategoryBrowsing,
                SearchStrategy.KeywordExtraction,
                SearchStrategy.FuzzyMatching,
            )

        /**
         * Search for audiobooks with multiple fallback strategies
         */
        suspend fun searchAudiobooks(
            query: String,
            categoryId: Int? = null,
            page: Int = 0,
            forceRefresh: Boolean = false,
            useCache: Boolean = true,
        ): SearchResult =
            withContext(Dispatchers.IO) {
                debugLogger.logDebug("RuTrackerSearchManager: Searching for audiobooks with query: $query")

                // Record search attempt
                recordSearchAttempt(query, categoryId)

                // Check cache first
                if (useCache && !forceRefresh) {
                    val cachedResult = getCachedSearchResults(query, categoryId, page)
                    if (cachedResult != null) {
                        debugLogger.logDebug("RuTrackerSearchManager: Search results found in cache")
                        recordSearchSuccess(query, categoryId, cachedResult.results.size, true)
                        return@withContext cachedResult
                    }
                }

                // Try different search strategies
                for (strategy in searchStrategies) {
                    try {
                        debugLogger.logDebug("RuTrackerSearchManager: Trying search strategy: $strategy")

                        val result =
                            when (strategy) {
                                SearchStrategy.DirectSearch -> performDirectSearch(query, categoryId, page)
                                SearchStrategy.CategoryBrowsing -> performCategoryBrowsing(query, categoryId, page)
                                SearchStrategy.KeywordExtraction -> performKeywordExtractionSearch(query, categoryId, page)
                                SearchStrategy.FuzzyMatching -> performFuzzyMatchingSearch(query, categoryId, page)
                            }

                        if (result.results.isNotEmpty()) {
                            debugLogger.logDebug("RuTrackerSearchManager: Search successful with strategy: $strategy")

                            // Cache successful results
                            cacheSearchResults(query, categoryId, page, result)

                            // Record search success
                            recordSearchSuccess(query, categoryId, result.results.size, false)

                            return@withContext result
                        }
                    } catch (e: Exception) {
                        debugLogger.logWarning("RuTrackerSearchManager: Search strategy $strategy failed", e)
                        // Continue with next strategy
                    }
                }

                // All strategies failed
                debugLogger.logWarning("RuTrackerSearchManager: All search strategies failed")
                recordSearchFailure(query, categoryId)

                SearchResult(
                    query = query,
                    categoryId = categoryId,
                    page = page,
                    results = emptyList(),
                    totalResults = 0,
                    strategy = SearchStrategy.DirectSearch,
                    fromCache = false,
                    error = "All search strategies failed",
                )
            }

        /**
         * Get categories with caching
         */
        suspend fun getCategories(forceRefresh: Boolean = false): Result<List<RuTrackerCategory>> {
            debugLogger.logDebug("RuTrackerSearchManager: Getting categories")

            // Check cache first
            if (!forceRefresh) {
                val cacheKey =
                    CacheKey(
                        namespace = "categories",
                        key = "categories",
                        version = 1,
                    )

                val cachedResult =
                    cacheManager.get(cacheKey) { json ->
                        Json.decodeFromString<List<RuTrackerCategory>>(json)
                    }

                if (cachedResult.isSuccess) {
                    debugLogger.logDebug("RuTrackerSearchManager: Categories found in cache")
                    return cachedResult
                }
            }

            // Get from API
            return apiService.getCategories(forceRefresh)
        }

        /**
         * Get search suggestions based on query
         */
        suspend fun getSearchSuggestions(
            query: String,
            limit: Int = 10,
        ): List<String> {
            debugLogger.logDebug("RuTrackerSearchManager: Getting search suggestions for: $query")

            return withContext(Dispatchers.IO) {
                try {
                    // Get from search history
                    val historySuggestions =
                        searchHistory
                            .filter { it.query.contains(query, ignoreCase = true) }
                            .map { it.query }
                            .distinct()
                            .take(limit / 2)

                    // Get from popular searches
                    val popularSuggestions =
                        popularSearches
                            .filter { it.query.contains(query, ignoreCase = true) }
                            .sortedByDescending { it.count }
                            .map { it.query }
                            .distinct()
                            .take(limit / 2)

                    // Combine and deduplicate
                    val suggestions =
                        (historySuggestions + popularSuggestions)
                            .distinct()
                            .take(limit)

                    debugLogger.logDebug("RuTrackerSearchManager: Found ${suggestions.size} suggestions")
                    suggestions
                } catch (e: Exception) {
                    debugLogger.logError("RuTrackerSearchManager: Failed to get search suggestions", e)
                    emptyList()
                }
            }
        }

        /**
         * Get search history
         */
        suspend fun getSearchHistory(limit: Int = 20): List<SearchHistoryItem> =
            withContext(Dispatchers.IO) {
                searchHistory
                    .sortedByDescending { it.timestamp }
                    .take(limit)
            }

        /**
         * Get popular searches
         */
        suspend fun getPopularSearches(limit: Int = 10): List<PopularSearchItem> =
            withContext(Dispatchers.IO) {
                popularSearches
                    .sortedByDescending { it.count }
                    .take(limit)
            }

        /**
         * Clear search history
         */
        suspend fun clearSearchHistory() {
            withContext(Dispatchers.IO) {
                searchHistory.clear()
                debugLogger.logInfo("RuTrackerSearchManager: Search history cleared")
            }
        }

        /**
         * Get search analytics
         */
        suspend fun getSearchAnalytics(): SearchAnalyticsSummary =
            withContext(Dispatchers.IO) {
                val totalSearches = searchAnalytics.values.sumOf { it.totalSearches }
                val successfulSearches = searchAnalytics.values.sumOf { it.successfulSearches }
                val failedSearches = searchAnalytics.values.sumOf { it.failedSearches }
                val cachedSearches = searchAnalytics.values.sumOf { it.cachedSearches }

                val averageResultsPerSearch =
                    if (successfulSearches > 0) {
                        searchAnalytics.values.sumOf { it.totalResults }.toDouble() / successfulSearches
                    } else {
                        0.0
                    }

                val successRate =
                    if (totalSearches > 0) {
                        (successfulSearches.toDouble() / totalSearches) * 100
                    } else {
                        0.0
                    }

                val cacheHitRate =
                    if (totalSearches > 0) {
                        (cachedSearches.toDouble() / totalSearches) * 100
                    } else {
                        0.0
                    }

                SearchAnalyticsSummary(
                    totalSearches = totalSearches,
                    successfulSearches = successfulSearches,
                    failedSearches = failedSearches,
                    cachedSearches = cachedSearches,
                    averageResultsPerSearch = averageResultsPerSearch,
                    successRate = successRate,
                    cacheHitRate = cacheHitRate,
                    topQueries =
                        searchAnalytics.entries
                            .sortedByDescending { it.value.totalSearches }
                            .take(10)
                            .map { it.key },
                )
            }

        /**
         * Perform direct search
         */
        private suspend fun performDirectSearch(
            query: String,
            categoryId: Int?,
            page: Int,
        ): SearchResult {
            val result = apiService.searchAudiobooks(query, categoryId, page)

            return if (result.isSuccess) {
                SearchResult(
                    query = query,
                    categoryId = categoryId,
                    page = page,
                    results = result.getOrNull() ?: emptyList(),
                    totalResults = result.getOrNull()?.size ?: 0,
                    strategy = SearchStrategy.DirectSearch,
                    fromCache = false,
                )
            } else {
                SearchResult(
                    query = query,
                    categoryId = categoryId,
                    page = page,
                    results = emptyList(),
                    totalResults = 0,
                    strategy = SearchStrategy.DirectSearch,
                    fromCache = false,
                    error = result.exceptionOrNull()?.message,
                )
            }
        }

        /**
         * Perform category browsing fallback
         */
        private suspend fun performCategoryBrowsing(
            query: String,
            categoryId: Int?,
            page: Int,
        ): SearchResult {
            try {
                // Get categories if not provided
                val targetCategoryId = categoryId ?: getAudiobookCategoryId()

                // Browse category
                val categoryResults = browseCategory(targetCategoryId, page)

                // Filter results based on query
                val filteredResults =
                    categoryResults.filter { result ->
                        result.title.contains(query, ignoreCase = true) ||
                            result.author.contains(query, ignoreCase = true)
                    }

                return SearchResult(
                    query = query,
                    categoryId = targetCategoryId,
                    page = page,
                    results = filteredResults,
                    totalResults = filteredResults.size,
                    strategy = SearchStrategy.CategoryBrowsing,
                    fromCache = false,
                )
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerSearchManager: Category browsing failed", e)
                throw e
            }
        }

        /**
         * Perform keyword extraction search
         */
        private suspend fun performKeywordExtractionSearch(
            query: String,
            categoryId: Int?,
            page: Int,
        ): SearchResult {
            try {
                // Extract keywords from query
                val keywords = extractKeywords(query)

                if (keywords.isEmpty()) {
                    return SearchResult(
                        query = query,
                        categoryId = categoryId,
                        page = page,
                        results = emptyList(),
                        totalResults = 0,
                        strategy = SearchStrategy.KeywordExtraction,
                        fromCache = false,
                    )
                }

                // Search for each keyword and combine results
                val allResults = mutableListOf<RuTrackerSearchResult>()

                for (keyword in keywords) {
                    val result = apiService.searchAudiobooks(keyword, categoryId, 0)
                    if (result.isSuccess) {
                        allResults.addAll(result.getOrNull() ?: emptyList())
                    }
                }

                // Remove duplicates and sort by relevance
                val uniqueResults =
                    allResults
                        .distinctBy { it.topicId }
                        .sortedByDescending { calculateRelevanceScore(it, query) }
                        .take(MAX_SEARCH_RESULTS)

                return SearchResult(
                    query = query,
                    categoryId = categoryId,
                    page = page,
                    results = uniqueResults,
                    totalResults = uniqueResults.size,
                    strategy = SearchStrategy.KeywordExtraction,
                    fromCache = false,
                )
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerSearchManager: Keyword extraction search failed", e)
                throw e
            }
        }

        /**
         * Perform fuzzy matching search
         */
        private suspend fun performFuzzyMatchingSearch(
            query: String,
            categoryId: Int?,
            page: Int,
        ): SearchResult {
            try {
                // Generate fuzzy search terms
                val fuzzyTerms = generateFuzzyTerms(query)

                if (fuzzyTerms.isEmpty()) {
                    return SearchResult(
                        query = query,
                        categoryId = categoryId,
                        page = page,
                        results = emptyList(),
                        totalResults = 0,
                        strategy = SearchStrategy.FuzzyMatching,
                        fromCache = false,
                    )
                }

                // Search for each fuzzy term
                val allResults = mutableListOf<RuTrackerSearchResult>()

                for (term in fuzzyTerms) {
                    val result = apiService.searchAudiobooks(term, categoryId, 0)
                    if (result.isSuccess) {
                        allResults.addAll(result.getOrNull() ?: emptyList())
                    }
                }

                // Remove duplicates and sort by fuzzy match score
                val uniqueResults =
                    allResults
                        .distinctBy { it.topicId }
                        .sortedByDescending { calculateFuzzyMatchScore(it, query) }
                        .take(MAX_SEARCH_RESULTS)

                return SearchResult(
                    query = query,
                    categoryId = categoryId,
                    page = page,
                    results = uniqueResults,
                    totalResults = uniqueResults.size,
                    strategy = SearchStrategy.FuzzyMatching,
                    fromCache = false,
                )
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerSearchManager: Fuzzy matching search failed", e)
                throw e
            }
        }

        /**
         * Get audiobook category ID
         */
        private suspend fun getAudiobookCategoryId(): Int =
            try {
                val categories = apiService.getCategories()
                if (categories.isSuccess) {
                    val audiobookCategory =
                        categories
                            .getOrNull()
                            ?.find { it.name.contains("Аудиокниги", ignoreCase = true) }
                    audiobookCategory?.id ?: 33 // Default audiobook category ID
                } else {
                    33 // Default audiobook category ID
                }
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerSearchManager: Failed to get audiobook category ID", e)
                33 // Default audiobook category ID
            }

        /**
         * Browse category for results
         */
        private suspend fun browseCategory(
            categoryId: Int,
            page: Int,
        ): List<RuTrackerSearchResult> {
            // This would need to be implemented based on the actual API
            // For now, return empty list
            return emptyList()
        }

        /**
         * Extract keywords from query
         */
        private fun extractKeywords(query: String): List<String> {
            // Simple keyword extraction - split by spaces and filter out common words
            val commonWords = setOf("и", "в", "на", "с", "по", "для", "не", "что", "как", "все", "или")

            return query
                .split(Regex("\\s+"))
                .filter { it.length > 2 && !commonWords.contains(it.lowercase()) }
                .distinct()
                .take(5) // Limit to 5 keywords
        }

        /**
         * Generate fuzzy search terms
         */
        private fun generateFuzzyTerms(query: String): List<String> {
            // Simple fuzzy term generation - use parts of the query
            val terms = mutableListOf<String>()

            // Add individual words
            terms.addAll(query.split(Regex("\\s+")).filter { it.length > 2 })

            // Add first part of query
            if (query.length > 5) {
                terms.add(query.substring(0, query.length / 2))
            }

            // Add last part of query
            if (query.length > 5) {
                terms.add(query.substring(query.length / 2))
            }

            return terms.distinct().take(3) // Limit to 3 fuzzy terms
        }

        /**
         * Calculate relevance score
         */
        private fun calculateRelevanceScore(
            result: RuTrackerSearchResult,
            query: String,
        ): Double {
            var score = 0.0

            // Title match
            if (result.title.contains(query, ignoreCase = true)) {
                score += 10.0
            }

            // Author match
            if (result.author.contains(query, ignoreCase = true)) {
                score += 5.0
            }

            // Seeders bonus
            score += result.seeders * 0.1

            // Size bonus (larger files might be more complete)
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

            // Simple fuzzy matching based on word overlap
            val queryWords = query.lowercase().split(Regex("\\s+"))
            val titleWords = result.title.lowercase().split(Regex("\\s+"))
            val authorWords = result.author.lowercase().split(Regex("\\s+"))

            // Count matching words
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

            // Seeders bonus
            score += result.seeders * 0.1

            return score
        }

        /**
         * Get cached search results
         */
        private suspend fun getCachedSearchResults(
            query: String,
            categoryId: Int?,
            page: Int,
        ): SearchResult? =
            try {
                val cacheKey =
                    CacheKey(
                        namespace = "search",
                        key = "search_${query}_${categoryId}_$page",
                        version = 1,
                    )

                val cachedResult =
                    cacheManager.get(cacheKey) { json ->
                        Json.decodeFromString<SearchResult>(json)
                    }

                if (cachedResult.isSuccess) {
                    cachedResult.getOrNull()
                } else {
                    null
                }
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerSearchManager: Failed to get cached search results", e)
                null
            }

        /**
         * Cache search results
         */
        private suspend fun cacheSearchResults(
            query: String,
            categoryId: Int?,
            page: Int,
            result: SearchResult,
        ) {
            try {
                val cacheKey =
                    CacheKey(
                        namespace = "search",
                        key = "search_${query}_${categoryId}_$page",
                        version = 1,
                    )

                cacheManager.put(
                    key = cacheKey,
                    data = result,
                    ttl = SEARCH_CACHE_TTL,
                    serializer = { Json.encodeToString(it) },
                )

                debugLogger.logDebug("RuTrackerSearchManager: Search results cached")
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerSearchManager: Failed to cache search results", e)
            }
        }

        /**
         * Record search attempt
         */
        private suspend fun recordSearchAttempt(
            query: String,
            categoryId: Int?,
        ) {
            val analyticsKey = "${query}_$categoryId"

            searchAnalytics.getOrPut(analyticsKey) { SearchAnalytics() }.apply {
                totalSearches++
                lastSearchTime = System.currentTimeMillis()
            }

            // Add to search history
            searchHistory.add(
                SearchHistoryItem(
                    query = query,
                    categoryId = categoryId,
                    timestamp = System.currentTimeMillis(),
                ),
            )

            // Limit search history size
            if (searchHistory.size > MAX_CACHED_SEARCHES) {
                searchHistory.removeAt(0)
            }

            // Update popular searches
            val popularSearch = popularSearches.find { it.query == query }
            if (popularSearch != null) {
                popularSearch.count++
            } else {
                popularSearches.add(PopularSearchItem(query = query, count = 1))
            }

            // Sort popular searches by count
            popularSearches.sortByDescending { it.count }
        }

        /**
         * Record search success
         */
        private suspend fun recordSearchSuccess(
            query: String,
            categoryId: Int?,
            resultCount: Int,
            fromCache: Boolean,
        ) {
            val analyticsKey = "${query}_$categoryId"

            searchAnalytics.getOrPut(analyticsKey) { SearchAnalytics() }.apply {
                successfulSearches++
                totalResults += resultCount
                if (fromCache) {
                    cachedSearches++
                }
                lastSuccessTime = System.currentTimeMillis()
            }
        }

        /**
         * Record search failure
         */
        private suspend fun recordSearchFailure(
            query: String,
            categoryId: Int?,
        ) {
            val analyticsKey = "${query}_$categoryId"

            searchAnalytics.getOrPut(analyticsKey) { SearchAnalytics() }.apply {
                failedSearches++
                lastFailureTime = System.currentTimeMillis()
            }
        }

        /**
         * Search strategies
         */
        enum class SearchStrategy {
            DirectSearch,
            CategoryBrowsing,
            KeywordExtraction,
            FuzzyMatching,
        }

        /**
         * Search result data class
         */
        data class SearchResult(
            val query: String,
            val categoryId: Int?,
            val page: Int,
            val results: List<RuTrackerSearchResult>,
            val totalResults: Int,
            val strategy: SearchStrategy,
            val fromCache: Boolean,
            val error: String? = null,
        )

        /**
         * Search analytics data class
         */
        data class SearchAnalytics(
            var totalSearches: Int = 0,
            var successfulSearches: Int = 0,
            var failedSearches: Int = 0,
            var cachedSearches: Int = 0,
            var totalResults: Int = 0,
            var lastSearchTime: Long = 0,
            var lastSuccessTime: Long = 0,
            var lastFailureTime: Long = 0,
        )

        /**
         * Search analytics summary data class
         */
        data class SearchAnalyticsSummary(
            val totalSearches: Int,
            val successfulSearches: Int,
            val failedSearches: Int,
            val cachedSearches: Int,
            val averageResultsPerSearch: Double,
            val successRate: Double,
            val cacheHitRate: Double,
            val topQueries: List<String>,
        )

        /**
         * Search history item data class
         */
        data class SearchHistoryItem(
            val query: String,
            val categoryId: Int?,
            val timestamp: Long,
        )

        /**
         * Popular search item data class
         */
        data class PopularSearchItem(
            val query: String,
            var count: Int,
        )
    }
