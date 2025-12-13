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

                    // Decode body to check for errors
                    // Try CP1251 first as it's default for Rutracker
                    val bodyString = String(rawBody, CP1251)

                    // Check for cookies (success)
                    // Rutracker typically redirects (302) on success, or returns 200 with error
                    // Retrofit/OkHttp follows redirects by default unless configured otherwise.
                    // If we followed redirect to index, we are good if we have valid session cookies.
                    // However, our OkHttpClient might be configured to NOT follow redirects for login?
                    // Let's assume standard behavior: if we land on index page (200) or get a 302, we check cookies.
                    // The most reliable check is "bb_session" cookie existence in the cookie jar.
                    // Since CookieJar handles cookies automatically, we just need to verify success/failure.

                    // Check for error messages in body
                    if (bodyString.contains("неверный пароль", ignoreCase = true) ||
                        bodyString.contains("wrong password", ignoreCase = true)
                    ) {
                        return@withContext AuthResult.Error("Invalid username or password")
                    }

                    if (bodyString.contains("введите код подтверждения", ignoreCase = true) ||
                        bodyString.contains("site want captcha", ignoreCase = true)
                    ) {
                        // Extract captcha
                        // Simplified extraction - in real scenario would use Jsoup or Regex
                        // This is a placeholder for captcha extraction logic matching legacy parser
                        val captcha = extractCaptcha(bodyString)
                        return@withContext AuthResult.Captcha(captcha)
                    }

                    if (response.isSuccessful) {
                        // Success!
                        return@withContext AuthResult.Success
                    } else {
                        return@withContext AuthResult.Error("HTTP Error: ${response.code()}")
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

        private fun extractCaptcha(html: String): CaptchaData {
            // Regex extraction similar to legacy
            // Placeholder implementation
            // Need to parse: <img src="//static.t-ru.org/captcha/..." ...>
            // and <input type="hidden" name="cap_sid" value="...">

            val sidRegex = """name="cap_sid"\s+value="(\d+)"""".toRegex()
            val sidMatch = sidRegex.find(html)
            val sid = sidMatch?.groupValues?.get(1) ?: ""

            val urlRegex = """img\s+src="([^"]+captcha[^"]+)"""".toRegex()
            val urlMatch = urlRegex.find(html)
            var url = urlMatch?.groupValues?.get(1) ?: ""
            if (url.startsWith("//")) url = "https:$url"

            return CaptchaData(url, sid)
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
