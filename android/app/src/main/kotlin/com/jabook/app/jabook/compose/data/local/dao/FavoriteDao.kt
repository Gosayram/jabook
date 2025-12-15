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

package com.jabook.app.jabook.compose.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jabook.app.jabook.compose.data.local.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for favorites table operations.
 */
@Dao
interface FavoriteDao {
    /**
     * Get all favorites ordered by date added (newest first).
     */
    @Query("SELECT * FROM favorites ORDER BY added_to_favorites DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    /**
     * Get set of all favorite topic IDs for quick membership checks.
     */
    @Query("SELECT topic_id FROM favorites")
    fun getAllFavoriteIds(): Flow<List<String>>

    /**
     * Get a single favorite by topic ID.
     */
    @Query("SELECT * FROM favorites WHERE topic_id = :topicId LIMIT 1")
    suspend fun getFavoriteById(topicId: String): FavoriteEntity?

    /**
     * Insert or replace a favorite.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    /**
     * Delete a favorite by topic ID.
     */
    @Query("DELETE FROM favorites WHERE topic_id = :topicId")
    suspend fun deleteFavorite(topicId: String)

    /**
     * Delete multiple favorites by topic IDs.
     */
    @Query("DELETE FROM favorites WHERE topic_id IN (:topicIds)")
    suspend fun deleteFavorites(topicIds: List<String>)

    /**
     * Clear all favorites.
     */
    @Query("DELETE FROM favorites")
    suspend fun clearAllFavorites()

    /**
     * Get total count of favorites.
     */
    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun getFavoritesCount(): Int

    /**
     * Check if a topic is favorited.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE topic_id = :topicId LIMIT 1)")
    suspend fun isFavorite(topicId: String): Boolean
}
