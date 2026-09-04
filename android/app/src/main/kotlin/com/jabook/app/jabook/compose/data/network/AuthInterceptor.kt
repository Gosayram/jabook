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
import kotlinx.coroutines.withTimeoutOrNull
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
        private val reauthLock = Any()
        private var reauthInProgress = false

        // Timestamp of the last successful login (guarded by reauthLock).
        // Lets concurrent 401s reuse one login instead of re-login churn.
        private var lastReauthSuccessMs = 0L

        public companion object {
            private const val LOGIN_PAGE_MARKER = "login.php"
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val requestStartedAt = System.currentTimeMillis()

            // Skip auth check for login endpoint itself
            if (request.url.encodedPath.contains(LOGIN_PAGE_MARKER)) {
                return chain.proceed(request)
            }

            val response = chain.proceed(request)

            // Check if session has expired. A 403 from a Cloudflare challenge is
            // NOT an expired session — re-login would churn against the same wall.
            val sessionExpired =
                response.code == 401 ||
                    (response.code == 403 && !isCloudflareChallenge(response)) ||
                    response.request.url.encodedPath
                        .contains(LOGIN_PAGE_MARKER)

            if (sessionExpired) {
                logger.w { "Session expired detected (code=${response.code}, url=${response.request.url})" }

                // Try to re-authenticate with stored credentials.
                // synchronized + flag prevents concurrent re-logins that could
                // starve the OkHttp dispatcher if login uses the same client.
                val reauthenticated =
                    synchronized(reauthLock) {
                        // Short-circuit: another thread already re-authenticated
                        // after our request was sent — session is fresh, reuse it.
                        if (lastReauthSuccessMs > requestStartedAt) {
                            logger.d { "Re-authentication already completed by another thread, reusing session" }
                            true
                        } else if (reauthInProgress) {
                            logger.d { "Re-authentication already in progress, skipping" }
                            false
                        } else {
                            reauthInProgress = true
                            try {
                                // ponytail: runBlocking is unavoidable in OkHttp interceptors.
                                // 10s timeout prevents dispatcher starvation on slow auth.
                                val loginResult =
                                    runBlocking {
                                        withTimeoutOrNull(10_000L) {
                                            val credentials =
                                                authRepository.get().getStoredCredentials()
                                            if (credentials != null) {
                                                logger.i { "Attempting automatic re-authentication..." }
                                                authRepository.get().login(credentials)
                                            } else {
                                                logger.w { "No stored credentials available for re-authentication" }
                                                null
                                            }
                                        }
                                    }
                                if (loginResult != null && loginResult.isSuccess) {
                                    logger.i { "Automatic re-authentication successful" }
                                    lastReauthSuccessMs = System.currentTimeMillis()
                                    true
                                } else if (loginResult != null) {
                                    logger.e {
                                        "Automatic re-authentication failed: ${loginResult.exceptionOrNull()}"
                                    }
                                    false
                                } else {
                                    false
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) {
                                    throw e
                                }
                                logger.e({ "Error during automatic re-authentication" }, e)
                                false
                            } finally {
                                reauthInProgress = false
                            }
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

        /**
         * Cloudflare challenge detection — same markers as MirrorManager health checks
         * (explicit cf-mitigated header or challenge-page body fragments).
         */
        private fun isCloudflareChallenge(response: Response): Boolean =
            response.header("cf-mitigated")?.equals("challenge", ignoreCase = true) == true ||
                runCatching { response.peekBody(8192).string() }.getOrDefault("").let { body ->
                    body.contains("Just a moment", ignoreCase = true) ||
                        body.contains("Checking your browser", ignoreCase = true) ||
                        body.contains("cf-chl", ignoreCase = true)
                }
    }
