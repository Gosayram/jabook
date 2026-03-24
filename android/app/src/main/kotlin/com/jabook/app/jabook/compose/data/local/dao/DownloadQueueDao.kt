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
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jabook.app.jabook.compose.data.local.entity.DownloadQueueEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for download queue operations.
 */
@Dao
public interface DownloadQueueDao {
    /**
     * Get active queue (queued, active, paused) ordered by priority and position.
     */
    @Query(
        """
        SELECT * FROM download_queue 
        WHERE status IN ('queued', 'active', 'paused') 
        ORDER BY priority DESC, queuePosition ASC
        """,
    )
    public fun getActiveQueue(): Flow<List<DownloadQueueEntity>>

    /**
     * Get all queue entries.
     */
    @Query("SELECT * FROM download_queue ORDER BY priority DESC, queuePosition ASC")
    public fun getAllQueue(): Flow<List<DownloadQueueEntity>>

    /**
     * Get queue entry by book ID.
     */
    @Query("SELECT * FROM download_queue WHERE bookId = :bookId")
    public suspend fun getByBookId(bookId: String): DownloadQueueEntity?

    /**
     * Insert or update queue entry.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insert(entity: DownloadQueueEntity)

    /**
     * Update priority for a download.
     */
    @Query("UPDATE download_queue SET priority = :priority, updatedAt = :updatedAt WHERE bookId = :bookId")
    public suspend fun updatePriority(
        bookId: String,
        priority: Int,
        updatedAt: Long = System.currentTimeMillis(),
    )

    /**
     * Update queue position.
     */
    @Query("UPDATE download_queue SET queuePosition = :position, updatedAt = :updatedAt WHERE bookId = :bookId")
    public suspend fun updatePosition(
        bookId: String,
        position: Int,
        updatedAt: Long = System.currentTimeMillis(),
    )

    /**
     * Update status.
     */
    @Query("UPDATE download_queue SET status = :status, updatedAt = :updatedAt WHERE bookId = :bookId")
    public suspend fun updateStatus(
        bookId: String,
        status: String,
        updatedAt: Long = System.currentTimeMillis(),
    )

    /**
     * Delete queue entry.
     */
    @Delete
    public suspend fun delete(entity: DownloadQueueEntity)

    /**
     * Delete by book ID.
     */
    @Query("DELETE FROM download_queue WHERE bookId = :bookId")
    public suspend fun deleteByBookId(bookId: String)

    /**
     * Clear completed/cancelled downloads.
     */
    @Query("DELETE FROM download_queue WHERE status IN ('completed', 'cancelled')")
    public suspend fun clearInactive()
}
