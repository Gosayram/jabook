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

package com.jabook.app.jabook.audio.data.repository

import com.jabook.app.jabook.audio.core.result.Result
import com.jabook.app.jabook.audio.core.result.asResult
import com.jabook.app.jabook.audio.data.local.dao.PlaylistDao
import com.jabook.app.jabook.audio.data.local.database.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing playlists.
 *
 * Provides offline-first access to playlists with reactive Flow API.
 */
@Singleton
class PlaylistRepository
    @Inject
    constructor(
        private val playlistDao: PlaylistDao,
    ) {
        /**
         * Gets the playlist for a book.
         * Returns Flow<Result<PlaylistEntity?>> for reactive updates.
         */
        fun getPlaylist(bookId: String): Flow<Result<PlaylistEntity?>> = playlistDao.getPlaylist(bookId).asResult()

        /**
         * Saves a playlist.
         */
        suspend fun savePlaylist(
            bookId: String,
            bookTitle: String,
            filePaths: List<String>,
            currentIndex: Int = 0,
        ): Result<Unit> =
            try {
                val filePathsJson = JSONArray(filePaths).toString()
                val entity =
                    PlaylistEntity(
                        bookId = bookId,
                        bookTitle = bookTitle,
                        filePaths = filePathsJson,
                        currentIndex = currentIndex,
                        lastUpdated = System.currentTimeMillis(),
                    )
                playlistDao.upsertPlaylist(entity)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(e)
            }

        /**
         * Parses file paths from JSON string.
         */
        fun parseFilePaths(filePathsJson: String): List<String> =
            try {
                val jsonArray = JSONArray(filePathsJson)
                (0 until jsonArray.length()).map { jsonArray.getString(it) }
            } catch (e: Exception) {
                emptyList()
            }

        /**
         * Deletes a playlist.
         */
        suspend fun deletePlaylist(bookId: String): Result<Unit> =
            try {
                playlistDao.deletePlaylist(bookId)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(e)
            }

        /**
         * Gets all playlists.
         */
        fun getAllPlaylists(): Flow<Result<List<PlaylistEntity>>> = playlistDao.getAllPlaylists().asResult()
    }
