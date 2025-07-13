package com.jabook.app.core.network

import com.jabook.app.core.domain.model.RuTrackerAudiobook
import com.jabook.app.core.domain.model.RuTrackerCategory
import com.jabook.app.core.domain.model.TorrentState
import com.jabook.app.shared.debug.IDebugLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.InputStream
import java.net.URLEncoder
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RuTracker API Service
 *
 * Handles HTTP requests to RuTracker.net for guest mode operations
 */
@Singleton
class RuTrackerApiService @Inject constructor(
    private val httpClient: OkHttpClient,
    private val parser: RuTrackerParser,
    private val debugLogger: IDebugLogger,
) {
    companion object {
        private const val BASE_URL = "https://rutracker.net"
        private const val SEARCH_URL = "$BASE_URL/forum/tracker.php"
        private const val TOPIC_URL = "$BASE_URL/forum/viewtopic.php"
        private const val CATEGORIES_URL = "$BASE_URL/forum/index.php"
    }

    /**
     * Search for audiobooks using guest mode
     */
    suspend fun searchAudiobooks(query: String): List<RuTrackerAudiobook> = withContext(Dispatchers.IO) {
        try {
            debugLogger.logDebug("RuTrackerApiService: Searching for '$query' in guest mode")

            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$SEARCH_URL?nm=$encodedQuery"

            val request = Request.Builder()
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
            val responseBody = response.body?.string() ?: ""

            debugLogger.logDebug("RuTrackerApiService: HTTP Response Code: $responseCode")
            debugLogger.logDebug("RuTrackerApiService: Response Body Length: ${responseBody.length}")

            if (responseCode != 200) {
                debugLogger.logError("RuTrackerApiService: HTTP Error $responseCode")
                debugLogger.logError("RuTrackerApiService: Response Body: ${responseBody.take(1000)}")
                return@withContext emptyList()
            }

            if (responseBody.isBlank()) {
                debugLogger.logWarning("RuTrackerApiService: Empty response body")
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
    suspend fun getAudiobookDetails(topicId: String): RuTrackerAudiobook? = withContext(Dispatchers.IO) {
        try {
            debugLogger.logDebug("RuTrackerApiService: Getting details for topic $topicId")

            val url = "$TOPIC_URL?t=$topicId"

            val request = Request.Builder()
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
            val responseBody = response.body?.string() ?: ""

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
    suspend fun getCategories(): List<RuTrackerCategory> = withContext(Dispatchers.IO) {
        try {
            debugLogger.logDebug("RuTrackerApiService: Getting categories")

            val url = "$CATEGORIES_URL?c=33"

            val request = Request.Builder()
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
            val responseBody = response.body?.string() ?: ""

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
    suspend fun extractMagnetLink(html: String): String? = withContext(Dispatchers.IO) {
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
    suspend fun extractTorrentLink(html: String): String? = withContext(Dispatchers.IO) {
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
    suspend fun parseTorrentState(html: String): TorrentState = withContext(Dispatchers.IO) {
        try {
            parser.parseTorrentState(html)
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerApiService: Failed to parse torrent state", e)
            TorrentState.APPROVED
        }
    }

    // Stub methods for compatibility with RuTrackerApiClient
    suspend fun searchGuest(query: String, category: String?, page: Int): Result<String> {
        return try {
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
            val responseBody = response.body?.string() ?: ""
            Result.success(responseBody)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCategoriesGuest(): Result<String> {
        return try {
            val url = "$CATEGORIES_URL?c=33"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            Result.success(responseBody)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAudiobookDetailsGuest(topicId: String): Result<String> {
        return try {
            val url = "$TOPIC_URL?t=$topicId"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            Result.success(responseBody)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTopAudiobooksGuest(limit: Int): Result<String> {
        return try {
            val url = "$BASE_URL/forum/viewforum.php?f=313&sk=t&sd=d"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            Result.success(responseBody)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getNewAudiobooksGuest(limit: Int): Result<String> {
        return try {
            val url = "$BASE_URL/forum/viewforum.php?f=313"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            Result.success(responseBody)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(username: String, password: String): Result<Boolean> {
        return try {
            val url = "$BASE_URL/forum/login.php"
            
            // Create form body with Windows-1251 encoding (as RuTracker expects)
            val charset = Charset.forName("windows-1251")
            val loginValue = "вход"
            val bodyString = "login_username=${URLEncoder.encode(username, "windows-1251")}" +
                "&login_password=${URLEncoder.encode(password, "windows-1251")}" +
                "&login=${URLEncoder.encode(loginValue, "windows-1251")}"
            val requestBody = bodyString.toByteArray(charset).toRequestBody(
                "application/x-www-form-urlencoded".toMediaType()
            )

            debugLogger.logDebug("RuTrackerApiService: Login request URL: $url")
            debugLogger.logDebug("RuTrackerApiService: Login request body: $bodyString")
            debugLogger.logDebug("RuTrackerApiService: Login request body bytes: ${bodyString.toByteArray(charset).contentToString()}")

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .addHeader("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
                .addHeader("Cache-Control", "max-age=0")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Origin", "https://rutracker.net")
                .addHeader("Referer", "https://rutracker.net/forum/index.php")
                .addHeader("Sec-Ch-Ua", "\"Not)A;Brand\";v=\"8\", \"Chromium\";v=\"138\", \"Google Chrome\";v=\"138\"")
                .addHeader("Sec-Ch-Ua-Mobile", "?0")
                .addHeader("Sec-Ch-Ua-Platform", "\"macOS\"")
                .addHeader("Sec-Fetch-Dest", "document")
                .addHeader("Sec-Fetch-Mode", "navigate")
                .addHeader("Sec-Fetch-Site", "same-origin")
                .addHeader("Sec-Fetch-User", "?1")
                .addHeader("Upgrade-Insecure-Requests", "1")
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            debugLogger.logDebug("RuTrackerApiService: Login response code: ${response.code}")
            debugLogger.logDebug("RuTrackerApiService: Login response headers: ${response.headers}")
            debugLogger.logDebug("RuTrackerApiService: Login response body (first 1000 chars): ${responseBody.take(1000)}")

            // Check for error messages in the response
            val hasError = responseBody.contains("Неверный логин или пароль") || 
                          responseBody.contains("Invalid login or password") ||
                          responseBody.contains("Ошибка авторизации") ||
                          responseBody.contains("error")
            
            if (hasError) {
                debugLogger.logWarning("RuTrackerApiService: Login failed - error message found in response")
                return Result.success(false)
            }

            // Check for successful login (302 redirect or bb_session cookie)
            val isSuccess = response.code == 302 || 
                response.headers.values("Set-Cookie").any { it.contains("bb_session") }

            if (isSuccess) {
                debugLogger.logInfo("RuTrackerApiService: Login successful")
                return Result.success(true)
            } else {
                debugLogger.logWarning("RuTrackerApiService: Login failed - no redirect or session cookie")
                debugLogger.logDebug("RuTrackerApiService: Response code: ${response.code}")
                debugLogger.logDebug("RuTrackerApiService: Set-Cookie headers: ${response.headers.values("Set-Cookie")}")
                return Result.success(false)
            }
        } catch (e: Exception) {
            debugLogger.logError("RuTrackerApiService: Login error", e)
            return Result.failure(e)
        }
    }

    suspend fun logout(): Result<Boolean> {
        return Result.success(true) // Not implemented for guest mode
    }

    suspend fun checkAvailability(): Result<Boolean> {
        return try {
            val request = Request.Builder().url(BASE_URL).build()
            val response = httpClient.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Additional stub methods for authenticated operations
    suspend fun searchAuthenticated(query: String, sort: String, order: String, page: Int): Result<String> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$SEARCH_URL?nm=$encodedQuery&o=$sort&s=$order"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            Result.success(responseBody)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadTorrent(topicId: String): Result<InputStream?> {
        return try {
            val url = "$BASE_URL/forum/dl.php?t=$topicId"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            val inputStream = response.body?.byteStream()
            Result.success(inputStream)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getAudiobookDetailsAuthenticated(topicId: String): Result<String> {
        return try {
            val url = "$TOPIC_URL?t=$topicId"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            Result.success(responseBody)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMagnetLinkAuthenticated(topicId: String): Result<String?> {
        return try {
            val url = "$TOPIC_URL?t=$topicId"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            val magnetLink = parser.extractMagnetLink(responseBody)
            Result.success(magnetLink)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
