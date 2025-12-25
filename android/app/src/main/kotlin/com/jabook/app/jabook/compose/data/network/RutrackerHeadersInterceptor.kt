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

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interceptor to add proper RuTracker headers to all requests.
 *
 * Adds:
 * - User-Agent: Browser-like to avoid bot detection
 * - Accept: Standard browser accept header
 * - Accept-Language: Russian + English
 * - Accept-Encoding: gzip, deflate, br (Brotli)
 *
 * These headers make requests look like a real browser to avoid
 * CloudFlare and other anti-bot protections.
 */
@Singleton
class RutrackerHeadersInterceptor
    @Inject
    constructor() : Interceptor {
        companion object {
            // Modern browser User-Agent to avoid bot detection
            private const val USER_AGENT =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

            private const val ACCEPT =
                "text/html,application/xhtml+xml,application/xml;q=0.9," +
                    "image/avif,image/webp,image/apng,*/*;q=0.8"

            private const val ACCEPT_LANGUAGE = "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7"

            // Include Brotli (br) for better compression
            private const val ACCEPT_ENCODING = "gzip, deflate, br"
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()

            // Add headers if not already present
            val requestBuilder = originalRequest.newBuilder()

            if (originalRequest.header("User-Agent") == null) {
                requestBuilder.header("User-Agent", USER_AGENT)
            }

            if (originalRequest.header("Accept") == null) {
                requestBuilder.header("Accept", ACCEPT)
            }

            if (originalRequest.header("Accept-Language") == null) {
                requestBuilder.header("Accept-Language", ACCEPT_LANGUAGE)
            }

            if (originalRequest.header("Accept-Encoding") == null) {
                requestBuilder.header("Accept-Encoding", ACCEPT_ENCODING)
            }

            return chain.proceed(requestBuilder.build())
        }
    }
