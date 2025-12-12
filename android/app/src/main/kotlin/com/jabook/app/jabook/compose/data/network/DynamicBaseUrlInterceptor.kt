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
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp Interceptor that dynamically replaces the base URL host
 * with the current mirror from MirrorManager.
 *
 * This allows switching RuTracker mirrors without recreating Retrofit instance.
 */
@Singleton
class DynamicBaseUrlInterceptor
    @Inject
    constructor(
        private val mirrorManager: MirrorManager,
    ) : Interceptor {
        companion object {
            private const val TAG = "DynamicBaseUrlInterceptor"
            private const val RUTRACKER_HOST_SUFFIX = "rutracker"
        }

        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val originalUrl = originalRequest.url

            // Only apply to RuTracker requests
            if (!originalUrl.host.contains(RUTRACKER_HOST_SUFFIX, ignoreCase = true)) {
                return chain.proceed(originalRequest)
            }

            // Get current mirror domain
            val currentMirror = mirrorManager.currentMirror.value

            // Replace host with current mirror
            val newUrl =
                originalUrl
                    .newBuilder()
                    .host(currentMirror)
                    .build()

            val newRequest =
                originalRequest
                    .newBuilder()
                    .url(newUrl)
                    .build()

            Log.d(TAG, "Redirecting ${originalUrl.host} → $currentMirror")

            return try {
                val response = chain.proceed(newRequest)

                // If auto-switch is enabled and request failed, try switching mirror
                if (!response.isSuccessful && shouldTriggerAutoSwitch(response.code)) {
                    Log.w(TAG, "Request failed with code ${response.code}, checking auto-switch")

                    // Check if auto-switch is enabled (blocking call, should be fast from cache)
                    val autoSwitchEnabled =
                        runBlocking {
                            mirrorManager.isAutoSwitchEnabled()
                        }

                    if (autoSwitchEnabled) {
                        Log.i(TAG, "Auto-switch enabled, attempting to find working mirror")
                        response.close() // Close failed response

                        val switched =
                            runBlocking {
                                mirrorManager.switchToNextMirror()
                            }

                        if (switched) {
                            // Retry with new mirror
                            val retryUrl =
                                newUrl
                                    .newBuilder()
                                    .host(mirrorManager.currentMirror.value)
                                    .build()

                            val retryRequest =
                                originalRequest
                                    .newBuilder()
                                    .url(retryUrl)
                                    .build()

                            Log.i(TAG, "Retrying request with new mirror: ${mirrorManager.currentMirror.value}")
                            return chain.proceed(retryRequest)
                        }
                    }
                }

                response
            } catch (e: Exception) {
                Log.e(TAG, "Request failed with exception: ${e.message}")

                // On network error, try auto-switch if enabled
                val autoSwitchEnabled =
                    runBlocking {
                        mirrorManager.isAutoSwitchEnabled()
                    }

                if (autoSwitchEnabled) {
                    val switched =
                        runBlocking {
                            mirrorManager.switchToNextMirror()
                        }

                    if (switched) {
                        val retryUrl =
                            newUrl
                                .newBuilder()
                                .host(mirrorManager.currentMirror.value)
                                .build()

                        val retryRequest =
                            originalRequest
                                .newBuilder()
                                .url(retryUrl)
                                .build()

                        Log.i(TAG, "Retrying after network error with new mirror: ${mirrorManager.currentMirror.value}")
                        return chain.proceed(retryRequest)
                    }
                }

                throw e
            }
        }

        /**
         * Determine if the response code should trigger auto-switch.
         *
         * Triggers on:
         * - 5xx server errors
         * - 403 Forbidden (might be blocking)
         * - 404 Not Found (mirror might be outdated)
         */
        private fun shouldTriggerAutoSwitch(code: Int): Boolean =
            when (code) {
                in 500..599 -> true // Server errors
                403 -> true // Forbidden (blocking?)
                404 -> true // Not found (mirror structure changed?)
                else -> false
            }
    }
