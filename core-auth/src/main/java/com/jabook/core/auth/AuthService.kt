package com.jabook.core.auth

import android.content.Context
import android.util.Log
import com.jabook.core.net.Http
import com.jabook.core.net.interceptor.UaInterceptor
import com.jabook.core.net.repository.UserAgentRepository
import com.jabook.core.endpoints.EndpointResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Authentication service for JaBook app
 * Handles WebView login and OkHttp fallback authentication
 */
class AuthService(
    private val context: Context,
    private val userAgentRepository: UserAgentRepository,
    private val endpointResolver: EndpointResolver
) {
    
    private val TAG = "AuthService"
    private val httpClient = Http.createClient(
        context = context,
        userAgentInterceptor = UaInterceptor(userAgentRepository),
        enableLogging = false
    )
    
    private val cookieStore = mutableMapOf<String, List<Cookie>>()
    
    /**
     * Login status data class
     */
    data class LoginStatus(
        val loggedIn: Boolean,
        val username: String? = null,
        val userId: String? = null,
        val error: String? = null
    )
    
    /**
     * Performs login using WebView
     */
    suspend fun loginWithWebView(username: String, password: String): Result<LoginStatus> {
        return withContext(Dispatchers.IO) {
            try {
                val activeUrl = endpointResolver.getActiveUrl()
                    ?: return@withContext Result.failure(Exception("No active endpoint available"))
                
                // Create login form data
                val formData = mapOf(
                    "login_username" to username,
                    "login_password" to password,
                    "login" to "submit"
                )
                
                // Build request
                val formBody = buildFormBody(formData)
                val url = "$activeUrl/forum/login.php"
                
                val request = Request.Builder()
                    .url(url)
                    .post(formBody)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Referer", activeUrl)
                    .build()
                
                val response = httpClient.newCall(request).execute()
                
                if (response.isSuccessful) {
                    // Check if login was successful by looking for redirect or success indicators
                    val responseBody = response.body?.string() ?: ""
                    
                    if (responseBody.contains("login_form") || responseBody.contains("Invalid username")) {
                        // Login failed
                        Result.failure(Exception("Invalid username or password"))
                    } else {
                        // Login successful - extract user info from response or cookies
                        val loginStatus = extractLoginStatus(response, responseBody)
                        Result.success(loginStatus)
                    }
                } else {
                    Result.failure(Exception("Login failed: ${response.code} ${response.message}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "WebView login failed", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Performs login using OkHttp (fallback)
     */
    suspend fun loginWithOkHttp(username: String, password: String): Result<LoginStatus> {
        return withContext(Dispatchers.IO) {
            try {
                val activeUrl = endpointResolver.getActiveUrl()
                    ?: return@withResult Result.failure(Exception("No active endpoint available"))
                
                // Create login request
                val loginData = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                    put("remember", "on")
                }
                
                val requestBody = loginData.toString().toRequestBody("application/json".toMediaType())
                val url = "$activeApiUrl/api/login"
                
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .header("Content-Type", "application/json")
                    .header("Referer", activeUrl)
                    .build()
                
                val response = httpClient.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    val jsonResponse = JSONObject(responseBody)
                    
                    if (jsonResponse.getBoolean("success")) {
                        val loginStatus = LoginStatus(
                            loggedIn = true,
                            username = jsonResponse.getString("username"),
                            userId = jsonResponse.getString("user_id")
                        )
                        Result.success(loginStatus)
                    } else {
                        Result.failure(Exception(jsonResponse.getString("error")))
                    }
                } else {
                    Result.failure(Exception("Login failed: ${response.code} ${response.message}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "OkHttp login failed", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Logs out the current user
     */
    suspend fun logout(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // Clear cookies
                cookieStore.clear()
                
                // Clear WebView cookies
                android.webkit.CookieManager.getInstance().removeAllCookies(null)
                android.webkit.CookieManager.getInstance().flush()
                
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Logout failed", e)
                Result.failure(e)
            }
        }
    }
    
    /**
     * Gets current login status
     */
    suspend fun getLoginStatus(): LoginStatus {
        return withContext(Dispatchers.IO) {
            try {
                val activeUrl = endpointResolver.getActiveUrl()
                    ?: return@withContext LoginStatus(loggedIn = false)
                
                val url = "$activeUrl/api/me"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                
                val response = httpClient.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: ""
                    val jsonResponse = JSONObject(responseBody)
                    
                    LoginStatus(
                        loggedIn = jsonResponse.getBoolean("loggedIn"),
                        username = jsonResponse.optString("username"),
                        userId = jsonResponse.optString("id")
                    )
                } else {
                    LoginStatus(loggedIn = false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get login status", e)
                LoginStatus(loggedIn = false)
            }
        }
    }
    
    /**
     * Checks if user is logged in
     */
    suspend fun isLoggedIn(): Boolean {
        return getLoginStatus().loggedIn
    }
    
    /**
     * Handles captcha challenge by prompting WebView login
     */
    suspend fun handleCaptchaChallenge(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            // In a real implementation, this would:
            // 1. Show WebView with captcha page
            // 2. Wait for user to complete captcha
            // 3. Extract captcha response
            // 4. Retry login with captcha
            
            Result.success(Unit) // Placeholder
        }
    }
    
    /**
     * Builds form body from map
     */
    private fun buildFormBody(data: Map<String, String>): RequestBody {
        val builder = FormBody.Builder()
        data.forEach { (key, value) ->
            builder.add(key, value)
        }
        return builder.build()
    }
    
    /**
     * Extracts login status from response
     */
    private fun extractLoginStatus(response: Response, responseBody: String): LoginStatus {
        // Extract cookies from response
        val cookies = response.headers("Set-Cookie")
        if (cookies.isNotEmpty()) {
            // Store cookies for future requests
            val domain = response.request.url.host
            cookieStore[domain] = cookies.map { Cookie.parse(response.request.url, it) }
        }
        
        // Try to extract user info from response
        // This is a simplified version - in reality, you'd parse the HTML
        return LoginStatus(
            loggedIn = true,
            username = "user_${System.currentTimeMillis()}", // Placeholder
            userId = "id_${System.currentTimeMillis()}" // Placeholder
        )
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        httpClient.dispatcher.executorService.shutdown()
    }
}