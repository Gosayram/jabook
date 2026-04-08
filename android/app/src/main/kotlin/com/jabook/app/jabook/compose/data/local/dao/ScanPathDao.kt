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
import com.jabook.app.jabook.compose.data.local.entity.ScanPathEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for custom scan paths.
 */
@Dao
public interface ScanPathDao {
    @Query("SELECT * FROM scan_paths ORDER BY added_date DESC")
    public fun getAllPaths(): Flow<List<ScanPathEntity>>

    @Query("SELECT * FROM scan_paths")
    public suspend fun getAllPathsList(): List<ScanPathEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    public suspend fun insertPath(path: ScanPathEntity)

    @Delete
    public suspend fun deletePath(path: ScanPathEntity)

    @Query("DELETE FROM scan_paths WHERE path = :path")
    public suspend fun deletePathByString(path: String)

    /**
     * Updates the last scan timestamp for a specific path.
     * Called after a successful scan to enable incremental scanning on the next run.
     */
    @Query("UPDATE scan_paths SET last_scan_timestamp = :timestamp WHERE path = :path")
    public suspend fun updateLastScanTimestamp(
        path: String,
        timestamp: Long,
    )
}
