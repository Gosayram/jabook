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

package com.jabook.app.jabook.compose.feature.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jabook.app.jabook.compose.data.repository.DownloadHistoryRepository
import com.jabook.app.jabook.compose.domain.model.DownloadHistoryItem
import com.jabook.app.jabook.compose.domain.model.HistorySortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for download history screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
public class DownloadHistoryViewModel
    @Inject
    constructor(
        private val downloadHistoryRepository: DownloadHistoryRepository,
    ) : ViewModel() {
        // Search query
        private val _searchQuery = MutableStateFlow("")
        public val searchQuery: StateFlow<String> = _searchQuery

        // Sort order
        private val _sortOrder = MutableStateFlow(HistorySortOrder.DATE_DESC)
        public val sortOrder: StateFlow<HistorySortOrder> = _sortOrder

        // History with filters applied - simplified approach
        public val history: StateFlow<List<DownloadHistoryItem>> =
            _searchQuery
                .flatMapLatest { query ->
                    downloadHistoryRepository.getHistoryWithFilter(query, _sortOrder.value)
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        // Update search query
        public fun updateSearchQuery(query: String) {
            _searchQuery.value = query
        }

        // Update sort order
        public fun updateSortOrder(order: HistorySortOrder) {
            _sortOrder.value = order
        }

        // Clear all history
        public fun clearHistory() {
            viewModelScope.launch {
                downloadHistoryRepository.clearHistory()
            }
        }

        // Delete old entries (older than 30 days)
        public fun deleteOldEntries() {
            viewModelScope.launch {
                val cutoffTime = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                downloadHistoryRepository.deleteOlderThan(cutoffTime)
            }
        }
    }
