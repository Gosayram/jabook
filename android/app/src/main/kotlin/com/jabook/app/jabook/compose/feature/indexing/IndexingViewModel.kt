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

package com.jabook.app.jabook.compose.feature.indexing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.jabook.app.jabook.compose.core.logger.LoggerFactory
import com.jabook.app.jabook.compose.data.indexing.ForumIndexer
import com.jabook.app.jabook.compose.data.indexing.IndexingProgress
import com.jabook.app.jabook.compose.data.local.dao.IndexMetadata
import com.jabook.app.jabook.compose.data.remote.RuTrackerError
import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.data.worker.IndexingWorkScheduler
import com.jabook.app.jabook.compose.domain.repository.AuthRepository
import com.jabook.app.jabook.compose.domain.usecase.auth.WithAuthorisedCheckUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing forum indexing operations.
 */
@HiltViewModel
public class IndexingViewModel
    @Inject
    constructor(
        private val forumIndexer: ForumIndexer,
        private val authRepository: AuthRepository,
        private val withAuthorisedCheckUseCase: WithAuthorisedCheckUseCase,
        private val indexingWorkScheduler: IndexingWorkScheduler,
        private val loggerFactory: LoggerFactory,
    ) : ViewModel() {
        private companion object {
            const val POST_COMPLETION_VERIFY_ATTEMPTS = 6
            const val POST_COMPLETION_VERIFY_DELAY_MS = 750L
        }

        private val logger = loggerFactory.get("IndexingViewModel")

        private val _indexingProgress = MutableStateFlow<IndexingProgress>(IndexingProgress.Idle)
        public val indexingProgress: StateFlow<IndexingProgress> = _indexingProgress.asStateFlow()

        // Timing state
        private val _indexingStartTime = MutableStateFlow<Long?>(null)
        public val indexingStartTime: StateFlow<Long?> = _indexingStartTime.asStateFlow()

        private val _clearingInProgress = MutableStateFlow(false)
        public val clearingInProgress: StateFlow<Boolean> = _clearingInProgress.asStateFlow()

        private val _isIndexing = MutableStateFlow(false)
        public val isIndexing: StateFlow<Boolean> = _isIndexing.asStateFlow()
        private val _indexSize = MutableStateFlow(0)
        public val indexSize: StateFlow<Int> = _indexSize.asStateFlow()
        private var indexingMonitorJob: Job? = null

        init {
            startIndexingWorkMonitor()
            viewModelScope.launch {
                refreshIndexSize()
            }
        }

        /**
         * Start full indexing of all audiobook forums using Foreground Service.
         * This allows indexing to continue in background with notification progress.
         * Checks authentication before starting - RuTracker requires login for forum access.
         *
         * @param context Context needed to start foreground service
         */
        public fun startIndexing(context: android.content.Context?) {
            if (_isIndexing.value) {
                logger.w { "Indexing already in progress" }
                return
            }

            // If context is provided, use foreground service for background indexing
            if (context != null) {
                logger.d { "Starting indexing via WorkManager" }
                _isIndexing.value = true
                _indexingStartTime.value = System.currentTimeMillis()
                _indexingProgress.value = IndexingProgress.Idle
                indexingWorkScheduler.enqueue()
                startIndexingWorkMonitor()
                // Progress will be updated from service via broadcast or we can observe service state
                // For now, we'll update state when service completes
                return
            }

            // Fallback: direct indexing (for testing or when context is not available)
            logger.d { "Starting indexing directly (no context provided)" }
            viewModelScope.launch {
                _isIndexing.value = true
                _indexingStartTime.value = System.currentTimeMillis()
                _indexingProgress.value = IndexingProgress.Idle

                try {
                    // Use WithAuthorisedCheckUseCase to ensure authentication before indexing
                    // RuTracker requires authentication to access forum pages
                    withAuthorisedCheckUseCase(operationId = "indexing") {
                        forumIndexer.indexForums(
                            forumIds = RutrackerApi.AUDIOBOOKS_FORUM_IDS,
                            preloadCovers = true,
                        ) { progress ->
                            _indexingProgress.value = progress
                        }
                    }
                } catch (e: RuTrackerError.Unauthorized) {
                    logger.w { "Indexing requires authentication" }
                    _indexingProgress.value =
                        IndexingProgress.Error(
                            message = "Требуется авторизация для индексации форумов. Пожалуйста, войдите в аккаунт.",
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e({ "Indexing failed" }, e)
                    _indexingProgress.value =
                        IndexingProgress.Error(
                            message = e.message ?: "Unknown error",
                        )
                } finally {
                    _isIndexing.value = false
                }
            }
        }

        /**
         * Cancel indexing (if possible).
         */
        public fun cancelIndexing() {
            indexingWorkScheduler.cancel()
            _isIndexing.value = false
            _indexingProgress.value = IndexingProgress.Idle
        }

        /**
         * Get current index size.
         */
        public suspend fun getIndexSize(): Int = refreshIndexSize()

        private suspend fun refreshIndexSize(): Int = retryingIndexSizeRead()

        private suspend fun resolveIndexSizeAfterServiceCompletion(): Int {
            var size = refreshIndexSize()
            if (size > 0) return size

            repeat(POST_COMPLETION_VERIFY_ATTEMPTS) {
                delay(POST_COMPLETION_VERIFY_DELAY_MS)
                size = refreshIndexSize()
                if (size > 0) {
                    logger.d { "Index materialized after service stop on attempt ${it + 1}" }
                    return size
                }
            }

            return size
        }

        private suspend fun retryingIndexSizeRead(): Int {
            var attempt = 0
            var lastError: Exception? = null
            while (attempt < 3) {
                try {
                    val size = forumIndexer.getIndexSize()
                    _indexSize.value = size
                    return size
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    attempt += 1
                    if (attempt < 3) {
                        delay(150L * attempt)
                    }
                }
            }
            logger.e({ "Failed to refresh index size after retries, using cached value" }, lastError)
            return _indexSize.value
        }

        /**
         * Check if index needs update.
         */
        public suspend fun needsUpdate(): Boolean = forumIndexer.needsUpdate()

        /**
         * Get index metadata (statistics).
         */
        public suspend fun getIndexMetadata(): IndexMetadata? = forumIndexer.getIndexMetadata()

        /**
         * Start indexing in foreground service (for background operation).
         * This allows indexing to continue even when dialog is closed.
         * Stops current indexing in ViewModel (if running) and transfers control to service.
         *
         * @param context Context needed to start foreground service
         */
        public fun startIndexingInBackground(context: android.content.Context) {
            logger.d { "Transferring indexing to WorkManager" }

            // Stop current indexing in ViewModel if running
            if (_isIndexing.value) {
                logger.d { "Stopping ViewModel indexing, transferring to service" }
                _isIndexing.value = false
                // Note: We can't actually cancel the indexing job, but we stop updating progress
                // The service will start its own indexing
            }

            // Start foreground service
            indexingWorkScheduler.enqueue()
            _isIndexing.value = true
            _indexingStartTime.value = System.currentTimeMillis()
            startIndexingWorkMonitor()
        }

        /**
         * Clear the entire index (delete all indexed topics).
         * Useful for rebuilding index from scratch.
         */
        public suspend fun clearIndex(): Boolean =
            try {
                logger.i { "Clearing index..." }
                _clearingInProgress.value = true
                val startTime = System.currentTimeMillis()
                forumIndexer.clearIndex()
                val duration = System.currentTimeMillis() - startTime
                logger.i { "Index cleared successfully in ${duration}ms (${duration / 1000}s)" }
                _indexSize.value = 0
                _isIndexing.value = false
                _indexingProgress.value = IndexingProgress.Idle
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e({ "Failed to clear index" }, e)
                false
            } finally {
                _clearingInProgress.value = false
            }

        private fun startIndexingWorkMonitor() {
            if (indexingMonitorJob?.isActive == true) {
                return
            }
            indexingMonitorJob =
                viewModelScope.launch {
                    var workWasActive = false
                    indexingWorkScheduler.observe().collect { workInfos ->
                        val activeWork = workInfos.firstOrNull { !it.state.isFinished }
                        if (activeWork != null) {
                            workWasActive = true
                            _isIndexing.value = true
                            _indexingProgress.value = activeWork.toIndexingProgress()
                        } else if (workWasActive) {
                            workWasActive = false
                            _isIndexing.value = false
                            val sizeAfterFinish = resolveIndexSizeAfterServiceCompletion()
                            _indexingProgress.value =
                                if (workInfos.any { it.state == WorkInfo.State.SUCCEEDED } && sizeAfterFinish > 0) {
                                    IndexingProgress.Completed(totalTopics = sizeAfterFinish, durationMs = 0L)
                                } else {
                                    IndexingProgress.Error("Индексация не завершилась успешно")
                                }
                            }
                        }
                    }
                }
        }

        override fun onCleared() {
            super.onCleared()
            indexingMonitorJob?.cancel()
            indexingMonitorJob = null
        }

        private fun WorkInfo.toIndexingProgress(): IndexingProgress {
            val percent = progress.getInt("progress_percent", 0).coerceIn(0, 100)
            return IndexingProgress.InProgress(
                currentForum = progress.getString("progress_message").orEmpty(),
                currentPage = percent,
                totalPages = 100,
                currentForumIndex = 0,
                totalForums = 1,
            )
        }
    }
