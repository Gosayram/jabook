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

package com.jabook.app.jabook.compose.data.repository

import com.jabook.app.jabook.compose.data.local.dao.FavoriteDao
import com.jabook.app.jabook.compose.data.local.entity.FavoriteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing favorite audiobooks.
 * Provides high-level API for favorites operations with Result wrapping.
 */
@Singleton
public class FavoritesRepository
    @Inject
    constructor(
        private val favoriteDao: FavoriteDao,
    ) {
        /**
         * Flow of all favorites, sorted by date added (newest first).
         */
        public val allFavorites: Flow<List<FavoriteEntity>> = favoriteDao.getAllFavorites()

        /**
         * Flow of all favorite topic IDs for quick membership checks.
         */
        public val favoriteIds: Flow<List<String>> = favoriteDao.getAllFavoriteIds()

        /**
         * Add an audiobook to favorites.
         * Automatically handles duplicates (replaces existing entry).
         */
        suspend fun addToFavorites(favorite: FavoriteEntity): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    favoriteDao.insertFavorite(favorite)
                }
            }

        /**
         * Remove an audiobook from favorites by topic ID.
         */
        suspend fun removeFromFavorites(topicId: String): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    favoriteDao.deleteFavorite(topicId)
                }
            }

        /**
         * Remove multiple audiobooks from favorites.
         */
        suspend fun removeMultipleFavorites(topicIds: List<String>): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    favoriteDao.deleteFavorites(topicIds)
                }
            }

        /**
         * Clear all favorites.
         */
        suspend fun clearAllFavorites(): Result<Unit> =
            withContext(Dispatchers.IO) {
                runCatching {
                    favoriteDao.clearAllFavorites()
                }
            }

        /**
         * Check if an audiobook is favorited.
         */
        suspend fun isFavorite(topicId: String): Boolean =
            withContext(Dispatchers.IO) {
                favoriteDao.isFavorite(topicId)
            }

        /**
         * Get favorite count.
         */
        suspend fun getFavoritesCount(): Int =
            withContext(Dispatchers.IO) {
                favoriteDao.getFavoritesCount()
            }

        /**
         * Get a single favorite by topic ID.
         */
        suspend fun getFavoriteById(topicId: String): FavoriteEntity? =
            withContext(Dispatchers.IO) {
                favoriteDao.getFavoriteById(topicId)
            }
    }
