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

package com.jabook.app.jabook.compose.data.network

import android.util.Log
import com.jabook.app.jabook.compose.domain.repository.AuthRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interceptor that detects session expiration and attempts automatic re-authentication.
 *
 * Checks for:
 * - 401 Unauthorized responses
 * - 403 Forbidden responses
 * - Redirects to login page
 *
 * If session has expired, attempts to re-login with stored credentials.
 */
@Singleton
class AuthInterceptor
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) : Interceptor {
        companion object {
            private const val TAG = "AuthInterceptor"
            private const val LOGIN_PAGE_MARKER = "login.php"
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()

            // Skip auth check for login endpoint itself
            if (request.url.encodedPath.contains(LOGIN_PAGE_MARKER)) {
                return chain.proceed(request)
            }

            val response = chain.proceed(request)

            // Check if session has expired
            val sessionExpired =
                response.code == 401 ||
                    response.code == 403 ||
                    response.request.url.encodedPath.contains(LOGIN_PAGE_MARKER)

            if (sessionExpired) {
                Log.w(TAG, "Session expired detected (code=${response.code}, url=${response.request.url})")
                response.close() // Close original response

                // Try to re-authenticate with stored credentials
                runBlocking {
                    try {
                        val credentials = authRepository.getStoredCredentials()
                        if (credentials != null) {
                            Log.i(TAG, "Attempting automatic re-authentication...")

                            val loginResult = authRepository.login(credentials)
                            if (loginResult.isSuccess) {
                                Log.i(TAG, "Automatic re-authentication successful")

                                // Retry original request with new session
                                return@runBlocking chain.proceed(request.newBuilder().build())
                            } else {
                                Log.e(TAG, "Automatic re-authentication failed: ${loginResult.exceptionOrNull()}")
                            }
                        } else {
                            Log.w(TAG, "No stored credentials available for re-authentication")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during automatic re-authentication", e)
                    }
                }

                // If re-authentication failed, return the error response
                return chain.proceed(request)
            }

            return response
        }
    }
