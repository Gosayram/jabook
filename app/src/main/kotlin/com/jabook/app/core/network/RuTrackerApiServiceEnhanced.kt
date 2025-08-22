package com.jabook.app.core.network

import com.jabook.app.core.cache.CacheKey
import com.jabook.app.core.cache.CacheStatistics
import com.jabook.app.core.cache.RuTrackerCacheManager
import com.jabook.app.core.circuitbreaker.CircuitBreaker
import com.jabook.app.core.circuitbreaker.CircuitBreakerState
import com.jabook.app.core.circuitbreaker.CircuitBreakerOpenException
import com.jabook.app.core.domain.model.RuTrackerCategory
import com.jabook.app.core.domain.model.RuTrackerSearchResult
import com.jabook.app.core.network.RuTrackerParserEnhanced.RuTrackerTorrentDetails
import com.jabook.app.core.network.RuTrackerParserImproved
import com.jabook.app.core.network.domain.RuTrackerDomainManager
import com.jabook.app.core.network.errorhandler.RuTrackerErrorHandler
import com.jabook.app.core.network.exceptions.CategoriesUnavailableException
import com.jabook.app.core.network.exceptions.DetailsUnavailableException
import com.jabook.app.core.network.exceptions.DomainUnavailableException
import com.jabook.app.core.network.exceptions.NetworkException
import com.jabook.app.core.network.exceptions.ParseException
import com.jabook.app.core.network.exceptions.SearchUnavailableException
import com.jabook.app.core.network.extractors.AuthorExtractor
import com.jabook.app.core.network.extractors.CategoryExtractor
import com.jabook.app.core.network.extractors.DescriptionExtractor
import com.jabook.app.core.network.extractors.DetailsExtractor
import com.jabook.app.core.network.extractors.TitleExtractor
import com.jabook.app.core.network.extractors.TopicIdExtractor
import com.jabook.app.shared.debug.IDebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced RuTracker API Service with domain switching, caching, and retry logic
 */
@Singleton
class RuTrackerApiServiceEnhanced
    @Inject
    constructor(
        private val httpClient: OkHttpClient,
        private val domainManager: RuTrackerDomainManager,
        private val cacheManager: RuTrackerCacheManager,
        private val errorHandler: RuTrackerErrorHandler,
        private val debugLogger: IDebugLogger,
        private val parser: RuTrackerParserImproved,
        private val titleExtractor: TitleExtractor,
        private val authorExtractor: AuthorExtractor,
        private val descriptionExtractor: DescriptionExtractor,
        private val categoryExtractor: CategoryExtractor,
        private val detailsExtractor: DetailsExtractor,
        private val topicIdExtractor: TopicIdExtractor,
    ) {
        companion object {
            private const val SEARCH_PATH = "/forum/tracker.php"
            private const val TOPIC_PATH = "/forum/viewtopic.php"
            private const val CATEGORIES_PATH = "/forum/index.php"
            private const val FORUM_PATH = "/forum/viewforum.php"
            private const val LOGIN_PATH = "/forum/login.php"
            private const val DOWNLOAD_PATH = "/forum/dl.php"
            private const val MAGNET_LINK_PATH = "/forum/viewtopic.php"

            private const val DEFAULT_TIMEOUT = 30L // seconds
            private const val MAX_RETRIES = 3
            private const val BASE_RETRY_DELAY = 1000L // milliseconds
            private const val RATE_LIMIT_DELAY = 2000L // milliseconds between requests
            private const val CACHE_TTL_SEARCH = 5 * 60 * 1000L // 5 minutes
            private const val CACHE_TTL_CATEGORIES = 30 * 60 * 1000L // 30 minutes
            private const val CACHE_TTL_DETAILS = 10 * 60 * 1000L // 10 minutes

            private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        }

        // Rate limiting
        private val lastRequestTime = mutableMapOf<String, Long>()
        private val requestCounter = AtomicInteger(0)

        // Circuit breakers for different operations
        private val searchCircuitBreaker =
            CircuitBreaker(
                failureThreshold = 5,
                timeout = 60_000L,
                resetTimeout = 300_000L,
            )

        private val detailsCircuitBreaker =
            CircuitBreaker(
                failureThreshold = 3,
                timeout = 30_000L,
                resetTimeout = 120_000L,
            )

        private val categoriesCircuitBreaker =
            CircuitBreaker(
                failureThreshold = 2,
                timeout = 20_000L,
                resetTimeout = 60_000L,
            )

        /**
         * Search for audiobooks with domain switching, caching, and retry logic
         */
        suspend fun searchAudiobooks(
            query: String,
            categoryId: Int? = null,
            page: Int = 0,
            forceRefresh: Boolean = false,
        ): Result<List<RuTrackerSearchResult>> =
            withContext(Dispatchers.IO) {
                debugLogger.logDebug("RuTrackerApiServiceEnhanced: Searching for audiobooks with query: $query")

                // Check cache first
                if (!forceRefresh) {
                    val cacheKey =
                        CacheKey(
                            namespace = "search",
                            key = "search_${query}_${categoryId}_$page",
                            version = 1,
                        )

                    val cachedResult =
                        cacheManager.get(cacheKey) { json ->
                            Json.decodeFromString<List<RuTrackerSearchResult>>(json)
                        }

                    if (cachedResult.isSuccess) {
                        debugLogger.logDebug("RuTrackerApiServiceEnhanced: Search result found in cache")
                        return@withContext cachedResult
                    }
                }

                // Execute with circuit breaker
                return searchCircuitBreaker
                    .execute {
                        executeWithDomainFallback { baseUrl ->
                            performSearch(baseUrl, query, categoryId, page)
                        }
                    }.map { results ->
                        // Cache successful results
                        if (results.isNotEmpty()) {
                            val cacheKey =
                                CacheKey(
                                    namespace = "search",
                                    key = "search_${query}_${categoryId}_$page",
                                    version = 1,
                                )

                            cacheManager.put(
                                key = cacheKey,
                                data = results,
                                ttl = CACHE_TTL_SEARCH,
                                serializer = { Json.encodeToString(it) },
                            )

                            debugLogger.logDebug("RuTrackerApiServiceEnhanced: Search result cached")
                        }

                        results
                    }.recover { exception ->
                        // Handle circuit breaker open state
                        if (exception is CircuitBreakerOpenException) {
                            debugLogger.logWarning("RuTrackerApiServiceEnhanced: Search circuit breaker is open")
                            Result.failure(SearchUnavailableException("Search temporarily unavailable due to repeated failures"))
                        } else {
                            Result.failure(exception)
                        }
                    }
            }

        /**
         * Get audiobook details with domain switching, caching, and retry logic
         */
        suspend fun getAudiobookDetails(
            topicId: String,
            forceRefresh: Boolean = false,
        ): Result<RuTrackerTorrentDetails> =
            withContext(Dispatchers.IO) {
                debugLogger.logDebug("RuTrackerApiServiceEnhanced: Getting details for topic: $topicId")

                // Check cache first
                if (!forceRefresh) {
                    val cacheKey =
                        CacheKey(
                            namespace = "details",
                            key = "details_$topicId",
                            version = 1,
                        )

                    val cachedResult =
                        cacheManager.get(cacheKey) { json ->
                            Json.decodeFromString<RuTrackerTorrentDetails>(json)
                        }

                    if (cachedResult.isSuccess) {
                        debugLogger.logDebug("RuTrackerApiServiceEnhanced: Details found in cache")
                        return@withContext cachedResult
                    }
                }

                // Execute with circuit breaker
                return detailsCircuitBreaker
                    .execute {
                        executeWithDomainFallback { baseUrl ->
                            performGetDetails(baseUrl, topicId)
                        }
                    }.map { details ->
                        // Cache successful results
                        val cacheKey =
                            CacheKey(
                                namespace = "details",
                                key = "details_$topicId",
                                version = 1,
                            )

                        cacheManager.put(
                            key = cacheKey,
                            data = details,
                            ttl = CACHE_TTL_DETAILS,
                            serializer = { Json.encodeToString(it) },
                        )

                        debugLogger.logDebug("RuTrackerApiServiceEnhanced: Details cached")
                        details
                    }.recover { exception ->
                        // Handle circuit breaker open state
                        if (exception is CircuitBreakerOpenException) {
                            debugLogger.logWarning("RuTrackerApiServiceEnhanced: Details circuit breaker is open")
                            Result.failure(DetailsUnavailableException("Details temporarily unavailable due to repeated failures"))
                        } else {
                            Result.failure(exception)
                        }
                    }
            }

        /**
         * Get categories with domain switching, caching, and retry logic
         */
        suspend fun getCategories(forceRefresh: Boolean = false): Result<List<RuTrackerCategory>> =
            withContext(Dispatchers.IO) {
                debugLogger.logDebug("RuTrackerApiServiceEnhanced: Getting categories")

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
                        debugLogger.logDebug("RuTrackerApiServiceEnhanced: Categories found in cache")
                        return@withContext cachedResult
                    }
                }

                // Execute with circuit breaker
                return categoriesCircuitBreaker
                    .execute {
                        executeWithDomainFallback { baseUrl ->
                            performGetCategories(baseUrl)
                        }
                    }.map { categories ->
                        // Cache successful results
                        val cacheKey =
                            CacheKey(
                                namespace = "categories",
                                key = "categories",
                                version = 1,
                            )

                        cacheManager.put(
                            key = cacheKey,
                            data = categories,
                            ttl = CACHE_TTL_CATEGORIES,
                            serializer = { Json.encodeToString(it) },
                        )

                        debugLogger.logDebug("RuTrackerApiServiceEnhanced: Categories cached")
                        categories
                    }.recover { exception ->
                        // Handle circuit breaker open state
                        if (exception is CircuitBreakerOpenException) {
                            debugLogger.logWarning("RuTrackerApiServiceEnhanced: Categories circuit breaker is open")
                            Result.failure(CategoriesUnavailableException("Categories temporarily unavailable due to repeated failures"))
                        } else {
                            Result.failure(exception)
                        }
                    }
            }

        /**
         * Get magnet link for a torrent
         */
        suspend fun getMagnetLink(
            topicId: String,
            forceRefresh: Boolean = false,
        ): Result<String> =
            withContext(Dispatchers.IO) {
                debugLogger.logDebug("RuTrackerApiServiceEnhanced: Getting magnet link for topic: $topicId")

                // Check cache first
                if (!forceRefresh) {
                    val cacheKey =
                        CacheKey(
                            namespace = "magnet",
                            key = "magnet_$topicId",
                            version = 1,
                        )

                    val cachedResult = cacheManager.get(cacheKey) { json -> json }

                    if (cachedResult.isSuccess) {
                        debugLogger.logDebug("RuTrackerApiServiceEnhanced: Magnet link found in cache")
                        return@withContext cachedResult
                    }
                }

                // Execute with domain fallback
                return executeWithDomainFallback { baseUrl ->
                    performGetMagnetLink(baseUrl, topicId)
                }.map { magnetLink ->
                    // Cache successful results
                    val cacheKey =
                        CacheKey(
                            namespace = "magnet",
                            key = "magnet_$topicId",
                            version = 1,
                        )

                    cacheManager.put(
                        key = cacheKey,
                        data = magnetLink,
                        ttl = CACHE_TTL_DETAILS,
                        serializer = { it },
                    )

                    debugLogger.logDebug("RuTrackerApiServiceEnhanced: Magnet link cached")
                    magnetLink
                }
            }

        /**
         * Login to RuTracker
         */
        suspend fun login(
            username: String,
            password: String,
        ): Result<Boolean> =
            withContext(Dispatchers.IO) {
                debugLogger.logDebug("RuTrackerApiServiceEnhanced: Attempting login for user: $username")

                executeWithDomainFallback { baseUrl ->
                    performLogin(baseUrl, username, password)
                }
            }

        /**
         * Download torrent file
         */
        suspend fun downloadTorrent(
            topicId: String,
            outputFile: java.io.File,
        ): Result<Boolean> =
            withContext(Dispatchers.IO) {
                debugLogger.logDebug("RuTrackerApiServiceEnhanced: Downloading torrent for topic: $topicId")

                executeWithDomainFallback { baseUrl ->
                    performDownloadTorrent(baseUrl, topicId, outputFile)
                }
            }

        /**
         * Execute operation with domain fallback and retry logic
         */
        private suspend fun <T> executeWithDomainFallback(operation: suspend (String) -> Result<T>): Result<T> {
            val domains = domainManager.getAvailableDomains()

            for (domain in domains) {
                try {
                    debugLogger.logDebug("RuTrackerApiServiceEnhanced: Trying domain: $domain")

                    // Apply rate limiting
                    applyRateLimiting(domain)

                    // Execute with retry logic
                    val result =
                        executeWithRetry {
                            operation("https://$domain")
                        }

                    if (result.isSuccess) {
                        debugLogger.logDebug("RuTrackerApiServiceEnhanced: Operation successful on domain: $domain")
                        return result
                    }

                    debugLogger.logWarning("RuTrackerApiServiceEnhanced: Operation failed on domain: $domain")
                } catch (e: Exception) {
                    debugLogger.logError("RuTrackerApiServiceEnhanced: Error with domain $domain", e)

                    // Report error to error handler
                    errorHandler.handleError(
                        exception = e,
                        domain = domain,
                        operation = "executeWithDomainFallback",
                    )
                }
            }

            return Result.failure(DomainUnavailableException("All domains failed"))
        }

        /**
         * Execute operation with retry logic
         */
        private suspend fun <T> executeWithRetry(
            operation: suspend () -> Result<T>,
            maxRetries: Int = MAX_RETRIES,
        ): Result<T> {
            var lastException: Exception? = null

            for (attempt in 1..maxRetries) {
                try {
                    val result = operation()
                    if (result.isSuccess) {
                        return result
                    }

                    lastException = result.exceptionOrNull() ?: Exception("Unknown error")
                } catch (e: Exception) {
                    lastException = e
                }

                if (attempt < maxRetries) {
                    val delay = calculateRetryDelay(attempt)
                    debugLogger.logDebug("RuTrackerApiServiceEnhanced: Retry $attempt/$maxRetries after ${delay}ms")
                    delay(delay)
                }
            }

            return Result.failure(lastException ?: Exception("All retries failed"))
        }

        /**
         * Calculate retry delay with exponential backoff
         */
        private fun calculateRetryDelay(attempt: Int): Long = BASE_RETRY_DELAY * (1L shl (attempt - 1))

        /**
         * Apply rate limiting
         */
        private suspend fun applyRateLimiting(domain: String) {
            val now = System.currentTimeMillis()
            val lastRequest = lastRequestTime[domain] ?: 0

            val timeSinceLastRequest = now - lastRequest
            if (timeSinceLastRequest < RATE_LIMIT_DELAY) {
                val delay = RATE_LIMIT_DELAY - timeSinceLastRequest
                debugLogger.logDebug("RuTrackerApiServiceEnhanced: Rate limiting, delaying for ${delay}ms")
                delay(delay)
            }

            lastRequestTime[domain] = System.currentTimeMillis()
        }

        /**
         * Perform search operation
         */
        private suspend fun performSearch(
            baseUrl: String,
            query: String,
            categoryId: Int?,
            page: Int,
        ): Result<List<RuTrackerSearchResult>> {
            try {
                val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
                val urlBuilder =
                    "$baseUrl$SEARCH_PATH".toHttpUrlOrNull()?.newBuilder()
                        ?: return Result.failure(NetworkException("Invalid URL"))

                urlBuilder.addQueryParameter("nm", encodedQuery)

                if (categoryId != null) {
                    urlBuilder.addQueryParameter("f", categoryId.toString())
                }

                if (page > 0) {
                    urlBuilder.addQueryParameter("start", (page * 50).toString())
                }

                val request =
                    Request
                        .Builder()
                        .url(urlBuilder.build())
                        .addDefaultHeaders()
                        .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    return Result.failure(NetworkException("HTTP ${response.code}: ${response.message}"))
                }

                val searchResults = parser.parseSearchResults(responseBody)

                if (searchResults.isEmpty()) {
                    debugLogger.logWarning("RuTrackerApiServiceEnhanced: No search results found")
                }

                return Result.success(searchResults)
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerApiServiceEnhanced: Search failed", e)
                return Result.failure(e)
            }
        }

        /**
         * Perform get details operation
         */
        private suspend fun performGetDetails(
            baseUrl: String,
            topicId: String,
        ): Result<RuTrackerTorrentDetails> {
            try {
                val url = "${baseUrl}$TOPIC_PATH?t=$topicId"

                val request =
                    Request
                        .Builder()
                        .url(url)
                        .addDefaultHeaders()
                        .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    return Result.failure(NetworkException("HTTP ${response.code}: ${response.message}"))
                }

                val details = parser.parseTorrentDetails(responseBody)

                return Result.success(details)
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerApiServiceEnhanced: Get details failed", e)
                return Result.failure(e)
            }
        }

        /**
         * Perform get categories operation
         */
        private suspend fun performGetCategories(baseUrl: String): Result<List<RuTrackerCategory>> {
            try {
                val url = "${baseUrl}$CATEGORIES_PATH"

                val request =
                    Request
                        .Builder()
                        .url(url)
                        .addDefaultHeaders()
                        .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    return Result.failure(NetworkException("HTTP ${response.code}: ${response.message}"))
                }

                val categories = parser.parseCategories(responseBody)

                return Result.success(categories)
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerApiServiceEnhanced: Get categories failed", e)
                return Result.failure(e)
            }
        }

        /**
         * Perform get magnet link operation
         */
        private suspend fun performGetMagnetLink(
            baseUrl: String,
            topicId: String,
        ): Result<String> {
            try {
                val url = "${baseUrl}$TOPIC_PATH?t=$topicId"

                val request =
                    Request
                        .Builder()
                        .url(url)
                        .addDefaultHeaders()
                        .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    return Result.failure(NetworkException("HTTP ${response.code}: ${response.message}"))
                }

                val magnetLink = parser.parseMagnetLink(responseBody)

                if (magnetLink.isNullOrEmpty()) {
                    return Result.failure(ParseException("Magnet link not found"))
                }

                return Result.success(magnetLink)
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerApiServiceEnhanced: Get magnet link failed", e)
                return Result.failure(e)
            }
        }

        /**
         * Perform login operation
         */
        private suspend fun performLogin(
            baseUrl: String,
            username: String,
            password: String,
        ): Result<Boolean> {
            try {
                // First, get login page to extract CSRF token
                val loginPageUrl = "${baseUrl}$LOGIN_PATH"

                val loginPageRequest =
                    Request
                        .Builder()
                        .url(loginPageUrl)
                        .addDefaultHeaders()
                        .build()

                val loginPageResponse = httpClient.newCall(loginPageRequest).execute()
                val loginPageBody = loginPageResponse.body?.string() ?: ""

                if (!loginPageResponse.isSuccessful) {
                    return Result.failure(NetworkException("Failed to get login page"))
                }

                // Extract CSRF token (simplified - in real implementation, use proper parsing)
                val csrfToken =
                    extractCsrfToken(loginPageBody)
                        ?: return Result.failure(ParseException("CSRF token not found"))

                // Perform login
                val loginForm =
                    FormBody
                        .Builder()
                        .add("login_username", username)
                        .add("login_password", password)
                        .add("login", "Вход")
                        .add("form_token", csrfToken)
                        .add("redirect", "")
                        .add("autologin", "on")
                        .build()

                val loginRequest =
                    Request
                        .Builder()
                        .url(loginPageUrl)
                        .post(loginForm)
                        .addDefaultHeaders()
                        .addHeader("Content-Type", "application/x-www-form-urlencoded")
                        .addHeader("Origin", baseUrl)
                        .addHeader("Referer", loginPageUrl)
                        .build()

                val loginResponse = httpClient.newCall(loginRequest).execute()

                return Result.success(loginResponse.isSuccessful)
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerApiServiceEnhanced: Login failed", e)
                return Result.failure(e)
            }
        }

        /**
         * Perform download torrent operation
         */
        private suspend fun performDownloadTorrent(
            baseUrl: String,
            topicId: String,
            outputFile: java.io.File,
        ): Result<Boolean> {
            try {
                val url = "${baseUrl}$DOWNLOAD_PATH?t=$topicId"

                val request =
                    Request
                        .Builder()
                        .url(url)
                        .addDefaultHeaders()
                        .build()

                val response = httpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    return Result.failure(NetworkException("HTTP ${response.code}: ${response.message}"))
                }

                response.body?.byteStream()?.use { inputStream ->
                    outputFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                return Result.success(true)
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerApiServiceEnhanced: Download torrent failed", e)
                return Result.failure(e)
            }
        }

        /**
         * Extract CSRF token from login page (simplified implementation)
         */
        private fun extractCsrfToken(html: String): String? {
            // This is a simplified implementation
            // In a real implementation, use proper HTML parsing
            val pattern = Regex("name=\"form_token\"\\s+value=\"([^\"]+)\"")
            val matchResult = pattern.find(html)
            return matchResult?.groupValues?.get(1)
        }

        /**
         * Add default headers to request
         */
        private fun Request.Builder.addDefaultHeaders(): Request.Builder =
            this
                .addHeader(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                ).addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .addHeader("Accept-Encoding", "gzip, deflate, br")
                .addHeader("Connection", "keep-alive")
                .addHeader("Upgrade-Insecure-Requests", "1")
                .addHeader("Sec-Fetch-Dest", "document")
                .addHeader("Sec-Fetch-Mode", "navigate")
                .addHeader("Sec-Fetch-Site", "none")
                .addHeader("Sec-Fetch-User", "?1")
                .addHeader("Cache-Control", "max-age=0")

        /**
         * Get circuit breaker states for monitoring
         */
        fun getCircuitBreakerStates(): Map<String, CircuitBreakerState> =
            mapOf(
                "search" to searchCircuitBreaker.getState(),
                "details" to detailsCircuitBreaker.getState(),
                "categories" to categoriesCircuitBreaker.getState(),
            )

        /**
         * Reset circuit breakers
         */
        fun resetCircuitBreakers() {
            searchCircuitBreaker.reset()
            detailsCircuitBreaker.reset()
            categoriesCircuitBreaker.reset()
            debugLogger.logInfo("RuTrackerApiServiceEnhanced: Circuit breakers reset")
        }

        /**
         * Clear cache for specific namespace
         */
        suspend fun clearCache(namespace: String): Result<Unit> =
            try {
                cacheManager.clearNamespace(namespace)
                debugLogger.logInfo("RuTrackerApiServiceEnhanced: Cache cleared for namespace: $namespace")
                Result.success(Unit)
            } catch (e: Exception) {
                debugLogger.logError("RuTrackerApiServiceEnhanced: Failed to clear cache for namespace: $namespace", e)
                Result.failure(e)
            }

        /**
         * Get cache statistics
         */
        fun getCacheStatistics(): Flow<CacheStatistics> = cacheManager.statistics

        /**
         * Enhanced cache statistics with detailed metrics
         */
        data class EnhancedCacheStatistics(
            val hitRate: Double,
            val missRate: Double,
            val totalRequests: Long,
            val totalHits: Long,
            val totalMisses: Long,
            val averageResponseTime: Long,
            val cacheSize: Int,
        )

        /**
         * Performance metrics for API operations
         */
        data class ApiOperationMetrics(
            var operation: String,
            var totalRequests: Int = 0,
            var successfulRequests: Int = 0,
            var failedRequests: Int = 0,
            var averageResponseTime: Long = 0,
            var fastestResponseTime: Long = Long.MAX_VALUE,
            var slowestResponseTime: Long = 0,
            var cacheHits: Int = 0,
            var cacheMisses: Int = 0,
            var lastRequestTime: Long = 0,
        )

        private val operationMetrics = mutableMapOf<String, ApiOperationMetrics>()
        private val errorContexts = mutableListOf<String>()
        private val maxErrorContexts = 100

        /**
         * Get enhanced cache statistics
         */
        suspend fun getEnhancedCacheStatistics(): EnhancedCacheStatistics {
            val basicStats = cacheManager.statistics.first()
            val totalRequests = basicStats.totalRequests
            val totalHits = basicStats.hits
            val totalMisses = totalRequests - totalHits

            val hitRate = if (totalRequests > 0) totalHits.toDouble() / totalRequests else 0.0
            val missRate = if (totalRequests > 0) totalMisses.toDouble() / totalRequests else 0.0

            val averageResponseTime =
                operationMetrics.values
                    .map { it.averageResponseTime }
                    .average()
                    .toLong()

            return EnhancedCacheStatistics(
                hitRate = hitRate,
                missRate = missRate,
                totalRequests = totalRequests,
                totalHits = totalHits,
                totalMisses = totalMisses,
                averageResponseTime = averageResponseTime,
                cacheSize = cacheManager.getSize().toInt(),
            )
        }

        /**
         * Get operation metrics
         */
        fun getOperationMetrics(): Map<String, ApiOperationMetrics> = operationMetrics.toMap()

        /**
         * Get error contexts for debugging
         */
        fun getErrorContexts(limit: Int = 20): List<String> = errorContexts.takeLast(limit)

        /**
         * Clear error contexts
         */
        fun clearErrorContexts() {
            errorContexts.clear()
            debugLogger.logInfo("RuTrackerApiServiceEnhanced: Error contexts cleared")
        }

        /**
         * Update operation metrics
         */
        private fun updateOperationMetrics(
            operation: String,
            success: Boolean,
            responseTime: Long,
            cacheHit: Boolean = false,
        ) {
            val metrics = operationMetrics.getOrPut(operation) { ApiOperationMetrics(operation) }

            metrics.totalRequests++
            metrics.lastRequestTime = System.currentTimeMillis()

            if (success) {
                metrics.successfulRequests++
                metrics.averageResponseTime =
                    calculateAverage(
                        metrics.averageResponseTime,
                        metrics.successfulRequests - 1,
                        responseTime,
                    )
                metrics.fastestResponseTime = minOf(metrics.fastestResponseTime, responseTime)
            } else {
                metrics.failedRequests++
                metrics.slowestResponseTime = maxOf(metrics.slowestResponseTime, responseTime)
            }

            if (cacheHit) {
                metrics.cacheHits++
            } else {
                metrics.cacheMisses++
            }

            operationMetrics[operation] = metrics
        }

        /**
         * Record error context
         */
        private fun recordErrorContext(context: String) {
            errorContexts.add(context)

            // Keep only recent error contexts
            if (errorContexts.size > maxErrorContexts) {
                errorContexts.removeAt(0)
            }
        }

        /**
         * Enhanced search with improved error handling and metrics
         */
        suspend fun searchAudiobooksEnhanced(
            query: String,
            categoryId: Int? = null,
            page: Int = 0,
            forceRefresh: Boolean = false,
            timeout: Long = DEFAULT_TIMEOUT,
        ): Result<List<RuTrackerSearchResult>> =
            withContext(Dispatchers.IO) {
                val startTime = System.currentTimeMillis()
                val operation = "search"

                debugLogger.logDebug("RuTrackerApiServiceEnhanced: Enhanced search for query: $query")

                try {
                    // Check cache first
                    if (!forceRefresh) {
                        val cacheKey =
                            CacheKey(
                                namespace = "search",
                                key = "search_${query}_${categoryId}_$page",
                                version = 1,
                            )

                        val cachedResult =
                            cacheManager.get(cacheKey) { json ->
                                Json.decodeFromString<List<RuTrackerSearchResult>>(json)
                            }

                        if (cachedResult.isSuccess) {
                            val responseTime = System.currentTimeMillis() - startTime
                            updateOperationMetrics(operation, true, responseTime, true)
                            debugLogger.logDebug("RuTrackerApiServiceEnhanced: Search result found in cache")
                            return@withContext cachedResult
                        }
                    }

                    // Execute with circuit breaker
                    val result =
                        searchCircuitBreaker
                            .execute {
                                executeWithDomainFallback { baseUrl ->
                                    performSearch(baseUrl, query, categoryId, page)
                                }
                            }.map { results ->
                                // Cache successful results
                                if (results.isNotEmpty()) {
                                    val cacheKey =
                                        CacheKey(
                                            namespace = "search",
                                            key = "search_${query}_${categoryId}_$page",
                                            version = 1,
                                        )

                                    cacheManager.put(
                                        key = cacheKey,
                                        data = results,
                                        ttl = CACHE_TTL_SEARCH,
                                        serializer = { Json.encodeToString(it) },
                                    )

                                    debugLogger.logDebug("RuTrackerApiServiceEnhanced: Search result cached")
                                }

                                results
                            }.recover { exception ->
                                // Handle circuit breaker open state
                                if (exception is CircuitBreakerOpenException) {
                                    debugLogger.logWarning("RuTrackerApiServiceEnhanced: Search circuit breaker is open")
                                    Result.failure(SearchUnavailableException("Search temporarily unavailable due to repeated failures"))
                                } else {
                                    Result.failure(exception)
                                }
                            }

                    val responseTime = System.currentTimeMillis() - startTime
                    updateOperationMetrics(operation, result.isSuccess, responseTime, false)

                    result
                } catch (e: Exception) {
                    val responseTime = System.currentTimeMillis() - startTime
                    updateOperationMetrics(operation, false, responseTime, false)

                    val errorContext = "Operation: $operation, Error: ${e.message}, Time: ${System.currentTimeMillis()}"
                    recordErrorContext(errorContext)
                    debugLogger.logError("RuTrackerApiServiceEnhanced: Enhanced search failed", e)
                    Result.failure(e)
                }
            }

        /**
         * Enhanced get details with improved error handling and metrics
         */
        suspend fun getAudiobookDetailsEnhanced(
            topicId: String,
            forceRefresh: Boolean = false,
            timeout: Long = DEFAULT_TIMEOUT,
        ): Result<RuTrackerTorrentDetails> =
            withContext(Dispatchers.IO) {
                val startTime = System.currentTimeMillis()
                val operation = "details"

                debugLogger.logDebug("RuTrackerApiServiceEnhanced: Enhanced details for topic: $topicId")

                try {
                    // Check cache first
                    if (!forceRefresh) {
                        val cacheKey =
                            CacheKey(
                                namespace = "details",
                                key = "details_$topicId",
                                version = 1,
                            )

                        val cachedResult =
                            cacheManager.get(cacheKey) { json ->
                                Json.decodeFromString<RuTrackerTorrentDetails>(json)
                            }

                        if (cachedResult.isSuccess) {
                            val responseTime = System.currentTimeMillis() - startTime
                            updateOperationMetrics(operation, true, responseTime, true)
                            debugLogger.logDebug("RuTrackerApiServiceEnhanced: Details found in cache")
                            return@withContext cachedResult
                        }
                    }

                    // Execute with circuit breaker
                    val result =
                        detailsCircuitBreaker
                            .execute {
                                executeWithDomainFallback { baseUrl ->
                                    performGetDetails(baseUrl, topicId)
                                }
                            }.map { details ->
                                // Cache successful results
                                val cacheKey =
                                    CacheKey(
                                        namespace = "details",
                                        key = "details_$topicId",
                                        version = 1,
                                    )

                                cacheManager.put(
                                    key = cacheKey,
                                    data = details,
                                    ttl = CACHE_TTL_DETAILS,
                                    serializer = { Json.encodeToString(it) },
                                )

                                debugLogger.logDebug("RuTrackerApiServiceEnhanced: Details cached")
                                details
                            }.recover { exception ->
                                // Handle circuit breaker open state
                                if (exception is CircuitBreakerOpenException) {
                                    debugLogger.logWarning("RuTrackerApiServiceEnhanced: Details circuit breaker is open")
                                    Result.failure(DetailsUnavailableException("Details temporarily unavailable due to repeated failures"))
                                } else {
                                    Result.failure(exception)
                                }
                            }

                    val responseTime = System.currentTimeMillis() - startTime
                    updateOperationMetrics(operation, result.isSuccess, responseTime, false)

                    result
                } catch (e: Exception) {
                    val responseTime = System.currentTimeMillis() - startTime
                    updateOperationMetrics(operation, false, responseTime, false)

                    val errorContext = "Operation: $operation, Error: ${e.message}, Time: ${System.currentTimeMillis()}"
                    recordErrorContext(errorContext)
                    debugLogger.logError("RuTrackerApiServiceEnhanced: Enhanced details failed", e)
                    Result.failure(e)
                }
            }

        /**
         * Export performance data for analytics
         */
        suspend fun exportPerformanceData(): Map<String, Any> =
            mapOf(
                "timestamp" to System.currentTimeMillis(),
                "operationMetrics" to operationMetrics,
                "errorContexts" to errorContexts.takeLast(50),
                "cacheStatistics" to getEnhancedCacheStatistics(),
                "circuitBreakerStates" to getCircuitBreakerStates(),
            )

        /**
         * Calculate running average
         */
        private fun calculateAverage(
            currentAverage: Long,
            count: Int,
            newValue: Long,
        ): Long = if (count == 0) newValue else (currentAverage * count + newValue) / (count + 1)
    }
