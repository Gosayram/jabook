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
import com.jabook.app.jabook.compose.data.repository.RutrackerRepository
import com.jabook.app.jabook.compose.domain.model.AppError
import com.jabook.app.jabook.compose.domain.model.Result
import com.jabook.app.jabook.utils.loggingCoroutineExceptionHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
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
    constructor(
        private val repository: RutrackerRepository,
        private val loggerFactory: LoggerFactory,
        private val ioDispatcher: CoroutineDispatcher,
        private val fetchCover: suspend (String) -> Result<String?, AppError> = { topicId ->
            repository.fetchAndSaveCover(topicId)
        },
    ) {
        @Inject
        public constructor(
            repository: RutrackerRepository,
            loggerFactory: LoggerFactory,
        ) : this(
            repository = repository,
            loggerFactory = loggerFactory,
            ioDispatcher = Dispatchers.IO,
        )

        public data class CoverLoadedEvent(
            val topicId: String,
            val coverUrl: String,
        )

        private val logger = loggerFactory.get("CoverLoader")
        private val scope =
            CoroutineScope(
                SupervisorJob() + ioDispatcher + loggingCoroutineExceptionHandler("CoverLoader"),
            )

        // Bounded queues: covers are best-effort. If a queue is momentarily full the
        // request is dropped and can be re-requested by the UI (never blocks, never grows).
        private val primaryQueue = Channel<String>(MAX_QUEUE_CAPACITY)
        private val retryQueue = Channel<String>(MAX_QUEUE_CAPACITY)
        private val activeLoads = ConcurrentHashMap.newKeySet<String>()

        // Simple memory cache for the session — bounded; when full the whole set is reset
        // (covers are cheap to reload, dedup is a nicety, not a guarantee).
        private val loadedCache = ConcurrentHashMap.newKeySet<String>()
        private val retryAttempts = ConcurrentHashMap<String, Int>()
        private val maxRetryAttempts = 3
        private val retryDelayMs = 1200L
        private val _coverLoadedEvents = MutableSharedFlow<CoverLoadedEvent>(replay = 0, extraBufferCapacity = 64)
        public val coverLoadedEvents: SharedFlow<CoverLoadedEvent> = _coverLoadedEvents.asSharedFlow()

        // Concurrency control: allow only N simultaneous loads
        private val maxConcurrentLoads = 3

        public companion object {
            private const val MAX_QUEUE_CAPACITY = 256
            private const val MAX_LOADED_CACHE_ENTRIES = 2_000
            private const val MAX_RETRY_TRACKED = 1_000
        }

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
                // If the bounded queue is full, drop and unmark so the UI can re-request later.
                if (!primaryQueue.trySend(topicId).isSuccess) {
                    activeLoads.remove(topicId)
                }
            }
        }

        private fun startProcessor() {
            // Launch N workers
            repeat(maxConcurrentLoads) {
                scope.launch {
                    while (true) {
                        val topicId =
                            select<String?> {
                                primaryQueue.onReceiveCatching { it.getOrNull() }
                                retryQueue.onReceiveCatching { it.getOrNull() }
                            } ?: break
                        processTopic(topicId)
                    }
                }
            }
        }

        private suspend fun processTopic(topicId: String) {
            try {
                val result = fetchCover(topicId)

                when (result) {
                    is Result.Success -> {
                        val resolvedCoverUrl = result.data?.takeIf { it.isNotBlank() }
                        if (resolvedCoverUrl != null) {
                            _coverLoadedEvents.tryEmit(
                                CoverLoadedEvent(
                                    topicId = topicId,
                                    coverUrl = resolvedCoverUrl,
                                ),
                            )
                            loadedCache.add(topicId)
                            retryAttempts.remove(topicId)
                            if (loadedCache.size > MAX_LOADED_CACHE_ENTRIES) {
                                loadedCache.clear()
                            }
                        } else {
                            scheduleRetry(topicId)
                        }
                    }
                    is Result.Error -> scheduleRetry(topicId)
                    is Result.Loading -> {}
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
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
                // Drop the counter so permanently-failing topics don't accumulate unbounded.
                retryAttempts.remove(topicId)
                return
            }
            retryAttempts[topicId] = currentAttempt + 1
            if (retryAttempts.size > MAX_RETRY_TRACKED) {
                retryAttempts.clear()
            }
            scope.launch {
                delay(retryDelayMs * (currentAttempt + 1))
                if (topicId !in loadedCache && topicId !in activeLoads) {
                    if (activeLoads.add(topicId)) {
                        if (!retryQueue.trySend(topicId).isSuccess) {
                            activeLoads.remove(topicId)
                        }
                    }
                }
            }
        }

        internal fun shutdown() {
            primaryQueue.close()
            retryQueue.close()
            scope.cancel("CoverLoader shutdown")
        }
    }
