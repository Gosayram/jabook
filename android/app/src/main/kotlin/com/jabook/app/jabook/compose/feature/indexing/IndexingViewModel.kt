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

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.compose.data.indexing.ForumIndexer
import com.jabook.app.jabook.compose.data.indexing.IndexingProgress
import com.jabook.app.jabook.compose.data.remote.RuTrackerError
import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
import com.jabook.app.jabook.compose.domain.repository.AuthRepository
import com.jabook.app.jabook.compose.domain.usecase.auth.WithAuthorisedCheckUseCase
import com.jabook.app.jabook.indexing.IndexingForegroundService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    ) : ViewModel() {
        public companion object {
            private const val TAG = "IndexingViewModel"
        }

        private val _indexingProgress = MutableStateFlow<IndexingProgress>(IndexingProgress.Idle)
        val indexingProgress: StateFlow<IndexingProgress> = _indexingProgress.asStateFlow()

        // Timing state
        private val _indexingStartTime = MutableStateFlow<Long?>(null)
        val indexingStartTime: StateFlow<Long?> = _indexingStartTime.asStateFlow()

        private val _clearingInProgress = MutableStateFlow(false)
        val clearingInProgress: StateFlow<Boolean> = _clearingInProgress.asStateFlow()

        private val _isIndexing = MutableStateFlow(false)
        val isIndexing: StateFlow<Boolean> = _isIndexing.asStateFlow()

        /**
         * Start full indexing of all audiobook forums using Foreground Service.
         * This allows indexing to continue in background with notification progress.
         * Checks authentication before starting - RuTracker requires login for forum access.
         *
         * @param context Context needed to start foreground service
         */
        public fun startIndexing(context: Context? = null) {
            if (_isIndexing.value) {
                Log.w(TAG, "Indexing already in progress")
                return
            }

            // If context is provided, use foreground service for background indexing
            if (context != null) {
                Log.d(TAG, "Starting indexing via Foreground Service (background mode)")
                _isIndexing.value = true
                _indexingStartTime.value = System.currentTimeMillis()
                _indexingProgress.value = IndexingProgress.Idle
                IndexingForegroundService.start(context)
                // Progress will be updated from service via broadcast or we can observe service state
                // For now, we'll update state when service completes
                return
            }

            // Fallback: direct indexing (for testing or when context is not available)
            Log.d(TAG, "Starting indexing directly (no context provided)")
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
                    Log.w(TAG, "Indexing requires authentication")
                    _indexingProgress.value =
                        IndexingProgress.Error(
                            message = "Требуется авторизация для индексации форумов. Пожалуйста, войдите в аккаунт.",
                        )
                } catch (e: Exception) {
                    Log.e(TAG, "Indexing failed", e)
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
            // Note: Current implementation doesn't support cancellation
            // This is a placeholder for future implementation
            Log.d(TAG, "Cancel indexing requested (not yet implemented)")
        }

        /**
         * Get current index size.
         */
        suspend fun getIndexSize(): Int = forumIndexer.getIndexSize()

        /**
         * Check if index needs update.
         */
        suspend fun needsUpdate(): Boolean = forumIndexer.needsUpdate()

        /**
         * Get index metadata (statistics).
         */
        suspend fun getIndexMetadata() = forumIndexer.getIndexMetadata()

        /**
         * Start indexing in foreground service (for background operation).
         * This allows indexing to continue even when dialog is closed.
         * Stops current indexing in ViewModel (if running) and transfers control to service.
         */
        public fun startIndexingInBackground(context: Context) {
            Log.d(TAG, "Transferring indexing to foreground service")

            // Stop current indexing in ViewModel if running
            if (_isIndexing.value) {
                Log.d(TAG, "Stopping ViewModel indexing, transferring to service")
                _isIndexing.value = false
                // Note: We can't actually cancel the indexing job, but we stop updating progress
                // The service will start its own indexing
            }

            // Start foreground service
            IndexingForegroundService.start(context)
        }

        /**
         * Clear the entire index (delete all indexed topics).
         * Useful for rebuilding index from scratch.
         */
        suspend fun clearIndex(): Boolean =
            try {
                Log.i(TAG, "Clearing index...")
                _clearingInProgress.value = true
                val startTime = System.currentTimeMillis()
                forumIndexer.clearIndex()
                val duration = System.currentTimeMillis() - startTime
                Log.i(TAG, "Index cleared successfully in ${duration}ms (${duration / 1000}s)")
                _clearingInProgress.value = false
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear index", e)
                _clearingInProgress.value = false
                false
            }
    }
