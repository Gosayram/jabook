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
import com.jabook.app.jabook.audio.data.local.database.entity.PlaybackPositionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for playback positions.
 */
@Dao
interface PlaybackPositionDao {
    /**
     * Gets the playback position for a book.
     */
    @Query("SELECT * FROM playback_positions WHERE bookId = :bookId")
    fun getPosition(bookId: String): Flow<PlaybackPositionEntity?>

    /**
     * Inserts or updates a playback position.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPosition(position: PlaybackPositionEntity)

    /**
     * Deletes a playback position.
     */
    @Query("DELETE FROM playback_positions WHERE bookId = :bookId")
    suspend fun deletePosition(bookId: String)

    /**
     * Gets all playback positions.
     */
    @Query("SELECT * FROM playback_positions")
    fun getAllPositions(): Flow<List<PlaybackPositionEntity>>
}
