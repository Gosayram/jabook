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

package com.jabook.app.jabook.compose.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.jabook.app.jabook.compose.data.local.entity.DownloadHistoryEntity
import com.jabook.app.jabook.compose.domain.model.HistorySortOrder
import kotlinx.coroutines.flow.Flow

/**
 * DAO for download history operations.
 */
@Dao
interface DownloadHistoryDao {
    /**
     * Get all download history with sorting and optional search.
     *
     * @param query SQLite query for flexible sorting/search
     */
    @RawQuery(observedEntities = [DownloadHistoryEntity::class])
    fun getHistory(query: SupportSQLiteQuery): Flow<List<DownloadHistoryEntity>>

    /**
     * Get history entries by status.
     */
    @Query("SELECT * FROM download_history WHERE status = :status ORDER BY completedAt DESC")
    fun getHistoryByStatus(status: String): Flow<List<DownloadHistoryEntity>>

    /**
     * Insert a history entry.
     */
    @Insert
    suspend fun insert(entry: DownloadHistoryEntity)

    /**
     * Delete history entries older than cutoff time.
     *
     * @param cutoffTime Timestamp - entries with completedAt < this will be deleted
     */
    @Query("DELETE FROM download_history WHERE completedAt < :cutoffTime")
    suspend fun deleteOlderThan(cutoffTime: Long)

    /**
     * Clear all history.
     */
    @Query("DELETE FROM download_history")
    suspend fun clearAll()

    /**
     * Get count of history entries.
     */
    @Query("SELECT COUNT(*) FROM download_history")
    suspend fun getCount(): Int
}

/**
 * Helper extension to get history with search and sort.
 */
fun DownloadHistoryDao.getHistoryWithFilter(
    searchQuery: String = "",
    sortOrder: HistorySortOrder = HistorySortOrder.DATE_DESC,
): Flow<List<DownloadHistoryEntity>> {
    val orderBy =
        when (sortOrder) {
            HistorySortOrder.DATE_DESC -> "completedAt DESC"
            HistorySortOrder.DATE_ASC -> "completedAt ASC"
            HistorySortOrder.TITLE_ASC -> "bookTitle COLLATE NOCASE ASC"
            HistorySortOrder.TITLE_DESC -> "bookTitle COLLATE NOCASE DESC"
            HistorySortOrder.SIZE_DESC -> "totalBytes DESC NULLS LAST"
            HistorySortOrder.SIZE_ASC -> "totalBytes ASC NULLS LAST"
        }

    val sql =
        if (searchQuery.isBlank()) {
            "SELECT * FROM download_history ORDER BY $orderBy"
        } else {
            "SELECT * FROM download_history WHERE bookTitle LIKE ? ORDER BY $orderBy"
        }

    val query =
        if (searchQuery.isBlank()) {
            SimpleSQLiteQuery(sql)
        } else {
            SimpleSQLiteQuery(sql, arrayOf("%$searchQuery%"))
        }

    return getHistory(query)
}
