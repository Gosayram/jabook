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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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
public class DynamicBaseUrlInterceptor
    @Inject
    constructor(
        private val mirrorManager: MirrorManager,
        private val loggerFactory: LoggerFactory,
    ) : Interceptor {
        private val logger = loggerFactory.get("DynamicBaseUrlInterceptor")

        public companion object {
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

            val requestStartTime = System.currentTimeMillis()
            logger.d { "Redirecting ${originalUrl.host} -> $currentMirror (${originalUrl.encodedPath})" }

            return try {
                val response = chain.proceed(newRequest)
                val requestDuration = System.currentTimeMillis() - requestStartTime

                // Log successful requests (only for important endpoints)
                if (response.isSuccessful && originalUrl.encodedPath.contains("search.php")) {
                    logger.d {
                        "Request succeeded: ${response.code} (${requestDuration}ms) - ${originalUrl.encodedPath}"
                    }
                }

                // If auto-switch is enabled and request failed, try switching mirror
                if (!response.isSuccessful && shouldTriggerAutoSwitch(response.code)) {
                    logger.w {
                        "Request failed: HTTP ${response.code} ${response.message} (${requestDuration}ms) - ${originalUrl.encodedPath}, checking auto-switch"
                    }

                    // Check if auto-switch is enabled (sync read from in-memory cache)
                    val autoSwitchEnabled = mirrorManager.isAutoSwitchEnabledSync()

                    if (autoSwitchEnabled) {
                        // Grace/backoff check before blocking: a switch attempt
                        // during these windows is guaranteed to fail anyway.
                        if (!mirrorManager.canSwitchNowSync()) {
                            logger.d { "Mirror switch in grace/backoff window, skipping switch attempt" }
                            return response
                        }

                        logger.i { "Auto-switch enabled, attempting to find working mirror" }

                        val switchStartTime = System.currentTimeMillis()
                        // ponytail: runBlocking is unavoidable in OkHttp interceptors
                        // (synchronous API). 15s timeout prevents dispatcher starvation.
                        val switched =
                            runBlocking {
                                withTimeoutOrNull(15_000L) {
                                    mirrorManager.switchToNextMirror()
                                }
                            } == true
                        val switchDuration = System.currentTimeMillis() - switchStartTime

                        if (switched) {
                            // Close the failed response ONLY now — if the switch
                            // fails we must return it open to the caller.
                            response.close()

                            val newMirror = mirrorManager.currentMirror.value
                            // Retry with new mirror
                            val retryUrl =
                                newUrl
                                    .newBuilder()
                                    .host(newMirror)
                                    .build()

                            val retryRequest =
                                originalRequest
                                    .newBuilder()
                                    .url(retryUrl)
                                    .build()

                            logger.i {
                                "Switched to mirror: $newMirror (took ${switchDuration}ms), retrying request: ${originalUrl.encodedPath}"
                            }
                            val retryStartTime = System.currentTimeMillis()
                            val retryResponse = chain.proceed(retryRequest)
                            val retryDuration = System.currentTimeMillis() - retryStartTime
                            if (retryResponse.isSuccessful) {
                                logger.i {
                                    "Retry succeeded: ${retryResponse.code} (${retryDuration}ms) with mirror $newMirror"
                                }
                            }
                            return retryResponse
                        } else {
                            logger.w { "Failed to switch to working mirror (took ${switchDuration}ms)" }
                        }
                    }
                }

                response
            } catch (e: Exception) {
                val requestDuration = System.currentTimeMillis() - requestStartTime

                val isCertificatePinningFailure =
                    e is javax.net.ssl.SSLPeerUnverifiedException &&
                        (e.message?.contains("Certificate pinning failure", ignoreCase = true) == true)

                if (isCertificatePinningFailure) {
                    logger.e(
                        {
                            "Certificate pinning validation failed for host ${newUrl.host}. " +
                                "Blocking request to prevent MITM risk. User must review mirror trust."
                        },
                        e,
                    )
                    throw SecurityException(
                        "Certificate pinning validation failed for ${newUrl.host}. " +
                            "Please switch mirror or update the app.",
                        e,
                    )
                }

                // Check if this is a network/DNS error that should trigger mirror switch
                val isNetworkError =
                    e is java.net.UnknownHostException ||
                        e is java.net.ConnectException ||
                        e is java.net.SocketTimeoutException ||
                        e is javax.net.ssl.SSLException ||
                        (e.message?.contains("Unable to resolve host", ignoreCase = true) == true) ||
                        (e.message?.contains("No address associated with hostname", ignoreCase = true) == true)

                logger.e({
                    "Request failed with exception: ${e.javaClass.simpleName} - ${e.message} (${requestDuration}ms) - ${originalUrl.encodedPath}"
                }, e)

                // Check if auto-switch is enabled (sync read from in-memory cache)
                val autoSwitchEnabled = mirrorManager.isAutoSwitchEnabledSync()

                // Only transport failures (DNS/connect/timeout/TLS) justify a mirror
                // switch — mirror failover cannot fix an application-level error.
                if (autoSwitchEnabled && isNetworkError) {
                    // Grace/backoff check before blocking: a switch attempt
                    // during these windows is guaranteed to fail anyway.
                    if (!mirrorManager.canSwitchNowSync()) {
                        logger.d { "Mirror switch in grace/backoff window, skipping switch attempt" }
                        throw e
                    }

                    logger.i {
                        "Network/DNS error detected (${e.javaClass.simpleName}), auto-switch enabled, attempting mirror switch"
                    }

                    val switchStartTime = System.currentTimeMillis()
                    // ponytail: runBlocking is unavoidable in OkHttp interceptors
                    // (synchronous API). 15s timeout prevents dispatcher starvation.
                    val switched =
                        runBlocking {
                            withTimeoutOrNull(15_000L) {
                                mirrorManager.switchToNextMirror()
                            }
                        } == true
                    val switchDuration = System.currentTimeMillis() - switchStartTime

                    if (switched) {
                        val newMirror = mirrorManager.currentMirror.value
                        val retryUrl =
                            newUrl
                                .newBuilder()
                                .host(newMirror)
                                .build()

                        val retryRequest =
                            originalRequest
                                .newBuilder()
                                .url(retryUrl)
                                .build()

                        logger.i {
                            "Switched to mirror: $newMirror (took ${switchDuration}ms), retrying after ${e.javaClass.simpleName}: ${originalUrl.encodedPath}"
                        }
                        val retryStartTime = System.currentTimeMillis()
                        try {
                            val retryResponse = chain.proceed(retryRequest)
                            val retryDuration = System.currentTimeMillis() - retryStartTime
                            if (retryResponse.isSuccessful) {
                                logger.i {
                                    "Retry succeeded: ${retryResponse.code} (${retryDuration}ms) with mirror $newMirror"
                                }
                            }
                            return retryResponse
                        } catch (retryException: Exception) {
                            val retryDuration = System.currentTimeMillis() - retryStartTime
                            logger.e {
                                "Retry also failed with ${retryException.javaClass.simpleName}: ${retryException.message} (${retryDuration}ms) with mirror $newMirror"
                            }
                            throw retryException
                        }
                    } else {
                        logger.w { "Failed to switch to working mirror (took ${switchDuration}ms)" }
                    }
                } else if (!autoSwitchEnabled) {
                    // Auto-switch is disabled - log but don't switch
                    if (isNetworkError) {
                        logger.w {
                            "Network/DNS error detected (${e.javaClass.simpleName}), but auto-switch is disabled. User must switch mirror manually."
                        }
                    } else {
                        logger.d {
                            "Non-transport error ${e.javaClass.simpleName}, mirror switch not applicable"
                        }
                    }
                } else {
                    logger.d {
                        "Non-transport error ${e.javaClass.simpleName}, mirror switch not applicable"
                    }
                }

                throw e
            }
        }

        /**
         * Determine if the response code should trigger auto-switch.
         *
         * Follows OkHttp's retry semantics: only transport/server-side failures
         * (5xx) indicate a possibly-dead mirror. 4xx is deliberately excluded:
         * - 403 on RuTracker means expired session / user block — every mirror
         *   shares the same backend, switching just ping-pongs.
         * - 404 means the page/topic doesn't exist — the mirror is alive.
         */
        private fun shouldTriggerAutoSwitch(code: Int): Boolean =
            when (code) {
                in 500..599 -> true // Server errors — mirror may be down
                else -> false
            }
    }
