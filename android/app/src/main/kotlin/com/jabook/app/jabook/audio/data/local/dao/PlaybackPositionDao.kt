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
import androidx.room.Query
import androidx.room.Upsert
import com.jabook.app.jabook.audio.data.local.database.entity.PlaybackPositionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Data Access Object for playback positions.
 */
@Dao
public interface PlaybackPositionDao {
    /**
     * Gets the playback position for a book.
     */
    @Query("SELECT * FROM playback_positions WHERE bookId = :bookId")
    public fun getPositionInternal(bookId: String): Flow<PlaybackPositionEntity?>

    public fun getPosition(bookId: String): Flow<PlaybackPositionEntity?> = getPositionInternal(bookId).distinctUntilChanged()

    /**
     * Inserts or updates a playback position.
     */
    @Upsert
    public suspend fun upsertPosition(position: PlaybackPositionEntity)

    /**
     * Deletes a playback position.
     */
    @Query("DELETE FROM playback_positions WHERE bookId = :bookId")
    public suspend fun deletePosition(bookId: String)

    /**
     * Gets all playback positions.
     */
    @Query("SELECT * FROM playback_positions")
    public fun getAllPositionsInternal(): Flow<List<PlaybackPositionEntity>>

    public fun getAllPositions(): Flow<List<PlaybackPositionEntity>> = getAllPositionsInternal().distinctUntilChanged()
}
