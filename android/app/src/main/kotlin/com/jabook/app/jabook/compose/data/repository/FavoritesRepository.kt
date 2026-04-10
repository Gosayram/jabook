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
import com.jabook.app.jabook.compose.data.local.entity.toFavoriteEntity
import com.jabook.app.jabook.compose.data.local.entity.toFavoriteItem
import com.jabook.app.jabook.compose.domain.model.FavoriteItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
        public val allFavorites: Flow<List<FavoriteItem>> =
            favoriteDao.getAllFavorites().map { favorites ->
                favorites.map { it.toFavoriteItem() }
            }

        /**
         * Flow of all favorite topic IDs for quick membership checks.
         */
        public val favoriteIds: Flow<List<String>> = favoriteDao.getAllFavoriteIds()

        /**
         * Add an audiobook to favorites.
         * Automatically handles duplicates (replaces existing entry).
         */
        public suspend fun addToFavorites(favorite: FavoriteItem): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    favoriteDao.insertFavorite(favorite.toFavoriteEntity())
                    Result.success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

        /**
         * Remove an audiobook from favorites by topic ID.
         */
        public suspend fun removeFromFavorites(topicId: String): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    favoriteDao.deleteFavorite(topicId)
                    Result.success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

        /**
         * Remove multiple audiobooks from favorites.
         */
        public suspend fun removeMultipleFavorites(topicIds: List<String>): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    favoriteDao.deleteFavorites(topicIds)
                    Result.success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

        /**
         * Clear all favorites.
         */
        public suspend fun clearAllFavorites(): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    favoriteDao.clearAllFavorites()
                    Result.success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

        /**
         * Check if an audiobook is favorited.
         */
        public suspend fun isFavorite(topicId: String): Boolean =
            withContext(Dispatchers.IO) {
                favoriteDao.isFavorite(topicId)
            }

        /**
         * Get favorite count.
         */
        public suspend fun getFavoritesCount(): Int =
            withContext(Dispatchers.IO) {
                favoriteDao.getFavoritesCount()
            }

        /**
         * Get a single favorite by topic ID.
         */
        public suspend fun getFavoriteById(topicId: String): FavoriteItem? =
            withContext(Dispatchers.IO) {
                favoriteDao.getFavoriteById(topicId)?.toFavoriteItem()
            }
    }
