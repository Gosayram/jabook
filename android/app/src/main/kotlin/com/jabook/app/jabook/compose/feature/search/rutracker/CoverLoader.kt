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

package com.jabook.app.jabook.compose.feature.search.rutracker

import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.remote.repository.RutrackerRepository
import com.jabook.app.jabook.utils.loggingCoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages background loading of covers for search results.
 *
 * Ensures:
 * 1. Covers are loaded independently of the search flow.
 * 2. Concurrency is limited to avoid network flooding.
 * 3. Duplicate requests for the same topic are ignored.
 */
@Singleton
public class CoverLoader
    @Inject
    constructor(
        private val repository: RutrackerRepository,
        private val loggerFactory: LoggerFactory,
    ) {
        private val logger = loggerFactory.get("CoverLoader")
        private val scope =
            CoroutineScope(
                SupervisorJob() + Dispatchers.IO + loggingCoroutineExceptionHandler("CoverLoader"),
            )
        private val loadQueue = Channel<String>(Channel.UNLIMITED)
        private val activeLoads = ConcurrentHashMap.newKeySet<String>()
        private val loadedCache = ConcurrentHashMap.newKeySet<String>() // Simple memory cache for session
        private val retryAttempts = ConcurrentHashMap<String, Int>()
        private val maxRetryAttempts = 3
        private val retryDelayMs = 1200L

        // Concurrency control: allow only N simultaneous loads
        private val concurrencyPermits = Mutex()
        private val maxConcurrentLoads = 3

        init {
            startProcessor()
        }

        /**
         * Request cover load for a topic.
         * Guaranteed to be non-blocking.
         */
        public fun loadCover(topicId: String) {
            if (topicId in loadedCache || topicId in activeLoads) {
                return
            }

            // Mark as active immediately to prevent duplicates in queue
            if (activeLoads.add(topicId)) {
                loadQueue.trySend(topicId)
            }
        }

        private fun startProcessor() {
            // Launch N workers
            repeat(maxConcurrentLoads) {
                scope.launch {
                    for (topicId in loadQueue) {
                        processTopic(topicId)
                    }
                }
            }
        }

        private suspend fun processTopic(topicId: String) {
            try {
                val result = repository.fetchAndSaveCover(topicId)

                if (result.isSuccess) {
                    loadedCache.add(topicId)
                    retryAttempts.remove(topicId)
                } else {
                    // If Rutracker failed (e.g. no cover), check Flibusta (To Be Implemented)
                    checkFlibusta(topicId)
                    scheduleRetry(topicId)
                }
            } catch (e: Exception) {
                // Log error
                logger.e(e) { "Error loading cover for $topicId" }
                scheduleRetry(topicId)
            } finally {
                activeLoads.remove(topicId)
            }
        }

        private fun scheduleRetry(topicId: String) {
            val currentAttempt = retryAttempts[topicId] ?: 0
            if (currentAttempt >= maxRetryAttempts) {
                logger.d { "Cover retries exhausted for topic $topicId" }
                return
            }
            retryAttempts[topicId] = currentAttempt + 1
            scope.launch {
                delay(retryDelayMs * (currentAttempt + 1))
                if (topicId !in loadedCache && topicId !in activeLoads) {
                    loadCover(topicId)
                }
            }
        }

        private fun checkFlibusta(topicId: String) {
            // TODO: Implement fallback to Flibusta
            // FlibustaCoverProvider.fetch(topicId)
        }
    }
