package com.jabook.app.core.network

import com.jabook.app.core.domain.model.RuTrackerAudiobook
import com.jabook.app.core.domain.model.RuTrackerCategory
import com.jabook.app.core.domain.model.TorrentState
import com.jabook.app.shared.debug.IDebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RuTracker API Service
 *
 * Handles HTTP requests to RuTracker.net for guest mode operations
 */
@Singleton
class RuTrackerApiService
  @Inject
  constructor(
    private val httpClient: OkHttpClient,
    private val parser: RuTrackerParserImproved,
    private val debugLogger: IDebugLogger,
  ) {
    companion object {
      private const val BASE_URL = "https://rutracker.net"
      private const val SEARCH_URL = "$BASE_URL/forum/tracker.php"
      private const val TOPIC_URL = "$BASE_URL/forum/viewtopic.php"
      private const val CATEGORIES_URL = "$BASE_URL/forum/index.php"
    }

    private var searchAttempts = 0

    /**
     * Search for audiobooks using guest mode
     */
    suspend fun searchAudiobooks(query: String): List<RuTrackerAudiobook> =
      withContext(Dispatchers.IO) {
        try {
          debugLogger.logDebug("RuTrackerApiService: Searching for '$query' in guest mode")

          // Simulate network error for testing
          if (searchAttempts < 2) {
            searchAttempts++
            debugLogger.logError("RuTrackerApiService: Simulating network error, attempt $searchAttempts")
            throw IOException("Simulated network error")
          }

          val encodedQuery = URLEncoder.encode(query, "UTF-8")
          val url = "$SEARCH_URL?nm=$encodedQuery"

          val request =
            Request
              .Builder()
              .url(url)
              .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
              .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
              .addHeader("Accept-Language", "en-US,en;q=0.5")
              .addHeader("Accept-Encoding", "gzip, deflate")
              .addHeader("Connection", "keep-alive")
              .addHeader("Upgrade-Insecure-Requests", "1")
              .build()

          debugLogger.logDebug("RuTrackerApiService: Request URL: ${request.url}")
          val response = httpClient.newCall(request).execute()
          val responseCode = response.code
          val responseBody = response.body.string()

          debugLogger.logDebug("RuTrackerApiService: HTTP Response Code: $responseCode")
          debugLogger.logDebug("RuTrackerApiService: Response Body Length: ${responseBody.length}")

          if (responseCode != 200) {
            debugLogger.logError("RuTrackerApiService: HTTP Error $responseCode")
            debugLogger.logError("RuTrackerApiService: Response Body: ${responseBody.take(1000)}")
            return@withContext emptyList()
          }

          if (responseBody.isBlank()) {
            debugLogger.logWarning("RuTrackerApiService: Empty response body")
            debugLogger.logWarning("RuTrackerApiService: Empty response body. Request URL: ${request.url}")
            return@withContext emptyList()
          }

          // Log HTML snippet for debugging
          val htmlSnippet = responseBody.take(2000)
          debugLogger.logDebug("RuTrackerApiService: HTML Snippet: $htmlSnippet")

          val results = parser.parseSearchResults(responseBody)
          debugLogger.logDebug("RuTrackerApiService: Parsed ${results.size} results")

          results
        } catch (e: Exception) {
          debugLogger.logError("RuTrackerApiService: Failed to search audiobooks", e)
          emptyList()
        }
      }

    /**
     * Get audiobook details by topic ID
     */
    suspend fun getAudiobookDetails(topicId: String): RuTrackerAudiobook? =
      withContext(Dispatchers.IO) {
        try {
          debugLogger.logDebug("RuTrackerApiService: Getting details for topic $topicId")

          val url = "$TOPIC_URL?t=$topicId"

          val request =
            Request
              .Builder()
              .url(url)
              .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
              .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
              .addHeader("Accept-Language", "en-US,en;q=0.5")
              .addHeader("Accept-Encoding", "gzip, deflate")
              .addHeader("Connection", "keep-alive")
              .addHeader("Upgrade-Insecure-Requests", "1")
              .build()

          val response = httpClient.newCall(request).execute()
          val responseCode = response.code
          val responseBody = response.body.string()

          debugLogger.logDebug("RuTrackerApiService: Details HTTP Response Code: $responseCode")
          debugLogger.logDebug("RuTrackerApiService: Details Response Body Length: ${responseBody.length}")

          if (responseCode != 200) {
            debugLogger.logError("RuTrackerApiService: Details HTTP Error $responseCode")
            return@withContext null
          }

          if (responseBody.isBlank()) {
            debugLogger.logWarning("RuTrackerApiService: Empty details response body")
            return@withContext null
          }

          parser.parseAudiobookDetails(responseBody)
        } catch (e: Exception) {
          debugLogger.logError("RuTrackerApiService: Failed to get audiobook details", e)
          null
        }
      }

    /**
     * Get categories list
     */
    suspend fun getCategories(): List<RuTrackerCategory> =
      withContext(Dispatchers.IO) {
        try {
          debugLogger.logDebug("RuTrackerApiService: Getting categories")

          val url = "$CATEGORIES_URL?c=33"

          val request =
            Request
              .Builder()
              .url(url)
              .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
              .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
              .addHeader("Accept-Language", "en-US,en;q=0.5")
              .addHeader("Accept-Encoding", "gzip, deflate")
              .addHeader("Connection", "keep-alive")
              .addHeader("Upgrade-Insecure-Requests", "1")
              .build()

          val response = httpClient.newCall(request).execute()
          val responseCode = response.code
          val responseBody = response.body.string()

          debugLogger.logDebug("RuTrackerApiService: Categories HTTP Response Code: $responseCode")

          if (responseCode != 200) {
            debugLogger.logError("RuTrackerApiService: Categories HTTP Error $responseCode")
            return@withContext emptyList()
          }

          parser.parseCategories(responseBody)
        } catch (e: Exception) {
          debugLogger.logError("RuTrackerApiService: Failed to get categories", e)
          emptyList()
        }
      }

    /**
     * Extract magnet link from HTML
     */
    suspend fun extractMagnetLink(html: String): String? =
      withContext(Dispatchers.IO) {
        try {
          parser.extractMagnetLink(html)
        } catch (e: Exception) {
          debugLogger.logError("RuTrackerApiService: Failed to extract magnet link", e)
          null
        }
      }

    /**
     * Extract torrent link from HTML
     */
    suspend fun extractTorrentLink(html: String): String? =
      withContext(Dispatchers.IO) {
        try {
          parser.extractTorrentLink(html)
        } catch (e: Exception) {
          debugLogger.logError("RuTrackerApiService: Failed to extract torrent link", e)
          null
        }
      }

    /**
     * Parse torrent state from HTML
     */
    suspend fun parseTorrentState(html: String): TorrentState =
      withContext(Dispatchers.IO) {
        try {
          parser.parseTorrentState(html)
        } catch (e: Exception) {
          debugLogger.logError("RuTrackerApiService: Failed to parse torrent state", e)
          TorrentState.APPROVED
        }
      }

    // Stub methods for compatibility with RuTrackerApiClient
    suspend fun searchGuest(
      query: String,
      category: String?,
      page: Int,
    ): Result<String> =
      try {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val urlBuilder = StringBuilder(SEARCH_URL)
        var hasParam = false
        if (!category.isNullOrBlank()) {
          urlBuilder.append("?f=").append(category)
          hasParam = true
        }
        urlBuilder.append(if (hasParam) "&" else "?")
        urlBuilder.append("nm=").append(encodedQuery)
        if (page > 1) {
          val start = (page - 1) * 50
          urlBuilder.append("&start=").append(start)
        }
        val url = urlBuilder.toString()
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        val responseBody = response.body.string()
        Result.success(responseBody)
      } catch (e: Exception) {
        Result.failure(e)
      }

    suspend fun getCategoriesGuest(): Result<String> =
      try {
        val url = "$CATEGORIES_URL?c=33"
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        val responseBody = response.body.string()
        Result.success(responseBody)
      } catch (e: Exception) {
        Result.failure(e)
      }

    suspend fun getAudiobookDetailsGuest(topicId: String): Result<String> =
      try {
        val url = "$TOPIC_URL?t=$topicId"
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        val responseBody = response.body.string()
        Result.success(responseBody)
      } catch (e: Exception) {
        Result.failure(e)
      }

    suspend fun getTopAudiobooksGuest(limit: Int): Result<String> =
      try {
        val url = "$BASE_URL/forum/viewforum.php?f=313&sk=t&sd=d&limit=$limit"
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        val responseBody = response.body.string()
        Result.success(responseBody)
      } catch (e: Exception) {
        Result.failure(e)
      }

    suspend fun getNewAudiobooksGuest(limit: Int): Result<String> =
      try {
        val url = "$BASE_URL/forum/viewforum.php?f=313&limit=$limit"
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        val responseBody = response.body.string()
        Result.success(responseBody)
      } catch (e: Exception) {
        Result.failure(e)
      }

    suspend fun login(
      username: String,
      password: String,
    ): Result<Boolean> {
      return try {
        debugLogger.logDebug("RuTrackerApiService: Starting login process for user: $username")

        // Step 1: Get login page to extract CSRF token
        val loginPageUrl = "$BASE_URL/forum/login.php"
        val loginPageRequest =
          Request
            .Builder()
            .url(loginPageUrl)
            .get()
            .build()

        val loginPageResponse = httpClient.newCall(loginPageRequest).execute()
        val loginPageBody = loginPageResponse.body.string()

        if (loginPageResponse.code != 200) {
          debugLogger.logError("RuTrackerApiService: Failed to get login page, code: ${loginPageResponse.code}")
          return Result.success(false)
        }

        // Step 2: Extract CSRF token from login page
        val csrfToken = extractCsrfToken(loginPageBody)
        if (csrfToken.isNullOrBlank()) {
          debugLogger.logWarning("RuTrackerApiService: Could not extract CSRF token from login page")
          // Continue without CSRF token as fallback
        } else {
          debugLogger.logDebug("RuTrackerApiService: Extracted CSRF token: ${csrfToken.take(10)}...")
        }

        // Step 3: Prepare login form data
        val formData =
          buildString {
            append("login_username=").append(URLEncoder.encode(username, "UTF-8"))
            append("&login_password=").append(URLEncoder.encode(password, "UTF-8"))
            append("&login=Вход") // Login button text
            if (!csrfToken.isNullOrBlank()) {
              append("&form_token=").append(URLEncoder.encode(csrfToken, "UTF-8"))
            }
            append("&redirect=")
            append("&autologin=on") // Remember me
          }

        val requestBody = formData.toRequestBody("application/x-www-form-urlencoded".toMediaType())

        debugLogger.logDebug("RuTrackerApiService: Login form data: $formData")

        // Step 4: Submit login form
        val loginRequest =
          Request
            .Builder()
            .url(loginPageUrl)
            .post(requestBody)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .addHeader("Origin", BASE_URL)
            .addHeader("Referer", loginPageUrl)
            .build()

        val loginResponse = httpClient.newCall(loginRequest).execute()
        val loginResponseBody = loginResponse.body.string()

        debugLogger.logDebug("RuTrackerApiService: Login response code: ${loginResponse.code}")
        debugLogger.logDebug("RuTrackerApiService: Login response headers: ${loginResponse.headers}")

        // Step 5: Check for login success
        val isSuccess = validateLoginSuccess(loginResponse, loginResponseBody)

        if (isSuccess) {
          debugLogger.logInfo("RuTrackerApiService: Login successful for user: $username")
        } else {
          debugLogger.logWarning("RuTrackerApiService: Login failed for user: $username")
          debugLogger.logDebug("RuTrackerApiService: Response body snippet: ${loginResponseBody.take(500)}")
        }

        Result.success(isSuccess)
      } catch (e: Exception) {
        debugLogger.logError("RuTrackerApiService: Login error", e)
        Result.failure(e)
      }
    }

    private fun extractCsrfToken(html: String): String? {
      return try {
        // Try multiple patterns for CSRF token extraction
        val patterns =
          listOf(
            Regex("""name="form_token"\s+value="([^"]+)""""),
            Regex("""<input[^>]*name="form_token"[^>]*value="([^"]+)""""),
            Regex("""form_token['"]\s*:\s*['"]([^'"]+)""""),
            Regex("""bb_session['"]\s*:\s*['"]([^'"]+)""""),
          )

        for (pattern in patterns) {
          val match = pattern.find(html)
          if (match != null) {
            return match.groupValues[1]
          }
        }

        null
      } catch (e: Exception) {
        debugLogger.logError("RuTrackerApiService: Error extracting CSRF token", e)
        null
      }
    }

    private fun validateLoginSuccess(
      response: okhttp3.Response,
      responseBody: String,
    ): Boolean {
      // Check for error messages first
      val errorMessages =
        listOf(
          "Неверный логин или пароль",
          "Invalid login or password",
          "Ошибка авторизации",
          "Неправильное имя пользователя",
          "Неправильный пароль",
          "login_error",
          "error_msg",
        )

      val hasError = errorMessages.any { responseBody.contains(it, ignoreCase = true) }
      if (hasError) {
        debugLogger.logWarning("RuTrackerApiService: Login error message found in response")
        return false
      }

      // Check for success indicators
      val successIndicators =
        listOf(
          // Redirect to main page
          response.code == 302 || response.code == 301,
          // Session cookie present
          response.headers.values("Set-Cookie").any {
            it.contains("bb_session", ignoreCase = true) ||
              it.contains("bb_userid", ignoreCase = true)
          },
          // Success message in body
          responseBody.contains("Вы успешно вошли", ignoreCase = true),
          // User panel present (indicates logged in state)
          responseBody.contains("class=\"username\"", ignoreCase = true) ||
            responseBody.contains("Выход", ignoreCase = true) ||
            responseBody.contains("logout", ignoreCase = true),
        )

      val isSuccess = successIndicators.any { it }

      if (!isSuccess) {
        debugLogger.logDebug("RuTrackerApiService: No success indicators found")
        debugLogger.logDebug("RuTrackerApiService: Response code: ${response.code}")
        debugLogger.logDebug("RuTrackerApiService: Cookies: ${response.headers.values("Set-Cookie")}")
      }

      return isSuccess
    }

    suspend fun logout(): Result<Boolean> {
      return Result.success(true) // Not implemented for guest mode
    }

    suspend fun checkAvailability(): Result<Boolean> =
      try {
        val request = Request.Builder().url(BASE_URL).build()
        val response = httpClient.newCall(request).execute()
        Result.success(response.isSuccessful)
      } catch (e: Exception) {
        Result.failure(e)
      }

    // Additional stub methods for authenticated operations
    suspend fun searchAuthenticated(
      query: String,
      sort: String,
      order: String,
      page: Int,
    ): Result<String> =
      try {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val urlBuilder = StringBuilder("$SEARCH_URL?nm=$encodedQuery&o=$sort&s=$order")
        if (page > 1) {
          val start = (page - 1) * 50
          urlBuilder.append("&start=").append(start)
        }
        val url = urlBuilder.toString()
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        val responseBody = response.body.string()
        Result.success(responseBody)
      } catch (e: Exception) {
        Result.failure(e)
      }

    suspend fun downloadTorrent(topicId: String): Result<InputStream?> =
      try {
        val url = "$BASE_URL/forum/dl.php?t=$topicId"
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        val inputStream = response.body.byteStream()
        Result.success(inputStream)
      } catch (e: Exception) {
        Result.failure(e)
      }

    suspend fun getAudiobookDetailsAuthenticated(topicId: String): Result<String> =
      try {
        val url = "$TOPIC_URL?t=$topicId"
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        val responseBody = response.body.string()
        Result.success(responseBody)
      } catch (e: Exception) {
        Result.failure(e)
      }

    suspend fun getMagnetLinkAuthenticated(topicId: String): Result<String?> =
      try {
        val url = "$TOPIC_URL?t=$topicId"
        val request = Request.Builder().url(url).build()
        val response = httpClient.newCall(request).execute()
        val responseBody = response.body.string()
        val magnetLink = parser.extractMagnetLink(responseBody)
        Result.success(magnetLink)
      } catch (e: Exception) {
        Result.failure(e)
      }
  }
