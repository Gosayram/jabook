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
    ) {
        companion object {
            private const val TAG = "RutrackerAuthService"
            private val CP1251 = Charset.forName("windows-1251")
            private val MEDIA_TYPE_FORM = "application/x-www-form-urlencoded".toMediaType()
        }

        /**
         * Attempt login.
         * @return AuthResult with status and optional captcha data
         */
        suspend fun login(
            credentials: UserCredentials,
            captchaCode: String? = null,
            captchaData: CaptchaData? = null,
        ): AuthResult {
            return withContext(Dispatchers.IO) {
                try {
                    // Encode params to CP1251
                    val postData = buildPostData(credentials, captchaCode, captchaData)
                    val body = postData.toRequestBody(MEDIA_TYPE_FORM)

                    val response = api.login(body)
                    val rawBody = response.body()?.bytes() ?: ByteArray(0)

                    // Decode body
                    val bodyString = String(rawBody, CP1251)

                    if (!response.isSuccessful && response.code() !in 300..399) {
                        return@withContext AuthResult.Error("HTTP Error: ${response.code()}")
                    }

                    // Use Parser
                    return@withContext when (val result = parser.parseLoginResponse(bodyString)) {
                        is com.jabook.app.jabook.compose.data.remote.parser.RutrackerParser.LoginResult.Success -> AuthResult.Success
                        is com.jabook.app.jabook.compose.data.remote.parser.RutrackerParser.LoginResult.Error ->
                            AuthResult.Error(
                                result.message,
                            )
                        is com.jabook.app.jabook.compose.data.remote.parser.RutrackerParser.LoginResult.Captcha ->
                            AuthResult.Captcha(
                                result.data,
                            )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Login failed", e)
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
                val response = api.searchTopics(
                    query = "test",
                    forumIds = "33" // Audiobooks forum
                )

                if (!response.isSuccessful) {
                    Log.d(TAG, "[$operationId] Search check: HTTP ${response.code()}")
                    return false
                }

                // searchTopics returns Response<String>, not ResponseBody
                val bodyString = response.body()?.lowercase() ?: run {
                    Log.d(TAG, "[$operationId] Search check: empty body")
                    return false
                }
                val finalUrl = response.raw().request.url.toString()

                // Check for redirect to login
                if (finalUrl.contains("login.php")) {
                    Log.d(TAG, "[$operationId] Search check: redirected to login")
                    return false
                }

                // Check for auth required messages
                val requiresAuth = bodyString.contains("profile.php?mode=register") ||
                                   bodyString.contains("авторизация") ||
                                   bodyString.contains("войдите в систему")

                if (requiresAuth) {
                    Log.d(TAG, "[$operationId] Search check: auth required message found")
                    return false
                }

                // Check for search page elements (authenticated)
                val hasSearchElements = bodyString.contains("поиск") ||
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
        private suspend fun validateIndexPage(operationId: String): Boolean =
            try {
                // Note: We need to add getIndex() to RutrackerApi
                // For now, we'll reuse profile check as fallback
                // TODO: Add api.getIndex() endpoint
                Log.d(TAG, "[$operationId] Index check: not implemented, using profile fallback")
                false
            } catch (e: Exception) {
                Log.w(TAG, "[$operationId] Index check exception", e)
                false
            }
    }
