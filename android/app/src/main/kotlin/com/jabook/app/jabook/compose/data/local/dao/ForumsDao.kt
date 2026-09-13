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
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.jabook.app.jabook.compose.data.local.entity.ForumEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the forum display-name cache.
 */
@Dao
public interface ForumsDao {
    /**
     * Atomically replace the whole catalog (delete + insert in one transaction)
     * so readers never observe a half-refreshed table.
     */
    @Transaction
    public suspend fun replaceAll(forums: List<ForumEntity>) {
        deleteAll()
        insertAll(forums)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public suspend fun insertAll(forums: List<ForumEntity>)

    @Query("DELETE FROM forums")
    public suspend fun deleteAll()

    @Query("SELECT * FROM forums ORDER BY sort_order ASC")
    public fun getAll(): Flow<List<ForumEntity>>

    @Query("SELECT * FROM forums ORDER BY sort_order ASC")
    public suspend fun getAllList(): List<ForumEntity>

    @Query("SELECT * FROM forums WHERE forum_id IN (:forumIds)")
    public suspend fun getByIds(forumIds: List<String>): List<ForumEntity>
}
