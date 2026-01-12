// Copyright 2026 Jabook Contributors
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

import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.domain.repository.AuthRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Intercepts HTTP responses to detect session expiry and automatically re-authenticate.
 */
@Singleton
public class AuthInterceptor
    @Inject
    constructor(
        private val authRepository: Provider<AuthRepository>,
        private val loggerFactory: LoggerFactory,
    ) : Interceptor {
        private val logger = loggerFactory.get("AuthInterceptor")

        public companion object {
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
                    response.request.url.encodedPath
                        .contains(LOGIN_PAGE_MARKER)

            if (sessionExpired) {
                logger.w { "Session expired detected (code=${response.code}, url=${response.request.url})" }
                response.close() // Close original response

                // Try to re-authenticate with stored credentials
                // Note: runBlocking is used here because interceptors are synchronous
                // This should be fast as it only reads from local storage
                val retryResponse: Response? =
                    runBlocking {
                        try {
                            val credentials = authRepository.get().getStoredCredentials()
                            if (credentials != null) {
                                logger.i { "Attempting automatic re-authentication..." }

                                val loginResult = authRepository.get().login(credentials)
                                if (loginResult.isSuccess) {
                                    logger.i { "Automatic re-authentication successful" }

                                    // Retry original request with new session
                                    chain.proceed(request.newBuilder().build())
                                } else {
                                    logger.e { "Automatic re-authentication failed: ${loginResult.exceptionOrNull()}" }
                                    null
                                }
                            } else {
                                logger.w { "No stored credentials available for re-authentication" }
                                null
                            }
                        } catch (e: Exception) {
                            logger.e(e) { "Error during automatic re-authentication" }
                            null
                        }
                    }

                // If re-authentication succeeded, return the retry response
                if (retryResponse != null) {
                    return retryResponse
                }

                // If re-authentication failed, return the error response
                return chain.proceed(request)
            }

            return response
        }
    }
