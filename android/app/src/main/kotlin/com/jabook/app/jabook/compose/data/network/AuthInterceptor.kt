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
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detects expired sessions on HTTP responses and logs them.
 *
 * ponytail: no interceptor re-login here — the previous runBlocking re-auth
 * (up to 10s inside intercept()) starved the OkHttp dispatcher. Session
 * refresh belongs to AuthRepository flows; this interceptor only reports.
 */
@Singleton
public class AuthInterceptor
    @Inject
    constructor(
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

            // Check if session has expired. A 403 from a Cloudflare challenge is
            // NOT an expired session — re-login would churn against the same wall.
            val sessionExpired =
                response.code == 401 ||
                    (response.code == 403 && !isCloudflareChallenge(response)) ||
                    response.request.url.encodedPath
                        .contains(LOGIN_PAGE_MARKER)

            if (sessionExpired) {
                logger.w { "Session expired detected (code=${response.code}, url=${response.request.url})" }
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
