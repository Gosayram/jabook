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

package com.jabook.app.jabook.compose.data.repository

import com.jabook.app.jabook.compose.data.local.dao.DownloadHistoryDao
import com.jabook.app.jabook.compose.data.local.dao.getHistoryWithFilter
import com.jabook.app.jabook.compose.data.local.entity.toDownloadHistoryItem
import com.jabook.app.jabook.compose.domain.model.DownloadHistoryItem
import com.jabook.app.jabook.compose.domain.model.HistorySortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
public class DownloadHistoryRepository
    @Inject
    constructor(
        private val downloadHistoryDao: DownloadHistoryDao,
    ) {
        public fun getHistoryWithFilter(
            searchQuery: String = "",
            sortOrder: HistorySortOrder = HistorySortOrder.DATE_DESC,
        ): Flow<List<DownloadHistoryItem>> =
            downloadHistoryDao
                .getHistoryWithFilter(searchQuery, sortOrder)
                .map { history -> history.map { it.toDownloadHistoryItem() } }

        public suspend fun clearHistory() {
            downloadHistoryDao.clearAll()
        }

        public suspend fun deleteOlderThan(cutoffTime: Long) {
            downloadHistoryDao.deleteOlderThan(cutoffTime)
        }
    }
