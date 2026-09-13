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
import androidx.room.Query
import androidx.room.Transaction

/**
 * Persists libtorrent resume data in its own table, decoupled from the hot
 * torrent-list reads. Every write here is a tiny row (hash + BLOB) — it never
 * rewrites the full torrent row.
 */
@Dao
public interface TorrentResumeDao {
    /**
     * Upsert a resume BLOB. Called whenever libtorrent emits SaveResumeDataAlert.
     */
    @Query(
        """
        INSERT OR REPLACE INTO torrent_resume (hash, resumeData)
        VALUES (:hash, :data)
        """,
    )
    public suspend fun updateResumeData(
        hash: String,
        data: ByteArray,
    )

    /**
     * All resume BLOBs in one query (session-restore path only).
     */
    @Query("SELECT hash, resumeData FROM torrent_resume")
    public suspend fun getAllResumeData(): List<ResumeDataRow>

    /**
     * Single resume BLOB (used when re-adding one torrent).
     */
    @Query("SELECT resumeData FROM torrent_resume WHERE hash = :hash")
    public suspend fun getResumeData(hash: String): ByteArray?

    /**
     * Delete a resume row together with its torrent.
     */
    @Query("DELETE FROM torrent_resume WHERE hash = :hash")
    public suspend fun deleteByHash(hash: String)

    /**
     * Atomically removes a torrent (downloads row + resume row).
     */
    @Transaction
    public suspend fun deleteTorrent(
        dao: TorrentDownloadDao,
        hash: String,
    ) {
        deleteByHash(hash)
        dao.deleteByHash(hash)
    }
}

/** Projection for a single torrent's resume BLOB (restore path only). */
public data class ResumeDataRow(
    public val hash: String,
    public val resumeData: ByteArray,
)
