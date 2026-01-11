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

import android.content.Context
import android.webkit.WebSettings
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interceptor to add proper RuTracker headers to all requests.
 *
 * Adds:
 * - User-Agent: Device's default User-Agent (unique per device, well-supported by services)
 * - Accept: Standard browser accept header
 * - Accept-Language: Russian + English
 *
 * NOTE: Accept-Encoding is NOT added here because BrotliInterceptor
 * handles it automatically. If Accept-Encoding is already set,
 * BrotliInterceptor won't work!
 *
 * These headers make requests look like a real browser to avoid
 * CloudFlare and other anti-bot protections.
 *
 * CRITICAL: User-Agent is ALWAYS set (even if already present) to ensure
 * we never use OkHttp's default User-Agent which may trigger bot detection.
 * Uses device's default User-Agent which is unique and well-supported by services.
 */
@Singleton
public class RutrackerHeadersInterceptor
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val loggerFactory: LoggerFactory,
    ) : Interceptor {
        private val logger = loggerFactory.get("RutrackerHeaders")
        public companion object {

            private const val ACCEPT =
                "text/html,application/xhtml+xml,application/xml;q=0.9," +
                    "image/avif,image/webp,image/apng,*/*;q=0.8"

            private const val ACCEPT_LANGUAGE = "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7"

            // NOTE: Accept-Encoding is NOT set here!
            // BrotliInterceptor will add "Accept-Encoding: br, gzip" automatically.
            // If we set it here, BrotliInterceptor won't work (it only works if Accept-Encoding is null).
        }

        // Lazy initialization of device User-Agent
        // Gets device-specific User-Agent using WebSettings.getDefaultUserAgent() (most efficient method)
        // Returns User-Agent like:
        // "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        // Note: On Android 10+ (User-Agent Reduction), some device details may be replaced with generic values
        private val deviceUserAgent: String by lazy {
            try {
                // Use static method WebSettings.getDefaultUserAgent() - most efficient way
                // This doesn't require creating a WebView instance
                val ua = WebSettings.getDefaultUserAgent(context)
                logger.d { "Device User-Agent: $ua" }
                ua
            } catch (e: Exception) {
                logger.e(e) { "Failed to get device User-Agent, using fallback" }
                // Fallback to a generic Android User-Agent if WebView is not available
                val androidVersion = android.os.Build.VERSION.RELEASE
                val model = android.os.Build.MODEL
                "Mozilla/5.0 (Linux; Android $androidVersion; $model) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val requestBuilder = originalRequest.newBuilder()

            // CRITICAL: Always set User-Agent, even if already present.
            // This ensures we never use OkHttp's default User-Agent (e.g., "okhttp/4.x.x")
            // which may trigger bot detection on RuTracker.
            // Uses device's default User-Agent which is unique per device and well-supported.
            val existingUserAgent = originalRequest.header("User-Agent")
            if (existingUserAgent != null && existingUserAgent != deviceUserAgent) {
                logger.d { "Replacing User-Agent: '$existingUserAgent' -> '$deviceUserAgent'" }
            }
            requestBuilder.removeHeader("User-Agent") // Remove any existing User-Agent
            requestBuilder.header("User-Agent", deviceUserAgent) // Set device's User-Agent

            // Set Accept header if not already present
            if (originalRequest.header("Accept") == null) {
                requestBuilder.header("Accept", ACCEPT)
            }

            // Set Accept-Language header if not already present
            if (originalRequest.header("Accept-Language") == null) {
                requestBuilder.header("Accept-Language", ACCEPT_LANGUAGE)
            }

            // NOTE: Do NOT add Accept-Encoding here!
            // BrotliInterceptor will add it automatically (br, gzip).
            // If Accept-Encoding is already set, BrotliInterceptor won't decompress responses!

            val modifiedRequest = requestBuilder.build()

            // Log User-Agent for auth requests to help debug authentication issues
            if (modifiedRequest.url.encodedPath.contains("login.php") ||
                modifiedRequest.url.encodedPath.contains("profile.php")
            ) {
                logger.d { "Auth request User-Agent: ${modifiedRequest.header("User-Agent")}" }
            }

            return chain.proceed(modifiedRequest)
        }
    }
