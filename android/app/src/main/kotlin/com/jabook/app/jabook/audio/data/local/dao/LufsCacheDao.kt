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

package com.jabook.app.jabook.audio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jabook.app.jabook.audio.data.local.database.entity.LufsCacheEntity

/**
 * Data Access Object for the LUFS analysis cache.
 *
 * P-02: LUFS cache with content-hash-based invalidation.
 */
@Dao
public interface LufsCacheDao {
    /**
     * Returns the cached LUFS entry for the given file path, or `null`.
     */
    @Query("SELECT * FROM lufs_cache WHERE filePath = :filePath LIMIT 1")
    public suspend fun getForPath(filePath: String): LufsCacheEntity?

    /**
     * Returns all LUFS entries for a given book.
     */
    @Query("SELECT * FROM lufs_cache WHERE bookId = :bookId")
    public suspend fun getForBook(bookId: String): List<LufsCacheEntity>

    /**
     * Inserts or replaces a LUFS cache entry.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun upsert(entity: LufsCacheEntity)

    /**
     * Deletes a single cache entry by file path.
     */
    @Query("DELETE FROM lufs_cache WHERE filePath = :filePath")
    public suspend fun delete(filePath: String)

    /**
     * Deletes all cache entries for a given book.
     */
    @Query("DELETE FROM lufs_cache WHERE bookId = :bookId")
    public suspend fun deleteForBook(bookId: String)

    /**
     * Deletes all cache entries.
     */
    @Query("DELETE FROM lufs_cache")
    public suspend fun deleteAll()
}
