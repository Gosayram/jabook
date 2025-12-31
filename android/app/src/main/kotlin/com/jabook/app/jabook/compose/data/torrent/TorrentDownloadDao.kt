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

package com.jabook.app.jabook.compose.data.torrent

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO for torrent downloads
 */
@Dao
interface TorrentDownloadDao {
    /**
     * Get all downloads as Flow
     */
    @Query("SELECT * FROM torrent_downloads ORDER BY addedTime DESC")
    fun getAllFlow(): Flow<List<TorrentDownloadEntity>>

    /**
     * Get all downloads (one-time)
     */
    @Query("SELECT * FROM torrent_downloads ORDER BY addedTime DESC")
    suspend fun getAll(): List<TorrentDownloadEntity>

    /**
     * Get download by hash
     */
    @Query("SELECT * FROM torrent_downloads WHERE hash = :hash")
    suspend fun getByHash(hash: String): TorrentDownloadEntity?

    /**
     * Get download by hash as Flow
     */
    @Query("SELECT * FROM torrent_downloads WHERE hash = :hash")
    fun getByHashFlow(hash: String): Flow<TorrentDownloadEntity?>

    /**
     * Get completed downloads
     */
    @Query("SELECT * FROM torrent_downloads WHERE state = 'COMPLETED' ORDER BY completedTime DESC")
    suspend fun getCompleted(): List<TorrentDownloadEntity>

    /**
     * Get active downloads (downloading, seeding, etc)
     */
    @Query(
        """
        SELECT * FROM torrent_downloads 
        WHERE state IN ('DOWNLOADING', 'SEEDING', 'STREAMING', 'CHECKING', 'DOWNLOADING_METADATA')
        ORDER BY addedTime DESC
        """,
    )
    fun getActiveFlow(): Flow<List<TorrentDownloadEntity>>

    /**
     * Insert download
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: TorrentDownloadEntity)

    /**
     * Insert multiple downloads
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(downloads: List<TorrentDownloadEntity>)

    /**
     * Update download
     */
    @Update
    suspend fun update(download: TorrentDownloadEntity)

    /**
     * Delete download
     */
    @Delete
    suspend fun delete(download: TorrentDownloadEntity)

    /**
     * Delete by hash
     */
    @Query("DELETE FROM torrent_downloads WHERE hash = :hash")
    suspend fun deleteByHash(hash: String)

    /**
     * Delete all completed downloads
     */
    @Query("DELETE FROM torrent_downloads WHERE state = 'COMPLETED'")
    suspend fun deleteAllCompleted()

    /**
     * Delete all downloads
     */
    @Query("DELETE FROM torrent_downloads")
    suspend fun deleteAll()

    /**
     * Update download state
     */
    @Query("UPDATE torrent_downloads SET state = :state WHERE hash = :hash")
    suspend fun updateState(
        hash: String,
        state: TorrentState,
    )

    /**
     * Update download progress
     */
    @Query(
        """
        UPDATE torrent_downloads 
        SET progress = :progress, downloadedSize = :downloadedSize 
        WHERE hash = :hash
        """,
    )
    suspend fun updateProgress(
        hash: String,
        progress: Float,
        downloadedSize: Long,
    )
}
