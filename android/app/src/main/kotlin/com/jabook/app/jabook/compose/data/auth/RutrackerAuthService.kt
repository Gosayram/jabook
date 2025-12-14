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
         * Validate authentication by checking profile page access.
         *
         * @return true if authenticated, false otherwise
         */
        suspend fun validateAuth(): Boolean {
            return withContext(Dispatchers.IO) {
                try {
                    val response = api.getProfile()

                    if (!response.isSuccessful) return@withContext false

                    val rawBody = response.body()?.bytes() ?: return@withContext false
                    val bodyString = String(rawBody, CP1251)

                    // Check for redirect to login or login form presence
                    // If Retrofit follows redirects, we check the final URL or body content
                    val finalUrl =
                        response
                            .raw()
                            .request.url
                            .toString()
                    if (finalUrl.contains("login.php")) {
                        return@withContext false
                    }

                    // Strict check: if body contains login input fields or "guest" markers
                    // "login_username" input field is present on login page
                    if (bodyString.contains("name=\"login_username\"", ignoreCase = true)) {
                        return@withContext false
                    }

                    // "Профиль" or "Profile" should be present for logged in user
                    // "Выход" (Logout) link should be present
                    val hasLogout =
                        bodyString.contains("login.php?logout=1") ||
                            bodyString.contains("mode=logout")

                    return@withContext hasLogout
                } catch (e: Exception) {
                    Log.e(TAG, "Validation failed", e)
                    return@withContext false
                }
            }
        }
    }
