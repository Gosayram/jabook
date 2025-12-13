// Copyright 2025 Jabook Contributors
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
import com.jabook.app.jabook.audio.data.local.database.entity.SavedPlayerStateEntity

/**
 * Data Access Object for saved player states.
 */
@Dao
interface SavedPlayerStateDao {
    /**
     * Gets the saved player state for a group path.
     */
    @Query("SELECT * FROM saved_player_states WHERE groupPath = :groupPath")
    suspend fun getState(groupPath: String): SavedPlayerStateEntity?

    /**
     * Gets the most recently updated saved player state.
     */
    @Query("SELECT * FROM saved_player_states ORDER BY lastUpdated DESC LIMIT 1")
    suspend fun getLatestState(): SavedPlayerStateEntity?

    /**
     * Inserts or updates a saved player state.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: SavedPlayerStateEntity)

    /**
     * Deletes a saved player state.
     */
    @Query("DELETE FROM saved_player_states WHERE groupPath = :groupPath")
    suspend fun deleteState(groupPath: String)

    /**
     * Deletes all saved player states.
     */
    @Query("DELETE FROM saved_player_states")
    suspend fun deleteAllStates()
}
