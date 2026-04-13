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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Network-aware scheduler for request gating.
 *
 * Behavior:
 * - If device is online now, request can run immediately.
 * - If device is offline, request is deferred until network is back or timeout expires.
 */
@Singleton
public class ConnectivityAwareRequestScheduler
    @Inject
    constructor(
        private val networkMonitor: NetworkMonitor,
        loggerFactory: LoggerFactory,
    ) {
        private val logger = loggerFactory.get("ConnectivityScheduler")

        public companion object {
            public const val DEFAULT_WAIT_TIMEOUT_MS: Long = 30_000L
        }

        public suspend fun awaitOnline(
            operation: String,
            timeoutMs: Long = DEFAULT_WAIT_TIMEOUT_MS,
        ): Boolean {
            if (networkMonitor.isOnline.first()) {
                return true
            }

            logger.w {
                "Network offline, scheduling '$operation' until connectivity restores (timeout=${timeoutMs}ms)"
            }

            val resumed =
                withTimeoutOrNull(timeoutMs) {
                    networkMonitor.isOnline.filter { it }.first()
                } != null

            if (resumed) {
                logger.i { "Network restored, resuming '$operation'" }
            } else {
                logger.w { "Connectivity wait timeout for '$operation' (${timeoutMs}ms)" }
            }

            return resumed
        }
    }
