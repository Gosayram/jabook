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
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * DAO for torrent downloads
 */
@Dao
@TypeConverters(TorrentDownloadConverters::class)
public interface TorrentDownloadDao {
    /**
     * Get all downloads as Flow.
     * Uses [TorrentDownloadRow] (no resumeData BLOB) so list reads never
     * materialize multi-KB resume BLOBs for every row.
     */
    @Query(
        """
        SELECT hash, name, state, progress, totalSize, downloadedSize, uploadedSize,
               savePath, files, errorMessage, addedTime, completedTime, pauseReason, topicId
        FROM torrent_downloads ORDER BY addedTime DESC
        """,
    )
    public fun getAllFlowInternal(): Flow<List<TorrentDownloadRow>>

    public fun getAllFlow(): Flow<List<TorrentDownloadRow>> = getAllFlowInternal().distinctUntilChanged()

    /**
     * Get all downloads (one-time)
     */
    @Query(
        """
        SELECT hash, name, state, progress, totalSize, downloadedSize, uploadedSize,
               savePath, files, errorMessage, addedTime, completedTime, pauseReason, topicId
        FROM torrent_downloads ORDER BY addedTime DESC
        """,
    )
    public suspend fun getAll(): List<TorrentDownloadRow>

    /**
     * Get download by hash
     */
    @Query(
        """
        SELECT hash, name, state, progress, totalSize, downloadedSize, uploadedSize,
               savePath, files, errorMessage, addedTime, completedTime, pauseReason, topicId
        FROM torrent_downloads WHERE hash = :hash
        """,
    )
    public suspend fun getByHash(hash: String): TorrentDownloadRow?

    /**
     * Insert downloads that do not exist yet. Existing rows are left untouched so
     * persisted [TorrentDownloadEntity.resumeData] is never clobbered by sync writes.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertAll(downloads: List<TorrentDownloadEntity>)

    /**
     * Persists a placeholder row the moment a torrent is added, so a process
     * death before the ADD_TORRENT alert fires cannot lose the download.
     * The real name/files arrive with the alert and overwrite this row.
     */
    @Query(
        """
        INSERT OR IGNORE INTO torrent_downloads
            (hash, name, state, progress, totalSize, downloadedSize, uploadedSize,
             savePath, files, errorMessage, addedTime, completedTime, pauseReason, topicId)
        VALUES (:hash, :hash, 'QUEUED', 0, 0, 0, 0, :savePath, '[]', NULL, :now, NULL, NULL, :topicId)
        """,
    )
    public suspend fun insertPendingRow(
        hash: String,
        savePath: String,
        topicId: String?,
        now: Long,
    )

    /**
     * Update all live-synced columns for one download, deliberately excluding
     * resumeData so periodic progress syncs cannot erase the resume BLOB.
     */
    @Query(
        """
        UPDATE torrent_downloads SET
            name = :name, state = :state, progress = :progress,
            totalSize = :totalSize, downloadedSize = :downloadedSize,
            uploadedSize = :uploadedSize, savePath = :savePath, files = :files,
            errorMessage = :errorMessage, addedTime = :addedTime,
            completedTime = :completedTime, pauseReason = :pauseReason,
            topicId = :topicId
        WHERE hash = :hash
        """,
    )
    public suspend fun updateSyncFields(
        hash: String,
        name: String,
        state: TorrentState,
        progress: Float,
        totalSize: Long,
        downloadedSize: Long,
        uploadedSize: Long,
        savePath: String,
        files: List<TorrentFile>,
        errorMessage: String?,
        addedTime: Long,
        completedTime: Long,
        pauseReason: PauseReason?,
        topicId: String?,
    )

    /**
     * Insert new downloads then update all live-synced columns.
     * Runs in a single transaction so callers never see a partial write.
     */
    @Transaction
    public suspend fun upsertSyncFields(downloads: List<TorrentDownloadEntity>) {
        insertAll(downloads)
        downloads.forEach { e ->
            updateSyncFields(
                hash = e.hash,
                name = e.name,
                state = e.state,
                progress = e.progress,
                totalSize = e.totalSize,
                downloadedSize = e.downloadedSize,
                uploadedSize = e.uploadedSize,
                savePath = e.savePath,
                files = e.files,
                errorMessage = e.errorMessage,
                addedTime = e.addedTime,
                completedTime = e.completedTime,
                pauseReason = e.pauseReason,
                topicId = e.topicId,
            )
        }
    }

    /**
     * Delete by hash
     */
    @Query("DELETE FROM torrent_downloads WHERE hash = :hash")
    public suspend fun deleteByHash(hash: String)

    /**
     * Persist a state override (e.g. STOPPED when a torrent is removed without
     * deleting files) so restoreActiveDownloads() won't re-add it on restart.
     */
    @Query("UPDATE torrent_downloads SET state = :state WHERE hash = :hash")
    public suspend fun updateState(
        hash: String,
        state: TorrentState,
    )

    /**
     * Returns all non-completed/non-error/non-stopped downloads regardless of
     * resume data, for re-adding on session init. STOPPED torrents were explicitly
     * removed by the user and must NOT be silently re-added on restart.
     */
    @Query(
        """
        SELECT hash, name, state, progress, totalSize, downloadedSize, uploadedSize,
               savePath, files, errorMessage, addedTime, completedTime, pauseReason, topicId
        FROM torrent_downloads WHERE state NOT IN ('COMPLETED', 'ERROR', 'STOPPED')
        """,
    )
    public suspend fun getActiveDownloads(): List<TorrentDownloadRow>
}
