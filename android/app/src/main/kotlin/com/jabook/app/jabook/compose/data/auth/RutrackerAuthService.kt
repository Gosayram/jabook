// Copyright 2025 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.jabook.app.jabook.compose.data.auth

import android.util.Log
import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.domain.model.CaptchaData
import com.jabook.app.jabook.compose.domain.model.UserCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for Rutracker authentication.
 * Handles CP1251 encoding and response parsing.
 */
@Singleton
class RutrackerAuthService
    @Inject
    constructor(
        private val api: RutrackerApi,
        private val parser: com.jabook.app.jabook.compose.data.remote.parser.RutrackerParser,
        private val decoder: com.jabook.app.jabook.compose.data.remote.encoding.RutrackerSimpleDecoder,
    ) {
        companion object {
            private const val TAG = "RutrackerAuthService"
            private val CP1251 = Charset.forName("windows-1251")
            private val MEDIA_TYPE_FORM = "application/x-www-form-urlencoded".toMediaType()
            private const val MAX_RETRIES = 3
            private const val INITIAL_BACKOFF_MS = 1000L
            private const val REQUEST_TIMEOUT_MS = 15000L
        }

        private var _lastAuthError: String? = null
        val lastAuthError: String?
            get() = _lastAuthError

        /**
         * Attempt login with detailed logging.
         * Based on Flutter's robust authentication implementation.
         *
         * @return AuthResult with status and optional captcha data
         */
        suspend fun login(
            credentials: UserCredentials,
            captchaCode: String? = null,
            captchaData: CaptchaData? = null,
        ): AuthResult {
            val operationId = "auth_login_${System.currentTimeMillis()}"
            val startTime = System.currentTimeMillis()

            return withContext(Dispatchers.IO) {
                try {
                    Log.i(TAG, "[$operationId] Authentication started for user: ${credentials.username}")

                    // Step 1: Encode credentials to CP1251
                    val encodeStart = System.currentTimeMillis()
                    val postData = buildPostData(credentials, captchaCode, captchaData)
                    val encodeDuration = System.currentTimeMillis() - encodeStart
                    Log.d(TAG, "[$operationId] Credentials encoded to CP1251 (${encodeDuration}ms), data length: ${postData.length}")

                    // Step 2: Build request body
                    val body = postData.toRequestBody(MEDIA_TYPE_FORM)
                    Log.d(TAG, "[$operationId] Request body built, content-type: application/x-www-form-urlencoded")

                    // Step 3: Send login request
                    // Step 3: Send login request with retry
                    val requestStart = System.currentTimeMillis()
                    val response =
                        retryWithBackoff(
                            times = MAX_RETRIES,
                            initialDelay = INITIAL_BACKOFF_MS,
                        ) {
                            // Apply timeout for the network call
                            withContext(Dispatchers.IO) {
                                kotlinx.coroutines.withTimeout(REQUEST_TIMEOUT_MS) {
                                    api.login(body)
                                }
                            }
                        }
                    val requestDuration = System.currentTimeMillis() - requestStart

                    val statusCode = response.code()
                    val isRedirect = statusCode in 300..399
                    val rawBody = response.body()?.bytes() ?: ByteArray(0)

                    Log.i(
                        TAG,
                        "[$operationId] Login request completed: HTTP $statusCode, " +
                            "isRedirect=$isRedirect, responseSize=${rawBody.size} bytes (${requestDuration}ms)",
                    )

                    // Step 4: Decode response body with simple decoder (matching Flutter implementation)
                    val decodeStart = System.currentTimeMillis()
                    val contentType = response.headers()["Content-Type"]
                    val bodyString = decoder.decode(rawBody, contentType)
                    val decodeDuration = System.currentTimeMillis() - decodeStart

                    Log.d(
                        TAG,
                        "[$operationId] Response decoded (${decodeDuration}ms)",
                    )

                    // Step 5: Check HTTP status
                    if (!response.isSuccessful && statusCode !in 300..399) {
                        val errorMsg = "HTTP Error: $statusCode"
                        Log.w(TAG, "[$operationId] Authentication failed: $errorMsg")
                        return@withContext AuthResult.Error(errorMsg)
                    }

                    // Step 6: Parse login response
                    val parseStart = System.currentTimeMillis()
                    val result = parser.parseLoginResponse(bodyString)
                    val parseDuration = System.currentTimeMillis() - parseStart

                    val totalDuration = System.currentTimeMillis() - startTime
                    Log.d(
                        TAG,
                        "[$operationId] Response parsed (${parseDuration}ms), " +
                            "total: ${totalDuration}ms (encode:${encodeDuration}ms, request:${requestDuration}ms, parse:${parseDuration}ms)",
                    )

                    // Step 7: Convert result and log outcome
                    return@withContext when (result) {
                        is com.jabook.app.jabook.compose.data.remote.parser.RutrackerParser.LoginResult.Success -> {
                            Log.i(TAG, "[$operationId] ✅ Authentication successful (${totalDuration}ms)")
                            AuthResult.Success
                        }
                        is com.jabook.app.jabook.compose.data.remote.parser.RutrackerParser.LoginResult.Error -> {
                            Log.w(TAG, "[$operationId] ❌ Authentication failed: ${result.message} (${totalDuration}ms)")
                            _lastAuthError = result.message
                            AuthResult.Error(result.message)
                        }
                        is com.jabook.app.jabook.compose.data.remote.parser.RutrackerParser.LoginResult.Captcha -> {
                            Log.i(TAG, "[$operationId] 🔐 Captcha required (${totalDuration}ms)")
                            AuthResult.Captcha(result.data)
                        }
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    val duration = System.currentTimeMillis() - startTime
                    Log.e(TAG, "[$operationId] ⏱️ Request timeout (${duration}ms)", e)
                    return@withContext AuthResult.Error("Request timeout. Please check your connection.")
                } catch (e: java.net.UnknownHostException) {
                    val duration = System.currentTimeMillis() - startTime
                    Log.e(TAG, "[$operationId] 🌐 Network error - unknown host (${duration}ms)", e)
                    return@withContext AuthResult.Error("Cannot reach RuTracker. Check internet connection or try another mirror.")
                } catch (e: java.io.IOException) {
                    val duration = System.currentTimeMillis() - startTime
                    Log.e(TAG, "[$operationId] 🌐 Network I/O error (${duration}ms)", e)
                    return@withContext AuthResult.Error("Network error: ${e.message}")
                } catch (e: Exception) {
                    val duration = System.currentTimeMillis() - startTime
                    Log.e(TAG, "[$operationId] ⚠️ Unexpected error (${duration}ms)", e)
                    _lastAuthError = e.message
                    return@withContext AuthResult.Error(e.message ?: "Unknown error")
                }
            }
        }

        private fun buildPostData(
            credentials: UserCredentials,
            captchaCode: String?,
            captchaData: CaptchaData?,
        ): String {
            val sb = StringBuilder()

            fun encode(s: String): String {
                val bytes = s.toByteArray(CP1251)
                // Manual percent encoding for CP1251 bytes
                return bytes.joinToString("") { byte ->
                    "%" + "%02X".format(byte)
                }
            }

            sb.append("login_username=").append(encode(credentials.username))
            sb.append("&login_password=").append(encode(credentials.password))
            sb.append("&login=").append(encode("Вход"))

            if (captchaCode != null && captchaData != null) {
                sb.append("&cap_sid=").append(captchaData.sid)
                sb
                    .append("&cap_code_")
                    .append(captchaData.sid)
                    .append("=")
                    .append(encode(captchaCode))
            }

            return sb.toString()
        }

        sealed interface AuthResult {
            data object Success : AuthResult

            data class Error(
                val message: String,
            ) : AuthResult

            data class Captcha(
                val data: CaptchaData,
            ) : AuthResult
        }

        /**
         * Validate authentication using multi-tier approach.
         * Based on Flutter's robust 3-tier validation strategy:
         * 1. Profile page (most reliable)
         * 2. Search page (fallback)
         * 3. Index page (final fallback)
         *
         * @param operationId Optional operation ID for logging correlation
         * @return true if authenticated, false otherwise
         */
        suspend fun validateAuth(operationId: String? = null): Boolean {
            val validationId =
                operationId?.let { "${it}_validation" }
                    ?: "auth_validation_${System.currentTimeMillis()}"
            val startTime = System.currentTimeMillis()

            return withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "[$validationId] Authentication validation started")

                    // Test 1: Profile page (most reliable indicator)
                    val profileResult = validateProfilePage(validationId)
                    if (profileResult) {
                        val duration = System.currentTimeMillis() - startTime
                        Log.i(TAG, "[$validationId] Auth validated via profile (${duration}ms)")
                        return@withContext true
                    }

                    // Test 2: Search page (fallback)
                    val searchResult = validateSearchPage(validationId)
                    if (searchResult) {
                        val duration = System.currentTimeMillis() - startTime
                        Log.i(TAG, "[$validationId] Auth validated via search (${duration}ms)")
                        return@withContext true
                    }

                    // Test 3: Index page (final fallback)
                    val indexResult = validateIndexPage(validationId)
                    val duration = System.currentTimeMillis() - startTime

                    if (indexResult) {
                        Log.i(TAG, "[$validationId] Auth validated via index (${duration}ms)")
                    } else {
                        Log.w(TAG, "[$validationId] Auth validation failed - all tests failed (${duration}ms)")
                    }

                    return@withContext indexResult
                } catch (e: Exception) {
                    val duration = System.currentTimeMillis() - startTime
                    Log.e(TAG, "[$validationId] Validation exception (${duration}ms)", e)
                    return@withContext false
                }
            }
        }

        /**
         * Check basic connectivity to RuTracker.
         * Used for diagnostics.
         */
        suspend fun checkConnectivity(operationId: String? = null): Boolean {
            val checkId = operationId?.let { "${it}_conn" } ?: "conn_check_${System.currentTimeMillis()}"
            return try {
                Log.d(TAG, "[$checkId] Checking connectivity...")
                val response = api.getIndex()
                if (response.isSuccessful) {
                    Log.i(TAG, "[$checkId] Connectivity check passed (HTTP ${response.code()})")
                    true
                } else {
                    Log.w(TAG, "[$checkId] Connectivity check failed (HTTP ${response.code()})")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "[$checkId] Connectivity check failed with exception", e)
                false
            }
        }

        /**
         * Test 1: Validate via profile page access.
         * Most reliable indicator of authentication status.
         */
        private suspend fun validateProfilePage(operationId: String): Boolean {
            return try {
                val response = api.getProfile()

                if (!response.isSuccessful) {
                    Log.d(TAG, "[$operationId] Profile check: HTTP ${response.code()}")
                    return false
                }

                val rawBody =
                    response.body()?.bytes() ?: run {
                        Log.d(TAG, "[$operationId] Profile check: empty body")
                        return false
                    }
                val bodyString = String(rawBody, CP1251).lowercase()
                val finalUrl =
                    response
                        .raw()
                        .request.url
                        .toString()

                // Check for redirect to login
                if (finalUrl.contains("login.php")) {
                    Log.d(TAG, "[$operationId] Profile check: redirected to login")
                    return false
                }

                // Check for login form presence (not authenticated)
                if (bodyString.contains("name=\"login_username\"")) {
                    Log.d(TAG, "[$operationId] Profile check: login form present")
                    return false
                }

                // Check for profile elements (authenticated user)
                val hasLogout =
                    bodyString.contains("login.php?logout=1") ||
                        bodyString.contains("mode=logout")
                val hasProfile =
                    bodyString.contains("личный кабинет") ||
                        bodyString.contains("profile") ||
                        bodyString.contains("личные данные")

                val isAuthenticated = hasLogout || hasProfile
                Log.d(TAG, "[$operationId] Profile check: logout=$hasLogout, profile=$hasProfile")

                isAuthenticated
            } catch (e: Exception) {
                Log.w(TAG, "[$operationId] Profile check exception", e)
                false
            }
        }

        /**
         * Test 2: Validate via search page access.
         * Fallback if profile page check is inconclusive.
         */
        private suspend fun validateSearchPage(operationId: String): Boolean {
            return try {
                // Perform a simple search to test authentication
                // searchTopics only accepts query and forumIds (optional)
                val response =
                    api.searchTopics(
                        query = "test",
                        forumIds = "33", // Audiobooks forum
                    )

                if (!response.isSuccessful) {
                    Log.d(TAG, "[$operationId] Search check: HTTP ${response.code()}")
                    return false
                }

                // searchTopics now returns Response<ResponseBody>
                val rawBytes = response.body()?.bytes()
                val bodyString =
                    if (rawBytes != null) {
                        String(rawBytes, charset("windows-1251")).lowercase()
                    } else {
                        Log.d(TAG, "[$operationId] Search check: empty body")
                        return false
                    }
                val finalUrl =
                    response
                        .raw()
                        .request.url
                        .toString()

                // Check for redirect to login
                if (finalUrl.contains("login.php")) {
                    Log.d(TAG, "[$operationId] Search check: redirected to login")
                    return false
                }

                // Check for auth required messages
                val requiresAuth =
                    bodyString.contains("profile.php?mode=register") ||
                        bodyString.contains("авторизация") ||
                        bodyString.contains("войдите в систему")

                if (requiresAuth) {
                    Log.d(TAG, "[$operationId] Search check: auth required message found")
                    return false
                }

                // Check for search page elements (authenticated)
                val hasSearchElements =
                    bodyString.contains("поиск") ||
                        bodyString.contains("search") ||
                        bodyString.contains("форум") ||
                        bodyString.length > 1000 // Search page usually >1KB

                Log.d(TAG, "[$operationId] Search check: hasElements=$hasSearchElements, size=${bodyString.length}")

                hasSearchElements
            } catch (e: Exception) {
                Log.w(TAG, "[$operationId] Search check exception", e)
                false
            }
        }

        /**
         * Test 3: Validate via index page access.
         * Final fallback - checks if forum index is accessible.
         */
        private suspend fun validateIndexPage(operationId: String): Boolean {
            try {
                // Test 3: Index page (final fallback) using api.getIndex()
                val response = api.getIndex()
                if (response.isSuccessful) {
                    val bodyString = response.body()?.string()?.lowercase() ?: ""
                    val isValidIndex = bodyString.contains("форум") || bodyString.contains("rutracker.org")
                    Log.d(TAG, "[$operationId] Index check: HTTP ${response.code()}, validContent=$isValidIndex")
                    return isValidIndex
                } else {
                    Log.w(TAG, "[$operationId] Index check failed: HTTP ${response.code()}")
                    return false
                }
            } catch (e: Exception) {
                Log.w(TAG, "[$operationId] Index check exception", e)
                return false
            }
        }

        /**
         * Helper function to retry operations with exponential backoff.
         */
        private suspend fun <T> retryWithBackoff(
            times: Int,
            initialDelay: Long,
            maxDelay: Long = 10000L,
            factor: Double = 2.0,
            block: suspend () -> T,
        ): T {
            var currentDelay = initialDelay
            repeat(times - 1) { attempt ->
                try {
                    return block()
                } catch (e: Exception) {
                    // Only retry on specific network exceptions
                    if (e !is java.io.IOException && e !is java.net.SocketTimeoutException) {
                        throw e
                    }
                    Log.w(TAG, "Operation failed, retrying in ${currentDelay}ms (attempt ${attempt + 1}/$times)", e)
                    kotlinx.coroutines.delay(currentDelay)
                    currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
                }
            }
            return block() // Last attempt
        }
    }
