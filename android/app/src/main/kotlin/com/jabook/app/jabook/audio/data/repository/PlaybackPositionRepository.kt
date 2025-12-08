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
import com.jabook.app.jabook.audio.data.local.dao.PlaybackPositionDao
import com.jabook.app.jabook.audio.data.local.database.entity.PlaybackPositionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing playback positions.
 *
 * Provides offline-first access to playback positions with reactive Flow API.
 */
@Singleton
class PlaybackPositionRepository
    @Inject
    constructor(
        private val positionDao: PlaybackPositionDao,
    ) {
        /**
         * Gets the playback position for a book.
         * Returns Flow<Result<PlaybackPositionEntity?>> for reactive updates.
         */
        fun getPosition(bookId: String): Flow<Result<PlaybackPositionEntity?>> = positionDao.getPosition(bookId).asResult()

        /**
         * Saves a playback position.
         */
        suspend fun savePosition(
            bookId: String,
            trackIndex: Int,
            position: Long,
        ): Result<Unit> =
            try {
                val entity =
                    PlaybackPositionEntity(
                        bookId = bookId,
                        trackIndex = trackIndex,
                        position = position,
                        lastUpdated = System.currentTimeMillis(),
                    )
                positionDao.upsertPosition(entity)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(e)
            }

        /**
         * Deletes a playback position.
         */
        suspend fun deletePosition(bookId: String): Result<Unit> =
            try {
                positionDao.deletePosition(bookId)
                Result.Success(Unit)
            } catch (e: Exception) {
                Result.Error(e)
            }

        /**
         * Gets all playback positions.
         */
        fun getAllPositions(): Flow<Result<List<PlaybackPositionEntity>>> = positionDao.getAllPositions().asResult()
    }
