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
import com.jabook.app.jabook.audio.data.local.database.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for playlists.
 */
@Dao
interface PlaylistDao {
    /**
     * Gets the playlist for a book.
     */
    @Query("SELECT * FROM playlists WHERE bookId = :bookId")
    fun getPlaylist(bookId: String): Flow<PlaylistEntity?>

    /**
     * Inserts or updates a playlist.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(playlist: PlaylistEntity)

    /**
     * Deletes a playlist.
     */
    @Query("DELETE FROM playlists WHERE bookId = :bookId")
    suspend fun deletePlaylist(bookId: String)

    /**
     * Gets all playlists.
     */
    @Query("SELECT * FROM playlists")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>
}
