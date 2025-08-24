package com.jabook.app.core.network

import com.jabook.app.core.cache.CacheKey
import com.jabook.app.core.cache.CacheStatistics
import com.jabook.app.core.cache.RuTrackerCacheManager
import com.jabook.app.core.domain.model.RuTrackerCategory
import com.jabook.app.core.domain.model.RuTrackerAudiobook
import com.jabook.app.core.network.domain.RuTrackerDomainManager
import com.jabook.app.core.network.errorhandler.RuTrackerErrorHandler
import com.jabook.app.shared.debug.IDebugLogger
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced RuTracker API Service with domain switching, caching, and retry logic
 *
 * Нюансы:
 * - В проекте обнаружен тип RuTrackerAudiobook (а не RuTrackerSearchResult), поэтому search возвращает List<RuTrackerAudiobook>.
 * - В некоторых модулях могут отсутствовать исключения/методы; для совместимости добавлены локальные Exception-классы
 *   и «безопасные» вызовы через рефлексию.
 */
@Singleton
class RuTrackerApiServiceEnhanced @Inject constructor(
  private val httpClient: OkHttpClient,
  private val domainManager: RuTrackerDomainManager,
  private val cacheManager: RuTrackerCacheManager,
  private val errorHandler: RuTrackerErrorHandler,
  private val debugLogger: IDebugLogger,
  // Парсер оставляем, но на критичных местах есть fallback
  private val parser: RuTrackerParserImproved,
) {

  // -------------------- Локальные типы/исключения для совместимости --------------------

  /** Детали торрента (минимальный набор полей). Если в проекте есть своя модель – можно сделать typealias. */
  data class RuTrackerTorrentDetails(
    val topicId: String,
    val title: String? = null,
    val magnet: String? = null,
    val size: String? = null,
    val seeds: Int? = null,
    val leeches: Int? = null,
    val author: String? = null,
    val postedAt: String? = null,
  )

  /** Если модуль ошибок отсутствует – используем локальные исключения с теми же именами. */
  open class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)
  class ParseException(message: String) : Exception(message)
  class DomainUnavailableException(message: String) : Exception(message)
  class SearchUnavailableException(message: String) : Exception(message)
  class DetailsUnavailableException(message: String) : Exception(message)
  class CategoriesUnavailableException(message: String) : Exception(message)

  companion object {
    private const val SEARCH_PATH = "/forum/tracker.php"
    private const val TOPIC_PATH = "/forum/viewtopic.php"
    private const val CATEGORIES_PATH = "/forum/index.php"
    private const val LOGIN_PATH = "/forum/login.php"
    private const val DOWNLOAD_PATH = "/forum/dl.php"

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

  // ---- Circuit Breakers (Resilience4j) ----
  private val searchCircuitBreaker: CircuitBreaker = CircuitBreaker.of(
    "search",
    CircuitBreakerConfig.custom()
      .failureRateThreshold(50f)
      .slidingWindowSize(10)
      .waitDurationInOpenState(Duration.ofMillis(60_000L))
      .permittedNumberOfCallsInHalfOpenState(3)
      .build()
  )

  private val detailsCircuitBreaker: CircuitBreaker = CircuitBreaker.of(
    "details",
    CircuitBreakerConfig.custom()
      .failureRateThreshold(50f)
      .slidingWindowSize(10)
      .waitDurationInOpenState(Duration.ofMillis(30_000L))
      .permittedNumberOfCallsInHalfOpenState(3)
      .build()
  )

  private val categoriesCircuitBreaker: CircuitBreaker = CircuitBreaker.of(
    "categories",
    CircuitBreakerConfig.custom()
      .failureRateThreshold(50f)
      .slidingWindowSize(10)
      .waitDurationInOpenState(Duration.ofMillis(20_000L))
      .permittedNumberOfCallsInHalfOpenState(2)
      .build()
  )

  /**
   * Helper to execute a suspending block under a Resilience4j CircuitBreaker.
   * Works with Result<T>.
   */
  private suspend fun <T> withBreaker(
    breaker: CircuitBreaker,
    block: suspend () -> Result<T>,
  ): Result<T> {
    if (!breaker.tryAcquirePermission()) {
      return Result.failure(NetworkException("Circuit is open"))
    }
    val start = System.nanoTime()
    return try {
      val result = block()
      if (result.isSuccess) {
        breaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS)
      } else {
        breaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, result.exceptionOrNull() ?: Exception("Unknown"))
      }
      result
    } catch (t: Throwable) {
      breaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, t)
      Result.failure(NetworkException(t.message ?: "Unknown error", t))
    }
  }

  // -------------------- Public API --------------------

  /** Поиск – возвращаем то, что реально есть в проекте: RuTrackerAudiobook */
  suspend fun searchAudiobooks(
    query: String,
    categoryId: Int? = null,
    page: Int = 0,
    forceRefresh: Boolean = false,
  ): Result<List<RuTrackerAudiobook>> =
    withContext(Dispatchers.IO) {
      debugLogger.logDebug("RuTrackerApiServiceEnhanced: Searching for audiobooks with query: $query")

      if (!forceRefresh) {
        val key = CacheKey("search", "search_${query}_${categoryId}_$page", 1)
        val cached = cacheManager.get(key) { json -> Json.decodeFromString<List<RuTrackerAudiobook>>(json) }
        if (cached.isSuccess) return@withContext cached
      }

      val result = withBreaker(searchCircuitBreaker) {
        executeWithDomainFallback { baseUrl -> performSearch(baseUrl, query, categoryId, page) }
      }

      if (result.isSuccess) {
        val value = result.getOrNull()
        if (!value.isNullOrEmpty()) {
          val key = CacheKey("search", "search_${query}_${categoryId}_$page", 1)
          cacheManager.put(key, value, CACHE_TTL_SEARCH) { Json.encodeToString(it) }
        }
      } else {
        val e = result.exceptionOrNull()
        if (e is NetworkException) debugLogger.logWarning("Search failed: ${e.message}")
      }

      if (searchCircuitBreaker.state == CircuitBreaker.State.OPEN) {
        return@withContext Result.failure(SearchUnavailableException("Search temporarily unavailable due to repeated failures"))
      }

      result
    }

  suspend fun getAudiobookDetails(
    topicId: String,
    forceRefresh: Boolean = false,
  ): Result<RuTrackerTorrentDetails> =
    withContext(Dispatchers.IO) {
      debugLogger.logDebug("RuTrackerApiServiceEnhanced: Getting details for topic: $topicId")

      if (!forceRefresh) {
        val key = CacheKey("details", "details_$topicId", 1)
        val cached = cacheManager.get(key) { json -> Json.decodeFromString<RuTrackerTorrentDetails>(json) }
        if (cached.isSuccess) return@withContext cached
      }

      val result = withBreaker(detailsCircuitBreaker) {
        executeWithDomainFallback { baseUrl -> performGetDetails(baseUrl, topicId) }
      }

      if (result.isSuccess) {
        val details = result.getOrNull()
        if (details != null) {
          val key = CacheKey("details", "details_$topicId", 1)
          cacheManager.put(key, details, CACHE_TTL_DETAILS) { Json.encodeToString(it) }
        }
      }

      if (detailsCircuitBreaker.state == CircuitBreaker.State.OPEN) {
        return@withContext Result.failure(DetailsUnavailableException("Details temporarily unavailable due to repeated failures"))
      }

      result
    }

  suspend fun getCategories(forceRefresh: Boolean = false): Result<List<RuTrackerCategory>> =
    withContext(Dispatchers.IO) {
      debugLogger.logDebug("RuTrackerApiServiceEnhanced: Getting categories")

      if (!forceRefresh) {
        val key = CacheKey("categories", "categories", 1)
        val cached = cacheManager.get(key) { json -> Json.decodeFromString<List<RuTrackerCategory>>(json) }
        if (cached.isSuccess) return@withContext cached
      }

      val result = withBreaker(categoriesCircuitBreaker) {
        executeWithDomainFallback { baseUrl -> performGetCategories(baseUrl) }
      }

      if (result.isSuccess) {
        val list = result.getOrNull()
        if (!list.isNullOrEmpty()) {
          val key = CacheKey("categories", "categories", 1)
          cacheManager.put(key, list, CACHE_TTL_CATEGORIES) { Json.encodeToString(it) }
        }
      }

      if (categoriesCircuitBreaker.state == CircuitBreaker.State.OPEN) {
        return@withContext Result.failure(CategoriesUnavailableException("Categories temporarily unavailable due to repeated failures"))
      }

      result
    }

  suspend fun getMagnetLink(topicId: String, forceRefresh: Boolean = false): Result<String> =
    withContext(Dispatchers.IO) {
      debugLogger.logDebug("RuTrackerApiServiceEnhanced: Getting magnet link for topic: $topicId")

      if (!forceRefresh) {
        val key = CacheKey("magnet", "magnet_$topicId", 1)
        val cached = cacheManager.get(key) { json -> json }
        if (cached.isSuccess) return@withContext cached
      }

      val result = executeWithDomainFallback { baseUrl -> performGetMagnetLink(baseUrl, topicId) }

      if (result.isSuccess) {
        val magnet = result.getOrNull()
        if (!magnet.isNullOrEmpty()) {
          val key = CacheKey("magnet", "magnet_$topicId", 1)
          cacheManager.put(key, magnet, CACHE_TTL_DETAILS) { it }
        }
      }

      result
    }

  suspend fun login(username: String, password: String): Result<Boolean> =
    withContext(Dispatchers.IO) {
      debugLogger.logDebug("RuTrackerApiServiceEnhanced: Attempting login for user: $username")
      executeWithDomainFallback { baseUrl -> performLogin(baseUrl, username, password) }
    }

  suspend fun downloadTorrent(topicId: String, outputFile: java.io.File): Result<Boolean> =
    withContext(Dispatchers.IO) {
      debugLogger.logDebug("RuTrackerApiServiceEnhanced: Downloading torrent for topic: $topicId")
      executeWithDomainFallback { baseUrl -> performDownloadTorrent(baseUrl, topicId, outputFile) }
    }

  // -------------------- Fallback & Retry --------------------

  private fun fetchAvailableDomains(): List<String> {
    // Пробуем вызвать domainManager.getAvailableDomains() через reflection,
    // иначе используем дефолтный список.
    return try {
      val m = domainManager.javaClass.methods.firstOrNull { it.name == "getAvailableDomains" && it.parameterCount == 0 }
      val value = if (m != null) m.invoke(domainManager) else null
      @Suppress("UNCHECKED_CAST")
      (value as? List<String>)?.takeIf { it.isNotEmpty() } ?: listOf(
        "rutracker.net",
        "rutracker.org",
        "rutracker.nl"
      )
    } catch (_: Throwable) {
      listOf("rutracker.net", "rutracker.org", "rutracker.nl")
    }
  }

  private fun safeHandleError(exception: Throwable, domain: String, operation: String) {
    // Пробуем разные сигнатуры errorHandler.handleError(...).
    try {
      // handleError(Throwable, String, String)
      val m1 = errorHandler.javaClass.methods.firstOrNull {
        it.name == "handleError" &&
                it.parameterCount == 3 &&
                Throwable::class.java.isAssignableFrom(it.parameterTypes[0]) &&
                it.parameterTypes[1] == String::class.java &&
                it.parameterTypes[2] == String::class.java
      }
      if (m1 != null) {
        m1.invoke(errorHandler, exception, domain, operation)
        return
      }
      // handleError(Throwable)
      val m2 = errorHandler.javaClass.methods.firstOrNull {
        it.name == "handleError" &&
                it.parameterCount == 1 &&
                Throwable::class.java.isAssignableFrom(it.parameterTypes[0])
      }
      if (m2 != null) {
        m2.invoke(errorHandler, exception)
        return
      }
    } catch (_: Throwable) {
      // проглатываем – логируем ниже
    }
    debugLogger.logError("RuTrackerApiServiceEnhanced: errorHandler fallback logging ($operation @ $domain)", exception)
  }

  private suspend fun <T> executeWithDomainFallback(operation: suspend (String) -> Result<T>): Result<T> {
    val domains = fetchAvailableDomains()
    for (domain in domains) {
      try {
        debugLogger.logDebug("RuTrackerApiServiceEnhanced: Trying domain: $domain")
        applyRateLimiting(domain)
        val result = executeWithRetry { operation("https://$domain") }
        if (result.isSuccess) {
          debugLogger.logDebug("RuTrackerApiServiceEnhanced: Operation successful on domain: $domain")
          return result
        }
        debugLogger.logWarning("RuTrackerApiServiceEnhanced: Operation failed on domain: $domain")
      } catch (e: Exception) {
        debugLogger.logError("RuTrackerApiServiceEnhanced: Error with domain $domain", e)
        safeHandleError(e, domain, "executeWithDomainFallback")
      }
    }
    return Result.failure(DomainUnavailableException("All domains failed"))
  }

  private suspend fun <T> executeWithRetry(
    operation: suspend () -> Result<T>,
    maxRetries: Int = MAX_RETRIES,
  ): Result<T> {
    var lastException: Exception? = null
    for (attempt in 1..maxRetries) {
      try {
        val result = operation()
        if (result.isSuccess) return result
        lastException = (result.exceptionOrNull() as? Exception) ?: Exception("Unknown error")
      } catch (e: Exception) {
        lastException = e
      }
      if (attempt < maxRetries) {
        val delayMs = calculateRetryDelay(attempt)
        debugLogger.logDebug("RuTrackerApiServiceEnhanced: Retry $attempt/$maxRetries after ${delayMs}ms")
        delay(delayMs)
      }
    }
    return Result.failure(lastException ?: Exception("All retries failed"))
  }

  private fun calculateRetryDelay(attempt: Int): Long = BASE_RETRY_DELAY * (1L shl (attempt - 1))

  private suspend fun applyRateLimiting(domain: String) {
    val now = System.currentTimeMillis()
    val lastRequest = lastRequestTime[domain] ?: 0
    val elapsed = now - lastRequest
    if (elapsed < RATE_LIMIT_DELAY) {
      val delayMs = RATE_LIMIT_DELAY - elapsed
      debugLogger.logDebug("RuTrackerApiServiceEnhanced: Rate limiting, delaying for ${delayMs}ms")
      delay(delayMs)
    }
    lastRequestTime[domain] = System.currentTimeMillis()
  }

  // -------------------- HTTP ops --------------------

  private suspend fun performSearch(
    baseUrl: String,
    query: String,
    categoryId: Int?,
    page: Int,
  ): Result<List<RuTrackerAudiobook>> {
    return try {
      val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
      val urlBuilder = "$baseUrl$SEARCH_PATH".toHttpUrlOrNull()?.newBuilder()
        ?: return Result.failure(NetworkException("Invalid URL"))

      // Важно: **не** кодировать параметр через URLEncoder ещё раз при addQueryParameter – он сам кодирует.
      urlBuilder.addQueryParameter("nm", query)
      if (categoryId != null) urlBuilder.addQueryParameter("f", categoryId.toString())
      if (page > 0) urlBuilder.addQueryParameter("start", (page * 50).toString())

      val request = Request.Builder().url(urlBuilder.build()).addDefaultHeaders().build()
      httpClient.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) return Result.failure(NetworkException("HTTP ${response.code}: ${response.message}"))
        // У парсера ожидается List<RuTrackerAudiobook>
        val results = try {
          parser.parseSearchResults(body)
        } catch (t: Throwable) {
          debugLogger.logWarning("RuTrackerApiServiceEnhanced: parser.parseSearchResults failed, returning empty list: ${t.message}")
          emptyList()
        }
        if (results.isEmpty()) debugLogger.logWarning("RuTrackerApiServiceEnhanced: No search results found")
        Result.success(results)
      }
    } catch (e: Exception) {
      debugLogger.logError("RuTrackerApiServiceEnhanced: Search failed", e)
      Result.failure(NetworkException(e.message ?: "Search failed", e))
    }
  }

  private suspend fun performGetDetails(baseUrl: String, topicId: String): Result<RuTrackerTorrentDetails> {
    return try {
      val url = "${baseUrl}$TOPIC_PATH?t=$topicId"
      val request = Request.Builder().url(url).addDefaultHeaders().build()
      httpClient.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) return Result.failure(NetworkException("HTTP ${response.code}: ${response.message}"))
        val details = try {
          // Пытаемся использовать парсер; если нет метода/типа – падаем в fallback
          val parsed = parser.parseTorrentDetails(body)
          // На случай несовпадения типов – аккуратно переливаем в локальную модель по известным полям
          when (parsed) {
            is RuTrackerTorrentDetails -> parsed
            else -> {
              // Простейший fallback: вытащим title и magnet из HTML
              val title = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
                .find(body)?.groupValues?.getOrNull(1)
              val magnet = extractMagnetFromHtml(body)
              RuTrackerTorrentDetails(topicId = topicId, title = title, magnet = magnet)
            }
          }
        } catch (t: Throwable) {
          debugLogger.logWarning("RuTrackerApiServiceEnhanced: parser.parseTorrentDetails failed, fallback: ${t.message}")
          val title = Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
            .find(body)?.groupValues?.getOrNull(1)
          val magnet = extractMagnetFromHtml(body)
          RuTrackerTorrentDetails(topicId, title = title, magnet = magnet)
        }
        Result.success(details)
      }
    } catch (e: Exception) {
      debugLogger.logError("RuTrackerApiServiceEnhanced: Get details failed", e)
      Result.failure(NetworkException(e.message ?: "Details failed", e))
    }
  }

  private suspend fun performGetCategories(baseUrl: String): Result<List<RuTrackerCategory>> {
    return try {
      val url = "${baseUrl}$CATEGORIES_PATH"
      val request = Request.Builder().url(url).addDefaultHeaders().build()
      httpClient.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) return Result.failure(NetworkException("HTTP ${response.code}: ${response.message}"))
        val categories = try {
          parser.parseCategories(body)
        } catch (t: Throwable) {
          debugLogger.logWarning("RuTrackerApiServiceEnhanced: parser.parseCategories failed, returning empty list: ${t.message}")
          emptyList()
        }
        Result.success(categories)
      }
    } catch (e: Exception) {
      debugLogger.logError("RuTrackerApiServiceEnhanced: Get categories failed", e)
      Result.failure(NetworkException(e.message ?: "Categories failed", e))
    }
  }

  private suspend fun performGetMagnetLink(baseUrl: String, topicId: String): Result<String> {
    return try {
      val url = "${baseUrl}$TOPIC_PATH?t=$topicId"
      val request = Request.Builder().url(url).addDefaultHeaders().build()
      httpClient.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) return Result.failure(NetworkException("HTTP ${response.code}: ${response.message}"))
        val magnetLink = try {
          // Если у парсера есть метод – используем
          parser.parseMagnetLink(body) ?: extractMagnetFromHtml(body)
        } catch (_: Throwable) {
          extractMagnetFromHtml(body)
        }
        if (magnetLink.isNullOrEmpty()) return Result.failure(ParseException("Magnet link not found"))
        Result.success(magnetLink)
      }
    } catch (e: Exception) {
      debugLogger.logError("RuTrackerApiServiceEnhanced: Get magnet link failed", e)
      Result.failure(NetworkException(e.message ?: "Magnet failed", e))
    }
  }

  private fun extractMagnetFromHtml(html: String): String? {
    // Ищем стандартную magnet-ссылку
    Regex("""magnet:\?xt=urn:[^"'<>\s]+""", RegexOption.IGNORE_CASE).find(html)?.value?.let { return it }
    // Иногда она лежит в href
    Regex("""href\s*=\s*["'](magnet:[^"']+)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.getOrNull(1)?.let { return it }
    return null
  }

  private suspend fun performLogin(baseUrl: String, username: String, password: String): Result<Boolean> {
    return try {
      val loginPageUrl = "${baseUrl}$LOGIN_PATH"
      val loginPageRequest = Request.Builder().url(loginPageUrl).addDefaultHeaders().build()
      httpClient.newCall(loginPageRequest).execute().use { loginPageResponse ->
        val loginPageBody = loginPageResponse.body?.string().orEmpty()
        if (!loginPageResponse.isSuccessful) return Result.failure(NetworkException("Failed to get login page"))

        val csrfToken = extractCsrfToken(loginPageBody) ?: return Result.failure(ParseException("CSRF token not found"))

        val loginForm = FormBody.Builder()
          .add("login_username", username)
          .add("login_password", password)
          .add("login", "Вход")
          .add("form_token", csrfToken)
          .add("redirect", "")
          .add("autologin", "on")
          .build()

        val loginRequest = Request.Builder()
          .url(loginPageUrl)
          .post(loginForm)
          .addDefaultHeaders()
          .addHeader("Content-Type", "application/x-www-form-urlencoded")
          .addHeader("Origin", baseUrl)
          .addHeader("Referer", loginPageUrl)
          .build()

        httpClient.newCall(loginRequest).execute().use { loginResponse ->
          return Result.success(loginResponse.isSuccessful)
        }
      }
    } catch (e: Exception) {
      debugLogger.logError("RuTrackerApiServiceEnhanced: Login failed", e)
      Result.failure(NetworkException(e.message ?: "Login failed", e))
    }
  }

  private suspend fun performDownloadTorrent(
    baseUrl: String,
    topicId: String,
    outputFile: java.io.File,
  ): Result<Boolean> {
    return try {
      val url = "${baseUrl}$DOWNLOAD_PATH?t=$topicId"
      val request = Request.Builder().url(url).addDefaultHeaders().build()
      httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) return Result.failure(NetworkException("HTTP ${response.code}: ${response.message}"))
        response.body?.byteStream()?.use { inputStream ->
          outputFile.outputStream().use { outputStream -> inputStream.copyTo(outputStream) }
        }
        Result.success(true)
      }
    } catch (e: Exception) {
      debugLogger.logError("RuTrackerApiServiceEnhanced: Download torrent failed", e)
      Result.failure(NetworkException(e.message ?: "Download failed", e))
    }
  }

  private fun extractCsrfToken(html: String): String? {
    val pattern = Regex("name=\"form_token\"\\s+value=\"([^\"]+)\"")
    return pattern.find(html)?.groupValues?.get(1)
  }

  private fun Request.Builder.addDefaultHeaders(): Request.Builder =
    this
      .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
      .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
      .addHeader("Accept-Language", "en-US,en;q=0.9")
      .addHeader("Accept-Encoding", "gzip, deflate, br")
      .addHeader("Connection", "keep-alive")
      .addHeader("Upgrade-Insecure-Requests", "1")
      .addHeader("Sec-Fetch-Dest", "document")
      .addHeader("Sec-Fetch-Mode", "navigate")
      .addHeader("Sec-Fetch-Site", "none")
      .addHeader("Sec-Fetch-User", "?1")
      .addHeader("Cache-Control", "max-age=0")

  // ----------------- Monitoring helpers -----------------

  enum class CircuitState { CLOSED, OPEN, HALF_OPEN }

  fun getCircuitBreakerStates(): Map<String, CircuitState> =
    mapOf(
      "search" to searchCircuitBreaker.state.toCircuitState(),
      "details" to detailsCircuitBreaker.state.toCircuitState(),
      "categories" to categoriesCircuitBreaker.state.toCircuitState(),
    )

  private fun CircuitBreaker.State.toCircuitState(): CircuitState = when (this) {
    CircuitBreaker.State.CLOSED -> CircuitState.CLOSED
    CircuitBreaker.State.OPEN -> CircuitState.OPEN
    CircuitBreaker.State.HALF_OPEN -> CircuitState.HALF_OPEN
    else -> CircuitState.CLOSED
  }

  fun resetCircuitBreakers() {
    searchCircuitBreaker.reset()
    detailsCircuitBreaker.reset()
    categoriesCircuitBreaker.reset()
    debugLogger.logInfo("RuTrackerApiServiceEnhanced: Circuit breakers reset")
  }

  suspend fun clearCache(namespace: String): Result<Unit> =
    try {
      cacheManager.clearNamespace(namespace)
      debugLogger.logInfo("RuTrackerApiServiceEnhanced: Cache cleared for namespace: $namespace")
      Result.success(Unit)
    } catch (e: Exception) {
      debugLogger.logError("RuTrackerApiServiceEnhanced: Failed to clear cache for namespace: $namespace", e)
      Result.failure(e)
    }

  fun getCacheStatistics(): Flow<CacheStatistics> = cacheManager.statistics

  data class EnhancedCacheStatistics(
    val hitRate: Double,
    val missRate: Double,
    val totalRequests: Long,
    val totalHits: Long,
    val totalMisses: Long,
    val averageResponseTime: Long,
    val cacheSizeBytes: Long,
  )

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

  suspend fun getEnhancedCacheStatistics(): EnhancedCacheStatistics {
    val basic = cacheManager.statistics.first()
    val totalHits = basic.hitCount
    val totalMisses = basic.missCount
    val totalRequests = totalHits + totalMisses
    val hitRate = if (totalRequests > 0) totalHits.toDouble() / totalRequests else 0.0
    val missRate = if (totalRequests > 0) totalMisses.toDouble() / totalRequests else 0.0
    val avgResp = operationMetrics.values.map { it.averageResponseTime }.average().toLong()
    return EnhancedCacheStatistics(
      hitRate = hitRate,
      missRate = missRate,
      totalRequests = totalRequests,
      totalHits = totalHits,
      totalMisses = totalMisses,
      averageResponseTime = avgResp,
      cacheSizeBytes = cacheManager.getSize()
    )
  }

  fun getOperationMetrics(): Map<String, ApiOperationMetrics> = operationMetrics.toMap()

  fun getErrorContexts(limit: Int = 20): List<String> = errorContexts.takeLast(limit)

  fun clearErrorContexts() {
    errorContexts.clear()
    debugLogger.logInfo("RuTrackerApiServiceEnhanced: Error contexts cleared")
  }

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
      metrics.averageResponseTime = calculateAverage(metrics.averageResponseTime, metrics.successfulRequests - 1, responseTime)
      metrics.fastestResponseTime = minOf(metrics.fastestResponseTime, responseTime)
    } else {
      metrics.failedRequests++
      metrics.slowestResponseTime = maxOf(metrics.slowestResponseTime, responseTime)
    }
    if (cacheHit) metrics.cacheHits++ else metrics.cacheMisses++
    operationMetrics[operation] = metrics
  }

  private fun recordErrorContext(context: String) {
    errorContexts.add(context)
    if (errorContexts.size > maxErrorContexts) errorContexts.removeAt(0)
  }

  suspend fun searchAudiobooksEnhanced(
    query: String,
    categoryId: Int? = null,
    page: Int = 0,
    forceRefresh: Boolean = false,
    timeout: Long = DEFAULT_TIMEOUT,
  ): Result<List<RuTrackerAudiobook>> =
    withContext(Dispatchers.IO) {
      val start = System.currentTimeMillis()
      val op = "search"
      try {
        val key = CacheKey("search", "search_${query}_${categoryId}_$page", 1)
        var cacheHit = false
        if (!forceRefresh) {
          val cached = cacheManager.get(key) { json -> Json.decodeFromString<List<RuTrackerAudiobook>>(json) }
          if (cached.isSuccess) {
            cacheHit = true
            val rt = System.currentTimeMillis() - start
            updateOperationMetrics(op, true, rt, cacheHit = true)
            return@withContext cached
          }
        }
        val res = searchAudiobooks(query, categoryId, page, forceRefresh)
        val rt = System.currentTimeMillis() - start
        updateOperationMetrics(op, res.isSuccess, rt, cacheHit = cacheHit)
        res
      } catch (e: Exception) {
        val rt = System.currentTimeMillis() - start
        updateOperationMetrics(op, false, rt, cacheHit = false)
        recordErrorContext("Operation: $op, Error: ${e.message}, Time: ${System.currentTimeMillis()}")
        debugLogger.logError("RuTrackerApiServiceEnhanced: Enhanced search failed", e)
        Result.failure(NetworkException(e.message ?: "Enhanced search failed", e))
      }
    }

  suspend fun getAudiobookDetailsEnhanced(
    topicId: String,
    forceRefresh: Boolean = false,
    timeout: Long = DEFAULT_TIMEOUT,
  ): Result<RuTrackerTorrentDetails> =
    withContext(Dispatchers.IO) {
      val start = System.currentTimeMillis()
      val op = "details"
      try {
        val key = CacheKey("details", "details_$topicId", 1)
        var cacheHit = false
        if (!forceRefresh) {
          val cached = cacheManager.get(key) { json -> Json.decodeFromString<RuTrackerTorrentDetails>(json) }
          if (cached.isSuccess) {
            cacheHit = true
            val rt = System.currentTimeMillis() - start
            updateOperationMetrics(op, true, rt, cacheHit = true)
            return@withContext cached
          }
        }
        val res = getAudiobookDetails(topicId, forceRefresh)
        val rt = System.currentTimeMillis() - start
        updateOperationMetrics(op, res.isSuccess, rt, cacheHit = cacheHit)
        res
      } catch (e: Exception) {
        val rt = System.currentTimeMillis() - start
        updateOperationMetrics(op, false, rt, cacheHit = false)
        recordErrorContext("Operation: $op, Error: ${e.message}, Time: ${System.currentTimeMillis()}")
        debugLogger.logError("RuTrackerApiServiceEnhanced: Enhanced details failed", e)
        Result.failure(NetworkException(e.message ?: "Enhanced details failed", e))
      }
    }

  suspend fun exportPerformanceData(): Map<String, Any> =
    mapOf(
      "timestamp" to System.currentTimeMillis(),
      "operationMetrics" to operationMetrics,
      "errorContexts" to getErrorContexts(50),
      "cacheStatistics" to getEnhancedCacheStatistics(),
      "circuitBreakerStates" to getCircuitBreakerStates(),
    )

  private fun calculateAverage(currentAverage: Long, count: Int, newValue: Long): Long =
    if (count == 0) newValue else (currentAverage * count + newValue) / (count + 1)
}
