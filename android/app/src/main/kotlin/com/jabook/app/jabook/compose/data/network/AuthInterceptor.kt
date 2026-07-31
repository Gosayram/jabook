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
import kotlinx.coroutines.CancellationException
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

                // Try to re-authenticate with stored credentials
                // Note: runBlocking is used here because interceptors are synchronous
                // This should be fast as it only reads from local storage
                val reauthenticated =
                    runBlocking {
                        try {
                            val credentials = authRepository.get().getStoredCredentials()
                            if (credentials != null) {
                                logger.i { "Attempting automatic re-authentication..." }

                                val loginResult = authRepository.get().login(credentials)
                                if (loginResult.isSuccess) {
                                    logger.i { "Automatic re-authentication successful" }
                                    true
                                } else {
                                    logger.e { "Automatic re-authentication failed: ${loginResult.exceptionOrNull()}" }
                                    false
                                }
                            } else {
                                logger.w { "No stored credentials available for re-authentication" }
                                false
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException) {
                                throw e
                            }
                            logger.e({ "Error during automatic re-authentication" }, e)
                            false
                        }
                    }

                if (reauthenticated) {
                    response.close()
                    // Retry original request with the refreshed session.
                    return chain.proceed(request.newBuilder().build())
                }

                // Preserve the original response when re-authentication fails. Retrying it
                // would duplicate potentially non-idempotent requests without a new session.
                return response
            }

            return response
        }
    }
