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

package com.jabook.app.jabook.compose.feature.indexing

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.compose.data.indexing.ForumIndexer
import com.jabook.app.jabook.compose.data.indexing.IndexingProgress
import com.jabook.app.jabook.compose.data.remote.api.RutrackerApi
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
class IndexingViewModel
    @Inject
    constructor(
        private val forumIndexer: ForumIndexer,
    ) : ViewModel() {
        companion object {
            private const val TAG = "IndexingViewModel"
        }

        private val _indexingProgress = MutableStateFlow<IndexingProgress>(IndexingProgress.Idle)
        val indexingProgress: StateFlow<IndexingProgress> = _indexingProgress.asStateFlow()

        private val _isIndexing = MutableStateFlow(false)
        val isIndexing: StateFlow<Boolean> = _isIndexing.asStateFlow()

        /**
         * Start full indexing of all audiobook forums.
         */
        fun startIndexing() {
            if (_isIndexing.value) {
                Log.w(TAG, "Indexing already in progress")
                return
            }

            viewModelScope.launch {
                _isIndexing.value = true
                _indexingProgress.value = IndexingProgress.Idle

                try {
                    forumIndexer.indexForums(
                        forumIds = RutrackerApi.AUDIOBOOKS_FORUM_IDS,
                        preloadCovers = true,
                    ) { progress ->
                        _indexingProgress.value = progress
                    }
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
        fun cancelIndexing() {
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
    }
