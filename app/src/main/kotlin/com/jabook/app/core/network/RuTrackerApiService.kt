package com.jabook.app.core.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

interface RuTrackerApiService {
    // Guest mode operations
    suspend fun searchGuest(query: String, category: String?, page: Int): Result<String>
    suspend fun getCategoriesGuest(): Result<String>
    suspend fun getAudiobookDetailsGuest(topicId: String): Result<String>
    suspend fun getTopAudiobooksGuest(limit: Int): Result<String>
    suspend fun getNewAudiobooksGuest(limit: Int): Result<String>

    // Authenticated mode operations
    suspend fun login(username: String, password: String): Result<Boolean>
    suspend fun searchAuthenticated(query: String, sort: String, order: String, page: Int): Result<String>
    suspend fun downloadTorrent(topicId: String): Result<InputStream?>
    suspend fun getAudiobookDetailsAuthenticated(topicId: String): Result<String>
    suspend fun logout(): Result<Boolean>

    // Common operations
    suspend fun checkAvailability(): Result<Boolean>
}

class RealRuTrackerApiService @Inject constructor(
    private val httpClient: OkHttpClient,
) : RuTrackerApiService {
    private val baseUrl = "https://rutracker.net"
    private var sessionCookies: String? = null

    override suspend fun searchGuest(query: String, category: String?, page: Int): Result<String> {
        return try {
            val searchUrl = "$baseUrl/forum/tracker.php?nm=$query"
            val response = makeRequest(searchUrl)
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                Result.success(html)
            } else {
                Result.failure(IOException("Search failed with code:  {response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCategoriesGuest(): Result<String> {
        return try {
            val categoriesUrl = "$baseUrl/forum/index.php"
            val response = makeRequest(categoriesUrl)
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                Result.success(html)
            } else {
                Result.failure(IOException("Categories failed with code: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAudiobookDetailsGuest(topicId: String): Result<String> {
        return try {
            val detailsUrl = "$baseUrl/forum/viewtopic.php?t=$topicId"
            val response = makeRequest(detailsUrl)
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                Result.success(html)
            } else {
                Result.failure(IOException("Details failed with code: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTopAudiobooksGuest(limit: Int): Result<String> {
        return try {
            val topUrl = "$baseUrl/forum/viewforum.php?f=313&sk=t&sd=d"
            val response = makeRequest(topUrl)
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                Result.success(html)
            } else {
                Result.failure(IOException("Top audiobooks failed with code: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getNewAudiobooksGuest(limit: Int): Result<String> {
        return try {
            val newUrl = "$baseUrl/forum/viewforum.php?f=313"
            val response = makeRequest(newUrl)
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                Result.success(html)
            } else {
                Result.failure(IOException("New audiobooks failed with code: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun login(username: String, password: String): Result<Boolean> {
        return try {
            val loginUrl = "$baseUrl/forum/login.php"
            val loginData = "login_username=$username&login_password=$password&login=Вход"
            val request = Request.Builder()
                .url(loginUrl)
                .post(okhttp3.RequestBody.create("application/x-www-form-urlencoded".toMediaType(), loginData))
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build()
            val response = httpClient.newCall(request).execute()
            val success = response.isSuccessful && response.headers["Set-Cookie"] != null
            if (success) {
                sessionCookies = response.headers["Set-Cookie"]
            }
            Result.success(success)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchAuthenticated(query: String, sort: String, order: String, page: Int): Result<String> {
        return try {
            val searchUrl = "$baseUrl/forum/tracker.php?nm=$query&o=$sort&s=$order"
            val response = makeRequest(searchUrl)
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                Result.success(html)
            } else {
                Result.failure(IOException("Authenticated search failed with code: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun downloadTorrent(topicId: String): Result<InputStream?> {
        return try {
            val downloadUrl = "$baseUrl/forum/dl.php?t=$topicId"
            val response = makeRequest(downloadUrl)
            if (response.isSuccessful) {
                val inputStream = response.body?.byteStream()
                Result.success(inputStream)
            } else {
                Result.failure(IOException("Torrent download failed with code: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAudiobookDetailsAuthenticated(topicId: String): Result<String> {
        return try {
            val detailsUrl = "$baseUrl/forum/viewtopic.php?t=$topicId"
            val response = makeRequest(detailsUrl)
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                Result.success(html)
            } else {
                Result.failure(IOException("Authenticated details failed with code: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout(): Result<Boolean> {
        return try {
            val logoutUrl = "$baseUrl/forum/login.php?logout=1"
            val response = makeRequest(logoutUrl)
            sessionCookies = null
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun checkAvailability(): Result<Boolean> {
        return try {
            val response = makeRequest(baseUrl)
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun makeRequest(url: String): Response {
        val requestBuilder = Request.Builder().url(url)
        requestBuilder.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        requestBuilder.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        requestBuilder.addHeader("Accept-Language", "ru-RU,ru;q=0.8,en-US;q=0.5,en;q=0.3")
        requestBuilder.addHeader("Accept-Encoding", "gzip, deflate")
        requestBuilder.addHeader("Connection", "keep-alive")
        sessionCookies?.let { cookies ->
            requestBuilder.addHeader("Cookie", cookies)
        }
        return httpClient.newCall(requestBuilder.build()).execute()
    }
}
