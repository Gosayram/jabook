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
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retry interceptor with exponential backoff for network requests.
 *
 * Retries failed requests with exponential backoff for:
 * - Network errors (IOException, SocketTimeoutException)
 * - Server errors (5xx)
 * - Rate limiting (429)
 *
 * Does NOT retry:
 * - Client errors (4xx except 429)
 * - Authentication errors (401, 403)
 *
 * Configuration:
 * - maxRetries: Maximum number of retry attempts (default: 3)
 * - initialDelayMs: Initial delay before first retry (default: 500ms)
 * - maxDelayMs: Maximum delay between retries (default: 10 seconds)
 * - backoffMultiplier: Multiplier for exponential backoff (default: 2.0)
 */
@Singleton
class RetryInterceptor
    @Inject
    constructor() : Interceptor {
        companion object {
            private const val TAG = "RetryInterceptor"
            private const val MAX_RETRIES = 3
            private const val INITIAL_DELAY_MS = 500L
            private const val MAX_DELAY_MS = 10_000L
            private const val BACKOFF_MULTIPLIER = 2.0
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            var response: Response? = null
            var lastException: Exception? = null
            var attempt = 0

            while (attempt <= MAX_RETRIES) {
                try {
                    // Execute request
                    response = chain.proceed(request)

                    // Check if response is successful or should not be retried
                    if (response.isSuccessful || !shouldRetry(response, attempt)) {
                        return response
                    }

                    // Close response body before retry
                    response.close()

                    // Check if we should retry based on status code
                    if (shouldRetryStatusCode(response.code)) {
                        val delay = calculateDelay(attempt)
                        Log.w(
                            TAG,
                            "Request failed with status ${response.code}, retrying in ${delay}ms (attempt ${attempt + 1}/$MAX_RETRIES): ${request.url}",
                        )
                        Thread.sleep(delay)
                        attempt++
                        continue
                    } else {
                        // Don't retry for this status code
                        return response
                    }
                } catch (e: Exception) {
                    lastException = e

                    // Check if we should retry based on exception type
                    if (shouldRetryException(e) && attempt < MAX_RETRIES) {
                        val delay = calculateDelay(attempt)
                        Log.w(
                            TAG,
                            "Request failed with ${e.javaClass.simpleName}: ${e.message}, retrying in ${delay}ms (attempt ${attempt + 1}/$MAX_RETRIES): ${request.url}",
                        )
                        Thread.sleep(delay)
                        attempt++
                        continue
                    } else {
                        // Don't retry or max retries reached
                        throw IOException("Request failed after ${attempt + 1} attempts: ${e.message}", e)
                    }
                }
            }

            // If we get here, all retries failed
            response?.close()
            throw IOException(
                "Request failed after ${MAX_RETRIES + 1} attempts: ${lastException?.message}",
                lastException,
            )
        }

        /**
         * Calculates delay for exponential backoff.
         *
         * @param attempt Current attempt number (0-based)
         * @return Delay in milliseconds
         */
        private fun calculateDelay(attempt: Int): Long {
            val delay = (INITIAL_DELAY_MS * Math.pow(BACKOFF_MULTIPLIER, attempt.toDouble())).toLong()
            return delay.coerceAtMost(MAX_DELAY_MS)
        }

        /**
         * Checks if exception should trigger a retry.
         *
         * @param exception Exception to check
         * @return true if should retry
         */
        private fun shouldRetryException(exception: Exception): Boolean =
            when (exception) {
                is SocketTimeoutException -> true
                is IOException -> true
                is java.net.ConnectException -> true
                is java.net.UnknownHostException -> true
                else -> false
            }

        /**
         * Checks if status code should trigger a retry.
         *
         * @param statusCode HTTP status code
         * @return true if should retry
         */
        private fun shouldRetryStatusCode(statusCode: Int): Boolean =
            when (statusCode) {
                // Server errors - retry
                500, 502, 503, 504 -> true
                // Rate limiting - retry
                429 -> true
                // Client errors - don't retry
                400, 401, 403, 404 -> false
                // Other - don't retry
                else -> false
            }

        /**
         * Checks if response should be retried.
         *
         * @param response Response to check
         * @param attempt Current attempt number
         * @return true if should retry
         */
        private fun shouldRetry(
            response: Response,
            attempt: Int,
        ): Boolean {
            if (attempt >= MAX_RETRIES) {
                return false
            }

            return shouldRetryStatusCode(response.code)
        }
    }
